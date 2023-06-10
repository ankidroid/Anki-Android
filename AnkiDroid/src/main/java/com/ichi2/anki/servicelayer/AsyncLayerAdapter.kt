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

import com.ichi2.async.ProgressSenderAndCancelListener
import com.ichi2.async.TaskDelegate
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import java.util.function.Consumer

/* This file exists to ensure that AsyncLayer.kt has no accidental references to com.ichi2.async */

/* Classes to convert from AsyncLayer to the CollectionTask interface */

fun <TProgress, TResult> execute(
    task: AnkiTask<TProgress, TResult>,
    listener: TaskListenerBuilder<TProgress, TResult?>
) {
    TaskManager.launchCollectionTask(task.toDelegate(), listener.toListener())
}

fun <TProgress, TResult> TaskListenerBuilder<TProgress, TResult?>.execute(task: AnkiTask<TProgress, TResult>) {
    TaskManager.launchCollectionTask(task.toDelegate(), this.toListener())
}

fun <TProgress, TResult> TaskListenerBuilder<TProgress, TResult?>.toListener(): TaskListener<TProgress, TResult?> {
    return object : TaskListener<TProgress, TResult?>() {
        override fun onPreExecute() {
            before?.let { it() }
        }

        override fun onPostExecute(result: TResult?) {
            after?.accept(result)
        }

        override fun onProgressUpdate(value: TProgress) {
            onProgressUpdate?.accept(value)
        }

        override fun onCancelled() {
            onCancelled?.let { it() }
        }
    }
}

class TaskListenerBuilder<TProgress, TResult> constructor() {
    constructor(listener: TaskListener<TProgress, TResult>) : this() {
        replaceWith(listener)
    }

    fun replaceWith(listener: TaskListener<TProgress, TResult>) {
        before = { listener.onPreExecute() }
        after = Consumer<TResult> { x -> listener.onPostExecute(x) }
        onProgressUpdate = Consumer<TProgress> { x -> listener.onProgressUpdate(x) }
        onCancelled = { listener.onCancelled() }
    }

    var before: (() -> Unit)? = null
    var after: Consumer<TResult>? = null
    var onProgressUpdate: Consumer<TProgress>? = null
    var onCancelled: (() -> Unit)? = null

    fun before(before: () -> Unit): TaskListenerBuilder<TProgress, TResult> {
        this.before = before
        return this
    }

    fun alsoExecuteBefore(before: () -> Unit): TaskListenerBuilder<TProgress, TResult> {
        val previousMethod = this.before
        if (previousMethod == null) {
            this.before = before
        } else {
            this.before = { previousMethod(); before() }
        }
        return this
    }

    fun after(after: Consumer<TResult>): TaskListenerBuilder<TProgress, TResult> {
        this.after = after
        return this
    }

    /** Executes after, changing the return value if more specific */
    fun <TNewResult : TResult> alsoExecuteAfter(after: Consumer<TNewResult>): TaskListenerBuilder<TProgress, TNewResult> {
        // PERF: We can avoid the new class if TResult == TNewResult
        val taskListenerBuilder = TaskListenerBuilder<TProgress, TNewResult>()

        // convert the type to something more specific
        val nextAfter = when (val previousMethod = this.after) {
            null -> { after }
            else -> { Consumer { res -> previousMethod.accept(res); after.accept(res) } }
        }
        taskListenerBuilder.after(nextAfter)

        // copy over the other methods
        val before = this.before
        if (before != null) {
            taskListenerBuilder.before(before)
        }

        val onCancelled = this.onCancelled
        if (onCancelled != null) {
            taskListenerBuilder.onCancelled(onCancelled)
        }

        val onProgressUpdate = this.onProgressUpdate
        if (onProgressUpdate != null) {
            taskListenerBuilder.onProgressUpdate(onProgressUpdate)
        }

        return taskListenerBuilder
    }

    fun onProgressUpdate(onProgressUpdate: Consumer<TProgress>): TaskListenerBuilder<TProgress, TResult> {
        this.onProgressUpdate = onProgressUpdate
        return this
    }

    fun alsoOnProgressUpdate(onProgressUpdate: Consumer<TProgress>): TaskListenerBuilder<TProgress, TResult> {
        val previousMethod = this.onProgressUpdate
        if (previousMethod == null) {
            this.onProgressUpdate = onProgressUpdate
        } else {
            this.onProgressUpdate = Consumer<TProgress> { res -> previousMethod.accept(res); onProgressUpdate.accept(res) }
        }
        return this
    }

    fun onCancelled(onCancelled: () -> Unit): TaskListenerBuilder<TProgress, TResult> {
        this.onCancelled = onCancelled
        return this
    }

    fun alsoOoCancelled(onCancelled: () -> Unit): TaskListenerBuilder<TProgress, TResult> {
        val previousMethod = this.onCancelled
        if (previousMethod == null) {
            this.onCancelled = onCancelled
        } else {
            this.onCancelled = { previousMethod(); onCancelled() }
        }
        return this
    }
}

/** Converts an AnkiTask to a TaskDelegate */
fun <TProgress, TResult> AnkiTask<TProgress, TResult>.asDelegate(): TaskDelegate<TProgress, TResult> {
    val wrapped: AnkiTask<TProgress, TResult> = this

    return object : TaskDelegate<TProgress, TResult>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<TProgress>): TResult {
            val executionContext = object : TaskExecutionContext<TProgress> {
                override fun isCancelled(): Boolean = collectionTask.isCancelled()
                override val col: Collection = col
                override fun doProgress(progress: TProgress) = collectionTask.doProgress(progress)
            }

            return wrapped.execute(executionContext)
        }
    }
}
