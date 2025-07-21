/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.libanki.testutils

import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.DB
import com.ichi2.anki.libanki.LibAnki
import com.ichi2.anki.libanki.Storage.collection
import com.ichi2.anki.libanki.testutils.InMemoryCollectionManager.discardBackend
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber

/**
 * Trimmed down version of `com.ichi2.anki.CollectionManager` which can be used without a reference
 * to Android & AnkiDroid app logic
 */
interface TestCollectionManager {
    /**
     * Close the currently cached backend and discard it. Saves and closes the collection if open.
     */
    suspend fun discardBackend()

    /**
     * Returns a reference to the open collection. This is not
     * safe, as code in other threads could open or close
     * the collection while the reference is held.
     */
    fun getColUnsafe(): Collection
}

/**
 * Lightweight CollectionManager: in memory-DB and no media folders/DB
 */
@VisibleForTesting
object InMemoryCollectionManager : TestCollectionManager {
    @Suppress("DEPRECATION") // deprecation is used to limit access
    private var backend: Backend?
        get() = LibAnki.backend
        set(value) {
            LibAnki.backend = value
        }

    @Suppress("DEPRECATION") // deprecation is used to limit access
    private var collection: Collection?
        get() = LibAnki.collection
        set(value) {
            LibAnki.collection = value
        }

    fun ensureOpenInner() {
        ensureBackendInner()
        if (collection?.dbClosed == false) return
        collection =
            collection(
                collectionFiles = CollectionFiles.InMemory,
                databaseBuilder = { backend -> createDatabaseUsingRustBackend(backend) },
                backend = backend,
            )
    }

    private fun ensureBackendInner() {
        if (backend == null) {
            backend = BackendFactory.getBackend()
        }
    }

    override fun getColUnsafe(): Collection {
        ensureOpenInner()
        return collection!!
    }

    /**
     * Close the currently cached backend and discard it. Useful when enabling the V16 scheduler in the
     * dev preferences, or if the active language changes. Saves and closes the collection if open.
     */
    override suspend fun discardBackend() {
        discardBackendInner()
    }

    /** See [discardBackend]. This must only be run inside the queue. */
    private fun discardBackendInner() {
        ensureClosedInner()
        if (backend != null) {
            backend!!.close()
            backend = null
        }
    }

    private fun ensureClosedInner() {
        if (collection == null) {
            return
        }
        try {
            collection!!.close()
        } catch (exc: Exception) {
            Timber.e("swallowing error on close: $exc")
        }
        collection = null
    }
}

/**
 * Wrap a Rust backend connection (which provides an SQL interface).
 * Caller is responsible for opening&closing the database.
 */
@DuplicatedCode("AnkiDroid:createDatabaseUsingRustBackend")
fun createDatabaseUsingRustBackend(backend: Backend): DB = DB(AnkiSupportSQLiteDatabase.withRustBackend(backend))
