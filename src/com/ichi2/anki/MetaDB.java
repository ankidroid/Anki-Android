package com.ichi2.anki;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used to store additional information besides what is stored in the deck itself.
 * <p>
 * Currently it used to store:
 * <ul>
 *   <li>The languages associated with questions and answers.</li>
 *   <li>The state of the whiteboard.</li>
 *   <li>The cached state of the widget.</li>
 * </ul>
 */
public class MetaDB {
    /** The name of the file storing the meta-db. */
    private static final String DATABASE_NAME = "ankidroid.db";

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


    /** Open the meta-db and creates any table that is missing. */
    private static void openDB(Context context) {
        try {
            mMetaDb = context.openOrCreateDatabase(DATABASE_NAME,  0, null);
            mMetaDb.execSQL(
                    "CREATE TABLE IF NOT EXISTS languages ("
                            + " _id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "deckpath TEXT NOT NULL, modelid INTEGER NOT NULL, "
                            + "cardmodelid INTEGER NOT NULL, "
                            + "qa INTEGER, "
                            + "language TEXT)");
            mMetaDb.execSQL(
                    "CREATE TABLE IF NOT EXISTS whiteboardState ("
                            + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "deckpath TEXT NOT NULL, "
                            + "state INTEGER)");
            mMetaDb.execSQL(
                    "CREATE TABLE IF NOT EXISTS widgetStatus ("
                            + "deckPath TEXT NOT NULL PRIMARY KEY, "
                            + "deckName TEXT NOT NULL, "
                            + "newCards INTEGER NOT NULL, "
                            + "dueCards INTEGER NOT NULL, "
                            + "failedCards INTEGER NOT NULL)");
            Log.i(AnkiDroidApp.TAG, "Opening MetaDB");
        } catch(Exception e) {
            Log.e("Error", "Error opening MetaDB ", e);
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
            mMetaDb.execSQL("DROP TABLE IF EXISTS widgetStatus;");
            Log.i(AnkiDroidApp.TAG, "Resetting widget status");
            return true;
        } catch(Exception e) {
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
        } catch(Exception e) {
            Log.e("Error", "Error resetting MetaDB ", e);
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
     *           {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED}
     * @param language the language to associate, as a two-characters, lowercase string
     */
    public static void storeLanguage(Context context, String deckPath, long modelId, long cardModelId, int qa,
            String language) {
        openDBIfClosed(context);
        deckPath = stripQuotes(deckPath);
        try {
            mMetaDb.execSQL(
                    "INSERT INTO languages (deckpath, modelid, cardmodelid, qa, language) "
                            + " VALUES (?, ?, ?, ?, ?);",
                            new Object[]{deckPath, modelId, cardModelId, qa, language});
            Log.i(AnkiDroidApp.TAG, "Store language for deck " + deckPath);
        } catch(Exception e) {
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
     *           {@link #LANGUAGES_QA_ANSWER}, or {@link #LANGUAGES_QA_UNDEFINED}
     * return the language associate with the type, as a two-characters, lowercase string, or the empty string if no
     *        association is defined
     */
    public static String getLanguage(Context context, String deckPath, long modelId, long cardModelId, int qa) {
        openDBIfClosed(context);
        String language = "";
        deckPath = stripQuotes(deckPath);
        Cursor cur = null;
        try {
            String query =
                    "SELECT language FROM languages "
                            + "WHERE deckpath = \'" + deckPath+ "\' "
                            + "AND modelid = " + modelId + " "
                            + "AND cardmodelid = " + cardModelId + " "
                            + "AND qa = " + qa + " "
                            + "LIMIT 1";
            cur = mMetaDb.rawQuery(query, null);
            Log.i(AnkiDroidApp.TAG, "getLanguage: " + query);
            if (cur.moveToNext()) {
                language = cur.getString(0);
            }
        } catch(Exception e) {
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
        } catch(Exception e) {
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
            cur = mMetaDb.rawQuery("SELECT state FROM whiteboardState"
                    + " WHERE deckpath = \'" + stripQuotes(deckPath) + "\'", null);
            if (cur.moveToNext()) {
                return cur.getInt(0);
            } else {
                return 0;
            }
        } catch(Exception e) {
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
            cur = mMetaDb.rawQuery("SELECT _id FROM whiteboardState"
                    + " WHERE deckpath = \'" + deckPath + "\'", null);
            if (cur.moveToNext()) {
                mMetaDb.execSQL("UPDATE whiteboardState "
                        + "SET deckpath=\'" + deckPath + "\', "
                        + "state=" + Integer.toString(state) + " "
                        + "WHERE _id=" + cur.getString(0) + ";");
                Log.i(AnkiDroidApp.TAG, "Store whiteboard state (" + state + ") for deck " + deckPath);
            } else {
                mMetaDb.execSQL("INSERT INTO whiteboardState (deckpath, state) VALUES (?, ?)",
                        new Object[]{deckPath, state});
                Log.i(AnkiDroidApp.TAG, "Store whiteboard state (" + state + ") for deck " + deckPath);
            }
        } catch(Exception e) {
            Log.e("Error", "Error storing whiteboard state in MetaDB ", e);
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
            cursor = mMetaDb.query("widgetStatus",
                    new String[]{"deckPath", "deckName", "newCards", "dueCards", "failedCards"},
                    null, null, null, null, "deckName");
            int count = cursor.getCount();
            DeckStatus[] decks = new DeckStatus[count];
            for(int index = 0; index < count; ++index) {
                if (!cursor.moveToNext()) {
                    throw new SQLiteException("cursor count was incorrect");
                }
                decks[index] = new DeckStatus(
                        cursor.getString(cursor.getColumnIndexOrThrow("deckPath")),
                        cursor.getString(cursor.getColumnIndexOrThrow("deckName")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("newCards")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("dueCards")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("failedCards")));
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
     * Stores the current state of the widget.
     * <p>
     * It replaces any stored state for the widget.
     *
     * @param decks an array of {@link DeckStatus} objects, one for each of the know decks.
     */
    public static void storeWidgetStatus(Context context, DeckStatus[] decks) {
        openDBIfClosed(context);
        mMetaDb.beginTransaction();
        // First clear all the existing content.
        mMetaDb.execSQL("DELETE FROM widgetStatus");
        try {
            for (DeckStatus deck : decks) {
                mMetaDb.execSQL("INSERT INTO widgetStatus(deckPath, deckName, newCards, dueCards, failedCards) "
                        + "VALUES (?, ?, ?, ?, ?)",
                        new Object[]{deck.mDeckPath, deck.mDeckName, deck.mNewCards, deck.mDueCards, deck.mFailedCards}
                        );
            }
            mMetaDb.setTransactionSuccessful();
        } catch (SQLiteException e) {
            Log.e(AnkiDroidApp.TAG, "MetaDB.storeWidgetStatus: failed", e);
        }
        mMetaDb.endTransaction();
    }
}
