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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import com.ichi2.anki.UIUtils.getDayStart
import com.ichi2.libanki.exception.UnknownDatabaseVersionException
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

object Storage {
    var isInMemory = false
        private set
    /**
     * Whether the collection can be opened. If true, [.Collection]
     * throws a [SQLiteDatabaseLockedException]
     */
    /**
     * The collection is locked from being opened via the [Storage] class. All collection accesses in the app
     * should use this class.
     *
     *
     * Opening a collection will then throw [SQLiteDatabaseLockedException]
     *
     *
     * A collection which was opened before sIsLocked was set will be usable until it is closed.
     */
    var isLocked = false
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
        if (isLocked) {
            throw SQLiteDatabaseLockedException("AnkiDroid has locked the database")
        }
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
            _createDB(db, time, backend)
        }
        return Pair(db, create)
    }

    /**
     * Add note types when creating database
     */
    @KotlinCleanup("col non-null")
    fun addNoteTypes(col: Collection?, backend: Backend) {
        if (!backend.legacySchema) {
            Timber.i("skipping adding note types - already exist")
            return
        }
        // add in reverse order so basic is default
        for (i in StdModels.STD_MODELS.indices.reversed()) {
            StdModels.STD_MODELS[i].add(col!!)
        }
    }

    private fun _createDB(db: DB, time: Time, backend: Backend) {
        if (backend.legacySchema) {
            _setColVars(db, time)
        }
        // This line is required for testing - otherwise Rust will override a mocked time.
        db.execute("update col set crt = ?", getDayStart(time) / 1000)
    }

    private fun _setColVars(db: DB, time: Time) {
        val g = JSONObject(Decks.DEFAULT_DECK)
        g.put("id", 1)
        g.put("name", "Default")
        g.put("conf", 1)
        g.put("mod", time.intTime())
        val gc = JSONObject(Decks.DEFAULT_CONF)
        gc.put("id", 1)
        val ag = JSONObject()
        ag.put("1", g)
        val agc = JSONObject()
        agc.put("1", gc)
        val values = ContentValues()
        values.put("conf", Collection.DEFAULT_CONF)
        values.put("decks", Utils.jsonToString(ag))
        values.put("dconf", Utils.jsonToString(agc))
        db.update("col", values)
    }

    private fun _updateIndices(db: DB) {
        db.execute("create index if not exists ix_notes_usn on notes (usn);")
        db.execute("create index if not exists ix_cards_usn on cards (usn);")
        db.execute("create index if not exists ix_revlog_usn on revlog (usn);")
        db.execute("create index if not exists ix_cards_nid on cards (nid);")
        db.execute("create index if not exists ix_cards_sched on cards (did, queue, due);")
        db.execute("create index if not exists ix_revlog_cid on revlog (cid);")
        db.execute("create index if not exists ix_notes_csum on notes (csum);)")
    }

    fun addIndices(db: DB) {
        _updateIndices(db)
    }

    fun setUseInMemory(useInMemoryDatabase: Boolean) {
        isInMemory = useInMemoryDatabase
    }

    /**
     * Allows the collection to be opened
     */
    fun unlockCollection() {
        isLocked = false
        Timber.i("unlocked collection")
    }

    /**
     * Stops the collection from being opened via throwing [SQLiteDatabaseLockedException].
     * does not affect a currently open collection
     *
     *
     * To ensure that the collection is locked and unopenable:
     *
     *
     * * Lock the collection
     * * Get an instance of the collection, if it succeeds, close it
     * * Ensure the collection is locked by trying to open it, it should fail.
     * * Perform your operation
     * * Unlock the collection
     */
    fun lockCollection() {
        isLocked = true
        Timber.i("locked collection. Opening will throw SQLiteDatabaseLockedException")
    }
}
