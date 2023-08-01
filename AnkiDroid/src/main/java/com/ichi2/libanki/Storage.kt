/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 * *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 */
@file:Suppress("FunctionName") // _createDb and other names defined by libAnki

package com.ichi2.libanki

import android.content.Context
import com.ichi2.anki.UIUtils.getDayStart
import com.ichi2.libanki.exception.UnknownDatabaseVersionException
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager.time
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

object Storage {
    var isInMemory = false
        private set

    /**
     * Helper method for when the collection can't be opened
     */
    @Throws(UnknownDatabaseVersionException::class)
    fun getDatabaseVersion(context: Context, path: String): Int {
        return try {
            if (!File(path).exists()) {
                throw UnknownDatabaseVersionException(FileNotFoundException(path))
            }
            val db = DB.withAndroidFramework(context, path)
            val result = db.queryScalar("SELECT ver FROM col")
            db.close()
            result
        } catch (e: Exception) {
            Timber.w(e, "Can't open database")
            throw UnknownDatabaseVersionException(e)
        }
    }

    /**
     *  Open a new or existing collection.
     *
     * @param Path The path to the collection.anki2 database. Should be unicode.
     * path should be tested with File.exists() and File.canWrite() before this is called.
     * */
    fun collection(
        context: Context,
        path: String,
        server: Boolean = false,
        log: Boolean = false,
        backend: Backend? = null
    ): Collection {
        val backend2 = backend ?: BackendFactory.getBackend(context)
        return Collection(context, path, server, log, backend2)
    }

    /**
     * Called as part of Collection initialization. Don't call directly.
     */
    internal fun openDB(path: String, backend: Backend, afterFullSync: Boolean): Pair<DB, Boolean> {
        val dbFile = File(path)
        var create = !dbFile.exists()
        if (afterFullSync) {
            create = false
        } else {
            backend.openCollection(if (isInMemory) ":memory:" else path)
        }
        val db = DB.withRustBackend(backend)

        // initialize
        if (create) {
            _createDB(db, time)
        }
        return Pair(db, create)
    }

    private fun _createDB(db: DB, time: Time) {
        // This line is required for testing - otherwise Rust will override a mocked time.
        db.execute("update col set crt = ?", getDayStart(time) / 1000)
    }

    fun setUseInMemory(useInMemoryDatabase: Boolean) {
        isInMemory = useInMemoryDatabase
    }
}
