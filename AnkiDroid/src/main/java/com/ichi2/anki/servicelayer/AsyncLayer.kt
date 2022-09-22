/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import com.ichi2.async.CancelListener
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection

/*
    Async Layer on top of the legacy CollectionTask layer
    These classes exist to remove the need for a class hierarchy while using a CollectionTask
 */

/**
 * Represents a task which will be executed asynchronously
 * A user may override [execute] and use local methods to access listeners and state
 *
 * Remarks: Using state in the class removes the need for parameters, allowing simplification of
 * the callers and interface.
 *
 * We need a class due to the methods delegated to the [TaskExecutionContext]
 */
abstract class AnkiTask<TProgress, TResult> : CancelListener {
    private lateinit var executionContext: TaskExecutionContext<TProgress>

    val col: Collection get() = executionContext.col
    override fun isCancelled(): Boolean = executionContext.isCancelled()
    fun doProgress(progress: TProgress) = executionContext.doProgress(progress)

    /** Executes the task using the provided [executionContext] */
    fun execute(executionContext: TaskExecutionContext<TProgress>): TResult {
        this.executionContext = executionContext
        return execute()
    }

    /** Executes the task. [executionContext] must be set before this is called */
    protected abstract fun execute(): TResult

    /* Below should be extension methods. They exist for a clean Java interface */
    fun toDelegate() = this.asDelegate()

    fun runWithHandler(block: TaskListenerBuilder<TProgress, TResult?>) {
        runWithHandler(block.toListener())
    }

    fun runWithHandler(toListener: TaskListener<TProgress, TResult?>) {
        TaskManager.launchCollectionTask(this.toDelegate(), toListener)
    }
}

/** A simple [AnkiTask] which does not call progress notifications */
abstract class AnkiMethod<TResult> : AnkiTask<Unit, TResult>()

/**
 * Async operation context which can be injected into a Task
 */
interface TaskExecutionContext<T> {
    fun isCancelled(): Boolean
    fun doProgress(progress: T)
    val col: Collection
}
