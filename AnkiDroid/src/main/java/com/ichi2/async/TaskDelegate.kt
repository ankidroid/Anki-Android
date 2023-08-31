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

import com.ichi2.libanki.Collection

/** @see [TaskDelegate] */
abstract class TaskDelegateBase<Progress, Result> {
    abstract fun execTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Progress>): Result
    abstract fun requiresOpenCollection(): Boolean
}

/**
 * TaskDelegate contains the business logic of background tasks.
 * While CollectionTask deals with all general task features, such as ensuring that no two tasks runs simultaneously
 * and Timberings, the Task contains the code that we actually want to execute.
 * <p>
 * TaskManager.launchCollectionTask takes a Task, and potentially a TaskListener. It is in charge of running
 * ensuring that the task is executed, by embedding this Task in an object that can actually be executed.
 * <p>
 * <p>
 * Currently, background processes uses CollectionTask, which inherits from AsyncTask, which is deprecated. Using this
 * delegation, we should hopefully eventually be able to stop using AsyncTask without making any change to the Task.
 * <p>
 * Tests can runs tasks in Foreground by changing the task manager. Those tasks can then be directly executed by
 * ForegroundTaskManager without needing an executor.
 * <p>
 * The Task type is used to cancel planified tasks. In particular it means that no Task should
 * be an anonymous class if we want to be able to cancel the task running it.
 *
 * @param <Progress> The type of values that the task can send to indicates its progress. E.g. a card to display while remaining work is done; the progression of a counter.
 * @param <Result>   The type of result returned by the task at the end. E.g. the tree of decks, counts for a particular deck
 */
abstract class TaskDelegate<Progress, Result> : TaskDelegateBase<Progress, Result>() {
    final override fun execTask(col: Collection, collectionTask: ProgressSenderAndCancelListener<Progress>): Result =
        task(col, collectionTask)
    protected abstract fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Progress>): Result
    final override fun requiresOpenCollection(): Boolean = true
}
