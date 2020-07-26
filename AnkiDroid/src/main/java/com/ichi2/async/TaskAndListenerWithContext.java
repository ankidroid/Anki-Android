package com.ichi2.async;

/** Both a background task and a listener */
public abstract class TaskAndListenerWithContext<CTX> extends TaskListenerWithContext<CTX> implements Task  {
    protected TaskAndListenerWithContext(CTX context) {
        super(context);
    }


    // Launch the task, with itself as task and listener
    public CollectionTask launch(CollectionTask.TASK_TYPE type) {
        return CollectionTask.launchCollectionTask(type, this, new TaskData(this));
    }

    public CollectionTask launch() {
        return launch(null);
    }
}
