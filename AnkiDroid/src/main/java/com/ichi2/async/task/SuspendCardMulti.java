package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.sched.AbstractSched;

public class SuspendCardMulti extends DismissMulti {

    public SuspendCardMulti(long[] cardsIDs) {
        super(cardsIDs, Collection.DismissType.SUSPEND_CARD_MULTI);
    }
    @Override
    public TaskData actualBackground(CollectionTask task, Card[] cards) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        // collect undo information
        long[] cids = new long[cards.length];
        boolean[] originalSuspended = new boolean[cards.length];
        boolean hasUnsuspended = false;
        for (int i = 0; i < cards.length; i++) {
            Card card = cards[i];
            cids[i] = card.getId();
            if (card.getQueue() != Consts.QUEUE_TYPE_SUSPENDED) {
                hasUnsuspended = true;
                originalSuspended[i] = false;
            } else {
                originalSuspended[i] = true;
            }
        }

        // if at least one card is unsuspended -> suspend all
        // otherwise unsuspend all
        if (hasUnsuspended) {
            sched.suspendCards(cids);
        } else {
            sched.unsuspendCards(cids);
        }

        // mark undo for all at once
        col.markUndo(new Undoable.UndoableSuspendCardMulti(cards, originalSuspended));

        // reload cards because they'll be passed back to caller
        for (Card c : cards) {
            c.load();
        }

        sched.deferReset();
        return null;
    }
}
