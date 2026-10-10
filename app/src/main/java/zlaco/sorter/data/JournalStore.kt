package zlaco.sorter.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Durable record of what was moved and where from.
 *
 * It does two jobs. It is the second of the two barriers that stop the sorter
 * re-moving its own output, and it is what makes a move reversible: without a
 * record of the original folder there would be no way back.
 */
class JournalStore(context: Context) {

    private val helper = object : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COLUMN_ID INTEGER PRIMARY KEY,
                    $COLUMN_NAME TEXT NOT NULL,
                    $COLUMN_MIME TEXT NOT NULL,
                    $COLUMN_APP TEXT NOT NULL,
                    $COLUMN_FROM TEXT NOT NULL,
                    $COLUMN_TO TEXT NOT NULL,
                    $COLUMN_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_${TABLE}_$COLUMN_AT ON $TABLE ($COLUMN_AT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v2 added mime_type, which undo needs to address the same
            // collection the row was read from. Adding the column keeps the
            // history: dropping the table would empty the list on upgrade and
            // look exactly like the recording bug it was meant to fix. A row
            // that predates the column defaults to the images collection, which
            // is where a screenshot is.
            if (oldVersion < 2) {
                runCatching {
                    db.execSQL(
                        "ALTER TABLE $TABLE ADD COLUMN $COLUMN_MIME TEXT NOT NULL DEFAULT ''"
                    )
                }
            }
        }
    }

    /** Ids already handled, so a re-scan of the same rows costs nothing. */
    fun handledIds(): Set<Long> = read { db ->
        buildSet {
            db.rawQuery("SELECT $COLUMN_ID FROM $TABLE", null).use { cursor ->
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }
    }

    fun record(move: MoveRecord) = write { db ->
        db.insertWithOnConflict(
            TABLE,
            null,
            ContentValues().apply {
                put(COLUMN_ID, move.mediaId)
                put(COLUMN_NAME, move.displayName)
                put(COLUMN_MIME, move.mimeType)
                put(COLUMN_APP, move.appLabel)
                put(COLUMN_FROM, move.fromRelativePath)
                put(COLUMN_TO, move.toRelativePath)
                put(COLUMN_AT, move.movedAtMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    fun recent(limit: Int): List<MoveRecord> = read { db ->
        buildList {
            db.query(
                TABLE,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_AT DESC",
                limit.toString(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(
                        MoveRecord(
                            mediaId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            displayName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIME)),
                            appLabel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_APP)),
                            fromRelativePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FROM)),
                            toRelativePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TO)),
                            movedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_AT)),
                        )
                    )
                }
            }
        }
    }

    fun find(mediaId: Long): MoveRecord? = read { db ->
        db.query(
            TABLE,
            null,
            "$COLUMN_ID = ?",
            arrayOf(mediaId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@read null
            MoveRecord(
                mediaId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                mimeType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIME)),
                appLabel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_APP)),
                fromRelativePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FROM)),
                toRelativePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TO)),
                movedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_AT)),
            )
        }
    }

    fun forget(mediaId: Long) = write { db ->
        db.delete(TABLE, "$COLUMN_ID = ?", arrayOf(mediaId.toString()))
        Unit
    }

    // The helper owns the connection and keeps it open for the life of the
    // process. Closing it per call would tear down and rebuild the database on
    // every pass, and SQLiteDatabase is not a Closeable so the obvious use{}
    // would not compile anyway.
    private fun <T> read(block: (SQLiteDatabase) -> T): T = block(helper.readableDatabase)

    private fun <T> write(block: (SQLiteDatabase) -> T): T = block(helper.writableDatabase)

    private companion object {
        const val DATABASE_NAME = "sorter-journal.db"

        // 2 added mime_type. Bumped deliberately: an install over an earlier
        // build without it would otherwise keep the old schema under the same
        // version number, and every undo would then fail on a missing column.
        const val DATABASE_VERSION = 2
        const val TABLE = "moves"
        const val COLUMN_ID = "media_id"
        const val COLUMN_NAME = "display_name"
        const val COLUMN_MIME = "mime_type"
        const val COLUMN_APP = "app_label"
        const val COLUMN_FROM = "from_path"
        const val COLUMN_TO = "to_path"
        const val COLUMN_AT = "moved_at"
    }
}
