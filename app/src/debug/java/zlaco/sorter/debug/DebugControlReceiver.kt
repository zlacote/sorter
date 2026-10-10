package zlaco.sorter.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import zlaco.sorter.data.SorterSettings
import zlaco.sorter.domain.SortEngine
import zlaco.sorter.watch.WatchScheduler

/**
 * Automation hook, present in debug builds only.
 *
 * The emulator test has to prove that a file really moves on a real media
 * store, and it cannot do that by tapping coordinates. Without a way in, the
 * test would only be able to check that the app starts, which is the part that
 * was never at risk.
 *
 * This lives in the debug source set, so it is merged into the debug APK and is
 * absent from release entirely.
 */
class DebugControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ENABLE -> {
                SorterSettings(context).enabled = true
                WatchScheduler.arm(context)
                Log.i(TAG, "debug: enabled and armed")
            }

            ACTION_RUN -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val report = SortEngine(context).runAndRecord()
                        Log.i(
                            TAG,
                            "debug: pass scanned=${report.scanned} moved=${report.movedCount} " +
                                "failures=${report.failures.size}",
                        )
                    } catch (error: Throwable) {
                        Log.e(TAG, "debug: pass failed", error)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_ENABLE = "zlaco.sorter.debug.ENABLE"
        const val ACTION_RUN = "zlaco.sorter.debug.RUN"
        private const val TAG = "DebugControl"
    }
}
