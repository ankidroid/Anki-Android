//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Pair;

import com.ichi2.anki.model.WhiteboardPenColor;
import com.ichi2.libanki.Sound;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * Used to store additional information besides what is stored in the deck itself.
 * <p>
 * Currently it used to store:
 * <ul>
 * <li>The languages associated with questions and answers.</li>
 * <li>The state of the whiteboard.</li>
 * <li>The cached state of the widget.</li>
 * </ul>
 */
public class MetaDB {
    /** The name of the file storing the meta-db. */
    private static final String DATABASE_NAME = "ankidroid.db";

    /** The Database Version, increase if you want updates to happen on next upgrade. */
    private static final int DATABASE_VERSION = 6;

    // Possible values for the qa column of the languages table.
    /** The language refers to the question. */
    public static final int LANGUAGES_QA_QUESTION = 0;
    /** The language refers to the answer. */
    public static final int LANGUAGES_QA_ANSWER = 1;
    /** The language does not refer to either the question or answer. */
    public static final int LANGUAGES_QA_UNDEFINED = 2;

    /** The pattern used to remove quotes from file names. */
    private static final Pattern quotePattern = Pattern.compile("[\"']");

    /** The database object used by the meta-db. */
    private static SQLiteDatabase mMetaDb = null;


    /** Remove any pairs of quotes from the given text. */
    private static String stripQuotes(String text) {
        Matcher matcher = quotePattern.matcher(text);
        text = matcher.replaceAll("");
        return text;
    }


    /** Open the meta-db */
    private static void openDB(Context context) {
        try {
            mMetaDb = context.openOrCreateDatabase(DATABASE_NAME, 0, null);
            if (mMetaDb.needUpgrade(DATABASE_VERSION)) {
                mMetaDb = upgradeDB(mMetaDb, DATABASE_VERSION);
            }
            Timber.v("Opening MetaDB");
        } catch (Exception e) {
            Timber.e(e, "Error opening MetaDB ");
        }
    }


    /** Creating any table that missing and upgrading necessary tables. */
    private static SQLiteDatabase upgradeDB(SQLiteDatabase metaDb, int databaseVersion) {
        Timber.i("MetaDB:: Upgrading Internal Database..");
        // if (mMetaDb.getVersion() == 0) {
        Timber.i("MetaDB:: Applying changes for version: 0");

        if (metaDb.getVersion() < 4) {
            metaDb.execSQL("DROP TABLE IF EXISTS languages;");
            metaDb.execSQL("DROP TABLE IF EXISTS customDictionary;");
            metaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;");
        }

        // Create tables if not exist
        metaDb.execSQL("CREATE TABLE IF NOT EXISTS languages (" + " _id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "did INTEGER NOT NULL, ord INTEGER, " + "qa INTEGER, " + "language TEXT)");
        metaDb.execSQL("CREATE TABLE IF NOT EXISTS customDictionary (" + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "did INTEGER NOT NULL, " + "dictionary INTEGER)");
        metaDb.execSQL("CREATE TABLE IF NOT EXISTS smallWidgetStatus (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "due INTEGER NOT NULL, eta INTEGER NOT NULL)");
        updateWidgetStatus(metaDb);
        updateWhiteboardState(metaDb);
        metaDb.setVersion(databaseVersion);
        Timber.i("MetaDB:: Upgrading Internal Database finished. New version: %d", databaseVersion);
        return metaDb;
    }


