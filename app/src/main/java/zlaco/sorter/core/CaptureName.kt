package zlaco.sorter.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What produced a file, as far as the file name reveals. */
enum class CaptureKind { SCREENSHOT, SCREEN_RECORDING, UNKNOWN }

/**
 * A capture file name broken into the parts that matter for sorting.
 *
 * [appLabel] is null when the name carries no application name at all, which is
 * what happens for screenshots taken on the lock screen: the shell refuses to
 * name the foreground app there to avoid leaking it. Such files are left alone.
 */
data class CaptureName(
    val kind: CaptureKind,
    val appLabel: String?,
    val capturedAtMillis: Long?,
)

/**
 * Reader for the file names device shells produce for screen captures.
 *
 * Two shapes exist in the wild and both are anchored on the same fixed-width
 * timestamp, which is what makes the split unambiguous:
 *
 *     Screenshot_20261010_064921_Soul.png              app label present
 *     Screen_Recording_20260928_220747_Brawl Stars.mp4 app label present
 *     Screenshot_20201215-090626.png                   no app label (stock)
 *
 * Because the timestamp is always 8 digits, an underscore, 6 digits, the label
 * is simply whatever follows it. That keeps app names containing spaces or
 * underscores intact instead of guessing where the name stops.
 */
object CaptureNames {

    private val PATTERN = Regex(
        "^(?<prefix>.*?)_" +
            "(?<year>\\d{4})(?<month>\\d{2})(?<day>\\d{2})[-_]" +
            "(?<hour>\\d{2})(?<minute>\\d{2})(?<second>\\d{2})" +
            "_?(?<label>.*)$"
    )

    private const val EXTENSION_LIMIT = 6

    /** Returns null when the name is not a capture name at all. */
    fun parse(displayName: String): CaptureName? {
        val base = stripExtension(displayName)
        val match = PATTERN.matchEntire(base) ?: return null
        val groups = match.groups

        val capturedAt = readInstant(
            year = groups["year"]?.value?.toInt() ?: return null,
            month = groups["month"]?.value?.toInt() ?: return null,
            day = groups["day"]?.value?.toInt() ?: return null,
            hour = groups["hour"]?.value?.toInt() ?: return null,
            minute = groups["minute"]?.value?.toInt() ?: return null,
            second = groups["second"]?.value?.toInt() ?: return null,
        ) ?: return null

        return CaptureName(
            kind = readKind(groups["prefix"]?.value.orEmpty()),
            appLabel = readLabel(groups["label"]?.value.orEmpty()),
            capturedAtMillis = capturedAt,
        )
    }

    private fun stripExtension(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        if (dot <= 0) return displayName
        val extension = displayName.substring(dot + 1)
        if (extension.isEmpty() || extension.length > EXTENSION_LIMIT) return displayName
        if (!extension.all { it.isLetterOrDigit() }) return displayName
        return displayName.substring(0, dot)
    }

    /**
     * A stamp only counts when it describes a real moment. Without this check a
     * name such as `Screenshot_99999999_999999_x.png` would parse happily and
     * be moved into a folder on the strength of digits.
     */
    private fun readInstant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long? {
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.of(hour, minute, second) }.getOrNull() ?: return null
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun readKind(prefix: String): CaptureKind {
        val normalized = prefix.lowercase()
        return when {
            normalized.contains("record") -> CaptureKind.SCREEN_RECORDING
            normalized.contains("screenshot") -> CaptureKind.SCREENSHOT
            else -> CaptureKind.UNKNOWN
        }
    }

    /**
     * A label made only of digits is a leftover from a malformed name rather
     * than an application name, so it is treated as absent.
     */
    private fun readLabel(raw: String): String? {
        val label = raw.trim().trim('_', '.', ' ')
        if (label.isEmpty()) return null
        if (label.all { it.isDigit() }) return null
        return label
    }
}
