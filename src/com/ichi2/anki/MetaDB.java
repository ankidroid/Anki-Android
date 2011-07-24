package com.ichi2.anki;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

public class MetaDB {
    private static final String DATABASE_NAME = "ankidroid.db";

    public static final int LANGUAGE_QUESTION = 0;
    public static final int LANGUAGE_ANSWER = 1;
    public static final int LANGUAGE_UNDEFINED = 2;

    private static final Pattern quotePattern = Pattern.compile("[\"']");

    private static SQLiteDatabase mMetaDb = null;


    private static String stripQuotes(String text) {
        Matcher matcher = quotePattern.matcher(text);
        text = matcher.replaceAll("");
        return text;
    }


    public static void openDB(Context context) {
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

    private static void openDBIfClosed(Context context) {
        if (mMetaDb == null || !mMetaDb.isOpen()) {
            openDB(context);
        }
    }

    public static void closeDB() {
        if (mMetaDb != null && mMetaDb.isOpen()) {
            mMetaDb.close();
            Log.i(AnkiDroidApp.TAG, "Closing MetaDB");
        }
    }

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

    public static void storeLanguage(Context context, String deckPath, long modelId, long cardModelId, int qa, String language) {
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
                Log.i(AnkiDroidApp.TAG,
                        "Store whiteboard state (" + state + ") for deck " + deckPath);
            } else {
                mMetaDb.execSQL("INSERT INTO whiteboardState (deckpath, state) VALUES (?, ?)",
                        new Object[]{deckPath, state});
                Log.i(AnkiDroidApp.TAG,
                        "Store whiteboard state (" + state + ") for deck " + deckPath);
            }
        } catch(Exception e) {
              Log.e("Error", "Error storing whiteboard state in MetaDB ", e);
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }

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
