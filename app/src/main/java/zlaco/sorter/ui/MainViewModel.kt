package zlaco.sorter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zlaco.sorter.core.SortPolicy
import zlaco.sorter.data.MoveRecord
import zlaco.sorter.data.StoragePermissions
import zlaco.sorter.domain.SortEngine
import zlaco.sorter.watch.WatchScheduler

data class LastRun(
    val atMillis: Long,
    val moved: Int,
    val scanned: Int,
    val failures: Int,
)

sealed interface Notice {
    data class Moved(val count: Int) : Notice

    /**
     * Nothing to do now, but the journal says files were sorted earlier. Saying
     * only "nothing to move" here reads as a failure when the app is in fact
     * working, which is the worst possible thing for a background tool to do.
     */
    data object AlreadySorted : Notice
    data object NothingToDo : Notice
    data object NeedsPermission : Notice
    data object UndoFailed : Notice
}

data class UiState(
    val enabled: Boolean = false,
    val hasPermission: Boolean = false,
    val isArmed: Boolean = false,
    val includeRecordings: Boolean = true,
    val isWorking: Boolean = false,
    val lastRun: LastRun? = null,
    val moves: List<MoveRecord> = emptyList(),
    val sourceFolders: List<String> = emptyList(),
    val targetRoot: String = "",
    val notice: Notice? = null,
) {
    /**
     * Permission buttons only appear when something is actually missing. On a
     * correctly set up phone the screen is a single switch and nothing else
     * shouting at the user.
     */
    val showPermissionAction: Boolean get() = !hasPermission
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = SortEngine(application)
    private val settings = engine.settings()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Called on every resume, because the permission is granted in the system
     * settings and comes back as a result, not as a callback.
     *
     * This is also where a missed arm heals itself: if the app is switched on,
     * the permission is granted, but no job is scheduled, it is scheduled now
     * rather than leaving the app looking healthy and sorting nothing.
     */
    fun refresh() {
        viewModelScope.launch {
            val hasPermission = StoragePermissions.hasAllFilesAccess(getApplication())
            val enabled = settings.enabled
            val armed = enabled && WatchScheduler.isArmed(getApplication())

            if (enabled && hasPermission && !armed) {
                WatchScheduler.arm(getApplication())
            }

            _state.update {
                it.copy(
                    enabled = enabled,
                    hasPermission = hasPermission,
                    isArmed = enabled && hasPermission,
                    includeRecordings = settings.includeScreenRecordings,
                    sourceFolders = settings.sourceFolders.sorted(),
                    targetRoot = settings.targetRoot,
                    lastRun = settings.lastRunAtMillis
                        .takeIf { it > 0L }
                        ?.let { at ->
                            LastRun(
                                atMillis = at,
                                moved = settings.lastRunMoved,
                                scanned = settings.lastRunScanned,
                                failures = settings.lastRunFailures,
                            )
                        },
                    moves = engine.recentMoves(),
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.enabled = enabled
            if (enabled && StoragePermissions.hasAllFilesAccess(getApplication())) {
                WatchScheduler.arm(getApplication())
            } else if (!enabled) {
                WatchScheduler.disarm(getApplication())
            }
            refresh()
        }
    }

    fun setIncludeRecordings(include: Boolean) {
        viewModelScope.launch {
            settings.includeScreenRecordings = include
            refresh()
        }
    }

    /** One pass over everything already on the phone, independent of the switch. */
    fun sortNow() {
        if (!StoragePermissions.hasAllFilesAccess(getApplication())) {
            _state.update { it.copy(notice = Notice.NeedsPermission) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, notice = null) }
            val report = engine.runAndRecord()
            val history = engine.recentMoves()
            _state.update {
                it.copy(
                    isWorking = false,
                    moves = history,
                    notice = when {
                        report.movedCount > 0 -> Notice.Moved(report.movedCount)
                        // Auto-sorting may well have done this work before the
                        // button was ever pressed. Report that, not a failure.
                        history.isNotEmpty() -> Notice.AlreadySorted
                        else -> Notice.NothingToDo
                    },
                )
            }
            refresh()
        }
    }

    /**
     * Adds a source folder. The value is normalised the same way the query
     * normalises it, so a user typing `DCIM/Screenshots` and one typing
     * `DCIM/Screenshots/` end up as the same folder rather than one silently
     * matching nothing.
     */
    fun addSourceFolder(raw: String): Boolean {
        val normalized = SortPolicy.normalize(raw)
        if (normalized.isEmpty()) return false
        val current = settings.sourceFolders
        if (normalized in current) return false
        settings.sourceFolders = current + normalized
        refresh()
        return true
    }

    fun removeSourceFolder(folder: String) {
        settings.sourceFolders = settings.sourceFolders - folder
        refresh()
    }

    fun setTargetRoot(raw: String): Boolean {
        val normalized = SortPolicy.normalize(raw)
        if (normalized.isEmpty()) return false
        settings.targetRoot = normalized
        refresh()
        return true
    }

    /** True when the edited text is already what is stored, so the field can reset. */
    fun isTargetRoot(raw: String): Boolean = SortPolicy.normalize(raw) == settings.targetRoot

    fun undo(record: MoveRecord) {
        viewModelScope.launch {
            val restored = engine.undo(record)
            _state.update {
                it.copy(
                    moves = engine.recentMoves(),
                    notice = if (restored) null else Notice.UndoFailed,
                )
            }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }
}
