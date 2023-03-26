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

package com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.model.RelativeFilePath
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.MoveConflictedFile
import com.ichi2.anki.servicelayer.scopedstorage.MoveFile
import com.ichi2.anki.servicelayer.scopedstorage.MoveFileOrDirectory
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.SingleRetryDecorator
import com.ichi2.compat.CompatHelper
import com.ichi2.exceptions.AggregateException
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch

typealias NumberOfBytes = Long

fun NumberOfBytes.toKB(): Int {
    return ((this / 1024).toInt())
}

fun NumberOfBytes.toMB(): Int {
    return this.toKB() / 1024
}

/**
 * Function that is executed when one file is migrated, with the number of bytes moved.
 * Called with 0 when the file is already present in destination (i.e. successful move with no byte copied)
 * Not called for directories.
 */
typealias MigrationProgressListener = (NumberOfBytes) -> Unit

/**
 * Migrating user data (images, backups etc..) to scoped storage
 * This needs to be performed in the background to allow users to use AnkiDroid.
 *
 * A file is not user data if it is moved by [MigrateEssentialFiles]:
 * * (collection and media SQL-related files, and .nomedia/collection logs)
 *
 * If this were performed in the foreground, users would be encouraged to uninstall the app
 * which means the app permanently loses access to the AnkiDroid directory.
 *
 * This also handles preemption, allowing media files to skip the queue
 * (if they're required for review)
 */
open class MigrateUserData protected constructor(val source: Directory, val destination: Directory) {
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
     * Exceptions that are expected to occur during migration, and that we can deal with.
     */
    open class MigrationException(message: String) : RuntimeException(message)

    /**
     * If a file exists in [destination] with different content than [source]
     *
     * If a file named `filename` exists in [destination] and in [source] with different content, move `source/filename` to `source/conflict/filename`.
     */
    class FileConflictException(val source: DiskFile, val destination: DiskFile) : MigrationException("File $source can not be copied to $destination, destination exists and differs.")

    /**
     * If [destination] is a directory. In this case, move `source/filename` to `source/conflict/filename`.
     */
    class FileDirectoryConflictException(val source: DiskFile, val destination: Directory) : MigrationException("File $source can not be copied to $destination, as destination is a directory.")

