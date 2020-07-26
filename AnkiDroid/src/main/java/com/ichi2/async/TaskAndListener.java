package com.ichi2.async;

public abstract class TaskAndListener extends TaskListener implements Task {
    public CollectionTask launch() {
        return CollectionTask.launchCollectionTask(this, this);
    }
}