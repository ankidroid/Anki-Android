/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                      *
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
 ****************************************************************************************/

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.backend.backendError
import com.ichi2.anki.servicelayer.ValidatedMigrationSourceAndDestination
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Storage.collection
import com.ichi2.libanki.importCollectionPackage
import com.ichi2.utils.Threads
import com.ichi2.utils.isRobolectric
import kotlinx.coroutines.*
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.Translations
import timber.log.Timber
import java.io.File

object CollectionManager {
    /**
     * The currently active backend, which is created on demand via [ensureBackend], and
     * implicitly via [ensureOpen] and routines like [withCol].
     * The backend is long-lived, and will generally only be closed when switching interface
     * languages or changing schema versions. A closed backend cannot be reused, and a new one
     * must be created.
     */
    private var backend: Backend? = null

    /**
     * The current collection, which is opened on demand via [withCol]. If you need to
     * close and reopen the collection in an atomic operation, add a new method that
     * calls [withQueue], and then executes [ensureClosedInner] and [ensureOpenInner] inside it.
     * A closed collection can be detected via [withOpenColOrNull] or by checking [Collection.dbClosed].
     */
    private var collection: Collection? = null

    private var queue: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    @VisibleForTesting
    var emulateOpenFailure = false

    /**
     * Execute the provided block on a serial background queue, to ensure
     * concurrent access does not happen.
     *
     * The background queue is run in a [Dispatchers.IO] context.
     * It's important that the block is not suspendable - if it were, it would allow
     * multiple requests to be interleaved when a suspend point was hit.
     *
     * TODO Disallow running functions that are supposed to be run inside the queue outside of it.
     *   For instance, this can be done by marking a [block] with a context
     *   that cannot be instantiated outside of this class:
     *
     *       suspend fun<T> withQueue(block: context(Queue) () -> T): T {
     *          return withContext(collectionOperationsDispatcher) {
     *              block(queue)
     *          }
     *      }
     *
     *   Then, only functions that are also marked can be run inside the block:
     *
     *       context(Queue) suspend fun canOnlyBeRunInWithQueue()
     */
    private suspend fun<T> withQueue(@WorkerThread block: CollectionManager.() -> T): T {
        return withContext(queue) {
            this@CollectionManager.block()
        }
    }

    /**
     * Execute the provided block with the collection, opening if necessary.
     *
     * Calls are serialized, and run in background [Dispatchers.IO] thread.
     *
     * Parallel calls to this function are guaranteed to be serialized, so you can be
     * sure the collection won't be closed or modified by another thread. This guarantee
     * does not hold if legacy code calls [getColUnsafe].
     */
    suspend fun <T> withCol(@WorkerThread block: Collection.() -> T): T {
        return withQueue {
            ensureOpenInner()
            block(collection!!)
        }
    }

    /**
     * Execute the provided block if the collection is already open. See [withCol] for more.
     * Since the block may return a null value, and a null value will also be returned in the
     * case of the collection being closed, if the calling code needs to distinguish between
     * these two cases, it should wrap the return value of the block in a class (eg Optional),
     * instead of returning a nullable object.
     */
    suspend fun<T> withOpenColOrNull(@WorkerThread block: Collection.() -> T): T? {
        return withQueue {
            if (collection != null && !collection!!.dbClosed) {
                block(collection!!)
            } else {
                null
            }
        }
    }

    /**
     * Return a handle to the backend, creating if necessary. This should only be used
     * for routines that don't depend on an open or closed collection, such as checking
     * the current progress state when importing a colpkg file. While the backend is
     * thread safe and can be accessed concurrently, if another thread closes the collection
     * and you call a routine that expects an open collection, it will result in an error.
     */
    fun getBackend(): Backend {
        if (backend == null) {
            runBlocking { withQueue { ensureBackendInner() } }
        }
        return backend!!
    }

    /**
     * Translations provided by the Rust backend/Anki desktop code.
     */
    val TR: Translations
        get() {
            // we bypass the lock here so that translations are fast - conflicts are unlikely,
            // as the backend is only ever changed on language preference change or schema switch
            return getBackend().tr
        }

    fun compareAnswer(expected: String, given: String): String {
        // bypass the lock, as the type answer code is heavily nested in non-suspend functions
        return getBackend().compareAnswer(expected, given)
    }

    /**
     * Close the currently cached backend and discard it. Useful when enabling the V16 scheduler in the
     * dev preferences, or if the active language changes. Saves and closes the collection if open.
     */
    suspend fun discardBackend() {
        withQueue {
            discardBackendInner()
        }
    }

    /** See [discardBackend]. This must only be run inside the queue. */
    private fun discardBackendInner() {
        ensureClosedInner()
        if (backend != null) {
            backend!!.close()
            backend = null
        }
    }

    /**
     * Open the backend if it's not already open.
     */
    private suspend fun ensureBackend() {
        withQueue {
            ensureBackendInner()
        }
    }

    /** See [ensureBackend]. This must only be run inside the queue. */
    private fun ensureBackendInner() {
        if (backend == null) {
            backend = BackendFactory.getBackend()
        }
    }

    /**
     * If the collection is open, close it.
     */
    suspend fun ensureClosed() {
        withQueue {
            ensureClosedInner()
        }
    }

    /** See [ensureClosed]. This must only be run inside the queue. */
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

