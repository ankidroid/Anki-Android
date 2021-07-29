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

package com.ichi2.async;

import com.ichi2.utils.ThreadUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * This class consists essentially in executing each received TaskDelegate in the order in which they are received.
 * A single instance should exists and be saved in TaskManager.sTaskManager.
 * TODO: It uses the deprecated AsyncTask and should eventually be replaced by a non deprecated class.
 * Even better would be to ensure that the TaskDelegate that reads (the majority of them) can be executed in parallels.
 */
public class SingleTaskManager extends TaskManager {

    /**
     * Tasks which are running or waiting to run.
     * */
    private final List<CollectionTask> mTasks = Collections.synchronizedList(new LinkedList<>());

    private void addTasks(CollectionTask task) {
        mTasks.add(task);
    }

    @Override
    protected boolean removeTaskConcrete(CollectionTask task) {
        return mTasks.remove(task);
    }

    /**
     * The most recently started {@link CollectionTask} instance.
     */
    private CollectionTask mLatestInstance;

    protected void setLatestInstanceConcrete(CollectionTask task) {
        mLatestInstance = task;
    }




    /**
     * Starts a new {@link CollectionTask}, with no listener
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param task the task to execute
     * @return the newly created task
     */
    @Override
    public <Progress, Result> Cancellable launchCollectionTaskConcrete(TaskDelegate<Progress, Result> task) {
        return launchCollectionTask(task, null);
    }



    /**
     * Starts a new {@link CollectionTask}, with a listener provided for callbacks during execution
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param task the task to execute
     * @param listener to the status and result of the task, may be null
     * @return the newly created task
     */
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public <Progress, Result> Cancellable
    launchCollectionTaskConcrete(@NonNull TaskDelegate<Progress, Result> task,
                         @Nullable TaskListener<? super Progress, ? super Result> listener) {
        // Start new task
        CollectionTask<Progress, Result> newTask = new CollectionTask<>(task, listener, mLatestInstance);
        addTasks(newTask);
        newTask.execute();
        return newTask;
    }


    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     */
    public void waitToFinishConcrete() {
        waitToFinish(null);
    }

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     * @param timeoutSeconds timeout in seconds
     * @return whether or not the previous task was successful or not
     */
    @Override
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public boolean waitToFinishConcrete(Integer timeoutSeconds) {
        try {
            if ((mLatestInstance != null) && (mLatestInstance.getStatus() != android.os.AsyncTask.Status.FINISHED)) {
                Timber.d("CollectionTask: waiting for task %s to finish...", mLatestInstance.getTask().getClass());
                if (timeoutSeconds != null) {
                    mLatestInstance.get(timeoutSeconds, TimeUnit.SECONDS);
                } else {
                    mLatestInstance.get();
                }

            }
            return true;
        } catch (Exception e) {
            Timber.e(e, "Exception waiting for task to finish");
            return false;
        }
    }


    /** Cancel the current task only if it's of type taskType */
    @Override
    public void cancelCurrentlyExecutingTaskConcrete() {
        CollectionTask latestInstance = mLatestInstance;
        if (latestInstance != null) {
            if (latestInstance.safeCancel()) {
                Timber.i("Cancelled task %s", latestInstance.getTask().getClass());
            }
        }
    }

    /** Cancel all tasks of type taskType*/
    public void cancelAllTasksConcrete(Class taskType) {
        int count = 0;
        // safeCancel modifies mTasks, so iterate over a concrete copy
        for (CollectionTask task: new ArrayList<>(mTasks)) {
            if (task.getTask().getClass() != taskType) {
                continue;
            }
            if (task.safeCancel()) {
                count++;
            }
        }
        if (count > 0) {
            Timber.i("Cancelled %d instances of task %s", count, taskType);
        }
    }

    /**
     * Block the current thread until all CollectionTasks have finished.
     * @param timeoutSeconds timeout in seconds
     * @return whether all tasks exited successfully
     */
    @SuppressWarnings("UnusedReturnValue")
    @Override
    public boolean waitForAllToFinishConcrete(Integer timeoutSeconds) {
        // HACK: This should be better - there is currently a race condition in sLatestInstance, and no means to obtain this information.
        // This should work in all reasonable cases given how few tasks we have concurrently blocking.
        boolean result;
        result = waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        result &= waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        result &= waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        result &= waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        Timber.i("Waited for all tasks to finish");
        return result;
    }
}
