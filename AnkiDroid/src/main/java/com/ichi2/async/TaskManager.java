package com.ichi2.async;

import android.content.res.Resources;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import timber.log.Timber;

public abstract class TaskManager {


    protected abstract void addTasks(CollectionTask task);
    protected abstract boolean removeTask(CollectionTask task);
    public abstract void cancelAllTasks(CollectionTask.TASK_TYPE taskType);


    protected abstract void setLatestInstance(CollectionTask task);

    /**
     * Starts a new {@link CollectionTask}, with no listener
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @return the newly created task
     */
    public static CollectionTask launchCollectionTask(CollectionTask.TASK_TYPE type) {
        return launchCollectionTask(type, null, null);
    }

    /**
     * Starts a new {@link CollectionTask}, with no listener
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @param param to pass to the task
     * @return the newly created task
     */
    public static CollectionTask launchCollectionTask(CollectionTask.TASK_TYPE type, TaskData param) {
        return launchCollectionTask(type, null, param);
    }

    /**
     * Starts a new {@link CollectionTask}, with a listener provided for callbacks during execution
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @param listener to the status and result of the task, may be null
     * @return the newly created task
     */
    public static CollectionTask launchCollectionTask(CollectionTask.TASK_TYPE type, @Nullable TaskListener listener) {
        // Start new task
        return launchCollectionTask(type, listener, null);
    }

    /**
     * Starts a new {@link CollectionTask}, with a listener provided for callbacks during execution
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @param listener to the status and result of the task, may be null
     * @param param to pass to the task
     * @return the newly created task
     */
    public abstract CollectionTask launchCollectionTask(CollectionTask.TASK_TYPE type, @Nullable TaskListener listener, TaskData param);


    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        waitToFinish(null);
    }

    public abstract boolean waitToFinish(Integer timeout);

    /** Cancel the current task only if it's of type taskType */
    public abstract void cancelCurrentlyExecutingTask();

    public static ProgressCallback progressCallback(CollectionTask task, Resources res) {
        return new ProgressCallback(task, res);
    }


    /**
     * Helper class for allowing inner function to publish progress of an AsyncTask.
     */
    public static class ProgressCallback {
        private Resources res;
        private CollectionTask task;


        protected ProgressCallback(CollectionTask task, Resources res) {
            this.res = res;
            if (res != null) {
                this.task = task;
            } else {
                this.task = null;
            }
        }


        public Resources getResources() {
            return res;
        }


        public void publishProgress(TaskData value) {
            if (task != null) {
                TaskManager.publishProgress(task, value);
            }
        }
    }

    // Here so that progress can be published differently depending on the manager.
    public abstract void publishProgress(CollectionTask ct, TaskData value);
}
