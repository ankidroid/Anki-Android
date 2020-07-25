package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.sched.AbstractSched;

public class BuryCard extends Dismiss {
    public BuryCard(Card card) {
        super(card, Collection.DismissType.BURY_CARD);
    }

    @Override
    protected void actualBackground(Collection col) {
        AbstractSched sched = col.getSched();
        Note note = getCard().note();
        // collect undo information
        col.markUndo(new Undoable.UndoableBuryCard(note.cards(), getCard().getId()));
        // then bury
        sched.buryCards(new long[] { getCard().getId() });
    }
}
