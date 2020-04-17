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

    private final SupportSQLiteDatabase wrapped;


    public DatabaseChangeDecorator(SupportSQLiteDatabase wrapped) {
        this.wrapped = wrapped;
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
        String lower = sql.toLowerCase();
        String upper = sql.toUpperCase();
        for (String modString : MOD_SQLS) {
            if (startsWithIgnoreCase(lower, upper, modString)) {
                markDataAsChanged();
                break;
            }
        }
    }


    private boolean startsWithIgnoreCase(String lowerHaystack, String upperHaystack, String needle) {
        // Needs to do both according to https://stackoverflow.com/a/38947571
        return lowerHaystack.startsWith(needle) || upperHaystack.startsWith(needle.toUpperCase());
    }


    public SupportSQLiteStatement compileStatement(String sql) {
        SupportSQLiteStatement supportSQLiteStatement = wrapped.compileStatement(sql);
        checkForChanges(sql); //technically a little hasty - as the statement hasn't been executed.
        return supportSQLiteStatement;
    }


    public void beginTransaction() {
        wrapped.beginTransaction();
    }


    public void beginTransactionNonExclusive() {
        wrapped.beginTransactionNonExclusive();
    }


    public void beginTransactionWithListener(SQLiteTransactionListener transactionListener) {
        wrapped.beginTransactionWithListener(transactionListener);
    }


    public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener transactionListener) {
        wrapped.beginTransactionWithListenerNonExclusive(transactionListener);
    }


    public void endTransaction() {
        wrapped.endTransaction();
    }


    public void setTransactionSuccessful() {
        wrapped.setTransactionSuccessful();
    }


    public boolean inTransaction() {
        return wrapped.inTransaction();
    }


    public boolean isDbLockedByCurrentThread() {
        return wrapped.isDbLockedByCurrentThread();
    }


    public boolean yieldIfContendedSafely() {
        return wrapped.yieldIfContendedSafely();
    }


    public boolean yieldIfContendedSafely(long sleepAfterYieldDelay) {
        return wrapped.yieldIfContendedSafely(sleepAfterYieldDelay);
    }


    public int getVersion() {
        return wrapped.getVersion();
    }


    public void setVersion(int version) {
        wrapped.setVersion(version);
    }


    public long getMaximumSize() {
        return wrapped.getMaximumSize();
    }


    public long setMaximumSize(long numBytes) {
        return wrapped.setMaximumSize(numBytes);
    }


    public long getPageSize() {
        return wrapped.getPageSize();
    }


    public void setPageSize(long numBytes) {
        wrapped.setPageSize(numBytes);
    }


    public Cursor query(String query) {
        return wrapped.query(query);
    }


    public Cursor query(String query, Object[] bindArgs) {
        return wrapped.query(query, bindArgs);
    }


    public Cursor query(SupportSQLiteQuery query) {
        return wrapped.query(query);
    }


    public Cursor query(SupportSQLiteQuery query, CancellationSignal cancellationSignal) {
        return wrapped.query(query, cancellationSignal);
    }


    public long insert(String table, int conflictAlgorithm, ContentValues values) throws SQLException {
        long insert = wrapped.insert(table, conflictAlgorithm, values);
        markDataAsChanged();
        return insert;
    }


    public int delete(String table, String whereClause, Object[] whereArgs) {
        int delete = wrapped.delete(table, whereClause, whereArgs);
        markDataAsChanged();
        return delete;
    }


    public int update(String table, int conflictAlgorithm, ContentValues values, String whereClause, Object[] whereArgs) {
        int update = wrapped.update(table, conflictAlgorithm, values, whereClause, whereArgs);
        markDataAsChanged();
        return update;
    }


    public void execSQL(String sql) throws SQLException {
        wrapped.execSQL(sql);
        checkForChanges(sql);
    }


    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        wrapped.execSQL(sql, bindArgs);
        checkForChanges(sql);
    }


    public boolean isReadOnly() {
        return wrapped.isReadOnly();
    }


    public boolean isOpen() {
        return wrapped.isOpen();
    }


    public boolean needUpgrade(int newVersion) {
        return wrapped.needUpgrade(newVersion);
    }


    public String getPath() {
        return wrapped.getPath();
    }


    public void setLocale(Locale locale) {
        wrapped.setLocale(locale);
    }


    public void setMaxSqlCacheSize(int cacheSize) {
        wrapped.setMaxSqlCacheSize(cacheSize);
    }


    public void setForeignKeyConstraintsEnabled(boolean enable) {
        wrapped.setForeignKeyConstraintsEnabled(enable);
    }


    public boolean enableWriteAheadLogging() {
        return wrapped.enableWriteAheadLogging();
    }


    public void disableWriteAheadLogging() {
        wrapped.disableWriteAheadLogging();
    }


    public boolean isWriteAheadLoggingEnabled() {
        return wrapped.isWriteAheadLoggingEnabled();
    }


    public List<Pair<String, String>> getAttachedDbs() {
        return wrapped.getAttachedDbs();
    }


    public boolean isDatabaseIntegrityOk() {
        return wrapped.isDatabaseIntegrityOk();
    }


    public void close() throws IOException {
        wrapped.close();
    }

    public SupportSQLiteDatabase getWrapped() {
        return wrapped;
    }
}

