/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Andrew <andrewdubya@gmail.com>                                    *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BuildConfig;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.dialogs.DatabaseErrorDialog;
import com.ichi2.utils.DatabaseChangeDecorator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import timber.log.Timber;

/**
 * Database layer for AnkiDroid. Can read the native Anki format through Android's SQLite driver.
 */
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes"})
public class DB {

    private static final String[] MOD_SQLS = new String[] { "insert", "update", "delete" };

    /** may be injected to use a different sqlite implementation - null means use default */
    private static SupportSQLiteOpenHelper.Factory sqliteOpenHelperFactory = null;

    /**
     * The collection, which is actually an SQLite database.
     */
    private final SupportSQLiteDatabase mDatabase;
    private boolean mMod = false;

    public DB(String ankiFilename) {
        this(ankiFilename, null);
    }

    /**
     * Open a connection to the SQLite collection database.
     */
    public DB(String ankiFilename, @Nullable OpenHelperFactory openHelperFactory) {

        SupportSQLiteOpenHelper.Configuration configuration = SupportSQLiteOpenHelper.Configuration.builder(AnkiDroidApp.getInstance())
                .name(ankiFilename)
                .callback(getDBCallback())
                .build();
        SupportSQLiteOpenHelper helper = getSqliteOpenHelperFactory(openHelperFactory).create(configuration);
        // Note: This line creates the database and schema when executed using a Rust backend
        mDatabase = new DatabaseChangeDecorator(helper.getWritableDatabase());
        mDatabase.disableWriteAheadLogging();
        mDatabase.query("PRAGMA synchronous = 2", null);
        mMod = false;
    }


    /**
     * You may swap in your own SQLite implementation by altering the factory here. An
     * example might be to use the framework implementation. If you set to null, we default
     * to requery
     * @param factory connection factory for the desired sqlite implementation, null for requery
     */
    public static void setSqliteOpenHelperFactory(@Nullable SupportSQLiteOpenHelper.Factory factory) {
        sqliteOpenHelperFactory = factory;
    }


    private SupportSQLiteOpenHelper.Factory getSqliteOpenHelperFactory(@Nullable OpenHelperFactory openHelper) {
        if (openHelper != null) {
            return openHelper.getFactory();
        }

        if (sqliteOpenHelperFactory == null) {
            return new FrameworkSQLiteOpenHelperFactory();
        }
        return sqliteOpenHelperFactory;
    }


    /** Get the SQLite callback object to use when creating connections - overridable for testability */
    protected SupportSQLiteOpenHelperCallback getDBCallback() {
        return new SupportSQLiteOpenHelperCallback(1);
    }


    /**
     * The default AnkiDroid SQLite database callback.
     * We do not handle versioning or connection config using the framework APIs, so those methods
     * do nothing in our implementation. However, we on corruption events we want to send messages but
     * not delete the database.
     */
    public static class SupportSQLiteOpenHelperCallback extends SupportSQLiteOpenHelper.Callback {

        protected SupportSQLiteOpenHelperCallback(int version) { super(version); }
        public void onCreate(@NonNull SupportSQLiteDatabase db) {/* do nothing */ }
        public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) { /* do nothing */ }


