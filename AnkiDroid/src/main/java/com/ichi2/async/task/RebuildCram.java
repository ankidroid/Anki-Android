package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class RebuildCram extends UpdateValuesFromDeck {
    public RebuildCram() {
        super(true);
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundRebuildCram");
        Collection col = task.getCol();
        col.getSched().rebuildDyn(col.getDecks().selected());
        return super.background(task);
    }
}
