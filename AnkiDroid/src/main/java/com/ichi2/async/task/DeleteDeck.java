package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class DeleteDeck extends Task {
    private final long mDid;

    public DeleteDeck(long did) {
        mDid = did;
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        Timber.d("doInBackgroundDeleteDeck");
        col.getDecks().rem(mDid, true);

        return new TaskData(true);
    }
}
