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

import android.content.SharedPreferences
import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.servicelayer.ScopedStorageService.UserDataMigrationPreferences
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.Operation
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.SingleRetryDecorator
import timber.log.Timber
import java.io.File

typealias NumberOfBytes = Long

/**
 * Migrating user data (images, backups etc..) to scoped storage
 * This needs to be performed in the background to allow users to use AnkiDroid
 *
 * If this were performed in the foreground, users would be encouraged to uninstall the app
 * which means the app permanently loses access to the AnkiDroid directory.
 *
 * This also handles preemption, allowing media files to skip the queue
 * (if they're required for review)
 */
class MigrateUserData private constructor(val source: Directory, val destination: Directory) {
    companion object {
        /**
         * Creates an instance of [MigrateUserData] if valid, returns null if a migration is not in progress, or throws if data is invalid
         * @return null if a migration is not taking place, otherwise a valid [MigrateUserData] instance
         *
         * @throws IllegalStateException If preferences are in an invalid state (should be logically impossible - currently unrecoverable)
         * @throws MissingDirectoryException If either or both the source/destination do not exist
         */
        fun createInstance(preferences: SharedPreferences): MigrateUserData? {
            val migrationPreferences = UserDataMigrationPreferences.createInstance(preferences)
            if (!migrationPreferences.migrationInProgress) {
                return null
            }

            return createInstance(migrationPreferences)
        }

        /**
         * Creates an instance of a [MigrateUserData]
         *
         * Assumes the paths come from preferences
         *
         * @throws MissingDirectoryException If either directory defined in [preferences] does not exist
         */
        private fun createInstance(preferences: UserDataMigrationPreferences): MigrateUserData {
            val directoryValidator = DirectoryValidator()

            val sourceDirectory = directoryValidator.tryCreate("source", preferences.sourceFile)
            val destinationDirectory = directoryValidator.tryCreate("destination", preferences.destinationFile)

            if (sourceDirectory == null || destinationDirectory == null) {
                throw directoryValidator.exceptionOnNullResult
            }

            return MigrateUserData(
                source = sourceDirectory,
                destination = destinationDirectory
            )
        }
    }

    /**
     * If a file exists in [destination] with different content than [source]
     *
     * If a file named `filename` exists in [destination] and in [source] with different content, move `source/filename` to `source/conflict/filename`.
     */
    class FileConflictException(val source: DiskFile, val destination: DiskFile) : RuntimeException("File $source can not be copied to $destination, destination exists and differs.")

    /**
     * If [destination] is a directory. In this case, move `source/filename` to `source/conflict/filename`.
     */
    class FileDirectoryConflictException(val source: DiskFile, val destination: Directory) : RuntimeException("File $source can not be copied to $destination, as destination is a directory.")

    /**
     * If one or more required directories were missing
     */
    class MissingDirectoryException(val directories: List<MissingFile>) : RuntimeException() {
        init {
            if (directories.isNullOrEmpty()) {
                throw IllegalArgumentException("directories should not be empty")
            }
        }

        /**
         * A file which should exist, but did not
         * @param source The variable name/identifier of the file
         * @param file A [File] reference to the missing file
         */
        data class MissingFile(val source: String, val file: File)
    }

    /**
     * If during a file move, two files refer to the same path
     * This implies that the file move should be cancelled
     */
    class EquivalentFileException(val source: File, val destination: File) : RuntimeException("Source and destination path are the same")

    /**
     * If a directory could not be deleted as it still contained files.
     */
    class DirectoryNotEmptyException(val directory: Directory) : RuntimeException("directory was not empty: $directory")

    /**
     * If the number of retries was exceeded when resolving a file conflict via moving it to the
     * /conflict/ folder.
     */
    class FileConflictResolutionFailedException(val sourceFile: DiskFile, val attemptedDestination: File) : RuntimeException("Failed to move $sourceFile to $attemptedDestination")

    /**
     * Context for an [Operation], allowing a change of execution behavior and
     * allowing progress and exception reporting logic when executing
     * a large mutable queue of tasks
     */
    abstract class MigrationContext {
        abstract fun reportError(throwingOperation: Operation, ex: Exception)
        abstract fun reportProgress(transferred: NumberOfBytes)
        /**
         * Whether [File#renameTo] should be attempted
         *
         * In scoped storage, this is typically false, as we may be moving between mount points
         */
        var attemptRename: Boolean = true

