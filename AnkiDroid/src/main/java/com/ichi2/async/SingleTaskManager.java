package com.ichi2.async;

import android.os.AsyncTask;

import com.ichi2.utils.ThreadUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

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
    public <ProgressBackground, ResultBackground> CollectionTask<ProgressBackground, ProgressBackground, ResultBackground, ResultBackground> launchCollectionTaskConcrete(CollectionTask.Task<ProgressBackground, ResultBackground> task) {
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
    public <ProgressListener, ProgressBackground extends ProgressListener, ResultListener, ResultBackground extends ResultListener> CollectionTask<ProgressListener, ProgressBackground, ResultListener, ResultBackground>
    launchCollectionTaskConcrete(@NonNull CollectionTask.Task<ProgressBackground, ResultBackground> task,
                         @Nullable TaskListener<ProgressListener, ResultListener> listener) {
        // Start new task
        CollectionTask<ProgressListener, ProgressBackground, ResultListener, ResultBackground> newTask = new CollectionTask<>(task, listener, mLatestInstance);
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
    public boolean waitToFinishConcrete(Integer timeoutSeconds) {
        try {
            if ((mLatestInstance != null) && (mLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
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
