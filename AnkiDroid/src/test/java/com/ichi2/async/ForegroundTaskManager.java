package com.ichi2.async;

import com.ichi2.libanki.CollectionGetter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ForegroundTaskManager extends TaskManager {
    private final CollectionGetter mColGetter;


    public ForegroundTaskManager(CollectionGetter colGetter) {
        mColGetter = colGetter;
    }


    @Override
    protected boolean removeTask(CollectionTask task) {
        return true;
    }


    @Override
    protected void setLatestInstance(CollectionTask task) {
    }


    @Override
    public <ProgressListener, ProgressBackground extends ProgressListener, ResultListener, ResultBackground extends ResultListener> CollectionTask<ProgressListener, ProgressBackground, ResultListener, ResultBackground> launchCollectionTask(
            @NonNull CollectionTask.Task<ProgressBackground, ResultBackground> task,
            @Nullable TaskListener<ProgressListener, ResultListener> listener) {
        CollectionTask<ProgressListener, ProgressBackground, ResultListener, ResultBackground>  ct =
                new CollectionTask<>(task, listener, null);
        ct.execute();
        try {
            ct.wait();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return ct;
    }


    @Override
    public void waitToFinish() {
    }


    @Override
    public boolean waitToFinish(Integer timeoutSeconds) {
        return true;
    }


    @Override
    public void cancelCurrentlyExecutingTask() {
    }


    @Override
    public void cancelAllTasks(Class taskType) {
    }


    @Override
    public boolean waitForAllToFinish(Integer timeoutSeconds) {
        return true;
    }
}
