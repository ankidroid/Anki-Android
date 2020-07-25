package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.sched.AbstractSched;

import java.util.ArrayList;

public class SuspendNote extends Dismiss {
    public SuspendNote(Card card) {
        super(card, Collection.DismissType.SUSPEND_NOTE);
    }
    @Override
    protected void actualBackground(Collection col) {
        AbstractSched sched = col.getSched();
        Note note = getCard().note();
        // collect undo information
        ArrayList<Card> cards = note.cards();
        long[] cids = new long[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            cids[i] = cards.get(i).getId();
        }
        col.markUndo(new Undoable.UndoableSuspendNote(cards, getCard().getId()));
        // suspend note
        sched.suspendCards(cids);
    }
}