    private static void updateWhiteboardState(SQLiteDatabase metaDb) {
        int columnCount  = DatabaseUtil.getTableColumnCount(metaDb, "whiteboardState");

        if (columnCount <= 0) {
            metaDb.execSQL("CREATE TABLE IF NOT EXISTS whiteboardState (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "did INTEGER NOT NULL, state INTEGER, visible INTEGER, lightpencolor INTEGER, darkpencolor INTEGER)");
            return;
        }

        if (columnCount < 4) {
            //Default to 1
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN visible INTEGER NOT NULL DEFAULT '1'");
            Timber.i("Added 'visible' column to whiteboardState");
        }

        if (columnCount < 5) {
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN lightpencolor INTEGER DEFAULT NULL");
            Timber.i("Added 'lightpencolor' column to whiteboardState");
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN darkpencolor INTEGER DEFAULT NULL");
            Timber.i("Added 'darkpencolor' column to whiteboardState");
        }
    }


    private static void updateWidgetStatus(SQLiteDatabase metaDb) {
        int columnCount = DatabaseUtil.getTableColumnCount(metaDb, "widgetStatus");
        if (columnCount > 0) {
            if (columnCount < 7) {
                metaDb.execSQL("ALTER TABLE widgetStatus " + "ADD COLUMN eta INTEGER NOT NULL DEFAULT '0'");
                metaDb.execSQL("ALTER TABLE widgetStatus " + "ADD COLUMN time INTEGER NOT NULL DEFAULT '0'");
            }
        } else {
            metaDb.execSQL("CREATE TABLE IF NOT EXISTS widgetStatus (" + "deckId INTEGER NOT NULL PRIMARY KEY, "
                    + "deckName TEXT NOT NULL, " + "newCards INTEGER NOT NULL, " + "lrnCards INTEGER NOT NULL, "
                    + "dueCards INTEGER NOT NULL, " + "progress INTEGER NOT NULL, " + "eta INTEGER NOT NULL)");
        }
    }


    /** Open the meta-db but only if it currently closed. */
    private static void openDBIfClosed(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
    }


    /** Close the meta-db. */
    public static void closeDB() {
        if (mMetaDb != null && mMetaDb.isOpen()) {
            mMetaDb.close();
            mMetaDb = null;
            Timber.d("Closing MetaDB");
        }
    }


    /** Reset the content of the meta-db, erasing all its content. */
    public static boolean resetDB(Context context) {
        openDBIfClosed(context);
        try {
            mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
            Timber.i("MetaDB:: Resetting all language assignment");
            mMetaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;");
            Timber.i("MetaDB:: Resetting whiteboard state");
            mMetaDb.execSQL("DROP TABLE IF EXISTS customDictionary;");
            Timber.i("MetaDB:: Resetting custom Dictionary");
            mMetaDb.execSQL("DROP TABLE IF EXISTS widgetStatus;");
            Timber.i("MetaDB:: Resetting widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS smallWidgetStatus;");
            Timber.i("MetaDB:: Resetting small widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS intentInformation;");
            Timber.i("MetaDB:: Resetting intentInformation");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            Timber.e(e, "Error resetting MetaDB ");
        }
        return false;
    }


