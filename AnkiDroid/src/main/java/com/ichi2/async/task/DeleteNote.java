package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Undoable;

import java.util.ArrayList;

public class DeleteNote extends Dismiss {
    public DeleteNote(Card card) {
        super(card, Collection.DismissType.DELETE_NOTE);
    }

    @Override
    protected void actualBackground(Collection col) {
        Note note = getCard().note();
        // collect undo information
        ArrayList<Card> allCs = note.cards();
        col.markUndo(new Undoable.UndoableDeleteNote(note, allCs, getCard().getId()));
        // delete note
        col.remNotes(new long[] { note.getId() });
    }
}
