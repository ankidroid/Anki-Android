/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer.scopedstorage

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.exception.RetryableException
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.*
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_DESTINATION
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_SOURCE
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.Companion.PRIORITY_FILES
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.Companion.SAFETY_MARGIN_BYTES
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.Companion.migrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.UserActionRequiredException
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.NumberOfBytes
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Storage
import com.ichi2.libanki.Utils
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.io.Closeable
import java.io.File

/**
 * Algorithm class which represents copying the essential files (collection and media SQL-related
 * files, and .nomedia/collection logs) to a location under scoped storage: [PRIORITY_FILES]
 * This exists as a class to allow overriding operations for fault injection testing
 *
 * Our main concerns here are ensuring that there are no errors, and the graceful handling of issues.
 * One primary concern is whether the failure case leaves files in the destination (scoped) directory.
 *
 * Many of our users are low on space, and leaving "difficult to delete" files in the app private
 * directory is user-hostile.
 *
 * See: [execute]
 *
 * Preconditions (verified inside [migrateEssentialFiles] and [execute] - exceptions thrown if not met):
 * * Collection is not corrupt and can be opened
 * * Collection basic check passes [UserActionRequiredException.CheckDatabaseException]
 * * Collection can be closed and locked
 * * User has space to move files [UserActionRequiredException.OutOfSpaceException] (the size of essential files + [SAFETY_MARGIN_BYTES]
 * * A migration is not currently taking place
 */
