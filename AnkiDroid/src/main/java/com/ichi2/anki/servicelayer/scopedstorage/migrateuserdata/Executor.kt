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

import androidx.annotation.VisibleForTesting
import timber.log.Timber

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
