package zlaco.sorter.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import zlaco.sorter.core.MediaCandidate
import zlaco.sorter.core.SortPolicy

/** A file that was moved, kept so the move can be undone. */
data class MoveRecord(
    val mediaId: Long,
    val displayName: String,
    val mimeType: String,
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
 *
 * Reads go through the Images and Video collections rather than the Files
 * collection. Under scoped storage the Files collection is filtered down to the
 * files this app created, which for a sorter means everything it needs is
 * invisible. The two well-defined collections show the whole library, and are
 * also the only ones the provider accepts a move under.
 */
class MediaStoreGateway(private val resolver: ContentResolver) {

    /** Every row sitting directly inside one of the source folders. */
    fun candidatesIn(folders: Set<String>): List<MediaCandidate> {
        val normalized = folders.map(SortPolicy::normalize).filter { it.isNotEmpty() }
        if (normalized.isEmpty()) return emptyList()

        // A media store query is filtered rather than refused when the caller
        // lacks access: it simply returns nothing and throws nothing. Reporting
        // the grant alongside the count is the only way to tell "no screenshots
        // there" apart from "not allowed to look".
        Log.d(TAG, "allFilesAccess=${Environment.isExternalStorageManager()} folders=$normalized")

        val selection = normalized.joinToString(
            separator = " OR ",
            prefix = "(",
            postfix = ")",
        ) { "${MediaStore.MediaColumns.RELATIVE_PATH}=?" }
        val args = normalized.toTypedArray()
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        return COLLECTIONS.flatMap { collection ->
            val rows = mutableListOf<MediaCandidate>()
            runCatching {
                resolver.query(collection, PROJECTION, selection, args, sortOrder)?.use { cursor ->
                    while (cursor.moveToNext()) rows += cursor.toCandidate()
                }
            }.onFailure { Log.w(TAG, "query of $collection failed", it) }
            Log.d(TAG, "query of $collection returned ${rows.size} row(s) in $normalized")
            rows
        }
    }

    fun move(mediaId: Long, mimeType: String, targetRelativePath: String): MoveOutcome {
        val uri = ContentUris.withAppendedId(collectionFor(mimeType), mediaId)

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

    /**
     * The move has to address the same collection the row was read from, since
     * the provider refuses to move anything addressed outside the well-defined
     * collections.
     */
    private fun collectionFor(mimeType: String): Uri =
        if (mimeType.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }

    private fun Cursor.toCandidate(): MediaCandidate {
        val pathIndex = getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val nameIndex = getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeIndex = getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val dateIndex = getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        return MediaCandidate(
            id = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID)),
            displayName = if (nameIndex >= 0) getString(nameIndex).orEmpty() else "",
            relativePath = if (pathIndex >= 0) getString(pathIndex).orEmpty() else "",
            mimeType = if (mimeIndex >= 0) getString(mimeIndex).orEmpty() else "",
            capturedAtMillis = if (dateIndex >= 0) getLong(dateIndex) else 0L,
        )
    }

    private companion object {
        const val TAG = "MediaStoreGateway"

        val COLLECTIONS = listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        )

        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
        )
    }
}
