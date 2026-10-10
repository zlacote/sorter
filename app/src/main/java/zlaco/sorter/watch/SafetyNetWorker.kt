package zlaco.sorter.watch

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import zlaco.sorter.data.SorterSettings
import zlaco.sorter.domain.SortEngine

/**
 * The safety net under the content trigger.
 *
 * It runs every fifteen minutes, does one indexed query of the source folders
 * and moves whatever is still sitting there. On a healthy device it finds
 * nothing and costs a few milliseconds. Its whole purpose is the device where
 * the trigger stops firing until the app is opened by hand: without it, files
 * taken during a long sleep would sit unsorted indefinitely.
 */
class SafetyNetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SorterSettings(applicationContext)
        if (!settings.enabled) return Result.success()

        return runCatching {
            SortEngine(applicationContext).runAndRecord()
            Result.success()
        }.getOrElse {
            Log.w(TAG, "safety net pass failed", it)
            Result.success()
        }
    }

    private companion object {
        const val TAG = "SafetyNetWorker"
    }
}
