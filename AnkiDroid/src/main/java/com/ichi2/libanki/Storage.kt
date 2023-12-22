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
package com.ichi2.libanki

import com.ichi2.anki.UIUtils.getDayStart
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager.time
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import java.io.File

object Storage {
    var isInMemory = false
        private set

    /**
     *  Open a new or existing collection.
     *
     * @param path The path to the collection.anki2 database. Should be unicode.
     * path should be tested with [File.exists] and [File.canWrite] before this is called.
     * */
    fun collection(
        path: String,
        log: Boolean = false,
        backend: Backend? = null,
    ): Collection {
        val backend2 = backend ?: BackendFactory.getBackend()
        return Collection(path, log, backend2)
    }

    /**
     * Called as part of [Collection] initialization. Don't call directly.
     */
    internal fun openDB(
        path: String,
        backend: Backend,
        afterFullSync: Boolean,
    ): Pair<DB, Boolean> {
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
            createDB(db, time)
        }
        return Pair(db, create)
    }

    private fun createDB(
        db: DB,
        time: Time,
    ) {
        // This line is required for testing - otherwise Rust will override a mocked time.
        db.execute("update col set crt = ?", getDayStart(time) / 1000)
    }

    fun setUseInMemory(useInMemoryDatabase: Boolean) {
        isInMemory = useInMemoryDatabase
    }
}
