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
package com.ichi2.utils

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.io.IOException
import java.util.*
import kotlin.Throws

/** Detects any database modifications and notifies the sync status of the application  */
@KotlinCleanup("Convert to Kotlin Delegation as a medium priority extension")
class DatabaseChangeDecorator(val wrapped: SupportSQLiteDatabase) : SupportSQLiteDatabase {
    private fun markDataAsChanged() {
        SyncStatus.markDataAsChanged()
    }

    private fun needsComplexCheck(): Boolean {
        // if we're marked in memory, we can assume no changes - this class only sets the mark.
        return !SyncStatus.hasBeenMarkedAsChangedInMemory()
    }

    private fun checkForChanges(sql: String) {
        if (!needsComplexCheck()) {
            return
        }
        val lower = sql.lowercase(Locale.ROOT)
        val upper = sql.uppercase(Locale.ROOT)
        for (modString in MOD_SQLS) {
            if (startsWithIgnoreCase(lower, upper, modString)) {
                markDataAsChanged()
                break
            }
        }
    }

    private fun startsWithIgnoreCase(lowerHaystack: String, upperHaystack: String, needle: String): Boolean {
        // Needs to do both according to https://stackoverflow.com/a/38947571
        return lowerHaystack.startsWith(needle) || upperHaystack.startsWith(needle.uppercase(Locale.ROOT))
    }

    override fun compileStatement(sql: String): SupportSQLiteStatement {
        val supportSQLiteStatement = wrapped.compileStatement(sql)
        checkForChanges(sql) // technically a little hasty - as the statement hasn't been executed.
        return supportSQLiteStatement
    }

    override fun beginTransaction() {
        wrapped.beginTransaction()
    }

    override fun beginTransactionNonExclusive() {
        wrapped.beginTransactionNonExclusive()
    }

    override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) {
        wrapped.beginTransactionWithListener(transactionListener)
    }

    override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) {
        wrapped.beginTransactionWithListenerNonExclusive(transactionListener)
    }

    override fun endTransaction() {
        wrapped.endTransaction()
    }

    override fun setTransactionSuccessful() {
        wrapped.setTransactionSuccessful()
    }

    override fun inTransaction(): Boolean {
        return wrapped.inTransaction()
    }

    override fun isDbLockedByCurrentThread(): Boolean {
        return wrapped.isDbLockedByCurrentThread
    }

    override fun yieldIfContendedSafely(): Boolean {
        return wrapped.yieldIfContendedSafely()
    }

    override fun yieldIfContendedSafely(sleepAfterYieldDelay: Long): Boolean {
        return wrapped.yieldIfContendedSafely(sleepAfterYieldDelay)
    }

    override fun getVersion(): Int {
        return wrapped.version
    }

    override fun setVersion(version: Int) {
        wrapped.version = version
    }

    override fun getMaximumSize(): Long {
        return wrapped.maximumSize
    }

    override fun setMaximumSize(numBytes: Long): Long {
        return wrapped.setMaximumSize(numBytes)
    }

    override fun getPageSize(): Long {
        return wrapped.pageSize
    }

    override fun setPageSize(numBytes: Long) {
        wrapped.pageSize = numBytes
    }

    override fun query(query: String): Cursor {
        return wrapped.query(query)
    }

    override fun query(query: String, bindArgs: Array<Any>?): Cursor {
        return wrapped.query(query, bindArgs)
    }

    override fun query(query: SupportSQLiteQuery): Cursor {
        return wrapped.query(query)
    }

    override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal): Cursor {
        return wrapped.query(query, cancellationSignal)
    }

    @Throws(SQLException::class)
    override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long {
        val insert = wrapped.insert(table, conflictAlgorithm, values)
        markDataAsChanged()
        return insert
    }

    override fun delete(table: String, whereClause: String, whereArgs: Array<Any>): Int {
        val delete = wrapped.delete(table, whereClause, whereArgs)
        markDataAsChanged()
        return delete
    }

    override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<Any>?): Int {
        val update = wrapped.update(table, conflictAlgorithm, values, whereClause, whereArgs)
        markDataAsChanged()
        return update
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String) {
        wrapped.execSQL(sql)
        checkForChanges(sql)
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String, bindArgs: Array<Any>) {
        wrapped.execSQL(sql, bindArgs)
        checkForChanges(sql)
    }

    override fun isReadOnly(): Boolean {
        return wrapped.isReadOnly
    }

    override fun isOpen(): Boolean {
        return wrapped.isOpen
    }

    override fun needUpgrade(newVersion: Int): Boolean {
        return wrapped.needUpgrade(newVersion)
    }

    override fun getPath(): String {
        return wrapped.path
    }

    override fun setLocale(locale: Locale) {
        wrapped.setLocale(locale)
    }

    override fun setMaxSqlCacheSize(cacheSize: Int) {
        wrapped.setMaxSqlCacheSize(cacheSize)
    }

    override fun setForeignKeyConstraintsEnabled(enable: Boolean) {
        wrapped.setForeignKeyConstraintsEnabled(enable)
    }

    override fun enableWriteAheadLogging(): Boolean {
        return wrapped.enableWriteAheadLogging()
    }

    override fun disableWriteAheadLogging() {
        wrapped.disableWriteAheadLogging()
    }

    override fun isWriteAheadLoggingEnabled(): Boolean {
        return wrapped.isWriteAheadLoggingEnabled
    }

    override fun getAttachedDbs(): List<Pair<String, String>> {
        return wrapped.attachedDbs
    }

    override fun isDatabaseIntegrityOk(): Boolean {
        return wrapped.isDatabaseIntegrityOk
    }

    @Throws(IOException::class)
    override fun close() {
        wrapped.close()
    }

    companion object {
        private val MOD_SQLS = arrayOf("insert", "update", "delete")
    }
}
