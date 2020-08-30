package com.ichi2.async;

import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import timber.log.Timber;

public class BackgroundTaskManager extends TaskManager {
    /**
     * Tasks which are running or waiting to run.
     * */
    private final List<CollectionTask> mTasks = Collections.synchronizedList(new LinkedList<>());

    /**
     * The most recently started {@link CollectionTask} instance.
     */
    private CollectionTask mLatestInstance;

    protected void setLatestInstance(CollectionTask task) {
        mLatestInstance = task;
    }

    protected void addTasks(CollectionTask task) {
        mTasks.add(task);
    }

    protected boolean removeTask(CollectionTask task) {
        return mTasks.remove(task);
    }

    /** Cancel all tasks of type taskType*/
    public void cancelAllTasks(CollectionTask.TASK_TYPE taskType) {
        int count = 0;
        // safeCancel modifies sTasks, so iterate over a concrete copy
        for (CollectionTask task: new ArrayList<>(mTasks)) {
            if (task.getType() != taskType) {
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

    public CollectionTask launchCollectionTask(CollectionTask.TASK_TYPE type, @Nullable TaskListener listener, TaskData param) {
        // Start new task
        CollectionTask newTask = new CollectionTask(type, listener, mLatestInstance);
        newTask.execute(param);
        return newTask;
    }

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     * @param timeout timeout in seconds
     * @return whether or not the previous task was successful or not
     */
    public boolean waitToFinish(Integer timeout) {
        try {
            if ((mLatestInstance != null) && (mLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                Timber.d("CollectionTask: waiting for task %s to finish...", mLatestInstance.getType());
                if (timeout != null) {
                    mLatestInstance.get(timeout, TimeUnit.SECONDS);
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
    public void cancelCurrentlyExecutingTask() {
        CollectionTask latestInstance = mLatestInstance;
        if (latestInstance != null) {
            if (latestInstance.safeCancel()) {
                Timber.i("Cancelled task %s", latestInstance.getType());
            }
        };
    }

    public void publishProgress(CollectionTask ct, TaskData value) {
        ct.publishProgress(value);
    }
}
