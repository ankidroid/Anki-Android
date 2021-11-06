/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteTransactionListener;
import android.os.CancellationSignal;
import android.util.Pair;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteStatement;

/** Detects any database modifications and notifies the sync status of the application */
public class DatabaseChangeDecorator implements SupportSQLiteDatabase {

    private static final String[] MOD_SQLS = new String[] { "insert", "update", "delete" };

    private final SupportSQLiteDatabase mWrapped;


    public DatabaseChangeDecorator(SupportSQLiteDatabase wrapped) {
        this.mWrapped = wrapped;
    }

    private void markDataAsChanged() {
        SyncStatus.markDataAsChanged();
    }

    private boolean needsComplexCheck() {
        // if we're marked in memory, we can assume no changes - this class only sets the mark.
        return !SyncStatus.hasBeenMarkedAsChangedInMemory();
    }

    private void checkForChanges(String sql) {
        if (!needsComplexCheck()) {
            return;
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        String upper = sql.toUpperCase(Locale.ROOT);
        for (String modString : MOD_SQLS) {
            if (startsWithIgnoreCase(lower, upper, modString)) {
                markDataAsChanged();
                break;
            }
        }
    }


    private boolean startsWithIgnoreCase(String lowerHaystack, String upperHaystack, String needle) {
        // Needs to do both according to https://stackoverflow.com/a/38947571
        return lowerHaystack.startsWith(needle) || upperHaystack.startsWith(needle.toUpperCase(Locale.ROOT));
    }


    public SupportSQLiteStatement compileStatement(String sql) {
        SupportSQLiteStatement supportSQLiteStatement = mWrapped.compileStatement(sql);
        checkForChanges(sql); //technically a little hasty - as the statement hasn't been executed.
        return supportSQLiteStatement;
    }


    public void beginTransaction() {
        mWrapped.beginTransaction();
    }


    public void beginTransactionNonExclusive() {
        mWrapped.beginTransactionNonExclusive();
    }


    public void beginTransactionWithListener(SQLiteTransactionListener transactionListener) {
        mWrapped.beginTransactionWithListener(transactionListener);
    }


    public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener transactionListener) {
        mWrapped.beginTransactionWithListenerNonExclusive(transactionListener);
    }


    public void endTransaction() {
        mWrapped.endTransaction();
    }


    public void setTransactionSuccessful() {
        mWrapped.setTransactionSuccessful();
    }


    public boolean inTransaction() {
        return mWrapped.inTransaction();
    }


    public boolean isDbLockedByCurrentThread() {
        return mWrapped.isDbLockedByCurrentThread();
    }


    public boolean yieldIfContendedSafely() {
        return mWrapped.yieldIfContendedSafely();
    }


    public boolean yieldIfContendedSafely(long sleepAfterYieldDelay) {
        return mWrapped.yieldIfContendedSafely(sleepAfterYieldDelay);
    }


    public int getVersion() {
        return mWrapped.getVersion();
    }


    public void setVersion(int version) {
        mWrapped.setVersion(version);
    }


    public long getMaximumSize() {
        return mWrapped.getMaximumSize();
    }


    public long setMaximumSize(long numBytes) {
        return mWrapped.setMaximumSize(numBytes);
    }


    public long getPageSize() {
        return mWrapped.getPageSize();
    }


    public void setPageSize(long numBytes) {
        mWrapped.setPageSize(numBytes);
    }


    public Cursor query(String query) {
        return mWrapped.query(query);
    }


    public Cursor query(String query, Object[] bindArgs) {
        return mWrapped.query(query, bindArgs);
    }


    public Cursor query(SupportSQLiteQuery query) {
        return mWrapped.query(query);
    }


    public Cursor query(SupportSQLiteQuery query, CancellationSignal cancellationSignal) {
        return mWrapped.query(query, cancellationSignal);
    }


    public long insert(String table, int conflictAlgorithm, ContentValues values) throws SQLException {
        long insert = mWrapped.insert(table, conflictAlgorithm, values);
        markDataAsChanged();
        return insert;
    }


    public int delete(String table, String whereClause, Object[] whereArgs) {
        int delete = mWrapped.delete(table, whereClause, whereArgs);
        markDataAsChanged();
        return delete;
    }


    public int update(String table, int conflictAlgorithm, ContentValues values, String whereClause, Object[] whereArgs) {
        int update = mWrapped.update(table, conflictAlgorithm, values, whereClause, whereArgs);
        markDataAsChanged();
        return update;
    }


    public void execSQL(String sql) throws SQLException {
        mWrapped.execSQL(sql);
        checkForChanges(sql);
    }


    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        mWrapped.execSQL(sql, bindArgs);
        checkForChanges(sql);
    }


    public boolean isReadOnly() {
        return mWrapped.isReadOnly();
    }


    public boolean isOpen() {
        return mWrapped.isOpen();
    }


    public boolean needUpgrade(int newVersion) {
        return mWrapped.needUpgrade(newVersion);
    }


    public String getPath() {
        return mWrapped.getPath();
    }


    public void setLocale(Locale locale) {
        mWrapped.setLocale(locale);
    }


    public void setMaxSqlCacheSize(int cacheSize) {
        mWrapped.setMaxSqlCacheSize(cacheSize);
    }


    public void setForeignKeyConstraintsEnabled(boolean enable) {
        mWrapped.setForeignKeyConstraintsEnabled(enable);
    }


    public boolean enableWriteAheadLogging() {
        return mWrapped.enableWriteAheadLogging();
    }


    public void disableWriteAheadLogging() {
        mWrapped.disableWriteAheadLogging();
    }


    public boolean isWriteAheadLoggingEnabled() {
        return mWrapped.isWriteAheadLoggingEnabled();
    }


    public List<Pair<String, String>> getAttachedDbs() {
        return mWrapped.getAttachedDbs();
    }


    public boolean isDatabaseIntegrityOk() {
        return mWrapped.isDatabaseIntegrityOk();
    }


    public void close() throws IOException {
        mWrapped.close();
    }

    public SupportSQLiteDatabase getWrapped() {
        return mWrapped;
    }
}