        /**
         * Performs an operation, reports errors and continues on failure
         */
        fun execSafe(operation: Operation, op: (Operation) -> Unit) {
            try {
                op(operation)
            } catch (e: Exception) {
                Timber.w(e, "Failed while executing %s", operation)
                reportError(operation, e)
            }
        }
    }

    /**
     * Used to validate a number of [Directory] instances and produces a [MissingDirectoryException] with all
     * missing directories.
     *
     * Usage:
     * ```kotlin
     * val directoryValidator = DirectoryValidator()
     *
     * val sourceDirectory = directoryValidator.tryCreate(source)
     * val destinationDirectory = directoryValidator.tryCreate(destination)
     *
     * if (sourceDirectory == null || destinationDirectory == null) {
     *     throw directoryValidator.exceptionOnNullResult
     * }
     *
     * // `sourceDirectory` and `destinationDirectory` may now be used
     * ```
     *
     * Alternately (just validation without using values):
     * ```kotlin
     * val directoryValidator = DirectoryValidator()
     *
     * directoryValidator.tryCreate(source)
     * directoryValidator.tryCreate(destination)
     *
     * exceptionBuilder.throwIfNecessary()
     * ```
     */
    class DirectoryValidator {
        /** Only valid if [tryCreate] returned null */
        val exceptionOnNullResult: MissingDirectoryException
            get() = MissingDirectoryException(failedFiles)

        /**
         * A list of files which failed to be created
         * Only valid if [tryCreate] returned null
         */
        private val failedFiles = mutableListOf<MissingDirectoryException.MissingFile>()

        /**
         * Tries to create a [Directory] object.
         *
         * If this returns null, [exceptionOnNullResult] should be thrown by the caller.
         * Example usages are provided in the [DirectoryValidator] documentation.
         *
         * @param [context] The "name" of the variable to test
         * @param [file] A file which may not point to a valid directory
         * @return A [Directory], or null if [file] did not point to an existing directory
         */
        fun tryCreate(context: String, file: File): Directory? {
            val ret = Directory.createInstance(file)
            if (ret == null) {
                failedFiles.add(MissingDirectoryException.MissingFile(context, file))
            }
            return ret
        }

        /**
         * If any directories were not created, throw a [MissingDirectoryException] listing the files
         * @throws MissingDirectoryException if any input files were invalid
         */
        fun throwIfNecessary() {
            if (failedFiles.any()) {
                throw MissingDirectoryException(failedFiles)
            }
        }
    }

    /**
     * Represents an arbitrary operation that we may want to execute.
     *
     * This operation should be doable as a sequence of atomic steps. In a single-threaded context,
     * it allows the thread and its resources to be preempted with minimal delay.
     *
     * For example, if an image is requested by the reviewer, I/O is guaranteed to rapidly get access to the image.
     */
    abstract class Operation {
        /**
         * Starts to execute the current operation. Only do as little non-trivial work as possible to start the operation, such as listing a directory content or moving a single file.
         * Returns the list of operations remaining to end this operation.
         *
         * E.g. for "move a directory", this method would simply compute the directory content and then retuns the following list of operations:
         * * creating the destination directory
         * * moving each file and subdirectory individually
         * * deleting the original directory.
         */
        abstract fun execute(context: MigrationContext): List<Operation>

        /** A list of operations to perform if the operation should be retried */
        open val retryOperations get() = emptyList<Operation>()
    }

    /**
     * A decorator for [Operation] which executes [standardOperation].
     * When retried, executes [retryOperation].
     * Ignores [retryOperations] defined in [standardOperation]
     */
    class SingleRetryDecorator(
        private val standardOperation: Operation,
        private val retryOperation: Operation
    ) : Operation() {
        override fun execute(context: MigrationContext) = standardOperation.execute(context)
        override val retryOperations get() = listOf(retryOperation)
    }
}

/**
 * Wraps an [Operation] with functionality to allow for retries
 *
 * Useful if you want to call a different operation when an operation is being retried.
 *
 * Example: call MoveDirectory again if DeleteEmptyDirectory fails
 *
 * @receiver The operation to be decorated with a retry action
 * @param operationOnRetry The action to perform is [Operation.retryOperations] is called
 */
internal fun Operation.onRetryExecute(operationOnRetry: Operation): Operation {
    val operationToBeDecorated = this
    return SingleRetryDecorator(
        standardOperation = operationToBeDecorated,
        retryOperation = operationOnRetry
    )
}

/** The operation was completed (not necessarily successfully) and no additional operations are required */
internal fun operationCompleted() = emptyList<Operation>()
