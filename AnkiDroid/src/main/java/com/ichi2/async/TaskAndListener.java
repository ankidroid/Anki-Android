package com.ichi2.async;

public abstract class TaskAndListener<Progress, Result> extends TaskListener<Progress, Result> implements Task<Progress, Result> {
    public CollectionTask<Progress, Result>launch() {
        return CollectionTask.launchCollectionTask(this, this);
    }
}
