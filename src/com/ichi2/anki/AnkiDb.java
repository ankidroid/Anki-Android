/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Andrew <andrewdubya@gmail.com>                                    *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Database layer for AnkiDroid. Can read the native Anki format through Android's SQLite driver.
 */
public class AnkiDb {

    private static final String[] MOD_SQLS = new String[] { "insert", "update", "delete" };

    /**
     * The deck, which is actually an SQLite database.
     */
    private SQLiteDatabase mDatabase;
    private boolean mMod = false;


    /**
     * Open a database connection to an ".anki" SQLite file.
     */
    public AnkiDb(String ankiFilename) {
        mDatabase = SQLiteDatabase.openDatabase(ankiFilename, null,
                (SQLiteDatabase.OPEN_READWRITE + SQLiteDatabase.CREATE_IF_NECESSARY)
                        | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        if (mDatabase != null) {
            if (AnkiDroidApp.SDK_VERSION >= 11) {
                queryString("PRAGMA journal_mode = WAL");
            } else {
                queryString("PRAGMA journal_mode = DELETE");
            }
            if (AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getBoolean("asyncMode", false)) {
                mDatabase.rawQuery("PRAGMA synchronous = 0", null);
            } else {
                mDatabase.rawQuery("PRAGMA synchronous = 2", null);
            }
        }
        // getDatabase().beginTransactionNonExclusive();
        mMod = false;
    }


    /**
     * Closes a previously opened database connection.
     */
    public void closeDatabase() {
        if (mDatabase != null) {
            // set journal mode again to delete in order to make the db accessible for anki desktop and for full upload
            queryString("PRAGMA journal_mode = DELETE");
            mDatabase.close();
            Log.i(AnkiDroidApp.TAG, "AnkiDb - closeDatabase, database " + mDatabase.getPath() + " closed = "  + !mDatabase.isOpen());
            mDatabase = null;
        }
    }


    public void commit() {
        // SQLiteDatabase db = getDatabase();
        // while (db.inTransaction()) {
        // db.setTransactionSuccessful();
        // db.endTransaction();
        // }
        // db.beginTransactionNonExclusive();
    }


    public SQLiteDatabase getDatabase() {
        return mDatabase;
    }


    public void setMod(boolean mod) {
        mMod = mod;
    }


    public boolean getMod() {
        return mMod;
    }


    /**
     * Convenience method for querying the database for a single integer result.
     * 
     * @param query The raw SQL query to use.
     * @return The integer result of the query.
     */
    public int queryScalar(String query) throws SQLException {
        return queryScalar(query, true);
    }


    public int queryScalar(String query, boolean throwException) throws SQLException {
        Cursor cursor = null;
        int scalar;
        try {
            cursor = mDatabase.rawQuery(query, null);
            if (!cursor.moveToNext()) {
                if (throwException) {
                    throw new SQLException("No result for query: " + query);
                } else {
                    return 0;
                }
            }

            scalar = cursor.getInt(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return scalar;
    }


    public String queryString(String query) throws SQLException {
        Cursor cursor = null;
        try {
            cursor = mDatabase.rawQuery(query, null);
            if (!cursor.moveToNext()) {
                throw new SQLException("No result for query: " + query);
            }
            return cursor.getString(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public long queryLongScalar(String query) throws SQLException {
        return queryLongScalar(query, true);
    }


    public long queryLongScalar(String query, boolean throwException) throws SQLException {
        Cursor cursor = null;
        long scalar;
        try {
            cursor = mDatabase.rawQuery(query, null);
            if (!cursor.moveToNext()) {
                if (throwException) {
                    throw new SQLException("No result for query: " + query);
                } else {
                    return 0;
                }
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


    public void execute(String sql) {
        execute(sql, null);
    }


    public void execute(String sql, Object[] object) {
        String s = sql.trim().toLowerCase();
        // mark modified?
        for (String mo : MOD_SQLS) {
            if (s.startsWith(mo)) {
                mMod = true;
                break;
            }
        }
        if (object == null) {
            this.getDatabase().execSQL(sql);
        } else {
            this.getDatabase().execSQL(sql, object);
        }
    }


    /** update must always be called via AnkiDb in order to mark the db as changed */
    public int update(String table, ContentValues values) {
        return update(table, values, null, null);
    }


    /** update must always be called via AnkiDb in order to mark the db as changed */
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        mMod = true;
        return getDatabase().update(table, values, whereClause, whereArgs);
    }


    /** insert must always be called via AnkiDb in order to mark the db as changed */
    public long insert(String table, String nullColumnHack, ContentValues values) {
        mMod = true;
        return getDatabase().insert(table, nullColumnHack, values);
    }


    public void executeMany(String sql, List<Object[]> list) {
        mMod = true;
        mDatabase.beginTransaction();
        try {
            for (Object[] o : list) {
                mDatabase.execSQL(sql, o);
            }
            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }
    }
}
