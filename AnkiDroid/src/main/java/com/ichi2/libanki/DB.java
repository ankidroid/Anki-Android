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

package com.ichi2.libanki;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Build;
import android.widget.Toast;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.dialogs.DatabaseErrorDialog;
import com.ichi2.compat.CompatHelper;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.requery.android.database.DatabaseErrorHandler;
import io.requery.android.database.sqlite.SQLiteDatabase;
import timber.log.Timber;

/**
 * Database layer for AnkiDroid. Can read the native Anki format through Android's SQLite driver.
 */
public class DB {

    private static final String[] MOD_SQLS = new String[] { "insert", "update", "delete" };

    /**
     * The deck, which is actually an SQLite database.
     */
    private SQLiteDatabase mDatabase;
    private boolean mMod = false;


    /**
     * Open a database connection to an ".anki" SQLite file.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public DB(String ankiFilename) {
        // Since API 11 we can provide a custom error handler which doesn't delete the database on corruption
        if (CompatHelper.isHoneycomb()) {
            mDatabase = SQLiteDatabase.openDatabase(ankiFilename, null,
                    (SQLiteDatabase.OPEN_READWRITE + SQLiteDatabase.CREATE_IF_NECESSARY)
                            | SQLiteDatabase.NO_LOCALIZED_COLLATORS, new MyDbErrorHandler());
        } else {
            mDatabase = SQLiteDatabase.openDatabase(ankiFilename, null,
                    (SQLiteDatabase.OPEN_READWRITE + SQLiteDatabase.CREATE_IF_NECESSARY)
                            | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        }

        if (mDatabase != null) {
            // TODO: we can remove this eventually once everyone has stopped using old AnkiDroid clients with WAL
            CompatHelper.getCompat().disableDatabaseWriteAheadLogging(mDatabase);
            mDatabase.rawQuery("PRAGMA synchronous = 2", null);
        }
        // getDatabase().beginTransactionNonExclusive();
        mMod = false;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public class MyDbErrorHandler implements DatabaseErrorHandler {
        @Override
        public void onCorruption(SQLiteDatabase db) {
            Timber.e("The database has been corrupted...");
            AnkiDroidApp.sendExceptionReport(new RuntimeException("Database corrupted"), "DB.MyDbErrorHandler.onCorruption", "Db has been corrupted ");
            CollectionHelper.getInstance().closeCollection(false);
            DatabaseErrorDialog.databaseCorruptFlag = true;
        }
    }


    /**
     * Closes a previously opened database connection.
     */
    public void close() {
        mDatabase.close();
        Timber.d("Database %s closed = %s", mDatabase.getPath(), !mDatabase.isOpen());
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
    public int queryScalar(String query) {
        return queryScalar(query, null);
    }


    public int queryScalar(String query, String[] selectionArgs) {
        Cursor cursor = null;
        int scalar;
        try {
            cursor = mDatabase.rawQuery(query, selectionArgs);
            if (!cursor.moveToNext()) {
                return 0;
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


    public long queryLongScalar(String query) {
        Cursor cursor = null;
        long scalar;
        try {
            cursor = mDatabase.rawQuery(query, null);
            if (!cursor.moveToNext()) {
                return 0;
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
        int nullExceptionCount = 0;
        InvocationTargetException nullException = null; // to catch the null exception for reporting
        ArrayList<T> results = new ArrayList<>();
        Cursor cursor = null;

        try {
            cursor = mDatabase.rawQuery(query, null);
            String methodName = getCursorMethodName(type.getSimpleName());
            while (cursor.moveToNext()) {
                try {
                    // The magical line. Almost as illegible as python code ;)
                    results.add(type.cast(Cursor.class.getMethod(methodName, int.class).invoke(cursor, column)));
                } catch (InvocationTargetException e) {
                    if (cursor.isNull(column)) { // null value encountered
                        nullExceptionCount++;
                        if (nullExceptionCount == 1) { // Toast and error report first time only
                            nullException = e;
                            Toast.makeText(AnkiDroidApp.getInstance().getBaseContext(),
                                    "Error report pending: unexpected null in database.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException e) {
            // This is really coding error, so it should be revealed if it ever happens
            throw new RuntimeException(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (nullExceptionCount > 0) {
                if (nullException != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("DB.queryColumn (column " + column + "): ");
                    sb.append("Exception due to null. Query: " + query);
                    sb.append(" Null occurrences during this query: " + nullExceptionCount);
                    AnkiDroidApp.sendExceptionReport(nullException, "queryColumn_encounteredNull", sb.toString());
                    Timber.w(sb.toString());
                } else { // nullException not properly initialized
                    StringBuilder sb = new StringBuilder();
                    sb.append("DB.queryColumn(): Critical error -- ");
                    sb.append("unable to pass in the actual exception to error reporting.");
                    AnkiDroidApp.sendExceptionReport(new RuntimeException("queryColumn null"), "queryColumn_encounteredNull", sb.toString());
                    Timber.e(sb.toString());
                }
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
        String s = sql.trim().toLowerCase(Locale.US);
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


    /**
     * WARNING: This is a convenience method that splits SQL scripts into separate queries with semicolons (;) 
     * as the delimiter. Only use this method on internal functions where we can guarantee that the script does
     * not contain any non-statement-terminating semicolons.
     */
    public void executeScript(String sql) {
        mMod = true;
        String[] queries = sql.split(";");
        for(String query : queries) {
            mDatabase.execSQL(query);
        }
    }


    /** update must always be called via DB in order to mark the db as changed */
    public int update(String table, ContentValues values) {
        return update(table, values, null, null);
    }


    /** update must always be called via DB in order to mark the db as changed */
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        mMod = true;
        return getDatabase().update(table, values, whereClause, whereArgs);
    }


    /** insert must always be called via DB in order to mark the db as changed */
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

    /**
     * @return The full path to this database file.
     */
    public String getPath() {
        return mDatabase.getPath();
    }
}
