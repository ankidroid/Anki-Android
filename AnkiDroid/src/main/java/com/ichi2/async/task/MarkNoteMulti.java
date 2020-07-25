package com.ichi2.async.task;

import com.ichi2.anki.CardUtils;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Undoable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class MarkNoteMulti extends DismissMulti {

    public MarkNoteMulti(long[] cardsIDs) {
        super(cardsIDs, Collection.DismissType.MARK_NOTE_MULTI);
    }

    @Override
    public TaskData actualBackground(CollectionTask task, Card[] cards) {
        Collection col = task.getCol();
        Set<Note> notes = CardUtils.getNotes(Arrays.asList(cards));
        // collect undo information
        List<Note> originalMarked = new ArrayList<>();
        List<Note> originalUnmarked = new ArrayList<>();

        for (Note n : notes) {
            if (n.hasTag("marked"))
                originalMarked.add(n);
            else
                originalUnmarked.add(n);
        }

        CardUtils.markAll(new ArrayList<>(notes), !originalUnmarked.isEmpty());

        // mark undo for all at once
        col.markUndo(new Undoable.UndoableMarkNoteMulti(originalMarked, originalUnmarked));

        // reload cards because they'll be passed back to caller
        for (Card c : cards) {
            c.load();
        }
        return null;
    }
}
