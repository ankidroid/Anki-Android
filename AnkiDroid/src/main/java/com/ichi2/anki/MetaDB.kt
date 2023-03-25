//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.ichi2.anki.model.WhiteboardPenColor
import com.ichi2.anki.model.WhiteboardPenColor.Companion.default
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Sound.SoundSide
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.lang.Exception
import java.lang.IllegalStateException
import java.util.regex.Pattern

/**
 * Used to store additional information besides what is stored in the deck itself.
 *
 *
 * Currently it used to store:
 *
 *  * The languages associated with questions and answers.
 *  * The state of the whiteboard.
 *  * The cached state of the widget.
 *
 */
@KotlinCleanup("see about lateinit")
@KotlinCleanup("IDE lint")
object MetaDB {
    /** The name of the file storing the meta-db.  */
    private const val DATABASE_NAME = "ankidroid.db"

    /** The Database Version, increase if you want updates to happen on next upgrade.  */
    private const val DATABASE_VERSION = 7
    // Possible values for the qa column of the languages table.
    /** The language refers to the question.  */
    const val LANGUAGES_QA_QUESTION = 0

    /** The language refers to the answer.  */
    const val LANGUAGES_QA_ANSWER = 1

    /** The language does not refer to either the question or answer.  */
    const val LANGUAGES_QA_UNDEFINED = 2

    /** The pattern used to remove quotes from file names.  */
    private val quotePattern = Pattern.compile("[\"']")

    /** The database object used by the meta-db.  */
    private var mMetaDb: SQLiteDatabase? = null

    /** Remove any pairs of quotes from the given text.  */
    private fun stripQuotes(textParam: String): String {
        var text = textParam
        val matcher = quotePattern.matcher(text)
        text = matcher.replaceAll("")
        return text
    }

    /** Open the meta-db  */
    @KotlinCleanup("scope function or lateinit db")
    private fun openDB(context: Context) {
        try {
            mMetaDb = context.openOrCreateDatabase(DATABASE_NAME, 0, null).let {
                if (it.needUpgrade(DATABASE_VERSION)) {
                    upgradeDB(it, DATABASE_VERSION)
                } else {
                    it
                }
            }
            Timber.v("Opening MetaDB")
        } catch (e: Exception) {
            Timber.e(e, "Error opening MetaDB ")
        }
    }

