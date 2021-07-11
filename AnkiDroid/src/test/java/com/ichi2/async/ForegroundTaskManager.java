package com.ichi2.async;

import com.ichi2.libanki.CollectionGetter;

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
    public <Progress, Result> Cancellable launchCollectionTaskConcrete(TaskDelegate<Progress, Result> task) {
        return launchCollectionTaskConcrete(task, null);
    }


    @Override
    protected void setLatestInstanceConcrete(CollectionTask task) {
    }


    @Override
    public <Progress, Result> Cancellable launchCollectionTaskConcrete(
            @NonNull TaskDelegate<Progress, Result> task,
            @Nullable TaskListener<? super Progress, ? super Result> listener) {
        return executeTaskWithListener(task, listener, mColGetter);
    }

    public static <Progress, Result> Cancellable executeTaskWithListener(
            @NonNull TaskDelegate<Progress, Result> task,
            @Nullable TaskListener<? super Progress, ? super Result> listener, CollectionGetter colGetter) {
        if (listener != null) {
            listener.onPreExecute();
        }
        final Result res;
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

    public static class MockTaskManager<ProgressListener, Progress extends ProgressListener> implements ProgressSenderAndCancelListener<Progress> {

        private final @Nullable TaskListener<? super Progress, ?> mTaskListener;


        public MockTaskManager(@Nullable TaskListener<? super Progress, ?> listener) {
            mTaskListener = listener;
        }


        @Override
        public boolean isCancelled() {
            return false;
        }


        @Override
        public void doProgress(@Nullable Progress value) {
            mTaskListener.onProgressUpdate(value);
        }
    }

    public static class EmptyTask<Progress, Result> extends
            CollectionTask<Progress, Result> {

        protected EmptyTask(TaskDelegate<Progress, Result> task, TaskListener<? super Progress, ? super Result> listener) {
            super(task, listener, null);
        }
    }
}
