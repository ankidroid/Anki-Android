package com.ichi2.async;

public abstract class TaskAndListener extends TaskListener implements Task {
    public CollectionTask launch() {
        return launch(null);
    }

    public CollectionTask launch(CollectionTask.TASK_TYPE type) {
        return CollectionTask.launchCollectionTask(type, this, new TaskData(this));
    }
}
