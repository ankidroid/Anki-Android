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
package com.ichi2.anki.libanki

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import androidx.annotation.WorkerThread
import com.ichi2.anki.common.utils.annotation.KotlinCleanup

/**
 * Database accessor for AnkiDroid, providing convenience methods (e.g. [queryStringList])
 */
// TODO: see if we can turn query methods into extensions
//  probably hard due to the casting of varargs Any to Array<out Any?>
@KotlinCleanup("Improve documentation")
@WorkerThread
interface DB {
    /**
     * Closes a previously opened database connection.
     */
    fun close()

    fun query(
        query: String,
        vararg selectionArgs: Any,
    ): Cursor

    fun execute(
        sql: String,
        vararg `object`: Any?,
    )

    /**
     * WARNING: This is a convenience method that splits SQL scripts into separate queries with semicolons (;)
     * as the delimiter. Only use this method on internal functions where we can guarantee that the script does
     * not contain any non-statement-terminating semicolons.
     */
    fun executeScript(sql: String)

    /**
     * Convenience method for querying the database for a single integer result.
     *
     * @param query The raw SQL query to use.
     * @return The integer result of the query.
     */
    fun queryScalar(
        query: String,
        vararg selectionArgs: Any,
    ): Int

    @Throws(SQLException::class)
    fun queryString(
        query: String,
        vararg bindArgs: Any,
    ): String

    fun queryLongScalar(
        query: String,
        vararg bindArgs: Any,
    ): Long

    /**
     * Convenience method for querying the database for an entire column of long.
     *
     * @param query The SQL query statement.
     * @return An ArrayList with the contents of the specified column.
     */
    fun queryLongList(
        query: String,
        vararg bindArgs: Any,
    ): ArrayList<Long>

    /**
     * Convenience method for querying the database for an entire column of String.
     *
     * @param query The SQL query statement.
     * @return An ArrayList with the contents of the specified column.
     */
    fun queryStringList(
        query: String,
        vararg bindArgs: Any,
    ): ArrayList<String>

    /** update must always be called via DB in order to mark the db as changed  */
    fun update(
        table: String,
        values: ContentValues,
        whereClause: String? = null,
        whereArgs: Array<String>? = null,
    ): Int

    /** insert must always be called via DB in order to mark the db as changed  */
    fun insert(
        table: String,
        values: ContentValues,
    ): Long

    /**
     * @return The full path to this database file.
     */
    val path: String
}