open class MigrateEssentialFiles
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal constructor(
    private val context: Context,
    private val sourceDirectory: AnkiDroidDirectory,
    private val destinationDirectory: ScopedAnkiDroidDirectory
) {
    /**
     * Copies (not moves) the [essential files][PRIORITY_FILES] to [destinationDirectory]
     *
     * Then opens a collection at the new location, and updates [CollectionHelper.PREF_COLLECTION_PATH] there.
     *
     * After:
     *
     * [PREF_MIGRATION_SOURCE] contains the [AnkiDroidDirectory] with the remaining items to move ([sourceDirectory])
     * [PREF_MIGRATION_DESTINATION] contains an [AnkiDroidDirectory] with the copied collection.anki2/media ([destinationDirectory])
     * [CollectionHelper.PREF_COLLECTION_PATH] now points to the new location of the collection in private storage
     * [ScopedStorageService.UserDataMigrationPreferences.migrationInProgress] returns `true`
     *
     * @throws IllegalStateException Migration in progress
     * @throws IllegalStateException [destinationDirectory] is not empty
     * @throws UserActionRequiredException.MissingEssentialFileException if an essential file does not exist
     * @throws UserActionRequiredException.CheckDatabaseException if 'Check Database' needs to be done first
     * @throws IllegalStateException If a lock cannot be acquired on the collection
     */
    fun execute() {
        if (ScopedStorageService.userMigrationIsInProgress(context)) {
            throw IllegalStateException("Migration is already in progress")
        }

        val destinationPath = destinationDirectory.path

        ensureFolderIsEmpty(destinationPath)

        // ensure the current collection is the one in sourcePath
        ensurePathIsCurrentCollectionPath(sourceDirectory)

        // Throws MissingEssentialFileException if the files we need to copy don't exist
        throwIfEssentialFilesDoNotExistInDestination(sourceDirectory)

        // Close the collection before we lock the files.
        // ensureCollectionNotCorrupted is not compatible with an already open collection
        closeCollection()

        // Race Condition! - The collection could be opened here before locking (maybe by API?).
        // This is resolved as a RetryableException is thrown if the collection is open

        // open the collection directly and ensure it's not corrupted (must be closed and not locked)
        throwIfCollectionIsCorrupted(sourceDirectory.getCollectionAnki2Path())

        // Lock the collection & journal, to ensure that nobody can open/corrupt it
        // Also ensures the collection may not be opened
        lockCollection().use {
            // Copy essential files to new location. Guaranteed to be empty
            for (file in iterateEssentialFiles(sourceDirectory)) {
                copyTopLevelFile(file, destinationPath)
            }
        }

        val destinationCollectionAnki2Path = destinationPath.getCollectionAnki2Path()

        throwIfEssentialFilesAreMutated(sourceDirectory, destinationDirectory)

        // Open the collection in the new location, checking for corruption
        throwIfCollectionIsCorrupted(destinationCollectionAnki2Path)

        // set the preferences to the new deck path + checks CollectionHelper
        // sets migration variables (migrationIsInProgress will be true)
        updatePreferences(destinationPath)

        // updatePreferences() opened the collection in the new location, which will have created
        // a -wal file if the new backend code is active. Close it again, so that tests don't
        // fail due to the presence of a -wal file in the destination folder.
        if (!BackendFactory.defaultLegacySchema) {
            closeCollection()
        }
    }

    /**
     * Ensures that [directory] is empty
     * @throws IllegalStateException if [directory] is not empty
     */
    private fun ensureFolderIsEmpty(directory: AnkiDroidDirectory) {
        val listFiles = directory.listFiles()

        if (listFiles.any()) {
            throw IllegalStateException("destination was non-empty '$directory'")
        }
    }

    /**
     * Ensures that the provided [path] represents the current AnkiDroid collection ([CollectionHelper.getCol])
     */
    private fun ensurePathIsCurrentCollectionPath(path: AnkiDroidDirectory) {
        val currentCollectionFilePath = getCurrentCollectionPath()
        if (path.directory.canonicalPath != currentCollectionFilePath.directory.canonicalPath) {
            throw IllegalStateException("paths did not match: '$path' and '$currentCollectionFilePath' (Collection)")
        }
    }

    @SuppressLint("NewApi") // contentEquals is API 26, we're guaranteed to be above this if performing a migration
    @NeedsTest("untested, needs documentation")
    private fun throwIfEssentialFilesAreMutated(sourceDirectory: AnkiDroidDirectory, destinationDirectory: ScopedAnkiDroidDirectory) {
        // TODO: For Arthur to improve
        for ((source, destination) in iterateEssentialFiles(sourceDirectory).zip(iterateEssentialFiles(destinationDirectory.path))) {
            try {
                throwIfContentUnequal(source, destination)
            } catch (e: Exception) {
                // 13807: .nomedia was reported as mutated, but we could not determine the cause
                if (source.name == ".nomedia") {
                    CrashReportService.sendExceptionReport(e, ".nomedia was mutated")
                    continue
                }
                // any other file should be reported as an error and fail the migration
                throw e
            }
        }
    }

    /**
     * Ensures that all files in [PRIORITY_FILES] and that are in the source exists in the destination.
     * @throws UserActionRequiredException.MissingEssentialFileException if a file does not exist
     */
    private fun throwIfEssentialFilesDoNotExistInDestination(sourcePath: AnkiDroidDirectory) {
        for (file in iterateEssentialFiles(sourcePath)) {
            if (!file.exists()) {
                throw UserActionRequiredException.MissingEssentialFileException(file)
            }
        }
    }

    /**
     * Copies [file] to [destinationDirectory], retaining the same filename
     */
    fun copyTopLevelFile(file: File, destinationDirectory: AnkiDroidDirectory) {
        val destinationPath = File(destinationDirectory.directory, file.name).path
        Timber.i("Migrating essential file: '${file.name}'")
        Timber.d("Copying '$file' to '$destinationPath'")
        CompatHelper.compat.copyFile(file.path, destinationPath)
    }

    /**
     * Updates preferences after a successful "essential files" migration.
     * The collection is opened with this new preference.
     * Any error in opening the collection are thrown, and the preference change is reverted.
     */
    private fun updatePreferences(destinationDirectory: AnkiDroidDirectory) {
        val prefs = AnkiDroidApp.getSharedPrefs(context)

        // keep the old values in case we need to restore them
        val oldPrefValues = listOf(PREF_MIGRATION_SOURCE, PREF_MIGRATION_DESTINATION, CollectionHelper.PREF_COLLECTION_PATH)
            .associateWith { prefs.getString(it, null) }

        prefs.edit {
            // specify that a migration is in progress
            putString(PREF_MIGRATION_SOURCE, sourceDirectory.directory.absolutePath)
            putString(PREF_MIGRATION_DESTINATION, destinationDirectory.directory.absolutePath)
            putString(CollectionHelper.PREF_COLLECTION_PATH, destinationDirectory.directory.absolutePath)
        }

        // open the collection in the new location - data is now migrated
        try {
            throwIfCollectionCannotBeOpened()
        } catch (e: Throwable) {
            // if we can't open the migrated collection, revert the preference change so the user
            // can still use their collection.
            Timber.w("error opening new collection, restoring old values")
            prefs.edit {
                oldPrefValues.forEach {
                    putString(it.key, it.value)
                }
            }
            throw e
        }
    }

    /**
     * Checks that the default collection (from [CollectionHelper.getCol]) can be opened
     * @throws IllegalStateException If collection can't be opened
     */
    @VisibleForTesting
    open fun throwIfCollectionCannotBeOpened() {
        CollectionHelper.instance.getCol(context) ?: throw IllegalStateException("collection could not be opened")
    }

    /**
     * Ensures that the collection is closed.
     * This will temporarily open the collection during the operation if it was already closed
     */
    private fun closeCollection() {
        runBlocking { CollectionManager.ensureClosed() }
    }

    /** Converts the current AnkiDroid collection path to an [AnkiDroidDirectory] instance */
    private fun getCurrentCollectionPath(): AnkiDroidDirectory {
        val collectionAnki2Path = File(CollectionHelper.getCollectionPath(context))
        // happy with the !! here: the parent of the AnkiDroid file is a directory
        return AnkiDroidDirectory.createInstance(File(collectionAnki2Path.canonicalPath).parent!!)!!
    }

    /**
     * Locks the collection and returns a [Closeable] when the closeable is closed, the collection is unlocked
     *
     * @throws IllegalStateException Collection is openable after lock acquired
     */
    private fun lockCollection(): Closeable {
        return createLockedCollection().also {
            // Since we locked the files, we want to ensure that the collection can no longer be opened
            try {
                ensureCollectionNotOpenable()
            } catch (e: Exception) {
                Timber.w(e, "collection was openable")
                it.close()
                throw e
            }
        }
    }

    /**
     * Locks the collection and returns a [LockedCollection] which allows the collection to be unlocked
     */
    @VisibleForTesting
    fun createLockedCollection() = LockedCollection.createLockedInstance()

    /**
     * Check that the collection is not openable. This is expected to be called after the collection is locked, to check whether it was correctly locked.
     * We must check it because improperly locked collections may lead to database corruption. (copying may mean the DB is out of sync with the journal)
     * If the collection is openable or open, close it.
     * @throws RetryableException ([IllegalStateException]) if the collection was openable
     */
    private fun ensureCollectionNotOpenable() {
        val lockedCollection: Collection?
        try {
            lockedCollection = CollectionHelper.instance.getCol(context)
        } catch (e: Exception) {
            Timber.i("Expected exception thrown: ", e)
            return
        }

        // Unexpected: collection was opened. Close it and report an error.
        // Note: it shouldn't be null - a null value infers a new collection can't be created
        // or if the storage media is removed
        try {
            lockedCollection?.close()
        } catch (e: Exception) {
        }

        throw RetryableException(IllegalStateException("Collection not locked correctly"))
    }

    /**
     * Given the path to a `collection.anki2` which is not open, ensures the collection is usable
     *
     * Otherwise: throws an exception
     *
     * @throws UserActionRequiredException.CheckDatabaseException If "check database" is required
     *
     * This may also fail for the following, less likely reasons:
     * * Collection is already open
     * * Collection directory does not exist
     * * Collection directory is not writable
     * * Error opening collection
     */
    open fun throwIfCollectionIsCorrupted(path: CollectionFilePath) {
        var result: Collection? = null
        var firstException: Throwable? = null
        try {
            // Store the collection in `result` so we can close it in the `finally`
            // this can throw [StorageAccessException]: locked or invalid
            result = CollectionHelper.instance.getColFromPath(path, context)
            if (!result.basicCheck()) {
                throw UserActionRequiredException.CheckDatabaseException()
            }
        } catch (e: Throwable) {
            firstException = e
        }
        // this can throw, which ruins the stack trace if the above block threw
        try {
            result?.close()
        } catch (ex: Exception) {
            Timber.w("exception thrown closing database", ex)
            firstException = firstException ?: ex
        }

        // If close() threw in the finally {}, we want to abort.
        if (firstException != null) {
            throw firstException
        }
    }

    /**
     * Represents a locked collection. Unlocks the collection when [close] is called.
     *
     * Note that collection locking is related to being unable to open the collection.
     * An open collection may still exist after this lock is taken.
     *
     * Usage:
     * ```kotlin
     * LockedCollection.createLockedInstance().use {
     *      // do something requiring the collection to be closed
     * } // collection is unlocked here
     * ```
     */
    class LockedCollection private constructor() : Closeable {
        companion object {
            /**
             * Locks the collection and creates an instance of [LockedCollection]
             * @see Storage.lockCollection
             */
            fun createLockedInstance(): LockedCollection {
                // Ideally, we would want to lock the files.
                // However, on macOS, file locking is only advisor and not mandatory to follow,
                // meaning that even if the lock succeed, another thread can still open the file.
                // This is why we instead decided to lock with static variable.
                // Locked using the static lock in [Storage]
                Storage.lockCollection()
                return LockedCollection()
            }
        }

        /** Unlocks the collection */
        override fun close() {
            Storage.unlockCollection()
        }
    }

    /**
     * A file, or group of files which must be migrated synchronously while the collection is closed
     * This is either because the file is vital for when a collection is reopened in a new location
     * Or because it is immediately created and may cause a conflict
     */
    abstract class PriorityFile {
        /**
         * A list of essential files.
         * The returned files are assumed to exists at the time when [getEssentialFiles] is called.
         * It is the caller responsibility to ensure that those files are not created nor deleted
         * between the time when `getFiles` is called and the time when the result is used.
         */
        abstract fun getEssentialFiles(sourceDirectory: String): List<File>

        fun spaceRequired(sourceDirectory: String): NumberOfBytes {
            return getEssentialFiles(sourceDirectory).sumOf { it.length() }
        }

        /** The list of filenames we would move (if they exist) */
        abstract val potentialFileNames: List<String>
    }

    /**
     * A SQLite database, which contains both a database and a journal
     * @see PriorityFile
     */
    internal class SQLiteDBFiles(val fileName: String) : PriorityFile() {
        override fun getEssentialFiles(sourceDirectory: String): List<File> {
            val list = mutableListOf(File(sourceDirectory, fileName))
            val journal = File(sourceDirectory, journalName)
            if (journal.exists()) {
                list.add(journal)
            }
            return list
        }

        // guaranteed to be + "-journal": https://www.sqlite.org/tempfiles.html
        private val journalName = "$fileName-journal"

        override val potentialFileNames get() = listOf(fileName, journalName)
    }

    /**
     * The file at [fileName] if it exists, no file otherwise.
     *
     * The existence test is delayed until the list of files is needed.
     * This means that the list of files may vary with time depending on file creation or deletion
     */
    class OptionalFile(val fileName: String) : PriorityFile() {
        override fun getEssentialFiles(sourceDirectory: String): List<File> {
            val file = File(sourceDirectory, fileName)
            return if (!file.exists()) {
                emptyList()
            } else {
                listOf(file)
            }
        }

        override val potentialFileNames get() = listOf(fileName)
    }

    /**
     * An exception which requires user action to resolve
     */
    abstract class UserActionRequiredException(message: String) : RuntimeException(message) {
        constructor() : this("")

        /**
         * The user must perform 'Check Database'
         */
        class CheckDatabaseException : UserActionRequiredException()

        /**
         * The user must determine why essential files don't exist
         */
        class MissingEssentialFileException(val file: File) : UserActionRequiredException("missing essential file: ${file.name}")

        /**
         * A user requires more free space on their device before starting a scoped storage migration
         */
        class OutOfSpaceException(val available: Long, val required: Long) : UserActionRequiredException("More free space is required. Available: $available. Required: $required") {
            companion object {
                /**
                 * Throws if [required] > [available]
                 * @throws OutOfSpaceException
                 */
                fun throwIfInsufficient(available: Long, required: Long) {
                    if (required > available) {
                        throw OutOfSpaceException(available, required)
                    }
                    Timber.d("Appropriate space for operation. Needed %d bytes. Had %d", required, available)
                }
            }
        }
    }

    companion object {
        /**
         * The buffer space required to migrate files (in addition to the size of the files that we move)
         */
        private const val SAFETY_MARGIN_BYTES = 10 * 1024 * 1024

        /**
         * Lists the files to be moved by [MigrateEssentialFiles]
         * Priority files are files to be moved if they exist.
         * Essential files are files which MUST be moved
         */
        val PRIORITY_FILES = listOf(
            SQLiteDBFiles("collection.anki2"), // Anki collection
            if (BackendFactory.defaultLegacySchema) {
                SQLiteDBFiles("collection.media.ad.db2")
            } else {
                // this is created on demand in the new backend
                OptionalFile("collection.media.db")
            }, // media database + journal
            OptionalFile(".nomedia"), // written immediately
            OptionalFile("collection.log") // written immediately and conflicts
        )

        /**
         * A collection of [File] objects to be moved by [MigrateEssentialFiles]
         */
        fun iterateEssentialFiles(sourcePath: AnkiDroidDirectory) =
            PRIORITY_FILES.flatMap { it.getEssentialFiles(sourcePath.directory.canonicalPath) }

        /**
         * Creates and invokes a [MigrateEssentialFiles] instance.
         *
         * Validates the paths are scoped and non-scoped and that the user has space.
         * Creates [destination] optionally
         * Runs the migration and handles retries
         *
         * @param destination The directory which is to be created and migrated to.
         * Does not need to exist. Must be non-legacy. If it does exist, it must be empty
         * @param transformAlgo test-only: Allows wrapping the generated [MigrateEssentialFiles] to allow for mocking
         *
         * @throws IllegalStateException The current collection was under scoped storage
         * @throws IllegalStateException [destination] was non-empty
         * @throws IllegalStateException [destination] was not under scoped storage
         * @throws UserActionRequiredException.OutOfSpaceException if insufficient space to migrate
         * @throws UserActionRequiredException.MissingEssentialFileException if an essential file does not exist
         * @throws UserActionRequiredException.CheckDatabaseException if 'Check Database' needs to be done first
         *
         * @see [execute] for other failures
         */
        internal fun migrateEssentialFiles(
            context: Context,
            destination: File,
            transformAlgo: ((MigrateEssentialFiles) -> MigrateEssentialFiles)? = null
        ) {
            val collectionPath: CollectionFilePath = CollectionHelper.instance.getCol(context)!!.path
            val sourceDirectory = File(collectionPath).parent!!

            if (!ScopedStorageService.isLegacyStorage(sourceDirectory, context)) {
                throw IllegalStateException("Directory is already under scoped storage")
            }

            // creates directory and ensures it's empty
            CompatHelper.compat.createDirectories(destination)
            if (CompatHelper.compat.hasFiles(destination)) {
                throw IllegalStateException("Target directory was not empty: '$destination'")
            }

            // ensure we have space
            // this must be after .mkdirs(): determineBytesAvailable works on non-empty directories,
            UserActionRequiredException.OutOfSpaceException.throwIfInsufficient(
                available = Utils.determineBytesAvailable(destination.absolutePath),
                required = PRIORITY_FILES.sumOf { it.spaceRequired(sourceDirectory) } + SAFETY_MARGIN_BYTES
            )

            val destinationDirectory = Directory.createInstance(destination)!!
            // ensure destination is under scoped storage
            val destinationAnkiDroidDirectory = ScopedAnkiDroidDirectory.createInstance(destinationDirectory, context) ?: throw IllegalStateException("Destination folder was not under scoped storage '$destinationDirectory'")

            val originalAlgo = MigrateEssentialFiles(
                context,
                AnkiDroidDirectory.createInstance(sourceDirectory)!!, // !! is fine here - parent of collection.anki2 is a directory
                destinationAnkiDroidDirectory
            )

            val algo = transformAlgo?.invoke(originalAlgo) ?: originalAlgo

            // this executes the algorithm
            RetryableException.retryOnce { algo.execute() }
        }
    }
}
