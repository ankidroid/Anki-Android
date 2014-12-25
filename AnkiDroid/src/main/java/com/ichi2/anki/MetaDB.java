
package com.ichi2.anki;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.ichi2.libanki.Utils;
import com.ichi2.widget.DeckStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            AnkiDroidApp.Log(Log.INFO, "Opening MetaDB");
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error opening MetaDB ", e);
        }
    }


    /** Creating any table that missing and upgrading necessary tables. */
    private static SQLiteDatabase upgradeDB(SQLiteDatabase mMetaDb, int databaseVersion) {
        AnkiDroidApp.Log(Log.INFO, "Upgrading Internal Database..");
        // if (mMetaDb.getVersion() == 0) {
        AnkiDroidApp.Log(Log.INFO, "Applying changes for version: 0");

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
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS intentInformation (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "fields TEXT NOT NULL)");
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS smallWidgetStatus (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "progress INTEGER NOT NULL, left INTEGER NOT NULL, eta INTEGER NOT NULL)");
        // Use pragma to get info about widgetStatus.
        Cursor c = mMetaDb.rawQuery("PRAGMA table_info(widgetStatus)", null);
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
        c = mMetaDb.rawQuery("PRAGMA table_info(intentInformation)", null);
        columnNumber = c.getCount();
        if (columnNumber > 2) {
            mMetaDb.execSQL("ALTER TABLE intentInformation " + "ADD COLUMN fields INTEGER NOT NULL DEFAULT '0'");
            mMetaDb.execSQL("ALTER TABLE intentInformation " + "DROP COLUMN source INTEGER NOT NULL DEFAULT '0'");
            mMetaDb.execSQL("ALTER TABLE intentInformation " + "DROP COLUMN target INTEGER NOT NULL DEFAULT '0'");
        }
        mMetaDb.setVersion(databaseVersion);
        AnkiDroidApp.Log(Log.INFO, "Upgrading Internal Database finished. New version: " + databaseVersion);
        return mMetaDb;
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
            AnkiDroidApp.Log(Log.INFO, "Closing MetaDB");
        }
    }


    /** Reset the content of the meta-db, erasing all its content. */
    public static boolean resetDB(Context context) {
        openDBIfClosed(context);
        try {
            mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
            AnkiDroidApp.Log(Log.INFO, "Resetting all language assignment");
            mMetaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;");
            AnkiDroidApp.Log(Log.INFO, "Resetting whiteboard state");
            mMetaDb.execSQL("DROP TABLE IF EXISTS customDictionary;");
            AnkiDroidApp.Log(Log.INFO, "Resetting custom Dictionary");
            mMetaDb.execSQL("DROP TABLE IF EXISTS widgetStatus;");
            AnkiDroidApp.Log(Log.INFO, "Resetting widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS smallWidgetStatus;");
            AnkiDroidApp.Log(Log.INFO, "Resetting small widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS intentInformation;");
            AnkiDroidApp.Log(Log.INFO, "Resetting intentInformation");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error resetting MetaDB ", e);
        }
        return false;
    }


    /** Reset the language associations for all the decks and card models. */
    public static boolean resetLanguages(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            AnkiDroidApp.Log(Log.INFO, "Resetting all language assignments");
            mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error resetting MetaDB ", e);
        }
        return false;
    }


    /** Reset the widget status. */
    public static boolean resetWidget(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            AnkiDroidApp.Log(Log.INFO, "Resetting widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS widgetStatus;");
            mMetaDb.execSQL("DROP TABLE IF EXISTS smallWidgetStatus;");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error resetting widgetStatus and smallWidgetStatus", e);
        }
        return false;
    }


    /** Reset the intent information. */
    public static boolean resetIntentInformation(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            AnkiDroidApp.Log(Log.INFO, "Resetting intent information");
            mMetaDb.execSQL("DROP TABLE IF EXISTS intentInformation;");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error resetting intentInformation ", e);
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
            AnkiDroidApp.Log(Log.INFO, "Store language for deck " + did);
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error storing language in MetaDB ", e);
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
            AnkiDroidApp.Log(Log.INFO, "getLanguage: " + query);
            if (cur.moveToNext()) {
                language = cur.getString(0);
            }
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error fetching language ", e);
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
            AnkiDroidApp.Log(Log.INFO, "Resetting language assignment for deck " + did);
            return true;
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error resetting deck language", e);
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
            AnkiDroidApp.Log(Log.ERROR, "Error retrieving whiteboard state from MetaDB ", e);
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
     * @param state 1 if the whiteboard should be shown, 0 otherwise
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
                AnkiDroidApp.Log(Log.INFO, "Store whiteboard state (" + state + ") for deck " + did);
            } else {
                mMetaDb.execSQL("INSERT INTO whiteboardState (did, state) VALUES (?, ?)", new Object[] { did, state });
                AnkiDroidApp.Log(Log.INFO, "Store whiteboard state (" + state + ") for deck " + did);
            }
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error storing whiteboard state in MetaDB ", e);
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
            AnkiDroidApp.Log(Log.ERROR, "Error retrieving custom dictionary from MetaDB ", e);
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
                AnkiDroidApp.Log(Log.INFO, "Store custom dictionary (" + dictionary + ") for deck " + did);
            } else {
                mMetaDb.execSQL("INSERT INTO customDictionary (did, dictionary) VALUES (?, ?)", new Object[] { did,
                        dictionary });
                AnkiDroidApp.Log(Log.INFO, "Store custom dictionary (" + dictionary + ") for deck " + did);
            }
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error storing custom dictionary to MetaDB ", e);
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    /**
     * Return the current status of the widget.
     * 
     * @return an array of {@link DeckStatus} objects, each representing the status of one of the known decks
     */
    public static DeckStatus[] getWidgetStatus(Context context) {
        openDBIfClosed(context);
        Cursor cursor = null;
        try {
            cursor = mMetaDb.query("widgetStatus", new String[] { "deckId", "deckName", "newCards", "lrnCards",
                    "dueCards", "progress", "eta" }, null, null, null, null, "deckName");
            int count = cursor.getCount();
            DeckStatus[] decks = new DeckStatus[count];
            for (int index = 0; index < count; ++index) {
                if (!cursor.moveToNext()) {
                    throw new SQLiteException("cursor count was incorrect");
                }
                decks[index] = new DeckStatus(cursor.getLong(cursor.getColumnIndexOrThrow("deckId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("deckName")), cursor.getInt(cursor
                                .getColumnIndexOrThrow("newCards")), cursor.getInt(cursor
                                .getColumnIndexOrThrow("lrnCards")), cursor.getInt(cursor
                                .getColumnIndexOrThrow("dueCards")), cursor.getInt(cursor
                                .getColumnIndexOrThrow("progress")), cursor.getInt(cursor.getColumnIndexOrThrow("eta")));
            }
            return decks;
        } catch (SQLiteException e) {
            AnkiDroidApp.Log(Log.ERROR, "Error while querying widgetStatus", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return new DeckStatus[0];
    }


    /**
     * Return the current status of the widget.
     * 
     * @return an int array, containing due, progress, eta
     */
    public static int[] getWidgetSmallStatus(Context context) {
        openDBIfClosed(context);
        Cursor cursor = null;
        try {
            cursor = mMetaDb.query("smallWidgetStatus", new String[] { "progress", "left", "eta" }, null, null, null,
                    null, null);
            while (cursor.moveToNext()) {
                return (new int[] { cursor.getInt(0), cursor.getInt(1), cursor.getInt(2) });
            }
        } catch (SQLiteException e) {
            AnkiDroidApp.Log(Log.ERROR, "Error while querying widgetStatus", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return null;
    }


    public static int getNotificationStatus(Context context) {
        openDBIfClosed(context);
        Cursor cursor = null;
        int due = 0;
        try {
            cursor = mMetaDb.query("smallWidgetStatus", new String[] { "left" }, null, null, null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            // while (cursor.moveToNext()) {
            // due += cursor.getInt(0) + cursor.getInt(1) + cursor.getInt(2);
            // }
        } catch (SQLiteException e) {
            AnkiDroidApp.Log(Log.ERROR, "Error while querying widgetStatus", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return due;
    }


    public static void storeSmallWidgetStatus(Context context, float[] progress) {
        openDBIfClosed(context);
        try {
            mMetaDb.beginTransaction();
            try {
                // First clear all the existing content.
                mMetaDb.execSQL("DELETE FROM smallWidgetStatus");
                mMetaDb.execSQL("INSERT INTO smallWidgetStatus(progress, left, eta) VALUES (?, ?, ?)", new Object[] {
                        (int) (progress[1] * 1000), (int) progress[2], (int) progress[3] });
                mMetaDb.setTransactionSuccessful();
            } finally {
                mMetaDb.endTransaction();
            }
        } catch (IllegalStateException e) {
            AnkiDroidApp.Log(Log.ERROR, "MetaDB.storeSmallWidgetStatus: failed", e);
        } catch (SQLiteException e) {
            AnkiDroidApp.Log(Log.ERROR, "MetaDB.storeSmallWidgetStatus: failed", e);
            closeDB();
            AnkiDroidApp.Log(Log.INFO, "Trying to reset Widget: " + resetWidget(context));
        }
    }


    /**
     * Stores the current state of the widget.
     * <p>
     * It replaces any stored state for the widget.
     * 
     * @param decks an array of {@link DeckStatus} objects, one for each of the know decks.
     */
    public static void storeWidgetStatus(Context context, DeckStatus[] decks) {
        openDBIfClosed(context);
        try {
            mMetaDb.beginTransaction();
            try {
                // First clear all the existing content.
                mMetaDb.execSQL("DELETE FROM widgetStatus");
                for (DeckStatus deck : decks) {
                    mMetaDb.execSQL(
                            "INSERT INTO widgetStatus(deckId, deckName, newCards, lrnCards, dueCards, progress, eta) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", new Object[] { deck.mDeckId, deck.mDeckName,
                                    deck.mNewCards, deck.mLrnCards, deck.mDueCards, deck.mProgress, deck.mEta });
                }
                mMetaDb.setTransactionSuccessful();
            } finally {
                mMetaDb.endTransaction();
            }
        } catch (IllegalStateException e) {
            AnkiDroidApp.Log(Log.ERROR, "MetaDB.storeWidgetStatus: failed", e);
        } catch (SQLiteException e) {
            AnkiDroidApp.Log(Log.ERROR, "MetaDB.storeWidgetStatus: failed", e);
            closeDB();
            AnkiDroidApp.Log(Log.INFO, "Trying to reset Widget: " + resetWidget(context));
        }
    }


    public static ArrayList<HashMap<String, String>> getIntentInformation(Context context) {
        openDBIfClosed(context);
        Cursor cursor = null;
        ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();
        try {
            cursor = mMetaDb.query("intentInformation", new String[] { "id", "fields" }, null, null, null, null, "id");
            while (cursor.moveToNext()) {
                HashMap<String, String> item = new HashMap<String, String>();
                item.put("id", Integer.toString(cursor.getInt(0)));
                String fields = cursor.getString(1);
                String[] split = Utils.splitFields(fields);
                String source = null;
                String target = null;
                for (int i = 0; i < split.length; i++) {
                    if (source == null || source.length() == 0) {
                        source = split[i];
                    } else if (target == null || target.length() == 0) {
                        target = split[i];
                    } else {
                        break;
                    }
                }
                item.put("source", source);
                item.put("target", target);
                item.put("fields", fields);
                list.add(item);
            }
        } catch (SQLiteException e) {
            upgradeDB(mMetaDb, DATABASE_VERSION);

            AnkiDroidApp.Log(Log.ERROR, "Error while querying intentInformation", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return list;
    }


    public static void saveIntentInformation(Context context, String fields) {
        openDBIfClosed(context);
        try {
            mMetaDb.execSQL("INSERT INTO intentInformation (fields) " + " VALUES (?);", new Object[] { fields });
            AnkiDroidApp.Log(Log.INFO, "Store intentInformation: " + fields);
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error storing intentInformation in MetaDB ", e);
        }
    }


    public static boolean removeIntentInformation(Context context, String id) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            AnkiDroidApp.Log(Log.INFO, "Deleting intent information " + id);
            mMetaDb.execSQL("DELETE FROM intentInformation WHERE id = " + id + ";");
            return true;
        } catch (Exception e) {
            AnkiDroidApp.Log(Log.ERROR, "Error deleting intentInformation " + id + ": ", e);
        }
        return false;
    }
}
