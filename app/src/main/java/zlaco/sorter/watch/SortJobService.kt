package zlaco.sorter.watch

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import zlaco.sorter.data.SorterSettings
import zlaco.sorter.domain.SortEngine

/**
 * Runs one sorting pass, then re-arms itself.
 *
 * A content trigger is consumed when it fires, so a new one has to be scheduled
 * before the next capture. That scheduling has to happen *after* the pass, not
 * before it, and the reason is sharp: `JobScheduler.schedule()` replaces any job
 * with the same id and stops it if it happens to be running. Re-arming on the
 * way in therefore stops this very job, which delivers `onStopJob` and tears
 * down the scope the pass was about to launch on. The result is a job that
 * reliably cancels itself, moves a few files if it was lucky, and never becomes
 * armed again, so the next screenshot does nothing.
 *
 * A capture that lands while the pass is running is not lost: the next trigger
 * or, failing that, the fifteen minute safety net picks it up. Each pass works
 * from a rescan rather than from a notification, so "not seen this time" costs
 * a delay and nothing else.
 */
class SortJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopping = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopping = false
        scope.launch {
            try {
                execute()
            } finally {
                if (stopping) return@launch
                // Finished before re-arming: the job must not be running when
                // the replacement is scheduled.
                runCatching { jobFinished(params, false) }
                WatchScheduler.arm(applicationContext)
            }
        }
        return true
    }

    /**
     * Returning true asks the scheduler to bring the job back, which re-arms
     * the trigger along with it. The pass in flight is deliberately not
     * cancelled: it is short, and half-finished work is worse than a pass that
     * overstays its welcome.
     */
    override fun onStopJob(params: JobParameters): Boolean {
        stopping = true
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
