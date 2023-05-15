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
 */
package com.ichi2.libanki

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.WorkerThread
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CrashReportService.sendExceptionReport
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.utils.DatabaseChangeDecorator
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase
import org.intellij.lang.annotations.Language
import timber.log.Timber

/**
 * Database layer for AnkiDroid. Wraps an SupportSQLiteDatabase (provided by either the Rust backend
 * or the Android framework), and provides some helpers on top.
 */
@KotlinCleanup("Improve documentation")
@WorkerThread
class DB(db: SupportSQLiteDatabase) {
    /**
     * The collection, which is actually an SQLite database.
     */
    val database: SupportSQLiteDatabase = DatabaseChangeDecorator(db)
    var mod = false

    /**
     * The default AnkiDroid SQLite database callback.
     *
     * IMPORTANT: this disables the default Android behaviour of removing the file if corruption
     * is encountered.
     *
     * We do not handle versioning or connection config using the framework APIs, so those methods
     * do nothing in our implementation. However, we on corruption events we want to send messages but
     * not delete the database.
     *
     * Note: this does not apply when using the Rust backend (ie for Collection)
     */
    class SupportSQLiteOpenHelperCallback(version: Int) : AnkiSupportSQLiteDatabase.DefaultDbCallback(version) {
        /** Send error message when corruption is encountered. We don't call super() as we don't accidentally
         * want to opt-in to the standard Android behaviour of removing the corrupted file, but as we're
         * inheriting from DefaultDbCallback which does not call super either, it would be technically safe
         * if we did so.  */
        override fun onCorruption(db: SupportSQLiteDatabase) {
            Timber.e("The database has been corrupted: %s", db.path)
            sendExceptionReport(
                RuntimeException("Database corrupted"),
                "DB.MyDbErrorHandler.onCorruption",
                "Db has been corrupted: " + db.path
            )
            CollectionHelper.instance.closeCollection("Database corrupted")
            DatabaseErrorDialog.databaseCorruptFlag = true
        }
    }

    /**
     * Closes a previously opened database connection.
     */
    fun close() {
        try {
            database.close()
            Timber.d("Database %s closed = %s", database.path, !database.isOpen)
        } catch (e: Exception) {
            // The pre-framework requery API ate this exception, but the framework API exposes it.
            // We may want to propagate it in the future, but for now maintain the old API and log.
            Timber.e(e, "Failed to close database %s", database.path)
        }
    }

    // Allows to avoid using new Object[]
    fun query(@Language("SQL") query: String, vararg selectionArgs: Any): Cursor {
        return database.query(query, selectionArgs)
    }

    /**
     * Convenience method for querying the database for a single integer result.
     *
     * @param query The raw SQL query to use.
     * @return The integer result of the query.
     */
    fun queryScalar(@Language("SQL") query: String, vararg selectionArgs: Any): Int {
        val scalar: Int
        database.query(query, selectionArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getInt(0)
        }
        return scalar
    }

    @Throws(SQLException::class)
    fun queryString(@Language("SQL") query: String, vararg bindArgs: Any): String {
        database.query(query, bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                throw SQLException("No result for query: $query")
            }
            return cursor.getString(0)
        }
    }

    fun queryLongScalar(@Language("SQL") query: String, vararg bindArgs: Any): Long {
        var scalar: Long
        database.query(query, bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getLong(0)
        }
        return scalar
    }

    /**
     * Convenience method for querying the database for an entire column of long.
     *
     * @param query The SQL query statement.
     * @return An ArrayList with the contents of the specified column.
     */
    fun queryLongList(@Language("SQL") query: String, vararg bindArgs: Any): ArrayList<Long> {
        val results = ArrayList<Long>()
        database.query(query, bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getLong(0))
            }
        }
        return results
    }

    /**
     * Convenience method for querying the database for an entire column of String.
     *
     * @param query The SQL query statement.
     * @return An ArrayList with the contents of the specified column.
     */
    fun queryStringList(@Language("SQL") query: String, vararg bindArgs: Any): ArrayList<String> {
        val results = ArrayList<String>()
        database.query(query, bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        return results
    }

    fun execute(@Language("SQL") sql: String, vararg `object`: Any?) {
        val s = sql.trim { it <= ' ' }.lowercase()
        // mark modified?
        for (mo in MOD_SQL_STATEMENTS) {
            if (s.startsWith(mo)) {
                break
            }
        }
        database.execSQL(sql, `object`)
    }

    /**
     * WARNING: This is a convenience method that splits SQL scripts into separate queries with semicolons (;)
     * as the delimiter. Only use this method on internal functions where we can guarantee that the script does
     * not contain any non-statement-terminating semicolons.
     */
    @KotlinCleanup("""Use Kotlin string. Change split so that there is no empty string after last ";".""")
    fun executeScript(@Language("SQL") sql: String) {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        val queries = java.lang.String(sql).split(";")
        for (query in queries) {
            database.execSQL(query)
        }
    }

    /** update must always be called via DB in order to mark the db as changed  */
    fun update(
        table: String,
        values: ContentValues,
        whereClause: String? = null,
        whereArgs: Array<String>? = null
    ): Int {
        return database.update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs)
    }

    /** insert must always be called via DB in order to mark the db as changed  */
    fun insert(table: String, values: ContentValues): Long {
        return database.insert(table, SQLiteDatabase.CONFLICT_NONE, values)
    }

    /**
     * @return The full path to this database file.
     */
    val path: String
        get() = database.path ?: ":memory:"

    companion object {
        private val MOD_SQL_STATEMENTS = arrayOf("insert", "update", "delete")

        /**
         * Open a connection using the system framework.
         */
        fun withAndroidFramework(context: Context, path: String): DB {
            val db = AnkiSupportSQLiteDatabase.withFramework(
                context,
                path,
                SupportSQLiteOpenHelperCallback(1)
            )
            db.disableWriteAheadLogging()
            db.query("PRAGMA synchronous = 2")
            return DB(db)
        }

        /**
         * Wrap a Rust backend connection (which provides an SQL interface).
         * Caller is responsible for opening&closing the database.
         */
        fun withRustBackend(backend: Backend): DB {
            return DB(AnkiSupportSQLiteDatabase.withRustBackend(backend))
        }
    }
}
