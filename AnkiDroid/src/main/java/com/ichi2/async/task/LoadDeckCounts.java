package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class LoadDeckCounts extends Task {

    public LoadDeckCounts() {
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundLoadDeckCounts");
        Collection col = task.getCol();
        try {
            // Get due tree
            Object[] o = new Object[] {col.getSched().deckDueTree(task)};
            return new TaskData(o);
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundLoadDeckCounts - error");
            return null;
        }
    }
}
