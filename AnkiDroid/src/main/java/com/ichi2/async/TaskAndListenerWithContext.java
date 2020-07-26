package com.ichi2.async;

/** Both a background task and a listener */
public abstract class TaskAndListenerWithContext<CTX, Progress, Result> extends TaskListenerWithContext<CTX, Progress, Result> implements Task<Progress, Result>  {
    protected TaskAndListenerWithContext(CTX context) {
        super(context);
    }


    // Launch the task, with itself as task and listener
    public CollectionTask<Progress, Result> launch() {
        return CollectionTask.launchCollectionTask(this, this);
    }
}
