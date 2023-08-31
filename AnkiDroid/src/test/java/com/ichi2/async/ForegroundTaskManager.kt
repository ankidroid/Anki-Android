/*
 *  Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.async

import com.ichi2.libanki.CollectionGetter
import timber.log.Timber

class ForegroundTaskManager(private val colGetter: CollectionGetter) : TaskManager() {
    override fun removeTaskConcrete(task: CollectionTask<*, *>): Boolean {
        return true
    }

    override fun <Progress, Result> launchCollectionTaskConcrete(task: TaskDelegateBase<Progress, Result>): Cancellable {
        return launchCollectionTaskConcrete(task, null)
    }

    override fun setLatestInstanceConcrete(task: CollectionTask<*, *>) {}
    override fun <Progress, Result> launchCollectionTaskConcrete(
        task: TaskDelegateBase<Progress, Result>,
        listener: TaskListener<in Progress, in Result?>?
    ): Cancellable {
        return executeTaskWithListener(task, listener, colGetter)
    }

    override fun waitToFinishConcrete() {}
    override fun waitToFinishConcrete(timeoutSeconds: Int?): Boolean {
        return true
    }

    override fun cancelCurrentlyExecutingTaskConcrete() {}
    override fun cancelAllTasksConcrete(taskType: Class<*>) {}
    override fun waitForAllToFinishConcrete(timeoutSeconds: Int): Boolean {
        return true
    }

    class MockTaskManager<ProgressListener, Progress : ProgressListener?>(
        private val taskListener: TaskListener<in Progress, *>?
    ) : ProgressSenderAndCancelListener<Progress> {
        override fun isCancelled(): Boolean {
            return false
        }

        override fun doProgress(value: Progress?) {
            taskListener!!.onProgressUpdate(value!!)
        }
    }

    class EmptyTask<Progress, Result>(
        task: TaskDelegateBase<Progress, Result>?,
        listener: TaskListener<in Progress, in Result?>?
    ) : CollectionTask<Progress, Result>(
        task!!,
        listener,
        null
    )

    companion object {
        fun <Progress, Result> executeTaskWithListener(
            task: TaskDelegateBase<Progress, Result>,
            listener: TaskListener<in Progress, in Result?>?,
            colGetter: CollectionGetter
        ): Cancellable {
            listener?.onPreExecute()
            val res: Result = try {
                task.execTask(colGetter.col, MockTaskManager(listener))
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "A new failure may have something to do with running in the foreground."
                )
                throw e
            }
            listener?.onPostExecute(res)
            return EmptyTask(task, listener)
        }
    }
}
