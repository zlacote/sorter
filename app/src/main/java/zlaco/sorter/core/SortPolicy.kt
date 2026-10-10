package zlaco.sorter.core

/** A media row the sorter is considering, reduced to what it needs to judge. */
data class MediaCandidate(
    val id: Long,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val capturedAtMillis: Long,
)

/** Where sources are and where they go. Paths are MediaStore relative paths. */
data class SortConfig(
    val sourceFolders: Set<String>,
    val targetRoot: String,
    val includeScreenRecordings: Boolean,
)

enum class SkipReason {
    ALREADY_SORTED,
    ALREADY_HANDLED,
    NOT_IN_SOURCE,
    UNREADABLE_NAME,
    NOT_A_CAPTURE,
    NO_APP_LABEL,
    UNUSABLE_FOLDER_NAME,
    RECORDINGS_EXCLUDED,
}

sealed interface SortVerdict {
    data class Move(
        val appLabel: String,
        val folderName: String,
        val targetRelativePath: String,
    ) : SortVerdict

    data class Skip(val reason: SkipReason) : SortVerdict
}

/**
 * Decides what happens to one file. Pure: it reads no Android state and touches
 * nothing, so every rule below is covered by a unit test rather than by hoping.
 *
 * Two independent barriers stop the sorter from chasing its own tail, because
 * moving a file is itself a change the media store announces. A file already
 * sitting under the target root can never be a candidate again, and a file id
 * already in the journal is refused even if it somehow reappears in the source
 * folder. Either barrier alone would close the loop; keeping both means a bug in
 * one does not turn into an infinite move.
 */
object SortPolicy {

    fun decide(
        candidate: MediaCandidate,
        config: SortConfig,
        isHandled: (Long) -> Boolean,
    ): SortVerdict {
        val path = normalize(candidate.relativePath)
        val targetRoot = normalize(config.targetRoot)

        if (isUnder(path, targetRoot)) return SortVerdict.Skip(SkipReason.ALREADY_SORTED)
        if (isHandled(candidate.id)) return SortVerdict.Skip(SkipReason.ALREADY_HANDLED)
        if (config.sourceFolders.none { normalize(it) == path }) {
            return SortVerdict.Skip(SkipReason.NOT_IN_SOURCE)
        }

        val parsed = CaptureNames.parse(candidate.displayName)
            ?: return SortVerdict.Skip(SkipReason.UNREADABLE_NAME)

        // A name that carries a timestamp but no capture prefix is something
        // else that happens to sit in the screenshots folder, not a screenshot.
        // Refusing it here fails safe: if a future shell renames captures, the
        // files stay put and show up in the log instead of being misfiled.
        if (parsed.kind == CaptureKind.UNKNOWN) {
            return SortVerdict.Skip(SkipReason.NOT_A_CAPTURE)
        }

        if (parsed.kind == CaptureKind.SCREEN_RECORDING && !config.includeScreenRecordings) {
            return SortVerdict.Skip(SkipReason.RECORDINGS_EXCLUDED)
        }

        val label = parsed.appLabel ?: return SortVerdict.Skip(SkipReason.NO_APP_LABEL)
        val folder = FolderName.from(label) ?: return SortVerdict.Skip(SkipReason.UNUSABLE_FOLDER_NAME)

        return SortVerdict.Move(
            appLabel = label,
            folderName = folder,
            targetRelativePath = targetRoot + folder + "/",
        )
    }

    /** MediaStore reports `DCIM/Screenshots/`; user input may omit the slash. */
    fun normalize(relativePath: String): String {
        val trimmed = relativePath.trim().trim('/')
        return if (trimmed.isEmpty()) "" else "$trimmed/"
    }

    private fun isUnder(path: String, root: String): Boolean =
        root.isNotEmpty() && (path == root || path.startsWith(root))
}
