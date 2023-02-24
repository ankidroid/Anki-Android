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
import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import net.ankiweb.rsdroid.RustCleanup
import java.util.*

/** Detects any database modifications and notifies the sync status of the application  */
@RustCleanup("After migrating to new backend, can use backend call instead of this class.")
class DatabaseChangeDecorator(val wrapped: SupportSQLiteDatabase) : SupportSQLiteDatabase by wrapped {
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

    @Throws(SQLException::class)
    override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long {
        val insert = wrapped.insert(table, conflictAlgorithm, values)
        markDataAsChanged()
        return insert
    }

    override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int {
        val delete = wrapped.delete(table, whereClause, whereArgs)
        markDataAsChanged()
        return delete
    }

    override fun update(
        table: String,
        conflictAlgorithm: Int,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<out Any?>?
    ): Int {
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
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
        wrapped.execSQL(sql, bindArgs)
        checkForChanges(sql)
    }

    companion object {
        private val MOD_SQLS = arrayOf("insert", "update", "delete")
    }
}
