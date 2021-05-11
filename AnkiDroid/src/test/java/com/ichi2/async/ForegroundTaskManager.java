package com.ichi2.async;

import com.ichi2.libanki.CollectionGetter;
import com.ichi2.libanki.Collection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class ForegroundTaskManager extends TaskManager {
    private final CollectionGetter mColGetter;


    public ForegroundTaskManager(CollectionGetter colGetter) {
        mColGetter = colGetter;
    }

    @Override
    protected boolean removeTaskConcrete(CollectionTask task) {
        return true;
    }


    @Override
    public <ProgressBackground, ResultBackground> CollectionTask<ProgressBackground, ResultBackground> launchCollectionTaskConcrete(CollectionTask.Task<ProgressBackground, ResultBackground> task) {
        return launchCollectionTaskConcrete(task, null);
    }


    @Override
    protected void setLatestInstanceConcrete(CollectionTask task) {
    }


    @Override
    public <ProgressBackground, ResultBackground> CollectionTask<ProgressBackground, ResultBackground> launchCollectionTaskConcrete(
            @NonNull CollectionTask.Task<ProgressBackground, ResultBackground> task,
            @Nullable TaskListener<? super ProgressBackground, ? super ResultBackground> listener) {
        return executeTaskWithListener(task, listener, mColGetter);
    }

    public static <ProgressBackground, ResultBackground> CollectionTask<ProgressBackground, ResultBackground> executeTaskWithListener(
            @NonNull CollectionTask.Task<ProgressBackground, ResultBackground> task,
            @Nullable TaskListener<? super ProgressBackground, ? super ResultBackground> listener, CollectionGetter colGetter) {
        if (listener != null) {
            listener.onPreExecute();
        }
        final ResultBackground res;
        try {
            res = task.task(colGetter.getCol(), new MockTaskManager<>(listener));
        } catch (Exception e) {
            Timber.w(e, "A new failure may have something to do with running in the foreground.");
            throw e;
        }
        if (listener != null) {
            listener.onPostExecute(res);
        }
        return new EmptyTask<>(task, listener);
    }


    @Override
    public void waitToFinishConcrete() {
    }


    @Override
    public boolean waitToFinishConcrete(Integer timeoutSeconds) {
        return true;
    }


    @Override
    public void cancelCurrentlyExecutingTaskConcrete() {
    }


    @Override
    public void cancelAllTasksConcrete(Class taskType) {
    }


    @Override
    public boolean waitForAllToFinishConcrete(Integer timeoutSeconds) {
        return true;
    }

    public static class MockTaskManager<ProgressListener, ProgressBackground extends ProgressListener> implements ProgressSenderAndCancelListener<ProgressBackground> {

        private final @Nullable TaskListener<? super ProgressBackground, ?> mTaskListener;


        public MockTaskManager(@Nullable TaskListener<? super ProgressBackground, ?> listener) {
            mTaskListener = listener;
        }


        @Override
        public boolean isCancelled() {
            return false;
        }


        @Override
        public void doProgress(@Nullable ProgressBackground value) {
            mTaskListener.onProgressUpdate(value);
        }
    }

    public static class EmptyTask<ProgressBackground, ResultBackground> extends
            CollectionTask<ProgressBackground, ResultBackground> {

        protected EmptyTask(Task<ProgressBackground, ResultBackground> task, TaskListener<? super ProgressBackground, ? super ResultBackground> listener) {
            super(task, listener, null);
        }
    }
}
