package zlaco.sorter.core

/**
 * Turns an application name into a folder name that is safe to write to disk.
 *
 * The name comes from a file name the shell produced, so it is untrusted input:
 * it may contain separators, control characters or names no filesystem accepts.
 * A label that sanitises down to nothing yields null, and the caller leaves the
 * file where it is rather than inventing a folder for it.
 */
object FolderName {

    private const val MAX_LENGTH = 100

    private val UNSAFE = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val WHITESPACE = Regex("\\s+")

    fun from(appLabel: String): String? {
        val cleaned = WHITESPACE
            .replace(UNSAFE.replace(appLabel, "_"), " ")
            .trim()
            .trim('.', ' ')
        if (isUnusable(cleaned)) return null
        return truncate(cleaned, MAX_LENGTH)
    }

    private fun isUnusable(name: String): Boolean =
        name.isEmpty() || name == "." || name == ".."

    /**
     * Truncates without splitting a surrogate pair, which would leave a lone
     * surrogate in the path and make the move fail on the device rather than in
     * a test.
     */
    private fun truncate(name: String, limit: Int): String? {
        if (name.length <= limit) return name
        val cut = name.take(limit)
        val safe = if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
        val trimmed = safe.trimEnd().trim('.', ' ')
        return trimmed.takeIf { !isUnusable(it) }
    }
}
