/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Andrew <andrewdubya@gmail.com>                                    *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map.Entry;

import com.tomgibara.android.veecheck.util.PrefSettings;
import com.ichi2.anki.Utils.SqlCommandType;
import static com.ichi2.anki.Utils.SqlCommandType.*;

/**
 * Database layer for AnkiDroid. Can read the native Anki format through Android's SQLite driver.
 */
public class AnkiDb {

    /**
     * The deck, which is actually an SQLite database.
     */
    private SQLiteDatabase mDatabase;

    /**
     * Open a database connection to an ".anki" SQLite file.
     */
    public AnkiDb(String ankiFilename, boolean forceDeleteJournalMode) {
        mDatabase = SQLiteDatabase.openDatabase(ankiFilename, null, SQLiteDatabase.OPEN_READWRITE
                | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        if (mDatabase != null) {
            Cursor cur = null;
            try {
                String mode;
            	SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
            	if (prefs.getBoolean("walMode", false) && !forceDeleteJournalMode) {
            		mode = "WAL";
            	} else {
            		mode = "DELETE";
            	}
                cur = mDatabase.rawQuery("PRAGMA journal_mode", null);
                if (cur.moveToFirst()) {
                	String journalModeOld = cur.getString(0);
                	cur.close();
                	Log.w(AnkiDroidApp.TAG, "Current Journal mode: " + journalModeOld);                    		
                	if (!journalModeOld.equalsIgnoreCase(mode)) {
                    	cur = mDatabase.rawQuery("PRAGMA journal_mode = " + mode, null);
                    	if (cur.moveToFirst()) {
                        	String journalModeNew = cur.getString(0);
                        	cur.close();
                        	Log.w(AnkiDroidApp.TAG, "Old journal mode was: " + journalModeOld + ". Trying to set journal mode to " + mode + ". Result: " + journalModeNew);                    		
                        	if (journalModeNew.equalsIgnoreCase("wal") && mode.equals("DELETE")) {
                        		Log.e(AnkiDroidApp.TAG, "Journal could not be changed to DELETE. Deck will probably be unreadable on sqlite < 3.7");
                        	}
                    	}
                	}
                }
                if (prefs.getBoolean("asyncMode", false)) {
                    cur = mDatabase.rawQuery("PRAGMA synchronous = 0", null);
                } else {
                    cur = mDatabase.rawQuery("PRAGMA synchronous = 2", null);
                }
                cur.close();
                cur = mDatabase.rawQuery("PRAGMA synchronous", null);
                if (cur.moveToFirst()) {
                	String syncMode = cur.getString(0);
                	Log.w(AnkiDroidApp.TAG, "Current synchronous setting: " + syncMode);                    		
                }
                cur.close();
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
    }


    /**
     * Closes a previously opened database connection.
     */
    public void closeDatabase() {
        if (mDatabase != null) {
            mDatabase.close();
            Log.i(AnkiDroidApp.TAG, "AnkiDb - closeDatabase, database " + mDatabase.getPath() + " closed = " + !mDatabase.isOpen());
            mDatabase = null;
        }
    }


    public SQLiteDatabase getDatabase() {
        return mDatabase;
    }


    /**
     * Convenience method for querying the database for a single integer result.
     * 
     * @param query The raw SQL query to use.
     * @return The integer result of the query.
     */
    public long queryScalar(String query) throws SQLException {
        Cursor cursor = null;
        long scalar;
        try {
            cursor = mDatabase.rawQuery(query, null);
            if (!cursor.moveToNext()) {
                throw new SQLException("No result for query: " + query);
            }

            scalar = cursor.getLong(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return scalar;
    }


    /**
     * Convenience method for querying the database for an entire column. The column will be returned as an ArrayList of
     * the specified class. See Deck.initUndo() for a usage example.
     *
     * @param type The class of the column's data type. Example: int.class, String.class.
     * @param query The SQL query statement.
     * @param column The column id in the result set to return.
     * @return An ArrayList with the contents of the specified column.
     */
    public <T> ArrayList<T> queryColumn(Class<T> type, String query, int column) {
        ArrayList<T> results = new ArrayList<T>();
        Cursor cursor = null;

        try {
            cursor = mDatabase.rawQuery(query, null);
            String methodName = getCursorMethodName(type.getSimpleName());
            while (cursor.moveToNext()) {
                // The magical line. Almost as illegible as python code ;)
                results.add(type.cast(Cursor.class.getMethod(methodName, int.class).invoke(cursor, column)));
            }
        } catch (NoSuchMethodException e) {
            // This is really coding error, so it should be revealed if it ever happens
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            // This is really coding error, so it should be revealed if it ever happens
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            // This is really coding error, so it should be revealed if it ever happens
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }


    /**
     * Method for executing db commands with simultaneous storing of undo information. This should only be called from undo method.
     */
    public void execSQL(Deck deck, SqlCommandType command, String table, ContentValues values, String whereClause) {
    	if (command == SQL_INS) {
			insert(deck, table, null, values);
    	} else if (command == SQL_UPD) {
			update(deck, table, values, whereClause, null);
    	} else if (command == SQL_DEL) {
    		delete(deck, table, whereClause, null);
    	} else {
    		Log.i(AnkiDroidApp.TAG, "wrong command. no action performed");
    	}
    }


    /**
     * Method for inserting rows into the db with simultaneous storing of undo information.
     * 
     * @return The id of the inserted row.
     */
    public long insert(Deck deck, String table, String nullColumnHack, ContentValues values) {
    	long rowid = mDatabase.insert(table, nullColumnHack, values);
    	if (rowid != -1 && deck.recordUndoInformation()) {
        	deck.addUndoCommand(SQL_DEL, table, null, "rowid = " + rowid);
    	}
    	return rowid;
    }


    /**
     * Method for updating rows of the database with simultaneous storing of undo information.
     * 
     * @param values A map from column names to new column values. Values must not contain sql code/variables. Otherwise use update(Deck deck, String table, ContentValues values, String whereClause, String[] whereArgs, boolean onlyFixedValues) with 'onlyFixedValues' = false.
     * @param whereClause The optional WHERE clause to apply when updating. Passing null will update all rows.
     * @param whereArgs Arguments which will replace all '?'s of the whereClause.
     */
    public void update(Deck deck, String table, ContentValues values, String whereClause, String[] whereArgs) {
    	update(deck, table, values, whereClause, whereArgs, true);
    }
    /**
     * Method for updating rows of the database with simultaneous storing of undo information.
     * 
     * @param values A map from column names to new column values. null is a valid value that will be translated to NULL.
     * @param whereClause The optional WHERE clause to apply when updating. Passing null will update all rows.
     * @param whereArgs Arguments which will replace all '?'s of the whereClause.
     * @param onlyFixedValues Set this to true, if 'values' contains only fixed values (no sql code). Otherwise, it must be set to false and fixed string values have to be extra quoted ("\'example-value\'").
     */
    public void update(Deck deck, String table, ContentValues values, String whereClause, String[] whereArgs, boolean onlyFixedValues) {
        update(deck, table, values, whereClause, whereArgs, onlyFixedValues, null, null);
    }
    public void update(Deck deck, String table, ContentValues values, String whereClause, String[] whereArgs, boolean onlyFixedValues, ContentValues[] oldValuesArray, String[] whereClauseArray) {
    	if (deck.recordUndoInformation()) {
        	if (oldValuesArray != null) {
                for (int i = 0; i < oldValuesArray.length; i++) {
                    deck.addUndoCommand(SQL_UPD, table, oldValuesArray[i], whereClauseArray[i]);
                }
        	} else {
        		ArrayList<String> ar = new ArrayList<String>();
                for (Entry<String, Object> entry : values.valueSet()) {
                	ar.add(entry.getKey());
                }
                int len = ar.size();
                String[] columns = new String[len + 1];
                ar.toArray(columns);
                columns[len] = "rowid";

                Cursor cursor = null;
                try {
                    cursor = mDatabase.query(table, columns, whereClause, whereArgs, null, null, null);
                    while (cursor.moveToNext()) {
                        ContentValues oldvalues = new ContentValues();
                        for (int i = 0; i < len; i++) {
//                        	String typeName;
//                        	if (values.get(columns[i]) != null) {
//                        		typeName = values.get(columns[i]).getClass().getSimpleName();
//                        	} else {
//                        		typeName = "String";
//                        	}
//                    		if (typeName.equals("String")) {
//                    			oldvalues.put(columns[i], cursor.getString(i));
//                            } else if (typeName.equals("Long")) {
//                            	oldvalues.put(columns[i], cursor.getLong(i));
//                            } else if (typeName.equals("Double")) {
//                            	oldvalues.put(columns[i], cursor.getDouble(i));
//                            } else if (typeName.equals("Integer")) {
//                            	oldvalues.put(columns[i], cursor.getInt(i));
//                            } else if (typeName.equals("Float")) {
//                            	oldvalues.put(columns[i], cursor.getFloat(i));
//                            } else {
                            	oldvalues.put(columns[i], cursor.getString(i));
//                            }
                        }
                        deck.addUndoCommand(SQL_UPD, table, oldvalues, "rowid = " + cursor.getString(len));
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
        	}    		
    	}
    	if (onlyFixedValues) {
    		mDatabase.update(table, values, whereClause, whereArgs);
    	} else {
    		StringBuilder sb = new StringBuilder();
    		sb.append("UPDATE ").append(table).append(" SET ");
    		for (Entry<String, Object> entry : values.valueSet()) {
				sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append(", ");
    		}
    		sb.deleteCharAt(sb.length() - 2);
    		if (whereArgs != null) {
        		for (int i = 0; i < whereArgs.length; i++) {
        			whereClause = whereClause.replaceFirst("?", whereArgs[i]);
        		}    			
    		}
    		sb.append("WHERE ").append(whereClause);
		    mDatabase.execSQL(sb.toString());
		}
    }


    /**
     * Method for deleting rows of the database with simultaneous storing of undo information.
     */
    public void delete(Deck deck, String table, String whereClause, String[] whereArgs) {
        if (deck.recordUndoInformation()) {
        	ArrayList<String> columnsNames = new ArrayList<String>();
//        	ArrayList<String> columnTypes = new ArrayList<String>();
            Cursor cursor = null;

            try {
                cursor = mDatabase.rawQuery("PRAGMA TABLE_INFO(" + table + ")", null);
                while (cursor.moveToNext()) {
                	columnsNames.add(cursor.getString(1));
//                	String t = cursor.getString(2).toLowerCase();
//                    String typeName = "";
//                    if (t.subSequence(0, 3).equals("int")) {
//                    	typeName = "Long";
//                    } else if (t.equals("float")) {
//                    	typeName = "Double";
//                    } else {
//                    	typeName = "String";
//                    }
//                    columnTypes.add(typeName);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            
        	int len = columnsNames.size();
        	String[] columns = new String[len];
        	columnsNames.toArray(columns);

            try {
                cursor = mDatabase.query(table, columns, whereClause, whereArgs, null, null, null);
                while (cursor.moveToNext()) {
                    ContentValues oldvalues = new ContentValues();
                    for (int i = 0; i < len; i++) {
//                    	String typeName = columnTypes.get(i);
//                		if (typeName.equals("String")) {
//                			oldvalues.put(columns[i], cursor.getString(i));
//                        } else if (typeName.equals("Long")) {
//                        	oldvalues.put(columns[i], cursor.getLong(i));
//                        } else if (typeName.equals("Double")) {
//                        	oldvalues.put(columns[i], cursor.getDouble(i));
//                        } else if (typeName.equals("Integer")) {
//                        	oldvalues.put(columns[i], cursor.getInt(i));
//                        } else if (typeName.equals("Float")) {
//                        	oldvalues.put(columns[i], cursor.getFloat(i));
//                        } else {
                        	oldvalues.put(columns[i], cursor.getString(i));
//                        }
                    }
                    deck.addUndoCommand(SQL_INS, table, oldvalues, null);
                }
			} finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
    	}
    	mDatabase.delete(table, whereClause, whereArgs);
    }


    /**
     * Mapping of Java type names to the corresponding Cursor.get method.
     *
     * @param typeName The simple name of the type's class. Example: String.class.getSimpleName().
     * @return The name of the Cursor method to be called.
     */
    private static String getCursorMethodName(String typeName) {
        if (typeName.equals("String")) {
            return "getString";
        } else if (typeName.equals("Long")) {
            return "getLong";
        } else if (typeName.equals("Integer")) {
            return "getInt";
        } else if (typeName.equals("Float")) {
            return "getFloat";
        } else if (typeName.equals("Double")) {
            return "getDouble";
        } else {
            return null;
        }
    }
}
