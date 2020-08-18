package com.ichi2.async;

/** Both a background task and a listener */
public abstract class TaskAndListenerWithContext<CTX> extends TaskListenerWithContext<CTX> implements Task  {
    protected TaskAndListenerWithContext(CTX context) {
        super(context);
    }


    // Launch the task, with itself as task and listener
    public CollectionTask launch() {
        return CollectionTask.launchCollectionTask(null, this, new TaskData(this));
    }
}