    /**
     * If one or more required directories were missing
     */
    class MissingDirectoryException(val directories: List<MissingFile>) : MigrationException("Directories $directories are missing.") {
        init {
            if (directories.isEmpty()) {
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
    class EquivalentFileException(val source: File, val destination: File) : MigrationException("Source and destination path are the same")

    /**
     * If a directory could not be deleted as it still contained files.
     */
    class DirectoryNotEmptyException(val directory: Directory) : MigrationException("directory was not empty: $directory")

    /**
     * If the number of retries was exceeded when resolving a file conflict via moving it to the
     * /conflict/ directory.
     */
    class FileConflictResolutionFailedException(val sourceFile: DiskFile, val attemptedDestination: File) : MigrationException("Failed to move $sourceFile to $attemptedDestination")

    /**
     * Context for an [Operation], allowing a change of execution behavior and
     * allowing progress and exception reporting logic when executing
     * a large mutable queue of tasks
     */
    abstract class MigrationContext {
        abstract fun reportError(throwingOperation: Operation, ex: Exception)

        /**
         * Called on each successful file migrated
         * @param transferred The number of bytes of the transferred file
         */
        abstract fun reportProgress(transferred: NumberOfBytes)

        /**
         * Whether [File#renameTo] should be attempted for files.
         *
         * This is not attempted for directories: very unlikely to work as we're copying across
         * mount points.
         * Android has internal logic which recovers renames from /storage/emulated
         * But this hasn't worked for me for directorys
         */
        var attemptRename: Boolean = true

        /**
         * Performs an operation, reports errors and continues on failure
         */
        open fun execSafe(operation: Operation, op: (Operation) -> Unit) {
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
         * E.g. for "move a directory", this method would simply compute the directory content and then returns the following list of operations:
         * * creating the destination directory
         * * moving each file and subdirectory individually
         * * deleting the original directory.
         */
        abstract fun execute(context: MigrationContext): List<Operation>

        /** A list of operations to perform if the operation should be retried */
        open val retryOperations get() = emptyList<Operation>()
    }

    class AwaitableOperation(private val operation: Operation) : Operation() {
        private val completion = CountDownLatch(1)

        override fun execute(context: MigrationContext): List<Operation> {
            try {
                return operation.execute(context)
            } finally {
                this.completion.countDown()
            }
        }
        fun await() = completion.await()
    }

    /**
     * A decorator for [Operation] which executes [standardOperation].
     * When retried, executes [retryOperation].
     * Ignores [retryOperations] defined in [standardOperation]
     */
    class SingleRetryDecorator(
        internal val standardOperation: Operation,
        private val retryOperation: Operation
    ) : Operation() {
        override fun execute(context: MigrationContext) = standardOperation.execute(context)
        override val retryOperations get() = listOf(retryOperation)
    }

    /**
     * An Executor allows execution of a list of tasks, provides progress reporting via a [MigrationContext]
     * and allows tasks to be preempted (for example: copying an image used in the Reviewer
     * should take priority over a background migration
     * of a random file)
     */
    open class Executor(private val operations: ArrayDeque<Operation>) {
        /** Whether [terminate] was called. Once this is called, a new instance should be used */
        var terminated: Boolean = false
            private set

        /**
         * A list of operations to be executed before [operations]
         * [operations] should only be executed if this list is clear
         */
        private val preempted: ArrayDeque<Operation> = ArrayDeque()

        /**
         * Executes operations from both [operations] and [preempted]
         * Any operation is [preempted] takes priority
         * Completes when:
         * * [MigrationContext] determines too many failures have occurred or a critical failure has occurred (via `reportError`)
         * * [operations] and [preempted] are empty
         * * [terminated] is set via [terminate]
         */
        fun execute(context: MigrationContext) {
            while (operations.any() || preempted.any()) {
                clearPreemptedQueue(context)
                if (terminated) {
                    return
                }
                val operation = operations.removeFirstOrNull() ?: return

                context.execSafe(operation) {
                    val replacements = executeOperationInternal(it, context)
                    operations.addAll(0, replacements)
                }
            }
        }

        @VisibleForTesting
        internal open fun executeOperationInternal(
            it: Operation,
            context: MigrationContext
        ) = it.execute(context)

        /**
         * Executes all items in the preempted queue
         *
         * After this has completed either: [preempted] is empty, OR [terminated] is true
         */
        private fun clearPreemptedQueue(context: MigrationContext) {
            while (true) {
                if (terminated) return

                // exit if we've got no more items
                val nextItem = getNextPreemptedItem() ?: return
                Timber.d("executing preempted operation: %s", nextItem)
                context.execSafe(nextItem) {
                    val replacements = it.execute(context)
                    addPreempted(replacements)
                }
            }
        }

        fun prepend(operation: Operation) = operations.addFirst(operation)
        fun append(operation: Operation) = operations.add(operation)
        fun appendAll(operations: List<Operation>) = this.operations.addAll(operations)

        // region preemption (synchronized)

        private fun addPreempted(replacements: List<Operation>) {
            // insert all at the start of the queue
            synchronized(preempted) { preempted.addAll(0, replacements) }
        }
        private fun getNextPreemptedItem() = synchronized(preempted) {
            return@synchronized preempted.removeFirstOrNull()
        }
        fun preempt(operation: Operation) = synchronized(preempted) { preempted.add(operation) }

        // endregion

        /** Stops execution of [execute] */
        fun terminate() {
            this.terminated = true
        }
    }

    /**
     * Abstracts the decision of what to do when an exception occurs when migrating a file.
     * Provides progress notifications.
     * @param executor The executor that will do the migration.
     * @param progressReportParam A function, called for each file that is migrated, with the number of bytes of the file.
     */
    open class UserDataMigrationContext(private val executor: Executor, val source: Directory, val progressReportParam: MigrationProgressListener) : MigrationContext() {
        val successfullyCompleted: Boolean get() = loggedExceptions.isEmpty() && !executor.terminated

        /**
         * The reason that the the execution of the whole migration was terminated early
         *
         * @see failOperationWith
         */
        var terminatedWith: Exception? = null
            private set

        val retriedDirectories = hashSetOf<File>()

        val loggedExceptions = mutableListOf<Exception>()
        private var consecutiveExceptionsWithoutProgress = 0
        override fun reportError(throwingOperation: Operation, ex: Exception) {
            when (ex) {
                is FileConflictException -> { moveToConflictedDirectory(ex.source) }
                is FileDirectoryConflictException -> { moveToConflictedDirectory(ex.source) }
                is DirectoryNotEmptyException -> {
                    // If a directory isn't empty, some more files may have been added. Retry (after all others are completed)
                    if (throwingOperation.retryOperations.any() && retriedDirectories.add(ex.directory.directory)) {
                        executor.appendAll(throwingOperation.retryOperations)
                    } else {
                        logExceptionAndContinue(ex)
                    }
                }
                is MissingDirectoryException -> failOperationWith(ex)
                // logical error: we tried to migrate to the same path
                is EquivalentFileException -> failOperationWith(ex)
                // if we couldn't move a file to /conflict/, log and continue.
                // we do not expect this exception to occur
                is FileConflictResolutionFailedException -> logExceptionAndContinue(ex)
                else -> logExceptionAndContinue(ex)
            }
        }

        /**
         * Keeps a circular buffer of the last 10 exceptions.
         * the oldest exception is evicted if more than 10 are added
         */
        private fun logExceptionAndContinue(ex: Exception) {
            if (loggedExceptions.size >= 10) {
                loggedExceptions.removeFirst()
            }
            loggedExceptions.add(ex)
            consecutiveExceptionsWithoutProgress++
            if (consecutiveExceptionsWithoutProgress >= 10) {
                val exception = AggregateException.raise("10 consecutive exceptions without progress", loggedExceptions)
                failOperationWith(exception)
            }
        }

        /**
         * On a conflicted file, move it from `<path>` to `/conflict/<path>`.
         * Files in this directory will not be moved again
         *
         * We perform this action immediately
         *
         * @see MoveConflictedFile
         */
        private fun moveToConflictedDirectory(conflictedFile: DiskFile) {
            val relativePath = RelativeFilePath.fromPaths(source, conflictedFile)!!
            val operation: MoveConflictedFile = MoveConflictedFile.createInstance(conflictedFile, source, relativePath)
            executor.prepend(operation)
        }

        override fun reportProgress(transferred: NumberOfBytes) {
            consecutiveExceptionsWithoutProgress = 0
            this.progressReportParam(transferred)
        }

        /** A fatal exception has occurred which should stop all file processing */
        private fun failOperationWith(ex: Exception) {
            executor.terminate()
            terminatedWith = ex
        }

        fun reset() {
            loggedExceptions.clear()
            consecutiveExceptionsWithoutProgress = 0
        }
    }

    @VisibleForTesting
    var executor = Executor(ArrayDeque())

    @VisibleForTesting
    var externalRetries = 0
        private set

    /**
     * Migrates all files and directorys to [destination], aside from [getEssentialFiles]
     *
     * @throws MissingDirectoryException
     * @throws EquivalentFileException
     * @throws AggregateException If multiple exceptions were thrown when executing
     * @throws RuntimeException Various other failings if only a single exception was thrown
     */
    fun migrateFiles(progressListener: MigrationProgressListener): Boolean {
        val context = initializeContext(progressListener)

        // define the function here, so we can execute it on retry
        fun moveRemainingFiles() {
            executor.appendAll(getMigrateUserDataOperations())
            executor.execute(context)
        }

        moveRemainingFiles()

        // try 2 times, then stop temporarily
        while (!context.successfullyCompleted && externalRetries < 2) {
            context.reset()
            externalRetries++
            moveRemainingFiles()
        }

        // if the operation was terminated (typically due to too many consecutive exceptions), throw that
        // otherwise, there were a few exceptions which didn't stop execution, throw these.
        if (!context.successfullyCompleted) {
            context.terminatedWith?.let { throw it }
            val migrationFailedMessage = AnkiDroidApp.instance.getString(R.string.migration_failed_message)
            throw AggregateException.raise(migrationFailedMessage, context.loggedExceptions)
        }

        // we are successfully migrated here
        // TODO: fix "conflicts" - check to see if conflicts are due to partially copied files in the destination

        return true
    }

    @VisibleForTesting
    /**
     * @return A User data migration context, executing the migration of [source] on [executor].
     * Calling [collectionTask::doProgress] on each migrated file, with the number of bytes migrated.
     */
    internal open fun initializeContext(progress: (MigrationProgressListener)) =
        UserDataMigrationContext(executor, source, progress)

    /**
     * Returns migration operations for the top level items in /AnkiDroid/
     */
    @VisibleForTesting
    internal open fun getMigrateUserDataOperations(): List<Operation> =
        getUserDataFiles()
            .map { fileOrDir ->
                MoveFileOrDirectory(
                    sourceFile = File(source.directory, fileOrDir.name),
                    destination = File(destination.directory, fileOrDir.name)
                )
            }.sortedWith(
                compareBy {
                    // Have user-generated files take priority over the media.
                    // the 'fonts' directory will impact UX
                    // 'card.html' is often regenerated and is likely to cause a conflict
                    // We want all the backups to be restorable ASAP)
                    when (it.sourceFile.name) {
                        "card.html" -> -3
                        "fonts" -> -2
                        "backups" -> -1
                        else -> 0
                    }
                }
            )
            .toList()

    /** Gets a sequence of content in [source] */
    private fun getDirectoryContent() = sequence {
        CompatHelper.compat.contentOfDirectory(source.directory).use {
            while (it.hasNext()) {
                yield(it.next())
            }
        }
    }

    /** Returns a sequence of the Files or Directories in [source] which are to be migrated */
    private fun getUserDataFiles() = getDirectoryContent().filter { isUserData(it) }

    fun isEssentialFileName(name: String): Boolean {
        return MigrateEssentialFiles.PRIORITY_FILES.flatMap { it.potentialFileNames }.contains(name)
    }

    /** Returns whether a file is "user data" and should be moved */
    private fun isUserData(file: File): Boolean {
        if (file.isFile && isEssentialFileName(file.name)) {
            return false
        }

        // don't move the "conflict" directory
        if (file.name == MoveConflictedFile.CONFLICT_DIRECTORY) {
            return false
        }

        return true
    }

    /**
     * Migrate a file to [expectedFileLocation] if it exists inside [source]
     * @param expectedFileLocation A file which should exist inside [destination]
     * */
    fun migrateFileImmediately(expectedFileLocation: File) {
        // It is possible, but unlikely that a file at the location already exists
        if (expectedFileLocation.exists()) {
            Timber.d("nothing to migrate: file already exists")
            return
        }

        // convert to a relative path WRT the destination (our current collection)
        val relativeDataPath = RelativeFilePath.fromPaths(destination.directory, expectedFileLocation)
            ?: throw IllegalStateException("Could not create relative path between ${destination.directory} and $expectedFileLocation")

        // get a reference to the source file
        val sourceFile = DiskFile.createInstance(relativeDataPath.toFile(source))
        if (sourceFile == null) {
            Timber.w("couldn't migrate: source file not found or not a file. Maybe a bad card. Maybe already moved")
            return
        }

        val moveFile = MoveFile(sourceFile, expectedFileLocation)
        AwaitableOperation(moveFile).also { operation ->
            this.executor.preempt(operation)
            operation.await()
        }

        Timber.w("complete migration: %s $relativeDataPath $sourceFile", expectedFileLocation)
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
