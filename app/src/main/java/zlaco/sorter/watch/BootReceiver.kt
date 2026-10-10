package zlaco.sorter.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import zlaco.sorter.data.SorterSettings

/**
 * Re-arms the trigger after the events that wipe scheduled jobs.
 *
 * A content trigger cannot be marked as persisted, so the job is gone after a
 * reboot or an app update. Without this the sorter would look enabled and quietly
 * do nothing, which is the worst failure mode it has: the user takes a
 * screenshot, sees it unsorted, and has no idea why.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> Unit

            else -> return
        }

        val enabled = SorterSettings(context).enabled
        Log.i(TAG, "re-arming after ${intent.action}, enabled=$enabled")
        if (enabled) WatchScheduler.arm(context) else WatchScheduler.disarm(context)
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
