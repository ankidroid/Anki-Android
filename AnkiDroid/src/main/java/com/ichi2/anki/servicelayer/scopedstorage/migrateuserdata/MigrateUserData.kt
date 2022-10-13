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
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.MoveConflictedFile
import com.ichi2.anki.servicelayer.scopedstorage.MoveFileOrDirectory
import com.ichi2.async.ProgressSenderAndCancelListener
import com.ichi2.async.TaskDelegate
import com.ichi2.compat.CompatHelper
import com.ichi2.exceptions.AggregateException
import com.ichi2.libanki.Collection
import timber.log.Timber
import java.io.File

typealias NumberOfBytes = Long
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
open class MigrateUserData protected constructor(val source: Directory, val destination: Directory) : TaskDelegate<NumberOfBytes, Boolean>() {
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
     * An Executor allows execution of a list of tasks, provides progress reporting via a [MigrationContext]
     * and allows tasks to be preempted (for example: copying an image used in the Reviewer
     * should take priority over a background migration
     * of a random file)
     */
    open class Executor(private val operations: ArrayDeque<Operation>) {
        /** Whether [terminate] was called. Once this is called, a new instance should be used */
        private var terminated: Boolean = false
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

    @VisibleForTesting
    var executor = Executor(ArrayDeque())

    @VisibleForTesting
    var externalRetries = 0
        private set

    /**
     * Migrates all files and folders to [destination], aside from [getEssentialFiles]
     *
     * @throws MissingDirectoryException
     * @throws EquivalentFileException
     * @throws AggregateException If multiple exceptions were thrown when executing
     * @throws RuntimeException Various other failings if only a single exception was thrown
     */
    override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<NumberOfBytes>): Boolean {

        val context = initializeContext(collectionTask::doProgress)

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
            throw AggregateException.raise("", context.loggedExceptions) // TODO
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
                    // the 'fonts' folder will impact UX
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
}
