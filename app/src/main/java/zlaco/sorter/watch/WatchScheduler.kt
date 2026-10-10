package zlaco.sorter.watch

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Arms the two things that can wake the sorter.
 *
 * The content trigger is the one that does the real work. The media store keeps
 * a watcher on its own tables and starts the job when a new row appears, so
 * between screenshots the process is not merely idle, it does not exist. There is
 * no service, no notification and no polling loop burning battery to stay ready.
 *
 * The periodic worker exists only to catch what the trigger missed. There are
 * reports of the trigger going quiet on some devices after long sleep, so every
 * fifteen minutes the sorter takes one cheap look at the source folders and puts
 * right anything that slipped through. It is a safety net, not the mechanism.
 */
object WatchScheduler {

    const val JOB_ID = 4101

    /**
     * Wait this long after the last change before acting, so taking five
     * screenshots in a row causes one pass instead of five.
     */
    private const val SETTLE_DELAY_MS = 5_000L

    /** But never wait longer than this, so a steady trickle still gets moved. */
    private const val MAX_SETTLE_DELAY_MS = 30_000L

    private const val SAFETY_WORK_NAME = "sorter-safety-net"
    private const val SAFETY_PERIOD_MINUTES = 15L

    private const val TAG = "WatchScheduler"

    fun arm(context: Context) {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, SortJobService::class.java))
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                )
            )
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                )
            )
            .setTriggerContentUpdateDelay(SETTLE_DELAY_MS)
            .setTriggerContentMaxDelay(MAX_SETTLE_DELAY_MS)
            .build()

        runCatching { jobScheduler(context).schedule(job) }
            .onFailure { Log.w(TAG, "could not arm the content trigger", it) }

        armSafetyNet(context)
    }

    fun disarm(context: Context) {
        runCatching { jobScheduler(context).cancel(JOB_ID) }
        WorkManager.getInstance(context).cancelUniqueWork(SAFETY_WORK_NAME)
    }

    fun isArmed(context: Context): Boolean =
        runCatching { jobScheduler(context).allPendingJobs.any { it.id == JOB_ID } }.getOrDefault(false)

    private fun armSafetyNet(context: Context) {
        val request = PeriodicWorkRequestBuilder<SafetyNetWorker>(SAFETY_PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SAFETY_WORK_NAME,
            // Keep whatever is already scheduled. Replacing it on every arm would
            // reset the window and could starve it if the app is opened often.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun jobScheduler(context: Context): JobScheduler =
        context.getSystemService(JobScheduler::class.java)
}
