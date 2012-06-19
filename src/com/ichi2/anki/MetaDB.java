
package com.ichi2.anki;

import com.ichi2.anki2.R;
import com.ichi2.libanki.Utils;
import com.ichi2.widget.DeckStatus;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

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
    private static final int DATABASE_VERSION = 1;

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
            Log.i(AnkiDroidApp.TAG, "Opening MetaDB");
        } catch (Exception e) {
            Log.e("Error", "Error opening MetaDB ", e);
        }
    }


    /** Creating any table that missing and upgrading necessary tables. */
    private static SQLiteDatabase upgradeDB(SQLiteDatabase mMetaDb, int databaseVersion) {
        Log.i(AnkiDroidApp.TAG, "Upgrading Internal Database..");
        // if (mMetaDb.getVersion() == 0) {
        Log.i(AnkiDroidApp.TAG, "Applying changes for version: 0");
        // Create tables if not exist
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS languages (" + " _id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "deckpath TEXT NOT NULL, modelid INTEGER NOT NULL, " + "cardmodelid INTEGER NOT NULL, "
                + "qa INTEGER, " + "language TEXT)");
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS whiteboardState (" + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "deckpath TEXT NOT NULL, " + "state INTEGER)");
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS customDictionary (" + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "deckpath TEXT NOT NULL, " + "dictionary INTEGER)");
        mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS intentInformation (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "source TEXT NOT NULL, " + "target INTEGER NOT NULL)");
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
        // }
        mMetaDb.setVersion(databaseVersion);
        Log.i(AnkiDroidApp.TAG, "Upgrading Internal Database finished. New version: " + databaseVersion);
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
            Log.i(AnkiDroidApp.TAG, "Closing MetaDB");
        }
    }


    /** Reset the content of the meta-db, erasing all its content. */
    public static boolean resetDB(Context context) {
        openDBIfClosed(context);
        try {
            mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
            Log.i(AnkiDroidApp.TAG, "Resetting all language assignment");
            mMetaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;");
            Log.i(AnkiDroidApp.TAG, "Resetting whiteboard state");
            mMetaDb.execSQL("DROP TABLE IF EXISTS customDictionary;");
            Log.i(AnkiDroidApp.TAG, "Resetting custom Dictionary");
            mMetaDb.execSQL("DROP TABLE IF EXISTS widgetStatus;");
            Log.i(AnkiDroidApp.TAG, "Resetting widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS intentInformation;");
            Log.i(AnkiDroidApp.TAG, "Resetting intentInformation");
            upgradeDB(mMetaDb, DATABASE_VERSION);
            return true;
        } catch (Exception e) {
            Log.e("Error", "Error resetting MetaDB ", e);
        }
        return false;
    }


    /** Reset the language associations for all the decks and card models. */
    public static boolean resetLanguages(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            Log.i(AnkiDroidApp.TAG, "Resetting all language assignments");
            mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
            openDB(context);
            return true;
        } catch (Exception e) {
            Log.e("Error", "Error resetting MetaDB ", e);
        }
        return false;
    }


    /** Reset the widget status. */
    public static boolean resetWidget(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            Log.i(AnkiDroidApp.TAG, "Resetting widget status");
            mMetaDb.execSQL("DROP TABLE IF EXISTS widgetStatus;");
            openDB(context);
            return true;
        } catch (Exception e) {
            Log.e("Error", "Error resetting widgetStatus ", e);
        }
        return false;
    }


    /** Reset the intent information. */
    public static boolean resetIntentInformation(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            Log.i(AnkiDroidApp.TAG, "Resetting intent information");
            mMetaDb.execSQL("DROP TABLE IF EXISTS intentInformation;");
            openDB(context);
            return true;
        } catch (Exception e) {
            Log.e("Error", "Error resetting intentInformation ", e);
        }
        return false;
    }


    /**
     * Associates a language to a deck, model, and card model for a given type.
     * 
     * @param deckPath the deck for which to store the language association
     * @param modelId the model for which to store the language association
     * @param cardModelId the card model for which to store the language association
     * @param qa the part of the card for which to store the association, {@link #LANGUAGES_QA_QUESTION},
     *            {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED}
     * @param language the language to associate, as a two-characters, lowercase string
     */
    public static void storeLanguage(Context context, String deckPath, int modelId, int cardModelId, int qa,
            String language) {
        openDBIfClosed(context);
        deckPath = stripQuotes(deckPath);
        try {
            mMetaDb.execSQL("INSERT INTO languages (deckpath, modelid, cardmodelid, qa, language) "
                    + " VALUES (?, ?, ?, ?, ?);", new Object[] { deckPath, modelId, cardModelId, qa, language });
            Log.i(AnkiDroidApp.TAG, "Store language for deck " + deckPath);
        } catch (Exception e) {
            Log.e("Error", "Error storing language in MetaDB ", e);
        }
    }


    /**
     * Returns the language associated with the given deck, model and card model, for the given type.
     * 
     * @param deckPath the deck for which to store the language association
     * @param modelId the model for which to store the language association
     * @param cardModelId the card model for which to store the language association
     * @param qa the part of the card for which to store the association, {@link #LANGUAGES_QA_QUESTION},
     *            {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED} return the language associate with
     *            the type, as a two-characters, lowercase string, or the empty string if no association is defined
     */
    public static String getLanguage(Context context, String deckPath, int modelId, int cardModelId, int qa) {
        openDBIfClosed(context);
        String language = "";
        deckPath = stripQuotes(deckPath);
        Cursor cur = null;
        try {
            String query = "SELECT language FROM languages " + "WHERE deckpath = \'" + deckPath + "\' "
                    + "AND modelid = " + modelId + " " + "AND cardmodelid = " + cardModelId + " " + "AND qa = " + qa
                    + " " + "LIMIT 1";
            cur = mMetaDb.rawQuery(query, null);
            Log.i(AnkiDroidApp.TAG, "getLanguage: " + query);
            if (cur.moveToNext()) {
                language = cur.getString(0);
            }
        } catch (Exception e) {
            Log.e("Error", "Error fetching language ", e);
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
     * @param deckPath the deck for which to reset the language associations
     * @return whether an error occurred while resetting the language for the deck
     */
    public static boolean resetDeckLanguages(Context context, String deckPath) {
        openDBIfClosed(context);
        deckPath = stripQuotes(deckPath);
        try {
            mMetaDb.execSQL("DELETE FROM languages WHERE deckpath = \'" + deckPath + "\';");
            Log.i(AnkiDroidApp.TAG, "Resetting language assignment for deck " + deckPath);
            return true;
        } catch (Exception e) {
            Log.e("Error", "Error resetting deck language", e);
        }
        return false;
    }


    /**
     * Returns the state of the whiteboard for the given deck.
     * 
     * @param deckPath the deck for which to retrieve the whiteboard state
     * @return 1 if the whiteboard should be shown, 0 otherwise
     */
    public static int getWhiteboardState(Context context, String deckPath) {
        openDBIfClosed(context);
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT state FROM whiteboardState" + " WHERE deckpath = \'" + stripQuotes(deckPath)
                    + "\'", null);
            if (cur.moveToNext()) {
                return cur.getInt(0);
            } else {
                return 1;
            }
        } catch (Exception e) {
            Log.e("Error", "Error retrieving whiteboard state from MetaDB ", e);
            return 0;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    /**
     * Stores the state of the whiteboard for a given deck.
     * 
     * @param deckPath the deck for which to store the whiteboard state
     * @param state 1 if the whiteboard should be shown, 0 otherwise
     */
    public static void storeWhiteboardState(Context context, String deckPath, int state) {
        openDBIfClosed(context);
        deckPath = stripQuotes(deckPath);
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT _id FROM whiteboardState" + " WHERE deckpath = \'" + deckPath + "\'", null);
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE whiteboardState " + "SET deckpath=\'" + deckPath + "\', " + "state="
                        + Integer.toString(state) + " " + "WHERE _id=" + cur.getString(0) + ";");
                Log.i(AnkiDroidApp.TAG, "Store whiteboard state (" + state + ") for deck " + deckPath);
            } else {
                mMetaDb.execSQL("INSERT INTO whiteboardState (deckpath, state) VALUES (?, ?)", new Object[] { deckPath,
                        state });
                Log.i(AnkiDroidApp.TAG, "Store whiteboard state (" + state + ") for deck " + deckPath);
            }
        } catch (Exception e) {
            Log.e("Error", "Error storing whiteboard state in MetaDB ", e);
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    /**
     * Returns a custom dictionary associated to a deck
     * 
     * @param deckPath the deck for which a custom dictionary should be retrieved
     * @return integer number of dictionary, -1 if not set (standard dictionary will be used)
     */
    public static int getLookupDictionary(Context context, String deckPath) {
        openDBIfClosed(context);
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT dictionary FROM customDictionary" + " WHERE deckpath = \'"
                    + stripQuotes(deckPath) + "\'", null);
            if (cur.moveToNext()) {
                return cur.getInt(0);
            } else {
                return -1;
            }
        } catch (Exception e) {
            Log.e("Error", "Error retrieving custom dictionary from MetaDB ", e);
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
     * @param deckPath the deck for which a custom dictionary should be retrieved
     * @param dictionary integer number of dictionary, -1 if not set (standard dictionary will be used)
     */
    public static void storeLookupDictionary(Context context, String deckPath, int dictionary) {
        openDBIfClosed(context);
        deckPath = stripQuotes(deckPath);
        Cursor cur = null;
        try {
            cur = mMetaDb.rawQuery("SELECT _id FROM customDictionary" + " WHERE deckpath = \'" + deckPath + "\'", null);
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE customDictionary " + "SET deckpath=\'" + deckPath + "\', " + "dictionary="
                        + Integer.toString(dictionary) + " " + "WHERE _id=" + cur.getString(0) + ";");
                Log.i(AnkiDroidApp.TAG, "Store custom dictionary (" + dictionary + ") for deck " + deckPath);
            } else {
                mMetaDb.execSQL("INSERT INTO customDictionary (deckpath, dictionary) VALUES (?, ?)", new Object[] {
                        deckPath, dictionary });
                Log.i(AnkiDroidApp.TAG, "Store custom dictionary (" + dictionary + ") for deck " + deckPath);
            }
        } catch (Exception e) {
            Log.e("Error", "Error storing custom dictionary to MetaDB ", e);
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
            Log.e(AnkiDroidApp.TAG, "Error while querying widgetStatus", e);
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
        int due = 0;
        int progress = 0;
        int eta = 0;
        boolean noDeck = true;
        try {
            cursor = mMetaDb.query("widgetStatus", new String[] { "deckName", "newCards", "lrnCards", "dueCards",
                    "progress", "eta" }, null, null, null, null, null);
            while (cursor.moveToNext()) {
                noDeck = false;
                if (cursor.getString(0).split("::").length == 1) {
                    due += cursor.getInt(1) + cursor.getInt(2) + cursor.getInt(3);
                    progress = cursor.getInt(4);
                    eta = cursor.getInt(5);
                }
            }
        } catch (SQLiteException e) {
            Log.e(AnkiDroidApp.TAG, "Error while querying widgetStatus", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return new int[] { noDeck ? -1 : due, progress, eta };
    }


    public static int getNotificationStatus(Context context) {
        openDBIfClosed(context);
        Cursor cursor = null;
        int due = 0;
        try {
            cursor = mMetaDb.query("widgetStatus", new String[] { "dueCards", "failedCards", "newCards" }, null, null,
                    null, null, null);
            while (cursor.moveToNext()) {
                due += cursor.getInt(0) + cursor.getInt(1) + cursor.getInt(2);
            }
        } catch (SQLiteException e) {
            Log.e(AnkiDroidApp.TAG, "Error while querying widgetStatus", e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return due;
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
            Log.e(AnkiDroidApp.TAG, "MetaDB.storeWidgetStatus: failed", e);
        } catch (SQLiteException e) {
            Log.e(AnkiDroidApp.TAG, "MetaDB.storeWidgetStatus: failed", e);
            closeDB();
            Log.i(AnkiDroidApp.TAG, "Trying to reset Widget: " + resetWidget(context));
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
                String source = "";
                String target = "";
                for (String s : split) {
                    if (s.length() > 0) {
                        if (source.length() == 0) {
                            source = s;
                        } else if (target.length() == 0) {
                            target = s;
                        } else {
                            break;
                        }
                    }
                }
                item.put("source", source);
                item.put("target", target);
                item.put("fields", fields);
                list.add(item);
            }
        } catch (SQLiteException e) {
            Log.e(AnkiDroidApp.TAG, "Error while querying intentInformation", e);
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
            Log.i(AnkiDroidApp.TAG, "Store intentInformation: " + fields);
        } catch (Exception e) {
            Log.e("Error", "Error storing intentInformation in MetaDB ", e);
        }
    }


    public static boolean removeIntentInformation(Context context, String id) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
        try {
            Log.i(AnkiDroidApp.TAG, "Deleting intent information " + id);
            mMetaDb.execSQL("DELETE FROM intentInformation WHERE id = " + id + ";");
            return true;
        } catch (Exception e) {
            Log.e("Error", "Error deleting intentInformation " + id + ": ", e);
        }
        return false;
    }
}
