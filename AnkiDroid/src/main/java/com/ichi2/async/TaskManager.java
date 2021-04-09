package com.ichi2.async;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public abstract class TaskManager {
    @NonNull private static TaskManager sTaskManager = new SingleTaskManager();

    /**
     * @param tm The new task manager
     * @return The previous one. It may still have tasks running
     */
    @VisibleForTesting
    public static TaskManager setTaskManager(TaskManager tm) {
        TaskManager previous = sTaskManager;
        sTaskManager = tm;
        return previous;
    }

    protected static boolean removeTask(CollectionTask task) {
        return sTaskManager.removeTaskConcrete(task);
    }

    protected abstract boolean removeTaskConcrete(CollectionTask task);

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
    protected static void setLatestInstance(CollectionTask task) {
        sTaskManager.setLatestInstanceConcrete(task);
    }

    public static <ProgressBackground, ResultBackground> CollectionTask<ProgressBackground, ProgressBackground, ResultBackground, ResultBackground> launchCollectionTask(CollectionTask.Task<ProgressBackground, ResultBackground> task) {
        return sTaskManager.launchCollectionTaskConcrete(task);
    }

    public abstract <ProgressBackground, ResultBackground> CollectionTask<ProgressBackground, ProgressBackground, ResultBackground, ResultBackground> launchCollectionTaskConcrete(CollectionTask.Task<ProgressBackground, ResultBackground> task);


    protected abstract void setLatestInstanceConcrete(CollectionTask task);

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
    public static <ProgressListener, ProgressBackground extends ProgressListener, ResultListener, ResultBackground extends ResultListener> CollectionTask<ProgressListener, ProgressBackground, ResultListener, ResultBackground>
    launchCollectionTask(@NonNull CollectionTask.Task<ProgressBackground, ResultBackground> task,
                         @Nullable TaskListener<ProgressListener, ResultListener> listener) {
        return sTaskManager.launchCollectionTaskConcrete(task, listener);
    }

    public abstract <ProgressListener, ProgressBackground extends ProgressListener, ResultListener, ResultBackground extends ResultListener> CollectionTask<ProgressListener, ProgressBackground, ResultListener, ResultBackground>
    launchCollectionTaskConcrete(@NonNull CollectionTask.Task<ProgressBackground, ResultBackground> task,
                         @Nullable TaskListener<ProgressListener, ResultListener> listener);

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        sTaskManager.waitToFinishConcrete();
    };
    public abstract void waitToFinishConcrete();

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     * @param timeoutSeconds timeout in seconds
     * @return whether or not the previous task was successful or not
     */
    public static boolean waitToFinish(Integer timeoutSeconds) {
        return sTaskManager.waitToFinishConcrete(timeoutSeconds);
    };
    public abstract boolean waitToFinishConcrete(Integer timeoutSeconds);


    /** Cancel the current task only if it's of type taskType */
    public static void cancelCurrentlyExecutingTask() {
        sTaskManager.cancelCurrentlyExecutingTaskConcrete();
    }
    public abstract void cancelCurrentlyExecutingTaskConcrete();

    /** Cancel all tasks of type taskType*/
    public static void cancelAllTasks(Class taskType) {
        sTaskManager.cancelAllTasksConcrete(taskType);
    }
    public abstract void cancelAllTasksConcrete(Class taskType);

    /**
     * Block the current thread until all CollectionTasks have finished.
     * @param timeoutSeconds timeout in seconds
     * @return whether all tasks exited successfully
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean waitForAllToFinish(Integer timeoutSeconds) {
        return sTaskManager.waitToFinishConcrete(timeoutSeconds);
    }
    public abstract boolean waitForAllToFinishConcrete(Integer timeoutSeconds);




        /**
         * Helper class for allowing inner function to publish progress of an AsyncTask.
         */
    public static class ProgressCallback<Progress> {
        private final Resources mRes;
        private final ProgressSender<Progress> mTask;


        protected ProgressCallback(ProgressSender<Progress> task, Resources res) {
            this.mRes = res;
            if (res != null) {
                this.mTask = task;
            } else {
                this.mTask = null;
            }
        }


        public Resources getResources() {
            return mRes;
        }


        public void publishProgress(Progress value) {
            if (mTask != null) {
                mTask.doProgress(value);
            }
        }
    }


    public static ProgressCallback progressCallback(CollectionTask task, Resources res) {
        return new ProgressCallback(task, res);
    }

}
