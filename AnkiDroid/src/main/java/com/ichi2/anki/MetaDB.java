
package com.ichi2.anki;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Pair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int DATABASE_VERSION = 4;

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
    private static SQLiteDatabase upgradeDB(SQLiteDatabase mMetaDb, int databaseVersion) {
        Timber.i("MetaDB:: Upgrading Internal Database..");
        // if (mMetaDb.getVersion() == 0) {
        Timber.i("MetaDB:: Applying changes for version: 0");

        if (mMetaDb.getVersion() < 4) {
            mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
            mMetaDb.execSQL("DROP TABLE IF EXISTS customDictionary;");
            mMetaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;");
        }

        // Create tables if not exist
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS languages (" + " _id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "did INTEGER NOT NULL, ord INTEGER, " + "qa INTEGER, " + "language TEXT)");
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS whiteboardState (" + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "did INTEGER NOT NULL, " + "state INTEGER)");
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS customDictionary (" + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "did INTEGER NOT NULL, " + "dictionary INTEGER)");
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS smallWidgetStatus (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "due INTEGER NOT NULL, eta INTEGER NOT NULL)");
        // Use pragma to get info about widgetStatus.
        Cursor c = null;
        try {
             c = mMetaDb.rawQuery("PRAGMA table_info(widgetStatus)", null);
            int columnNumber = c.getCount();
            if (columnNumber > 0) {
                if (columnNumber < 7) {
                    mMetaDb.execSQL("ALTER TABLE widgetStatus " + "ADD COLUMN eta INTEGER NOT NULL DEFAULT '0'");
                    mMetaDb.execSQL("ALTER TABLE widgetStatus " + "ADD COLUMN time INTEGER NOT NULL DEFAULT '0'");
                }
            } else {
                mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS widgetStatus (" + "deckId INTEGER NOT NULL PRIMARY KEY, "
                        + "deckName TEXT NOT NULL, " + "newCards INTEGER NOT NULL, " + "lrnCards INTEGER NOT NULL, "
                        + "dueCards INTEGER NOT NULL, " + "progress INTEGER NOT NULL, " + "eta INTEGER NOT NULL)");
            }
            mMetaDb.setVersion(databaseVersion);
            Timber.i("MetaDB:: Upgrading Internal Database finished. New version: %d", databaseVersion);
            return mMetaDb;
        } finally {
            if (c != null) {
                c.close();
            }
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
    public static void storeLanguage(Context context, long did, int ord, int qa, String language) {
        openDBIfClosed(context);
        try {
            mMetaDb.execSQL("INSERT INTO languages (did, ord, qa, language) " + " VALUES (?, ?, ?, ?);", new Object[] {
                    did, ord, qa, language });
            Timber.v("Store language for deck %d", did);
        } catch (Exception e) {
            Timber.e(e,"Error storing language in MetaDB ");
        }
    }

    /**
     * Associates a language to a deck, model, and card model for a given type.
     *
     * @param qa the part of the card for which to store the association, {@link #LANGUAGES_QA_QUESTION},
     *            {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED}
     * @param language the language to associate, as a two-characters, lowercase string
     */
    public static void updateLanguage(Context context, long did, int ord, int qa, String language) {
        openDBIfClosed(context);
        try {
            mMetaDb.execSQL("UPDATE languages SET language = ? WHERE did = ? AND ord = ? AND qa = ?;", new Object[] {
                    language, did, ord, qa});
            Timber.v("Update language for deck %d", did);
        } catch (Exception e) {
            Timber.e(e,"Error updating language in MetaDB ");
        }
    }


    /**
     * Returns the language associated with the given deck, model and card model, for the given type.
     * 
     * @param qa the part of the card for which to store the association, {@link #LANGUAGES_QA_QUESTION},
     *            {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED} return the language associate with
     *            the type, as a two-characters, lowercase string, or the empty string if no association is defined
     */
    public static String getLanguage(Context context, long did, int ord, int qa) {
        openDBIfClosed(context);
        String language = "";
        Cursor cur = null;
        try {
            String query = "SELECT language FROM languages " + "WHERE did = " + did + " AND ord = " + ord
                    + " AND qa = " + qa + " " + "LIMIT 1";
            cur = mMetaDb.rawQuery(query, null);
            Timber.v("getLanguage: %s", query);
            if (cur.moveToNext()) {
                language = cur.getString(0);
            }
        } catch (Exception e) {
            Timber.e(e, "Error fetching language ");
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
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
            mMetaDb.execSQL("DELETE FROM languages WHERE did = " + did + ";");
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
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT state FROM whiteboardState" + " WHERE did = " + did, null);
            if (cur.moveToNext()) {
                return cur.getInt(0) > 0;
            } else {
                return false;
            }
        } catch (Exception e) {
            Timber.e(e, "Error retrieving whiteboard state from MetaDB ");
            return false;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
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
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT _id FROM whiteboardState" + " WHERE did  = " + did, null);
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE whiteboardState " + "SET did = " + did + ", " + "state="
                        + Integer.toString(state) + " " + "WHERE _id=" + cur.getString(0) + ";");
                Timber.d("Store whiteboard state (%d) for deck %d", state, did);
            } else {
                mMetaDb.execSQL("INSERT INTO whiteboardState (did, state) VALUES (?, ?)", new Object[] { did, state });
                Timber.d("Store whiteboard state (%d) for deck %d", state, did);
            }
        } catch (Exception e) {
            Timber.e(e,"Error storing whiteboard state in MetaDB ");
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    /**
     * Returns a custom dictionary associated to a deck
     * 
     * @return integer number of dictionary, -1 if not set (standard dictionary will be used)
     */
    public static int getLookupDictionary(Context context, long did) {
        openDBIfClosed(context);
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT dictionary FROM customDictionary" + " WHERE did = " + did, null);
            if (cur.moveToNext()) {
                return cur.getInt(0);
            } else {
                return -1;
            }
        } catch (Exception e) {
            Timber.e(e, "Error retrieving custom dictionary from MetaDB ");
            return -1;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    /**
     * Stores a custom dictionary for a given deck.
     * 
     * @param dictionary integer number of dictionary, -1 if not set (standard dictionary will be used)
     */
    public static void storeLookupDictionary(Context context, long did, int dictionary) {
        openDBIfClosed(context);
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT _id FROM customDictionary" + " WHERE did = " + did, null);
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE customDictionary " + "SET did = " + did + ", " + "dictionary="
                        + Integer.toString(dictionary) + " " + "WHERE _id=" + cur.getString(0) + ";");
                Timber.i("MetaDB:: Store custom dictionary (%d) for deck %d", dictionary, did);
            } else {
                mMetaDb.execSQL("INSERT INTO customDictionary (did, dictionary) VALUES (?, ?)", new Object[] { did,
                        dictionary });
                Timber.i("MetaDB:: Store custom dictionary (%d) for deck %d", dictionary, did);
            }
        } catch (Exception e) {
            Timber.e(e, "Error storing custom dictionary to MetaDB ");
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
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
            while (cursor.moveToNext()) {
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
            Timber.i("MetaDB:: Trying to reset Widget: " + resetWidget(context));
        }
    }
}
