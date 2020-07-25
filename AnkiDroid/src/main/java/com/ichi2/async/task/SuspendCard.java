package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.sched.AbstractSched;

public class SuspendCard extends Dismiss{
    public SuspendCard(Card card) {
        super(card, Collection.DismissType.SUSPEND_CARD);
    }

    @Override
    protected void actualBackground(Collection col) {
        AbstractSched sched = col.getSched();
        // collect undo information
        col.markUndo(new Undoable.UndoableSuspendCard(getCard().clone()));
        // suspend card
        if (getCard().getQueue() == Consts.QUEUE_TYPE_SUSPENDED) {
            sched.unsuspendCards(new long[] { getCard().getId() });
        } else {
            sched.suspendCards(new long[] { getCard().getId() });
        }
    }
}
