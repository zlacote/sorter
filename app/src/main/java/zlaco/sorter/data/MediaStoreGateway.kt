package zlaco.sorter.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import zlaco.sorter.core.MediaCandidate
import zlaco.sorter.core.SortPolicy

/** A file that was moved, kept so the move can be undone. */
data class MoveRecord(
    val mediaId: Long,
    val displayName: String,
    val appLabel: String,
    val fromRelativePath: String,
    val toRelativePath: String,
    val movedAtMillis: Long,
)

sealed interface MoveOutcome {
    data object Moved : MoveOutcome
    data class Refused(val reason: String) : MoveOutcome
    data class Failed(val error: String) : MoveOutcome
}

/**
 * Reads and moves media through MediaStore.
 *
 * Movement is done by updating RELATIVE_PATH rather than by touching the file
 * system directly. MediaStore performs the move itself, which keeps its own
 * index and the gallery's view of the file consistent. Doing it by hand instead
 * moves bytes that the database does not know about, and the gallery then shows
 * a file that no longer exists.
 */
class MediaStoreGateway(private val resolver: ContentResolver) {

    /** Every row sitting directly inside one of the source folders. */
    fun candidatesIn(folders: Set<String>): List<MediaCandidate> {
        val normalized = folders.map(SortPolicy::normalize).filter { it.isNotEmpty() }
        if (normalized.isEmpty()) return emptyList()

        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val selection = normalized.joinToString(
            separator = " OR ",
            prefix = "(",
            postfix = ")",
        ) { "${MediaStore.MediaColumns.RELATIVE_PATH}=?" }

        val rows = mutableListOf<MediaCandidate>()
        runCatching {
            resolver.query(
                collection,
                PROJECTION,
                selection,
                normalized.toTypedArray(),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) rows += cursor.toCandidate()
            }
        }.onFailure { Log.w(TAG, "candidate query failed", it) }
        return rows
    }

    fun move(mediaId: Long, targetRelativePath: String): MoveOutcome {
        val uri = itemUri(mediaId)
            ?: return MoveOutcome.Failed("media row $mediaId is no longer present")

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }

        return try {
            // Must address the single item, not the collection: MediaStore only
            // raises the recoverable-permission error per file, and a
            // collection-wide update silently matches nothing instead.
            val updated = resolver.update(uri, values, null, null)
            if (updated > 0) MoveOutcome.Moved else MoveOutcome.Refused("media store refused the move")
        } catch (error: SecurityException) {
            Log.w(TAG, "move of $mediaId rejected for want of permission", error)
            MoveOutcome.Failed("permission denied")
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "move of $mediaId rejected by media store", error)
            MoveOutcome.Refused(error.message ?: "media store rejected the path")
        } catch (error: Exception) {
            Log.w(TAG, "move of $mediaId failed", error)
            MoveOutcome.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private fun itemUri(mediaId: Long): Uri? = runCatching {
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), mediaId)
    }.getOrNull()

    private fun Cursor.toCandidate(): MediaCandidate {
        val pathIndex = getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val nameIndex = getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val dateIndex = getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        return MediaCandidate(
            id = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID)),
            displayName = if (nameIndex >= 0) getString(nameIndex).orEmpty() else "",
            relativePath = if (pathIndex >= 0) getString(pathIndex).orEmpty() else "",
            capturedAtMillis = if (dateIndex >= 0) getLong(dateIndex) else 0L,
        )
    }

    private companion object {
        const val TAG = "MediaStoreGateway"

        /**
         * The Files collection rather than Images or Video: one query then
         * covers captures of both kinds, and the source folder already narrows
         * it to what belongs there.
         */
        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_TAKEN,
        )
    }
}