    /** Reset the language associations for all the decks and card models. */
    public static boolean resetLanguages(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            Timber.i("MetaDB:: Resetting all language assignments");
            mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            Timber.e(e, "Error resetting MetaDB ");
        }
        return false;
    }


    /** Reset the widget status. */
    public static boolean resetWidget(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            Timber.i("MetaDB:: Resetting widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS widgetStatus;");
            mMetaDb.execSQL("DROP TABLE IF EXISTS smallWidgetStatus;");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            Timber.e(e, "Error resetting widgetStatus and smallWidgetStatus");
        }
        return false;
    }


    /**
     * Associates a language to a deck, model, and card model for a given type.
     * 
     * @param qa the part of the card for which to store the association, {@link #LANGUAGES_QA_QUESTION},
     *            {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED}
     * @param language the language to associate, as a two-characters, lowercase string
     */
    public static void storeLanguage(Context context, long did, int ord, Sound.SoundSide qa, String language) {
        openDBIfClosed(context);
        try {
            if ("".equals(getLanguage(context, did, ord, qa))) {
                mMetaDb.execSQL("INSERT INTO languages (did, ord, qa, language) " + " VALUES (?, ?, ?, ?);", new Object[] {
                        did, ord, qa.getInt(), language });
                Timber.v("Store language for deck %d", did);
            } else {
                mMetaDb.execSQL("UPDATE languages SET language = ? WHERE did = ? AND ord = ? AND qa = ?;", new Object[] {
                        language, did, ord, qa.getInt()});
                Timber.v("Update language for deck %d", did);
            }
        } catch (Exception e) {
            Timber.e(e,"Error storing language in MetaDB ");
        }
    }


    /**
     * Returns the language associated with the given deck, model and card model, for the given type.
     * 
     * @param qa the part of the card for which to store the association, {@link #LANGUAGES_QA_QUESTION},
     *            {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED} return the language associate with
     *            the type, as a two-characters, lowercase string, or the empty string if no association is defined
     */
    public static String getLanguage(Context context, long did, int ord, Sound.SoundSide qa) {
        openDBIfClosed(context);
        String language = "";
        String query = "SELECT language FROM languages WHERE did = ? AND ord = ? AND qa = ? LIMIT 1";
        try (Cursor cur = mMetaDb.rawQuery(query, new String[] {Long.toString(did), Integer.toString(ord), Integer.toString(qa.getInt())})) {
            Timber.v("getLanguage: %s", query);
            if (cur.moveToNext()) {
                language = cur.getString(0);
            }
        } catch (Exception e) {
            Timber.e(e, "Error fetching language ");
        }
        return language;
    }


    /**
     * Resets all the language associates for a given deck.
     * 
     * @return whether an error occurred while resetting the language for the deck
     */
    public static boolean resetDeckLanguages(Context context, long did) {
        openDBIfClosed(context);
        try {
            mMetaDb.execSQL("DELETE FROM languages WHERE did = ?;", new Long[] {did});
            Timber.i("MetaDB:: Resetting language assignment for deck %d", did);
            return true;
        } catch (Exception e) {
            Timber.e(e, "Error resetting deck language");
        }
        return false;
    }


    /**
     * Returns the state of the whiteboard for the given deck.
     * 
     * @return 1 if the whiteboard should be shown, 0 otherwise
     */
    public static boolean getWhiteboardState(Context context, long did) {
        openDBIfClosed(context);
        try (Cursor cur = mMetaDb.rawQuery("SELECT state FROM whiteboardState  WHERE did = ?", new String[] {Long.toString(did)})) {
            return DatabaseUtil.getScalarBoolean(cur);
        } catch (Exception e) {
            Timber.e(e, "Error retrieving whiteboard state from MetaDB ");
            return false;
        }
    }


    /**
     * Stores the state of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param whiteboardState 1 if the whiteboard should be shown, 0 otherwise
     */
    public static void storeWhiteboardState(Context context, long did, boolean whiteboardState) {
        int state = (whiteboardState) ? 1 : 0;
        openDBIfClosed(context);
        try (Cursor cur = mMetaDb.rawQuery("SELECT _id FROM whiteboardState WHERE did = ?", new String[] {Long.toString(did)})) {
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE whiteboardState SET did = ?, state=? WHERE _id=?;", new Object[]{did, state, cur.getString(0)});
                Timber.d("Store whiteboard state (%d) for deck %d", state, did);
            } else {
                mMetaDb.execSQL("INSERT INTO whiteboardState (did, state) VALUES (?, ?)", new Object[] { did, state });
                Timber.d("Store whiteboard state (%d) for deck %d", state, did);
            }
        } catch (Exception e) {
            Timber.e(e,"Error storing whiteboard state in MetaDB ");
        }
    }

    /**
     * Returns the state of the whiteboard for the given deck.
     *
     * @return 1 if the whiteboard should be shown, 0 otherwise
     */
    public static boolean getWhiteboardVisibility(Context context, long did) {
        openDBIfClosed(context);
        try (Cursor cur = mMetaDb.rawQuery("SELECT visible FROM whiteboardState WHERE did = ?", new String[] {Long.toString(did)})) {
            return DatabaseUtil.getScalarBoolean(cur);
        } catch (Exception e) {
            Timber.e(e, "Error retrieving whiteboard state from MetaDB ");
            return false;
        }
    }

    /**
     * Stores the state of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param isVisible 1 if the whiteboard should be shown, 0 otherwise
     */
    public static void storeWhiteboardVisibility(Context context, long did, boolean isVisible) {
        int isVisibleState = (isVisible) ? 1 : 0;
        openDBIfClosed(context);
        try (Cursor cur = mMetaDb.rawQuery("SELECT _id FROM whiteboardState WHERE did  = ?", new String[]{Long.toString(did)})) {
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE whiteboardState SET did = ?, visible= ?  WHERE _id=?;", new Object[] {did, isVisibleState, cur.getString(0)});
                Timber.d("Store whiteboard visibility (%d) for deck %d", isVisibleState, did);
            } else {
                mMetaDb.execSQL("INSERT INTO whiteboardState (did, visible) VALUES (?, ?)", new Object[] { did, isVisibleState });
                Timber.d("Store whiteboard visibility (%d) for deck %d", isVisibleState, did);
            }
        } catch (Exception e) {
            Timber.e(e,"Error storing whiteboard visibility in MetaDB ");
        }
    }

    /**
     * Returns the pen color of the whiteboard for the given deck.
     */
    public static WhiteboardPenColor getWhiteboardPenColor(Context context, long did) {
        openDBIfClosed(context);
        try (Cursor cur = mMetaDb.rawQuery("SELECT lightpencolor, darkpencolor FROM whiteboardState WHERE did = ?", new String[] {Long.toString(did)})) {
            cur.moveToFirst();
            Integer light = DatabaseUtil.getInteger(cur, 0);
            Integer dark = DatabaseUtil.getInteger(cur, 1);
            return new WhiteboardPenColor(light, dark);
        } catch (Exception e) {
            Timber.e(e, "Error retrieving whiteboard pen color from MetaDB ");
            return WhiteboardPenColor.getDefault();
        }
    }

    /**
     * Stores the pen color of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param isLight if dark mode is disabled
     * @param value The new color code to store
     */
    public static void storeWhiteboardPenColor(Context context, long did, boolean isLight, Integer value) {
        openDBIfClosed(context);
        String columnName = isLight ? "lightpencolor" : "darkpencolor";
        try (Cursor cur = mMetaDb.rawQuery("SELECT _id FROM whiteboardState WHERE did  = ?", new String[]{Long.toString(did)})) {
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE whiteboardState SET did = ?, "
                        + columnName + "= ? " +
                        " WHERE _id=?;", new Object[] {did, value, cur.getString(0)});
            } else {
                String sql = "INSERT INTO whiteboardState (did, " + columnName + ") VALUES (?, ?)";
                mMetaDb.execSQL(sql, new Object[] { did, value });
            }
            Timber.d("Store whiteboard %s (%d) for deck %d", columnName, value, did);
        } catch (Exception e) {
            Timber.w(e, "Error storing whiteboard color in MetaDB");
        }
    }

    /**
     * Returns a custom dictionary associated to a deck
     * 
     * @return integer number of dictionary, -1 if not set (standard dictionary will be used)
     */
    public static int getLookupDictionary(Context context, long did) {
        openDBIfClosed(context);
        try (Cursor cur = mMetaDb.rawQuery("SELECT dictionary FROM customDictionary WHERE did = ?", new String[] {Long.toString(did)})) {
            if (cur.moveToNext()) {
                return cur.getInt(0);
            } else {
                return -1;
            }
        } catch (Exception e) {
            Timber.e(e, "Error retrieving custom dictionary from MetaDB ");
            return -1;
        }
    }


    /**
     * Stores a custom dictionary for a given deck.
     * 
     * @param dictionary integer number of dictionary, -1 if not set (standard dictionary will be used)
     */
    public static void storeLookupDictionary(Context context, long did, int dictionary) {
        openDBIfClosed(context);
        try (Cursor cur = mMetaDb.rawQuery("SELECT _id FROM customDictionary WHERE did = ?", new String[] {Long.toString(did)})) {
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE customDictionary SET did = ?, dictionary=? WHERE _id=?;", new Object[]{did, dictionary, cur.getString(0)});
                Timber.i("MetaDB:: Store custom dictionary (%d) for deck %d", dictionary, did);
            } else {
                mMetaDb.execSQL("INSERT INTO customDictionary (did, dictionary) VALUES (?, ?)", new Object[] {did,
                        dictionary});
                Timber.i("MetaDB:: Store custom dictionary (%d) for deck %d", dictionary, did);
            }
        } catch (Exception e) {
            Timber.e(e, "Error storing custom dictionary to MetaDB ");
        }
    }


    /**
     * Return the current status of the widget.
     * 
     * @return [due, eta]
     */
    public static int[] getWidgetSmallStatus(Context context) {
        openDBIfClosed(context);
        Cursor cursor = null;
        try {
            cursor = mMetaDb.query("smallWidgetStatus", new String[] { "due", "eta" },
                    null, null, null, null, null);
            if (cursor.moveToNext()) {
                return new int[]{cursor.getInt(0), cursor.getInt(1)};
            }
        } catch (SQLiteException e) {
            Timber.e(e, "Error while querying widgetStatus");
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return new int[]{0, 0};
    }


    public static int getNotificationStatus(Context context) {
        openDBIfClosed(context);
        Cursor cursor = null;
        int due = 0;
        try {
            cursor = mMetaDb.query("smallWidgetStatus", new String[] { "due" }, null, null, null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (SQLiteException e) {
            Timber.e(e, "Error while querying widgetStatus");
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return due;
    }


    public static void storeSmallWidgetStatus(Context context, Pair<Integer, Integer> status) {
        openDBIfClosed(context);
        try {
            mMetaDb.beginTransaction();
            try {
                // First clear all the existing content.
                mMetaDb.execSQL("DELETE FROM smallWidgetStatus");
                mMetaDb.execSQL("INSERT INTO smallWidgetStatus(due, eta) VALUES (?, ?)",
                        new Object[]{status.first, status.second});
                mMetaDb.setTransactionSuccessful();
            } finally {
                mMetaDb.endTransaction();
            }
        } catch (IllegalStateException e) {
            Timber.e(e, "MetaDB.storeSmallWidgetStatus: failed");
        } catch (SQLiteException e) {
            Timber.e(e, "MetaDB.storeSmallWidgetStatus: failed");
            closeDB();
            Timber.i("MetaDB:: Trying to reset Widget: %b", resetWidget(context));
        }
    }

    public static void close() {
        if (mMetaDb != null) {
            try {
                mMetaDb.close();
            } catch (Exception e) {
                Timber.w(e, "Failed to close MetaDB");
            }
        }
    }


    private static class DatabaseUtil {
        private static boolean getScalarBoolean(Cursor cur) {
            if (cur.moveToNext()) {
                return cur.getInt(0) > 0;
            } else {
                return false;
            }
        }

        @SuppressWarnings("TryFinallyCanBeTryWithResources") //API LEVEL
        private static int getTableColumnCount(SQLiteDatabase metaDb, String tableName) {
            Cursor c = null;
            try {
                c = metaDb.rawQuery("PRAGMA table_info(" + tableName + ")", null);
                return c.getCount();
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Nullable
        public static Integer getInteger(@NonNull Cursor cur, int columnIndex) {
            return cur.isNull(columnIndex) ? null : cur.getInt(columnIndex);
        }
    }
}
