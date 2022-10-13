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
    abstract fun execute(context: MigrateUserData.MigrationContext): List<Operation>

    /** A list of operations to perform if the operation should be retried */
    open val retryOperations get() = emptyList<Operation>()
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
    override fun execute(context: MigrateUserData.MigrationContext) = standardOperation.execute(context)
    override val retryOperations get() = listOf(retryOperation)
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
