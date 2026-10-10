package zlaco.sorter.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zlaco.sorter.core.SkipReason
import zlaco.sorter.core.SortPolicy
import zlaco.sorter.core.SortVerdict
import zlaco.sorter.data.JournalStore
import zlaco.sorter.data.MediaStoreGateway
import zlaco.sorter.data.MoveOutcome
import zlaco.sorter.data.MoveRecord
import zlaco.sorter.data.RunStatus
import zlaco.sorter.data.RunStatusBus
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
        val status = RunStatus(
            atMillis = System.currentTimeMillis(),
            moved = report.movedCount,
            scanned = report.scanned,
            failures = report.failures.size,
        )
        settings.recordRun(status)
        RunStatusBus.publish(status)
        return report
    }

    /**
     * Serialised process-wide, not per engine instance: the wake-up job, the
     * safety net and the button each build their own engine, and all three can
     * fire at once. Two passes reading the journal at the same moment would
     * both see a file as unhandled and both try to move it, so the second would
     * fail on a row the first had already moved. Queuing also makes the button
     * honest: it waits for the background pass to finish and then reports what
     * is left rather than racing it.
     */
    suspend fun run(): RunReport = passMutex.withLock { runPass() }

    private suspend fun runPass(): RunReport = withContext(Dispatchers.IO) {
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
                    // The file has already moved by this point. A journal write
                    // that fails must not abort the pass or drop the rest of
                    // the batch, so it is contained here rather than allowed to
                    // travel up and cancel work that already succeeded.
                    runCatching { journal.record(record) }
                        .onFailure { Log.w(TAG, "could not journal ${record.displayName}", it) }
                    moved += record
                }

                is MoveOutcome.Refused -> failures += "${candidate.displayName}: ${outcome.reason}"
                is MoveOutcome.Failed -> failures += "${candidate.displayName}: ${outcome.error}"
            }
        }

        RunReport(moved = moved, skipped = skipped, failures = failures, scanned = candidates.size)
    }

    /** Puts a file back where it came from. The journal entry is dropped only once it lands. */
    suspend fun undo(record: MoveRecord): Boolean = passMutex.withLock {
        withContext(Dispatchers.IO) {
            when (gateway.move(record.mediaId, record.mimeType, record.fromRelativePath)) {
                is MoveOutcome.Moved -> {
                    journal.forget(record.mediaId)
                    true
                }

                else -> false
            }
        }
    }

    fun recentMoves(limit: Int = 50): List<MoveRecord> = journal.recent(limit)

    fun settings(): SorterSettings = settings

    private companion object {
        const val TAG = "SortEngine"

        val passMutex = Mutex()
    }
}
