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

import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.model.RelativeFilePath
import com.ichi2.anki.servicelayer.scopedstorage.MoveConflictedFile
import com.ichi2.exceptions.AggregateException
import timber.log.Timber
import java.io.File

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
 * Abstracts the decision of what to do when an exception occurs when migrating a file.
 * Provides progress notifications
 */
open class UserDataMigrationContext(private val executor: MigrateUserData.Executor, val source: Directory, val progressReportParam: ((NumberOfBytes) -> Unit)) : MigrationContext() {
    val successfullyCompleted: Boolean get() = loggedExceptions.isEmpty()

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
            is FileConflictException -> { moveToConflictedFolder(ex.source) }
            is FileDirectoryConflictException -> { moveToConflictedFolder(ex.source) }
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
     * Files in this folder will not be moved again
     *
     * We perform this action immediately
     *
     * @see MoveConflictedFile
     */
    private fun moveToConflictedFolder(conflictedFile: DiskFile) {
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
