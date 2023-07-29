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
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.model.Directory
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.*
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_DESTINATION
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_SOURCE
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.Companion.PRIORITY_FILES
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles.UserActionRequiredException
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.NumberOfBytes
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper
import timber.log.Timber
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
 * See: [migrateFiles]
 *
 * Preconditions (verified inside [migrateEssentialFiles] and [migrateFiles] - exceptions thrown if not met):
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
    private val folders: ValidatedMigrationSourceAndDestination
) {
    private var oldPrefValues: Map<String, String?>? = null

    /**
     * Copies (not moves) the [essential files][PRIORITY_FILES] to [destinationDirectory]
     *
     * Then opens a collection at the new location, and updates [CollectionHelper.PREF_COLLECTION_PATH] there.
     *
     * After, call updateCollectionPath(), and then:
     *
     * [PREF_MIGRATION_SOURCE] contains the unscopedSourceDirectory with the remaining items to move ([sourceDirectory])
     * [PREF_MIGRATION_DESTINATION] contains the scopedDestinationDirectory with the copied collection.anki2/media ([destinationDirectory])
     * [CollectionHelper.PREF_COLLECTION_PATH] now points to the new location of the collection in private storage
     * [ScopedStorageService.UserDataMigrationPreferences.migrationInProgress] returns `true`
     *
     * @throws IllegalStateException Migration in progress
     * @throws IllegalStateException [destinationDirectory] is not empty
     * @throws UserActionRequiredException.MissingEssentialFileException if an essential file does not exist
     * @throws UserActionRequiredException.CheckDatabaseException if 'Check Database' needs to be done first
     * @throws IllegalStateException If a lock cannot be acquired on the collection
     */
    fun migrateFiles() {
        val (unscopedSourceDirectory, scopedDestinationDirectory) = folders

        try {
            // Throws MissingEssentialFileException if the files we need to copy don't exist
            throwIfEssentialFilesDoNotExistInDirectory(unscopedSourceDirectory)

            // Copy essential files to new location. Guaranteed to be empty
            for (file in iterateEssentialFiles(unscopedSourceDirectory)) {
                copyTopLevelFile(file, scopedDestinationDirectory)
            }

            // Check that the files in the target location are identical.
            throwIfEssentialFilesAreMutated(unscopedSourceDirectory, scopedDestinationDirectory)
        } catch (e: Exception) {
            try {
                // MigrateEssentialFiles performs a COPY. Delete the data so we don't take up space.
                folders.scopedDestinationDirectory.directory.deleteRecursively()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    @SuppressLint("NewApi") // contentEquals is API 26, we're guaranteed to be above this if performing a migration
    @NeedsTest("untested, needs documentation")
    private fun throwIfEssentialFilesAreMutated(sourceDirectory: Directory, destinationDirectory: Directory) {
        // TODO: For Arthur to improve
        for ((source, destination) in iterateEssentialFiles(sourceDirectory).zip(iterateEssentialFiles(destinationDirectory))) {
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
    private fun throwIfEssentialFilesDoNotExistInDirectory(sourcePath: Directory) {
        for (file in iterateEssentialFiles(sourcePath)) {
            if (!file.exists()) {
                throw UserActionRequiredException.MissingEssentialFileException(file)
            }
        }
    }

    /**
     * Copies [file] to [destinationDirectory], retaining the same filename
     */
    fun copyTopLevelFile(file: File, destinationDirectory: Directory) {
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
    fun updateCollectionPath() {
        val prefs = context.sharedPrefs()

        // keep the old values in case we need to restore them
        oldPrefValues = listOf(
            PREF_MIGRATION_SOURCE,
            PREF_MIGRATION_DESTINATION,
            CollectionHelper.PREF_COLLECTION_PATH
        )
            .associateWith { prefs.getString(it, null) }

        prefs.edit {
            // specify that a migration is in progress
            putString(PREF_MIGRATION_SOURCE, folders.unscopedSourceDirectory.directory.absolutePath)
            putString(
                PREF_MIGRATION_DESTINATION,
                folders.scopedDestinationDirectory.directory.absolutePath
            )
            putString(
                CollectionHelper.PREF_COLLECTION_PATH,
                folders.scopedDestinationDirectory.directory.absolutePath
            )
        }
    }

    /** Can be called if collection fails to open after migration completes. */
    fun restoreOldCollectionPath() {
        val prefs = context.sharedPrefs()
        prefs.edit {
            oldPrefValues?.forEach {
                putString(it.key, it.value)
            }
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
         * Lists the files to be moved by [MigrateEssentialFiles]
         * Priority files are files to be moved if they exist.
         * Essential files are files which MUST be moved
         */
        val PRIORITY_FILES = listOf(
            SQLiteDBFiles("collection.anki2"), // Anki collection
            // this is created on demand in the new backend
            OptionalFile("collection.media.db"),
            OptionalFile("collection.anki2-wal"),
            OptionalFile("collection.media.db-wal"),
            OptionalFile(".nomedia"),
            OptionalFile("collection.log") // written immediately and conflicts
        )

        /**
         * A collection of [File] objects to be moved by [MigrateEssentialFiles]
         */
        fun iterateEssentialFiles(sourcePath: Directory) =
            PRIORITY_FILES.flatMap { it.getEssentialFiles(sourcePath.directory.canonicalPath) }
    }
}