    /**
     * Open the collection, if it's not already open.
     *
     * Automatically called by [withCol]. Can be called directly to ensure collection
     * is loaded at a certain point in time, or to ensure no errors occur.
     */
    private suspend fun ensureOpen() {
        withQueue {
            ensureOpenInner()
        }
    }

    /** See [ensureOpen]. This must only be run inside the queue. */
    private fun ensureOpenInner() {
        ensureBackendInner()
        if (emulateOpenFailure) {
            throw BackendException.BackendDbException.BackendDbLockedException(backendError {})
        }
        if (collection == null || collection!!.dbClosed) {
            val path = collectionPathInValidFolder()
            collection =
                collection(path, backend)
        }
    }

    suspend fun deleteCollectionDirectory() {
        withQueue {
            ensureClosedInner()
            getCollectionDirectory().deleteRecursively()
        }
    }

    fun getCollectionDirectory() =
        // Allow execution if AnkiDroidApp.instance is not initialized
        File(CollectionHelper.getCurrentAnkiDroidDirectoryOptionalContext(AnkiDroidApp.sharedPrefs()) { AnkiDroidApp.instance })

    /** Ensures the AnkiDroid directory is created, then returns the path to the collection file
     * inside it. */
    fun collectionPathInValidFolder(): String {
        val dir = getCollectionDirectory().path
        CollectionHelper.initializeAnkiDroidDirectory(dir)
        return File(dir, "collection.anki2").absolutePath
    }

    /**
     * Like [withQueue], but can be used in a synchronous context.
     *
     * Note: Because [runBlocking] inside `RobolectricTest.runTest` will lead to
     * deadlocks, this will not block when run under Robolectric,
     * and there is no guarantee about concurrent access.
     */
    private fun <T> blockForQueue(block: CollectionManager.() -> T): T {
        return if (isRobolectric) {
            block(this)
        } else {
            runBlocking {
                withQueue(block)
            }
        }
    }

    fun closeCollectionBlocking() {
        runBlocking { ensureClosed() }
    }

    /**
     * Returns a reference to the open collection. This is not
     * safe, as code in other threads could open or close
     * the collection while the reference is held. [withCol]
     * is a better alternative.
     */
    fun getColUnsafe(): Collection {
        return logUIHangs {
            blockForQueue {
                ensureOpenInner()
                collection!!
            }
        }
    }

    /**
     Execute [block]. If it takes more than 100ms of real time, Timber an error like:
     > Blocked main thread for 2424ms: com.ichi2.anki.DeckPicker.onCreateOptionsMenu(DeckPicker.kt:624)
     */
    // using TimeManager breaks a sched test that makes assumptions about the time, so we
    // access the time directly
    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
    private fun <T> logUIHangs(block: () -> T): T {
        val start = System.currentTimeMillis()
        return block().also {
            val elapsed = System.currentTimeMillis() - start
            if (Threads.isOnMainThread && elapsed > 100) {
                val stackTraceElements = Thread.currentThread().stackTrace
                // locate the probable calling file/line in the stack trace, by filtering
                // out our own code, and standard dalvik/java.lang stack frames
                val caller = stackTraceElements.filter {
                    val klass = it.className
                    val toCheck = listOf(
                        "CollectionManager",
                        "dalvik",
                        "java.lang",
                        "CollectionHelper",
                        "AnkiActivity"
                    )
                    for (text in toCheck) {
                        if (text in klass) {
                            return@filter false
                        }
                    }
                    true
                }.first()
                Timber.w("blocked main thread for %dms:\n%s", elapsed, caller)
            }
        }
    }

    /**
     * True if the collection is open. Unsafe, as it has the potential to race.
     */
    fun isOpenUnsafe(): Boolean {
        return logUIHangs {
            blockForQueue {
                if (emulateOpenFailure) {
                    false
                } else {
                    collection?.dbClosed == false
                }
            }
        }
    }

    /**
     Use [col] as collection in tests.
     This collection persists only up to the next (direct or indirect) call to `ensureClosed`
     */
    fun setColForTests(col: Collection?) {
        blockForQueue {
            if (col == null) {
                ensureClosedInner()
            }
            collection = col
        }
    }

    /**
     * Replace the collection with the provided colpkg file if it is valid.
     */
    suspend fun importColpkg(colpkgPath: String) {
        withQueue {
            ensureClosedInner()
            ensureBackendInner()
            importCollectionPackage(backend!!, collectionPathInValidFolder(), colpkgPath)
        }
    }

    /** Migrate collection and media databases to scoped storage.
     * * Closes the collection, and performs the work in our queue so no
     * other code can open the collection while the operation runs. Reopens
     * at the end, and rolls back the path change if reopening fails.
     */
    suspend fun migrateEssentialFiles(context: Context, folders: ValidatedMigrationSourceAndDestination) {
        withQueue {
            ensureClosedInner()
            val migrator = MigrateEssentialFiles(context, folders)
            migrator.migrateFiles()
            migrator.updateCollectionPath()
            try {
                ensureOpenInner()
            } catch (e: Exception) {
                migrator.restoreOldCollectionPath()
                throw e
            }
        }
    }

    fun setTestDispatcher(dispatcher: CoroutineDispatcher) {
        // note: we avoid the call to .limitedParallelism() here,
        // as it does not seem to be compatible with the test scheduler
        queue = dispatcher
    }
}
