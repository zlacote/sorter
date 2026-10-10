package zlaco.sorter.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zlaco.sorter.core.SkipReason
import zlaco.sorter.core.SortPolicy
import zlaco.sorter.core.SortVerdict
import zlaco.sorter.data.JournalStore
import zlaco.sorter.data.MediaStoreGateway
import zlaco.sorter.data.MoveOutcome
import zlaco.sorter.data.MoveRecord
import zlaco.sorter.data.SorterSettings

data class RunReport(
    val moved: List<MoveRecord>,
    val skipped: Map<SkipReason, Int>,
    val failures: List<String>,
    val scanned: Int,
) {
    val movedCount: Int get() = moved.size
}

/**
 * Runs one pass over the source folders.
 *
 * The pass is driven by a rescan of what is actually on disk rather than by the
 * list of files a change notification happened to mention. A notification lost
 * to a killed process or a missed wake therefore costs nothing: the next pass
 * sees the file anyway. Notifications only decide *when* a pass happens, never
 * *what* it acts on.
 */
class SortEngine(context: Context) {

    private val appContext = context.applicationContext
    private val gateway = MediaStoreGateway(appContext.contentResolver)
    private val journal = JournalStore(appContext)
    private val settings = SorterSettings(appContext)

    /**
     * The one entry point every caller uses. Running and recording the outcome
     * are deliberately not separable: a pass that does not update the status
     * would leave the screen lying about when the sorter last worked.
     */
    suspend fun runAndRecord(): RunReport {
        val report = run()
        settings.recordRun(
            moved = report.movedCount,
            scanned = report.scanned,
            failures = report.failures.size,
        )
        return report
    }

    suspend fun run(): RunReport = withContext(Dispatchers.IO) {
        val config = settings.sortConfig()
        val handled = journal.handledIds()
        val candidates = gateway.candidatesIn(config.sourceFolders)

        val moved = mutableListOf<MoveRecord>()
        val skipped = mutableMapOf<SkipReason, Int>()
        val failures = mutableListOf<String>()

        for (candidate in candidates) {
            val verdict = SortPolicy.decide(candidate, config) { it in handled }
            if (verdict is SortVerdict.Skip) {
                skipped.merge(verdict.reason, 1, Int::plus)
                continue
            }
            val plan = (verdict as SortVerdict.Move)

            when (val outcome = gateway.move(candidate.id, candidate.mimeType, plan.targetRelativePath)) {
                is MoveOutcome.Moved -> {
                    val record = MoveRecord(
                        mediaId = candidate.id,
                        displayName = candidate.displayName,
                        mimeType = candidate.mimeType,
                        appLabel = plan.appLabel,
                        fromRelativePath = candidate.relativePath,
                        toRelativePath = plan.targetRelativePath,
                        movedAtMillis = System.currentTimeMillis(),
                    )
                    journal.record(record)
                    moved += record
                }

                is MoveOutcome.Refused -> failures += "${candidate.displayName}: ${outcome.reason}"
                is MoveOutcome.Failed -> failures += "${candidate.displayName}: ${outcome.error}"
            }
        }

        RunReport(moved = moved, skipped = skipped, failures = failures, scanned = candidates.size)
    }

    /** Puts a file back where it came from. The journal entry is dropped only once it lands. */
    suspend fun undo(record: MoveRecord): Boolean = withContext(Dispatchers.IO) {
        when (gateway.move(record.mediaId, record.mimeType, record.fromRelativePath)) {
            is MoveOutcome.Moved -> {
                journal.forget(record.mediaId)
                true
            }

            else -> false
        }
    }

    fun recentMoves(limit: Int = 50): List<MoveRecord> = journal.recent(limit)

    fun settings(): SorterSettings = settings
}
