package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.sched.AbstractSched;

public class BuryNote extends Dismiss {
    public BuryNote(Card card) {
        super(card, Collection.DismissType.BURY_NOTE);
    }

    @Override
    protected void actualBackground(Collection col) {
        Note note = getCard().note();
        AbstractSched sched = col.getSched();
        // collect undo information
        col.markUndo(new Undoable.UndoableBuryNote(note.cards(), getCard().getId()));
        // then bury
        sched.buryNote(note.getId());
    }
}
