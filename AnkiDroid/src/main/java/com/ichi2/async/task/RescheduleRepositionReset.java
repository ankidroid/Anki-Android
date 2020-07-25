package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.sched.AbstractSched;

import java.util.concurrent.CancellationException;

import timber.log.Timber;

public abstract class RescheduleRepositionReset extends DismissMulti {
    protected RescheduleRepositionReset(long[] cardsIDs, Collection.DismissType type) {
        super(cardsIDs, type);
    }

    protected abstract void actualActualBackground(AbstractSched sched);

    @Override
    public TaskData actualBackground(CollectionTask task, Card[] cards) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        // collect undo information, sensitive to memory pressure, same for all 3 cases
        try {
            Timber.d("Saving undo information of type %s on %d cards", getType(), cards.length);
            col.markUndo(new Undoable.UndoableRepositionRescheduleResetCards(getType(), deepCopyCardArray(task, cards)));
        } catch (CancellationException ce) {
            Timber.i(ce, "Cancelled while handling type %s, skipping undo", getType());
        }
        actualActualBackground(sched);
        // In all cases schedule a new card so Reviewer doesn't sit on the old one
        col.reset();
        task.doProgress(new TaskData(sched.getCard(), 0));
        return null;
    }
}