    /** Creating any table that missing and upgrading necessary tables.  */
    private fun upgradeDB(metaDb: SQLiteDatabase, databaseVersion: Int): SQLiteDatabase {
        Timber.i("MetaDB:: Upgrading Internal Database..")
        // if (mMetaDb.getVersion() == 0) {
        Timber.i("MetaDB:: Applying changes for version: 0")
        if (metaDb.version < 4) {
            metaDb.execSQL("DROP TABLE IF EXISTS languages;")
            metaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;")
        }

        // Create tables if not exist
        metaDb.execSQL(
            "CREATE TABLE IF NOT EXISTS languages (" + " _id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "did INTEGER NOT NULL, ord INTEGER, " + "qa INTEGER, " + "language TEXT)"
        )
        metaDb.execSQL(
            "CREATE TABLE IF NOT EXISTS smallWidgetStatus (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "due INTEGER NOT NULL, eta INTEGER NOT NULL)"
        )
        updateWidgetStatus(metaDb)
        updateWhiteboardState(metaDb)
        metaDb.version = databaseVersion
        Timber.i("MetaDB:: Upgrading Internal Database finished. New version: %d", databaseVersion)
        return metaDb
    }

    private fun updateWhiteboardState(metaDb: SQLiteDatabase) {
        val columnCount = DatabaseUtil.getTableColumnCount(metaDb, "whiteboardState")
        if (columnCount <= 0) {
            metaDb.execSQL(
                "CREATE TABLE IF NOT EXISTS whiteboardState (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "did INTEGER NOT NULL, state INTEGER, visible INTEGER, lightpencolor INTEGER, darkpencolor INTEGER, stylus INTEGER)"
            )
            return
        }
        if (columnCount < 4) {
            // Default to 1
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN visible INTEGER NOT NULL DEFAULT '1'")
            Timber.i("Added 'visible' column to whiteboardState")
        }
        if (columnCount < 5) {
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN lightpencolor INTEGER DEFAULT NULL")
            Timber.i("Added 'lightpencolor' column to whiteboardState")
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN darkpencolor INTEGER DEFAULT NULL")
            Timber.i("Added 'darkpencolor' column to whiteboardState")
        }
        if (columnCount < 7) {
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN stylus INTEGER")
            Timber.i("Added 'stylus mode' column to whiteboardState")
        }
    }

    private fun updateWidgetStatus(metaDb: SQLiteDatabase) {
        val columnCount = DatabaseUtil.getTableColumnCount(metaDb, "widgetStatus")
        if (columnCount > 0) {
            if (columnCount < 7) {
                metaDb.execSQL("ALTER TABLE widgetStatus " + "ADD COLUMN eta INTEGER NOT NULL DEFAULT '0'")
                metaDb.execSQL("ALTER TABLE widgetStatus " + "ADD COLUMN time INTEGER NOT NULL DEFAULT '0'")
            }
        } else {
            metaDb.execSQL(
                "CREATE TABLE IF NOT EXISTS widgetStatus (" + "deckId INTEGER NOT NULL PRIMARY KEY, " +
                    "deckName TEXT NOT NULL, " + "newCards INTEGER NOT NULL, " + "lrnCards INTEGER NOT NULL, " +
                    "dueCards INTEGER NOT NULL, " + "progress INTEGER NOT NULL, " + "eta INTEGER NOT NULL)"
            )
        }
    }

    /** Open the meta-db but only if it currently closed.  */
    private fun openDBIfClosed(context: Context) {
        if (!isDBOpen()) {
            openDB(context)
        }
    }

    /** Close the meta-db.  */
    fun closeDB() {
        if (isDBOpen()) {
            mMetaDb!!.close()
            mMetaDb = null
            Timber.d("Closing MetaDB")
        }
    }

    /** Reset the content of the meta-db, erasing all its content.  */
    fun resetDB(context: Context): Boolean {
        openDBIfClosed(context)
        try {
            mMetaDb!!.run {
                execSQL("DROP TABLE IF EXISTS languages;")
                Timber.i("MetaDB:: Resetting all language assignment")
                execSQL("DROP TABLE IF EXISTS whiteboardState;")
                Timber.i("MetaDB:: Resetting whiteboard state")
                execSQL("DROP TABLE IF EXISTS widgetStatus;")
                Timber.i("MetaDB:: Resetting widget status")
                execSQL("DROP TABLE IF EXISTS smallWidgetStatus;")
                Timber.i("MetaDB:: Resetting small widget status")
                execSQL("DROP TABLE IF EXISTS intentInformation;")
                Timber.i("MetaDB:: Resetting intentInformation")
                upgradeDB(this, DATABASE_VERSION)
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error resetting MetaDB ")
        }
        return false
    }

    /** Reset the language associations for all the decks and card models.  */
    fun resetLanguages(context: Context): Boolean {
        openDBIfClosed(context)
        try {
            Timber.i("MetaDB:: Resetting all language assignments")
            mMetaDb!!.run {
                execSQL("DROP TABLE IF EXISTS languages;")
                upgradeDB(this, DATABASE_VERSION)
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error resetting MetaDB ")
        }
        return false
    }

    /** Reset the widget status.  */
    fun resetWidget(context: Context): Boolean {
        openDBIfClosed(context)
        try {
            Timber.i("MetaDB:: Resetting widget status")
            mMetaDb!!.run {
                execSQL("DROP TABLE IF EXISTS widgetStatus;")
                execSQL("DROP TABLE IF EXISTS smallWidgetStatus;")
                upgradeDB(this, DATABASE_VERSION)
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error resetting widgetStatus and smallWidgetStatus")
        }
        return false
    }

    /**
     * Associates a language to a deck, model, and card model for a given type.
     *
     * @param qa the part of the card for which to store the association, [.LANGUAGES_QA_QUESTION],
     * [.LANGUAGES_QA_ANSWER], or [.LANGUAGES_QA_UNDEFINED]
     * @param language the language to associate, as a two-characters, lowercase string
     */
    fun storeLanguage(context: Context, did: DeckId, ord: Int, qa: SoundSide, language: String) {
        openDBIfClosed(context)
        try {
            if ("" == getLanguage(context, did, ord, qa)) {
                mMetaDb!!.execSQL(
                    "INSERT INTO languages (did, ord, qa, language) " + " VALUES (?, ?, ?, ?);",
                    arrayOf<Any>(
                        did,
                        ord,
                        qa.int,
                        language
                    )
                )
                Timber.v("Store language for deck %d", did)
            } else {
                mMetaDb!!.execSQL(
                    "UPDATE languages SET language = ? WHERE did = ? AND ord = ? AND qa = ?;",
                    arrayOf<Any>(
                        language,
                        did,
                        ord,
                        qa.int
                    )
                )
                Timber.v("Update language for deck %d", did)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error storing language in MetaDB ")
        }
    }

    /**
     * Returns the language associated with the given deck, model and card model, for the given type.
     *
     * @param qa the part of the card for which to store the association, [.LANGUAGES_QA_QUESTION],
     * [.LANGUAGES_QA_ANSWER], or [.LANGUAGES_QA_UNDEFINED] return the language associate with
     * the type, as a two-characters, lowercase string, or the empty string if no association is defined
     */
    fun getLanguage(context: Context, did: DeckId, ord: Int, qa: SoundSide): String {
        openDBIfClosed(context)
        var language = ""
        val query = "SELECT language FROM languages WHERE did = ? AND ord = ? AND qa = ? LIMIT 1"
        try {
            mMetaDb!!.rawQuery(
                query,
                arrayOf(
                    java.lang.Long.toString(did),
                    Integer.toString(ord),
                    Integer.toString(qa.int)
                )
            ).use { cur ->
                Timber.v("getLanguage: %s", query)
                if (cur.moveToNext()) {
                    language = cur.getString(0)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching language ")
        }
        return language
    }

    /**
     * Resets all the language associates for a given deck.
     *
     * @return whether an error occurred while resetting the language for the deck
     */
    fun resetDeckLanguages(context: Context, did: DeckId): Boolean {
        openDBIfClosed(context)
        try {
            mMetaDb!!.execSQL("DELETE FROM languages WHERE did = ?;", arrayOf(did))
            Timber.i("MetaDB:: Resetting language assignment for deck %d", did)
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error resetting deck language")
        }
        return false
    }

    /**
     * Returns the state of the whiteboard for the given deck.
     *
     * @return 1 if the whiteboard should be shown, 0 otherwise
     */
    fun getWhiteboardState(context: Context, did: DeckId): Boolean {
        openDBIfClosed(context)
        try {
            mMetaDb!!.rawQuery(
                "SELECT state FROM whiteboardState  WHERE did = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur -> return DatabaseUtil.getScalarBoolean(cur) }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard state from MetaDB ")
            return false
        }
    }

    /**
     * Stores the state of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param whiteboardState 1 if the whiteboard should be shown, 0 otherwise
     */
    fun storeWhiteboardState(context: Context, did: DeckId, whiteboardState: Boolean) {
        val state = if (whiteboardState) 1 else 0
        openDBIfClosed(context)
        try {
            val metaDb = mMetaDb!!
            metaDb.rawQuery(
                "SELECT _id FROM whiteboardState WHERE did = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur ->
                if (cur.moveToNext()) {
                    metaDb.execSQL(
                        "UPDATE whiteboardState SET did = ?, state=? WHERE _id=?;",
                        arrayOf<Any>(did, state, cur.getString(0))
                    )
                    Timber.d("Store whiteboard state (%d) for deck %d", state, did)
                } else {
                    metaDb.execSQL(
                        "INSERT INTO whiteboardState (did, state) VALUES (?, ?)",
                        arrayOf<Any>(did, state)
                    )
                    Timber.d("Store whiteboard state (%d) for deck %d", state, did)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error storing whiteboard state in MetaDB ")
        }
    }

    /**
     * Returns the state of the whiteboard stylus mode for the given deck.
     *
     * @return true if the whiteboard stylus mode should be enabled, false otherwise
     */
    fun getWhiteboardStylusState(context: Context, did: DeckId): Boolean {
        openDBIfClosed(context)
        try {
            mMetaDb!!.rawQuery(
                "SELECT stylus FROM whiteboardState WHERE did = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur -> return DatabaseUtil.getScalarBoolean(cur) }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard stylus mode state from MetaDB ")
            return false
        }
    }

    /**
     * Stores the state of the whiteboard stylus mode for a given deck.
     *
     * @param did deck id to store whiteboard stylus mode state for
     * @param whiteboardStylusState true if the whiteboard stylus mode should be enabled, false otherwise
     */
    fun storeWhiteboardStylusState(context: Context, did: DeckId, whiteboardStylusState: Boolean) {
        val state = if (whiteboardStylusState) 1 else 0
        openDBIfClosed(context)
        try {
            val metaDb = mMetaDb!!
            metaDb.rawQuery(
                "SELECT _id FROM whiteboardState WHERE did = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur ->
                if (cur.moveToNext()) {
                    metaDb.execSQL(
                        "UPDATE whiteboardState SET did = ?, stylus=? WHERE _id=?;",
                        arrayOf<Any>(did, state, cur.getString(0))
                    )
                    Timber.d("Store whiteboard stylus mode state (%d) for deck %d", state, did)
                } else {
                    metaDb.execSQL(
                        "INSERT INTO whiteboardState (did, stylus) VALUES (?, ?)",
                        arrayOf<Any>(did, state)
                    )
                    Timber.d("Store whiteboard stylus mode state (%d) for deck %d", state, did)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error storing whiteboard stylus mode state in MetaDB ")
        }
    }

    /**
     * Returns the state of the whiteboard for the given deck.
     *
     * @return 1 if the whiteboard should be shown, 0 otherwise
     */
    fun getWhiteboardVisibility(context: Context, did: DeckId): Boolean {
        openDBIfClosed(context)
        try {
            mMetaDb!!.rawQuery(
                "SELECT visible FROM whiteboardState WHERE did = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur -> return DatabaseUtil.getScalarBoolean(cur) }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard state from MetaDB ")
            return false
        }
    }

    /**
     * Stores the state of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param isVisible 1 if the whiteboard should be shown, 0 otherwise
     */
    fun storeWhiteboardVisibility(context: Context, did: DeckId, isVisible: Boolean) {
        val isVisibleState = if (isVisible) 1 else 0
        openDBIfClosed(context)
        try {
            val metaDb = mMetaDb!!
            metaDb.rawQuery(
                "SELECT _id FROM whiteboardState WHERE did  = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur ->
                if (cur.moveToNext()) {
                    metaDb.execSQL(
                        "UPDATE whiteboardState SET did = ?, visible= ?  WHERE _id=?;",
                        arrayOf<Any>(did, isVisibleState, cur.getString(0))
                    )
                    Timber.d("Store whiteboard visibility (%d) for deck %d", isVisibleState, did)
                } else {
                    metaDb.execSQL(
                        "INSERT INTO whiteboardState (did, visible) VALUES (?, ?)",
                        arrayOf<Any>(did, isVisibleState)
                    )
                    Timber.d("Store whiteboard visibility (%d) for deck %d", isVisibleState, did)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error storing whiteboard visibility in MetaDB ")
        }
    }

    /**
     * Returns the pen color of the whiteboard for the given deck.
     */
    fun getWhiteboardPenColor(context: Context, did: DeckId): WhiteboardPenColor {
        openDBIfClosed(context)
        try {
            mMetaDb!!.rawQuery(
                "SELECT lightpencolor, darkpencolor FROM whiteboardState WHERE did = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur ->
                cur.moveToFirst()
                val light = DatabaseUtil.getInteger(cur, 0)
                val dark = DatabaseUtil.getInteger(cur, 1)
                return WhiteboardPenColor(light, dark)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard pen color from MetaDB ")
            return default
        }
    }

    /**
     * Stores the pen color of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param isLight if dark mode is disabled
     * @param value The new color code to store
     */
    fun storeWhiteboardPenColor(context: Context, did: DeckId, isLight: Boolean, value: Int?) {
        openDBIfClosed(context)
        val columnName = if (isLight) "lightpencolor" else "darkpencolor"
        try {
            val metaDb = mMetaDb!!
            metaDb.rawQuery(
                "SELECT _id FROM whiteboardState WHERE did  = ?",
                arrayOf(java.lang.Long.toString(did))
            ).use { cur ->
                if (cur.moveToNext()) {
                    metaDb.execSQL(
                        "UPDATE whiteboardState SET did = ?, " +
                            columnName + "= ? " +
                            " WHERE _id=?;",
                        arrayOf<Any?>(did, value, cur.getString(0))
                    )
                } else {
                    val sql = "INSERT INTO whiteboardState (did, $columnName) VALUES (?, ?)"
                    metaDb.execSQL(sql, arrayOf<Any?>(did, value))
                }
                Timber.d("Store whiteboard %s (%d) for deck %d", columnName, value, did)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error storing whiteboard color in MetaDB")
        }
    }

    /**
     * Return the current status of the widget.
     *
     * @return [due, eta]
     */
    fun getWidgetSmallStatus(context: Context): IntArray {
        openDBIfClosed(context)
        var cursor: Cursor? = null
        try {
            cursor = mMetaDb!!.query(
                "smallWidgetStatus",
                arrayOf("due", "eta"),
                null,
                null,
                null,
                null,
                null
            )
            if (cursor.moveToNext()) {
                return intArrayOf(cursor.getInt(0), cursor.getInt(1))
            }
        } catch (e: SQLiteException) {
            Timber.e(e, "Error while querying widgetStatus")
        } finally {
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
        }
        return intArrayOf(0, 0)
    }

    fun getNotificationStatus(context: Context): Int {
        openDBIfClosed(context)
        var cursor: Cursor? = null
        val due = 0
        try {
            cursor =
                mMetaDb!!.query("smallWidgetStatus", arrayOf("due"), null, null, null, null, null)
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        } catch (e: SQLiteException) {
            Timber.e(e, "Error while querying widgetStatus")
        } finally {
            if (cursor != null && !cursor.isClosed) {
                cursor.close()
            }
        }
        return due
    }

    fun storeSmallWidgetStatus(context: Context, status: Pair<Int, Int>) {
        openDBIfClosed(context)
        try {
            val metaDb = mMetaDb!!
            metaDb.beginTransaction()
            try {
                // First clear all the existing content.
                metaDb.execSQL("DELETE FROM smallWidgetStatus")
                metaDb.execSQL(
                    "INSERT INTO smallWidgetStatus(due, eta) VALUES (?, ?)",
                    arrayOf<Any>(status.first, status.second)
                )
                metaDb.setTransactionSuccessful()
            } finally {
                metaDb.endTransaction()
            }
        } catch (e: IllegalStateException) {
            Timber.e(e, "MetaDB.storeSmallWidgetStatus: failed")
        } catch (e: SQLiteException) {
            Timber.e(e, "MetaDB.storeSmallWidgetStatus: failed")
            closeDB()
            Timber.i("MetaDB:: Trying to reset Widget: %b", resetWidget(context))
        }
    }

    fun close() {
        mMetaDb?.run {
            try {
                close()
            } catch (e: Exception) {
                Timber.w(e, "Failed to close MetaDB")
            }
        }
    }

    private object DatabaseUtil {
        fun getScalarBoolean(cur: Cursor): Boolean {
            return if (cur.moveToNext()) {
                cur.getInt(0) > 0
            } else {
                false
            }
        }

        // API LEVEL
        fun getTableColumnCount(metaDb: SQLiteDatabase, tableName: String): Int {
            var c: Cursor? = null
            return try {
                c = metaDb.rawQuery("PRAGMA table_info($tableName)", null)
                c.count
            } finally {
                c?.close()
            }
        }

        fun getInteger(cur: Cursor, columnIndex: Int): Int? {
            return if (cur.isNull(columnIndex)) null else cur.getInt(columnIndex)
        }
    }

    private fun isDBOpen() = mMetaDb?.isOpen == true
}
