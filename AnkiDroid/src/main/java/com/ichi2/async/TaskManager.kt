/****************************************************************************************
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.async

import android.content.res.Resources
import androidx.annotation.VisibleForTesting

/**
 * The TaskManager has two related purposes.
 *
 * A concrete TaskManager's mission is to take a TaskDelegate, potentially a CollectionListener, and execute them.
 * Currently, the default TaskManager is SingleTaskManager, which executes the tasks in order in which they are generated. It essentially consists in using basic AsyncTask properties with CollectionTask.
 * It should eventually be replaced by non deprecated system.
 *
 * The only other TaskManager currently is ForegroundTaskManager, which runs everything foreground and is used for unit testings.
 *
 * The class itself contains a static element which is the currently used TaskManager. Tasks can be executed on the current TaskManager with the static method launchTaskManager.
 */
abstract class TaskManager {
    protected abstract fun removeTaskConcrete(task: CollectionTask<*, *>): Boolean
    abstract fun <Progress, Result> launchCollectionTaskConcrete(task: TaskDelegateBase<Progress, Result>): Cancellable
    protected abstract fun setLatestInstanceConcrete(task: CollectionTask<*, *>)
    abstract fun <Progress, Result> launchCollectionTaskConcrete(
        task: TaskDelegateBase<Progress, Result>,
        listener: TaskListener<in Progress, in Result?>?
    ): Cancellable

    abstract fun waitToFinishConcrete()
    abstract fun waitToFinishConcrete(timeoutSeconds: Int?): Boolean
    abstract fun cancelCurrentlyExecutingTaskConcrete()
    abstract fun cancelAllTasksConcrete(taskType: Class<*>)
    abstract fun waitForAllToFinishConcrete(timeoutSeconds: Int): Boolean

    /**
     * Helper class for allowing inner function to publish progress of an AsyncTask.
     */
    @Suppress("SENSELESS_COMPARISON")
    class ProgressCallback<Progress>(task: ProgressSender<Progress>, val resources: Resources) {
        private var mTask: ProgressSender<Progress>? = null
        fun publishProgress(value: Progress) {
            mTask?.doProgress(value)
        }

        init {
            if (resources != null) {
                mTask = task
            } else {
                mTask = null
            }
        }
    }

    companion object {
        private var sTaskManager: TaskManager = SingleTaskManager()

        /**
         * @param tm The new task manager
         * @return The previous one. It may still have tasks running
         */
        @VisibleForTesting
        fun setTaskManager(tm: TaskManager): TaskManager {
            val previous = sTaskManager
            sTaskManager = tm
            return previous
        }

        fun removeTask(task: CollectionTask<*, *>): Boolean {
            return sTaskManager.removeTaskConcrete(task)
        }

        /**
         * Starts a new [CollectionTask], with no listener
         *
         *
         * Tasks will be executed serially, in the order in which they are started.
         *
         *
         * This method must be called on the main thread.
         *
         * @param task the task to execute
         * @return the newly created task
         */
        fun setLatestInstance(task: CollectionTask<*, *>) {
            sTaskManager.setLatestInstanceConcrete(task)
        }

        fun <Progress, Result> launchCollectionTask(task: TaskDelegateBase<Progress, Result>): Cancellable {
            return sTaskManager.launchCollectionTaskConcrete(task)
        }

        /**
         * Starts a new [CollectionTask], with a listener provided for callbacks during execution
         *
         *
         * Tasks will be executed serially, in the order in which they are started.
         *
         *
         * This method must be called on the main thread.
         *
         * @param task the task to execute
         * @param listener to the status and result of the task, may be null
         * @return the newly created task
         */
        fun <Progress, Result> launchCollectionTask(
            task: TaskDelegateBase<Progress, Result>,
            listener: TaskListener<in Progress, in Result?>?
        ): Cancellable {
            return sTaskManager.launchCollectionTaskConcrete(task, listener)
        }

        /**
         * Block the current thread until the currently running CollectionTask instance (if any) has finished.
         */
        fun waitToFinish() {
            sTaskManager.waitToFinishConcrete()
        }

        /**
         * Block the current thread until the currently running CollectionTask instance (if any) has finished.
         * @param timeoutSeconds timeout in seconds (or null to wait indefinitely)
         * @return whether or not the previous task was successful or not, OR if an exception occurred (for example: timeout)
         */
        fun waitToFinish(timeoutSeconds: Int?): Boolean {
            return sTaskManager.waitToFinishConcrete(timeoutSeconds)
        }

        /** Cancel the current task only if it's of type taskType  */
        fun cancelCurrentlyExecutingTask() {
            sTaskManager.cancelCurrentlyExecutingTaskConcrete()
        }

        /** Cancel all tasks of type taskType */
        fun cancelAllTasks(taskType: Class<*>) {
            sTaskManager.cancelAllTasksConcrete(taskType)
        }

        /**
         * Block the current thread until all CollectionTasks have finished.
         * @param timeoutSeconds timeout in seconds
         * @return whether all tasks exited successfully
         */
        fun waitForAllToFinish(timeoutSeconds: Int): Boolean {
            return sTaskManager.waitForAllToFinishConcrete(timeoutSeconds)
        }
    }
}
