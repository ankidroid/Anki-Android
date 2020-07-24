package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class EmptyCram extends UpdateValuesFromDeck {
    public EmptyCram() {
        super(true);
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundEmptyCram");
        col.getSched().emptyDyn(col.getDecks().selected());
        return super.background(task);
    }
}
