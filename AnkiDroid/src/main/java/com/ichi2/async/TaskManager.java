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

    private static final TaskManager sTaskManager = new BackgroundTaskManager();


    protected static void addTasks(CollectionTask task){
        sTaskManager.addTasks_(task);
    }
    protected abstract void addTasks_(CollectionTask task);

    protected static boolean removeTask(CollectionTask task) {
        return sTaskManager.removeTask_(task);
    }

    protected abstract boolean removeTask_(CollectionTask task);

    public static void cancelAllTasks(CollectionTask.TASK_TYPE taskType) {
        sTaskManager.cancelAllTasks_(taskType);
    }

    public abstract void cancelAllTasks_(CollectionTask.TASK_TYPE taskType);

    protected static void setLatestInstance(CollectionTask task){
        sTaskManager.setLatestInstance_(task);
    }
    protected abstract void setLatestInstance_(CollectionTask task);

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
    public static CollectionTask launchCollectionTask(CollectionTask.TASK_TYPE type, @Nullable TaskListener listener, TaskData param) {
        return sTaskManager.launchCollectionTask_(type, listener, param);
    }
    public abstract CollectionTask launchCollectionTask_(CollectionTask.TASK_TYPE type, @Nullable TaskListener listener, TaskData param);


    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     */
    public static boolean waitToFinish(){
        return waitToFinish(null);
    }

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     */
    public static boolean waitToFinish(Integer timeout){
        return sTaskManager.waitToFinish_(timeout);
    }

    public abstract boolean waitToFinish_(Integer timeout);

    /** Cancel the current task only if it's of type taskType */
    public static void cancelCurrentlyExecutingTask() {
        sTaskManager.cancelCurrentlyExecutingTask_();
    }
    public abstract void cancelCurrentlyExecutingTask_();

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
    public static void publishProgress(CollectionTask ct, TaskData value) {
        sTaskManager.publishProgress_(ct, value);
    }
    public abstract void publishProgress_(CollectionTask ct, TaskData value);
}
