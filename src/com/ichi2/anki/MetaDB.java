package com.ichi2.anki;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class MetaDB {

//public class MetaDB extends SQLiteOpenHelper {
    private static final String DATENBANK_NAME = "ankidroid.db";
    private static final int DATENBANK_VERSION = 1;
    private static SQLiteDatabase mMetaDb = null;
    public static int LANGUAGE_QUESTION = 0;
    public static int LANGUAGE_ANSWER = 1;
    public static int LANGUAGE_UNDEFINED = 2;

    public static void openDB(Context context) {
  	  try {
  		  mMetaDb = context.openOrCreateDatabase(DATENBANK_NAME,  0, null);
		  mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS languages (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
		  			+ "deckpath TEXT NOT NULL, modelid INTEGER NOT NULL, cardmodelid INTEGER NOT NULL, qa INTEGER, language TEXT)");
		  mMetaDb.execSQL("CREATE TABLE IF NOT EXISTS whiteboardState (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
		  			+ "deckpath TEXT NOT NULL, state INTEGER)");
		  Log.i(AnkiDroidApp.TAG, "Opening MetaDB");
	  } catch(Exception e) {
		  Log.e("Error", "Error opening MetaDB ", e);
	  }
    }
    
    public static void closeDB() {
    	if (mMetaDb != null && !mMetaDb.isOpen()) {
    		mMetaDb.close();
    		Log.i(AnkiDroidApp.TAG, "Closing MetaDB");
   	  	}	
    }
    
    public static boolean resetDB(Context context) {
    	if (mMetaDb == null || !mMetaDb.isOpen()) {
    		openDB(context);
   	  	}
    	try {
    		  mMetaDb.execSQL("DROP TABLE IF EXISTS languages;");
      		  mMetaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;");
      		  openDB(context);
  		  Log.i(AnkiDroidApp.TAG, "Resetting all language assignment");
  		  return true;
  	  } catch(Exception e) {
  		  Log.e("Error", "Error resetting MetaDB ", e);
  	  }
  	  return false;
    }

    public static void storeLanguage(Context context, String deckPath, long modelId, long cardModelId, int qa, String language) {
    	if (mMetaDb == null || !mMetaDb.isOpen()) {
    		openDB(context);
   	  	}	
    	try {
    		mMetaDb.execSQL("INSERT INTO languages (deckpath, modelid, cardmodelid, qa, language) VALUES (\'" 
    				+ deckPath + "\', " + modelId + ", " + cardModelId + ", " + qa + ", \'" + language + "\');");
    		Log.i(AnkiDroidApp.TAG, "Store language for deck " + deckPath);
    	} catch(Exception e) {
   		  Log.e("Error", "Error storing language in MetaDB ", e);
   	  	}
    }
    
    public static String getLanguage(Context context, String deckPath, long modelId, long cardModelId, int qa) {
    	if (mMetaDb == null || !mMetaDb.isOpen()) {
    		openDB(context);
    	}
    	String language = "";
    	Cursor cur = null;
    	try {
    		cur = mMetaDb.rawQuery("SELECT language FROM languages" + " WHERE deckpath = \'" + deckPath + "\' AND modelid = "
    					+ modelId + " AND cardmodelid = " + cardModelId + " AND qa = " + qa + " LIMIT 1", null);
    		Log.i(AnkiDroidApp.TAG, "SELECT language FROM languages" + " WHERE deckpath = \'" + deckPath + "\' AND modelid = " + modelId + " AND cardmodelid = " + cardModelId + " AND qa = " + qa + " LIMIT 1");
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
    	if (mMetaDb == null || !mMetaDb.isOpen()) {
    		openDB(context);
    	}
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
    	if (mMetaDb == null || !mMetaDb.isOpen()) {
    		openDB(context);
   	  	}
    	Cursor cur = null;
    	try {
    		cur = mMetaDb.rawQuery("SELECT state FROM whiteboardState" + " WHERE deckpath = \'" + deckPath + "\'", null);
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
    	if (mMetaDb == null || !mMetaDb.isOpen()) {
    		openDB(context);
   	  	}
    	Cursor cur = null;
    	try {
    		cur = mMetaDb.rawQuery("SELECT _id FROM whiteboardState" + " WHERE deckpath = \'" + deckPath + "\'", null);
    		if (cur.moveToNext()) {
        		mMetaDb.execSQL("UPDATE whiteboardState SET deckpath=\'" + deckPath + "\', state=" + Integer.toString(state) + " WHERE _id=" + cur.getString(0) + ";");
        		Log.i(AnkiDroidApp.TAG, "Store whiteboard state (" + state + ") for deck " + deckPath);  			
    		} else {
        		mMetaDb.execSQL("INSERT INTO whiteboardState (deckpath, state) VALUES (\'" + deckPath + "\', " + Integer.toString(state) + ");");
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
}
