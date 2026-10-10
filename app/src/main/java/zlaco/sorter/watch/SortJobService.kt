package zlaco.sorter.watch

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import zlaco.sorter.data.SorterSettings
import zlaco.sorter.domain.SortEngine

/**
 * Runs one sorting pass, then re-arms itself.
 *
 * Re-arming happens before the work starts, not after. A content trigger is
 * consumed when it fires, so a change that lands while the pass is running would
 * otherwise be missed. Arming first means that change is picked up by the next
 * job, and the settle delay keeps the two passes apart.
 *
 * The extra pass that our own moves cause is bounded and harmless: moving files
 * announces a change, that change wakes the job once more, and the second pass
 * finds nothing to do and announces nothing, which ends it.
 */
class SortJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        WatchScheduler.arm(applicationContext)
        scope.launch { execute() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.cancel()
        // The pass did not finish; let the scheduler call us again.
        return true
    }

    private suspend fun execute() {
        val settings = SorterSettings(applicationContext)
        if (!settings.enabled) return

        runCatching { SortEngine(applicationContext).runAndRecord() }
            .onFailure { Log.w(TAG, "sorting pass failed", it) }
    }

    private companion object {
        const val TAG = "SortJobService"
    }
}
