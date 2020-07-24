package com.ichi2.async.task;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.AbstractSched;

import timber.log.Timber;

public class UpdateValuesFromDeck extends Task {
    private final boolean mReset;

    public UpdateValuesFromDeck(boolean reset) {
        mReset = reset;
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundUpdateValuesFromDeck");
        Collection col = task.getCol();
        try {
            AbstractSched sched = col.getSched();
            if (mReset) {
                // reset actually required because of counts, which is used in getCollectionTaskListener
                sched.reset();
            }
            int[] counts = sched.counts();
            int totalNewCount = sched.totalNewForCurrentDeck();
            int totalCount = sched.cardCount();
            return new TaskData(new Object[]{counts[0], counts[1], counts[2], totalNewCount,
                    totalCount, sched.eta(counts)});
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundUpdateValuesFromDeck - an error occurred");
            return null;
        }
    }
}
