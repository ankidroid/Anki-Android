/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.ThreadUtil.sleep
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This class consists essentially in executing each received TaskDelegate in the order in which they are received.
 * A single instance should exists and be saved in TaskManager.sTaskManager.
 * TODO: It uses the deprecated AsyncTask and should eventually be replaced by a non deprecated class.
 * Even better would be to ensure that the TaskDelegate that reads (the majority of them) can be executed in parallels.
 */
class SingleTaskManager : TaskManager() {
    /**
     * Tasks which are running or waiting to run.
     */
    private val mTasks = Collections.synchronizedList(LinkedList<CollectionTask<*, *>>())
    private fun addTasks(task: CollectionTask<*, *>) {
        mTasks.add(task)
    }

    @KotlinCleanup("See if removeTaskConcrete could be renamed to removeTask (and similar for other method) once all TaskManagers are in Kotlin")
    override fun removeTaskConcrete(task: CollectionTask<*, *>): Boolean {
        return mTasks.remove(task)
    }

    /**
     * The most recently started [CollectionTask] instance.
     */
    private var mLatestInstance: CollectionTask<*, *>? = null
    override fun setLatestInstanceConcrete(task: CollectionTask<*, *>) {
        mLatestInstance = task
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
    override fun <Progress, Result> launchCollectionTaskConcrete(task: TaskDelegateBase<Progress, Result>): Cancellable {
        return launchCollectionTask(task, null)
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
    // #7108: AsyncTask
    @Suppress("DEPRECATION")
    override fun <Progress, Result> launchCollectionTaskConcrete(
        task: TaskDelegateBase<Progress, Result>,
        listener: TaskListener<in Progress, in Result?>?
    ): Cancellable {
        // Start new task
        return CollectionTask(task, listener, mLatestInstance).apply {
            addTasks(this)
            execute()
        }
    }

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     */
    override fun waitToFinishConcrete() {
        waitToFinish(null)
    }

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     * @param timeoutSeconds timeout in seconds
     * @return whether or not the previous task was successful or not
     */
    // #7108: AsyncTask
    @Suppress("DEPRECATION")
    override fun waitToFinishConcrete(timeoutSeconds: Int?): Boolean {
        return try {
            mLatestInstance?.apply {
                if (status != android.os.AsyncTask.Status.FINISHED) {
                    Timber.d(
                        "CollectionTask: waiting for task %s to finish...",
                        task.javaClass
                    )
                    if (timeoutSeconds != null) {
                        get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                    } else {
                        get()
                    }
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Exception waiting for task to finish")
            false
        }
    }

    /** Cancel the current task only if it's of type taskType  */
    override fun cancelCurrentlyExecutingTaskConcrete() {
        val latestInstance = mLatestInstance
        if (latestInstance != null) {
            if (latestInstance.safeCancel()) {
                Timber.i("Cancelled task %s", latestInstance.task.javaClass)
            }
        }
    }

    /** Cancel all tasks of type taskType */
    override fun cancelAllTasksConcrete(taskType: Class<*>) {
        var count = 0
        // safeCancel modifies mTasks, so iterate over a concrete copy
        for (task in ArrayList(mTasks)) {
            if (task.task.javaClass != taskType) {
                continue
            }
            if (task.safeCancel()) {
                count++
            }
        }
        if (count > 0) {
            Timber.i("Cancelled %d instances of task %s", count, taskType)
        }
    }

    /**
     * Block the current thread until all CollectionTasks have finished.
     * @param timeoutSeconds timeout in seconds
     * @return whether all tasks exited successfully
     */
    @KotlinCleanup("remove unused function")
    override fun waitForAllToFinishConcrete(timeoutSeconds: Int): Boolean {
        // HACK: This should be better - there is currently a race condition in sLatestInstance, and no means to obtain this information.
        // This should work in all reasonable cases given how few tasks we have concurrently blocking.
        var result = true

        repeat(4) {
            result = result and waitToFinish(timeoutSeconds / 4)
            sleep(10)
        }

        Timber.i("Waited for all tasks to finish")
        return result
    }
}
