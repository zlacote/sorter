package zlaco.sorter.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
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

            // Pushing a file through adb does not reliably land it in the media
            // index, and a file the store does not know about is invisible to
            // the engine, so the test would be measuring the shell rather than
            // the sorter. This asks the platform to index it properly.
            ACTION_SCAN -> {
                val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
                if (path.isEmpty()) {
                    Log.w(TAG, "debug: SCAN without a path")
                    return
                }
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        MediaScannerConnection.scanFile(context, arrayOf(path), null) { scanned, uri ->
                            Log.i(TAG, "debug: scanned $scanned -> $uri")
                        }
                    } catch (error: Throwable) {
                        Log.e(TAG, "debug: scan of $path failed", error)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_RUN -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val report = SortEngine(context).runAndRecord()
                        Log.i(
                            TAG,
                            "debug: pass scanned=${report.scanned} moved=${report.movedCount} " +
                                "skipped=${report.skipped} failures=${report.failures}",
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
        const val ACTION_SCAN = "zlaco.sorter.debug.SCAN"
        const val ACTION_RUN = "zlaco.sorter.debug.RUN"
        const val EXTRA_PATH = "path"
        private const val TAG = "DebugControl"
    }
}