        /** Send error message, but do not call super() which would delete the database */
        public void onCorruption(SupportSQLiteDatabase db) {
            Timber.e("The database has been corrupted: %s", db.getPath());
            AnkiDroidApp.sendExceptionReport(new RuntimeException("Database corrupted"), "DB.MyDbErrorHandler.onCorruption", "Db has been corrupted: " + db.getPath());
            CollectionHelper.getInstance().closeCollection(false, "Database corrupted");
            DatabaseErrorDialog.databaseCorruptFlag = true;
        }
    }


    /**
     * Closes a previously opened database connection.
     */
    public void close() {
        try {
            mDatabase.close();
            Timber.d("Database %s closed = %s", mDatabase.getPath(), !mDatabase.isOpen());
        } catch (Exception e) {
            // The pre-framework requery API ate this exception, but the framework API exposes it.
            // We may want to propagate it in the future, but for now maintain the old API and log.
            Timber.e(e, "Failed to close database %s", this.getDatabase().getPath());
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


    public SupportSQLiteDatabase getDatabase() {
        return mDatabase;
    }


    public void setMod(boolean mod) {
        mMod = mod;
    }


    public boolean getMod() {
        return mMod;
    }

    // Allows to avoid using new Object[]
    public Cursor query(String query, Object... selectionArgs) {
        return mDatabase.query(query, selectionArgs);
    }

    /**
     * Convenience method for querying the database for a single integer result.
     *
     * @param query The raw SQL query to use.
     * @return The integer result of the query.
     */
    public int queryScalar(String query, Object... selectionArgs) {
        Cursor cursor = null;
        int scalar;
        try {
            cursor = mDatabase.query(query, selectionArgs);
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


    public String queryString(String query, Object... bindArgs) throws SQLException {
        try (Cursor cursor = mDatabase.query(query, bindArgs)) {
            if (!cursor.moveToNext()) {
                throw new SQLException("No result for query: " + query);
            }
            return cursor.getString(0);
        }
    }


    public long queryLongScalar(String query, Object... bindArgs) {
        long scalar;
        try (Cursor cursor = mDatabase.query(query, bindArgs)) {
            if (!cursor.moveToNext()) {
                return 0;
            }
            scalar = cursor.getLong(0);
        }

        return scalar;
    }


    /**
     * Convenience method for querying the database for an entire column of long.
     *
     * @param query The SQL query statement.
     * @return An ArrayList with the contents of the specified column.
     */
    public ArrayList<Long> queryLongList(String query, Object... bindArgs) {
        ArrayList<Long> results = new ArrayList<>();

        try (Cursor cursor = mDatabase.query(query, bindArgs)) {
            while (cursor.moveToNext()) {
                results.add(cursor.getLong(0));
            }
        }

        return results;
    }

    /**
     * Convenience method for querying the database for an entire column of String. 
     *
     * @param query The SQL query statement.
     * @return An ArrayList with the contents of the specified column.
     */
    public ArrayList<String> queryStringList(String query, Object... bindArgs) {
        ArrayList<String> results = new ArrayList<>();

        try (Cursor cursor = mDatabase.query(query, bindArgs)) {
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0));
            }
        }

        return results;
    }


    public void execute(String sql, Object... object) {
        String s = sql.trim().toLowerCase(Locale.ROOT);
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
        return getDatabase().update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs);
    }


    /** insert must always be called via DB in order to mark the db as changed */
    public long insert(String table, ContentValues values) {
        mMod = true;
        return getDatabase().insert(table, SQLiteDatabase.CONFLICT_NONE, values);
    }

    public void executeMany(String sql, List<Object[]> list) {
        mMod = true;
        if (BuildConfig.DEBUG) {
            if (list.size() <= 1) {
                Timber.w("Query %s called with a list of at most one element. Usually that's not expected.", sql);
            }
        }
        executeInTransaction(() -> executeManyNoTransaction(sql, list));
    }

    /** Use this executeMany version with external transaction management */
    public void executeManyNoTransaction(String sql, List<Object[]> list) {
        mMod = true;
        for (Object[] o : list) {
            mDatabase.execSQL(sql, o);
        }
    }

    /**
     * @return The full path to this database file.
     */
    public String getPath() {
        String path = mDatabase.getPath();
        if (path == null) {
            return ":memory:";
        }
        return path;
    }


    public void executeInTransaction(Runnable r) {
        // Ported from code which started the transaction outside the try..finally
        getDatabase().beginTransaction();
        try {
            r.run();
            if (getDatabase().inTransaction()) {
                try {
                    getDatabase().setTransactionSuccessful();
                } catch (Exception e) {
                    // Unsure if this can happen - copied the structure from endTransaction()
                    Timber.w(e);
                }
            } else {
                Timber.w("Not in a transaction. Cannot mark transaction successful.");
            }
        } finally {
            safeEndInTransaction(getDatabase());
        }
    }

    public static void safeEndInTransaction(DB database) {
        safeEndInTransaction(database.getDatabase());
    }

    public static void safeEndInTransaction(SupportSQLiteDatabase database) {
        if (database.inTransaction()) {
            try {
                database.endTransaction();
            } catch (Exception e) {
                // endTransaction throws about invalid transaction even when you check first!
                Timber.w(e);
            }
        } else {
            Timber.w("Not in a transaction. Cannot end transaction.");
        }
    }

    @FunctionalInterface
    public interface OpenHelperFactory {
        SupportSQLiteOpenHelper.Factory getFactory();
    }
}
