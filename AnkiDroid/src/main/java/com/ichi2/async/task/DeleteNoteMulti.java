package com.ichi2.async.task;

import com.ichi2.anki.CardUtils;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.sched.AbstractSched;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DeleteNoteMulti extends DismissMulti {

    public DeleteNoteMulti(long[] cardsIDs) {
        super(cardsIDs, Collection.DismissType.DELETE_NOTE_MULTI);
    }

    @Override
    public TaskData actualBackground(CollectionTask task, Card[] cards) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        // list of all ids to pass to remNotes method.
        // Need Set (-> unique) so we don't pass duplicates to col.remNotes()
        Set<Note> notes = CardUtils.getNotes(Arrays.asList(cards));
        List<Card> allCards = CardUtils.getAllCards(notes);
        // delete note
        long[] uniqueNoteIds = new long[notes.size()];
        Note[] notesArr = notes.toArray(new Note[notes.size()]);
        int count = 0;
        for (Note note : notes) {
            uniqueNoteIds[count] = note.getId();
            count++;
        }

        col.markUndo(new Undoable.UndoableDeleteNoteMulti(notesArr, allCards));

        col.remNotes(uniqueNoteIds);
        sched.deferReset();
        // pass back all cards because they can't be retrieved anymore by the caller (since the note is deleted)
        task.doProgress(new TaskData(allCards.toArray(new Card[allCards.size()])));
        return null;
    }
}
