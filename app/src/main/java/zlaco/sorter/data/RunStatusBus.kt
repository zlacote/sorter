package zlaco.sorter.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RunStatus(
    val atMillis: Long,
    val moved: Int,
    val scanned: Int,
    val failures: Int,
)

/**
 * Announces that a pass finished, wherever it was started from.
 *
 * The status line used to be refreshed only when the activity resumed or the
 * button was pressed. A screenshot sorted by the wake-up job while the app
 * sat open and idle therefore changed nothing on screen until the user left
 * and came back, which reads as the app not noticing its own work.
 *
 * Settings alone cannot fix that, because it is written by whichever component
 * ran the pass and read by another that has no idea a pass happened. A signal
 * in the process carries the news directly; the stored copy in settings stays
 * for the case where the pass ran in a process with no UI to tell.
 */
object RunStatusBus {

    private val _status = MutableStateFlow<RunStatus?>(null)
    val status: StateFlow<RunStatus?> = _status.asStateFlow()

    fun publish(status: RunStatus) {
        _status.value = status
    }
}
