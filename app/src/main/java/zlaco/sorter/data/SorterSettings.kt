package zlaco.sorter.data

import android.content.Context
import android.content.SharedPreferences
import zlaco.sorter.core.SortConfig

/**
 * User-visible choices. Defaults are the folders a One UI device actually uses;
 * everything the user can change is here rather than baked into the engine, so
 * a different shell is a settings change and not a code change.
 */
class SorterSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var targetRoot: String
        get() = prefs.getString(KEY_TARGET_ROOT, DEFAULT_TARGET_ROOT) ?: DEFAULT_TARGET_ROOT
        set(value) = prefs.edit().putString(KEY_TARGET_ROOT, value).apply()

    var sourceFolders: Set<String>
        get() = prefs.getStringSet(KEY_SOURCES, DEFAULT_SOURCES) ?: DEFAULT_SOURCES
        set(value) = prefs.edit().putStringSet(KEY_SOURCES, value).apply()

    fun sortConfig(): SortConfig = SortConfig(
        sourceFolders = sourceFolders,
        targetRoot = targetRoot,
    )

    /**
     * Outcome of the most recent pass, kept so the screen can answer "when did
     * this last work" without the user having to reproduce a screenshot to find
     * out. A job that silently stopped firing is otherwise invisible.
     */
    var lastRunAtMillis: Long
        get() = prefs.getLong(KEY_LAST_RUN_AT, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_RUN_AT, value).apply()

    var lastRunMoved: Int
        get() = prefs.getInt(KEY_LAST_MOVED, 0)
        private set(value) = prefs.edit().putInt(KEY_LAST_MOVED, value).apply()

    var lastRunScanned: Int
        get() = prefs.getInt(KEY_LAST_SCANNED, 0)
        private set(value) = prefs.edit().putInt(KEY_LAST_SCANNED, value).apply()

    var lastRunFailures: Int
        get() = prefs.getInt(KEY_LAST_FAILURES, 0)
        private set(value) = prefs.edit().putInt(KEY_LAST_FAILURES, value).apply()

    fun recordRun(moved: Int, scanned: Int, failures: Int) {
        prefs.edit()
            .putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
            .putInt(KEY_LAST_MOVED, moved)
            .putInt(KEY_LAST_SCANNED, scanned)
            .putInt(KEY_LAST_FAILURES, failures)
            .apply()
    }

    private companion object {
        const val NAME = "sorter"
        const val KEY_ENABLED = "enabled"
        const val KEY_TARGET_ROOT = "target_root"
        const val KEY_SOURCES = "source_folders"
        const val KEY_LAST_RUN_AT = "last_run_at"
        const val KEY_LAST_MOVED = "last_run_moved"
        const val KEY_LAST_SCANNED = "last_run_scanned"
        const val KEY_LAST_FAILURES = "last_run_failures"

        /** MediaStore relative paths: no leading slash, trailing slash required. */
        val DEFAULT_SOURCES = setOf("DCIM/Screenshots/", "DCIM/Screen recordings/")
        const val DEFAULT_TARGET_ROOT = "Pictures/AppScreenshots/"
    }
}
