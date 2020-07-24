package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;

import timber.log.Timber;

public class UpdateNotesMulti extends Task {
    private final Card[] mCards;

    public UpdateNotesMulti(Card[] cards) {
        mCards = cards;
    }

    // same as doInBackgroundUpdateNote but for multiple notes
    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundUpdateNotes");
        Collection col = task.getCol();
        // Save the note

        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                for (Card card : mCards) {
                    Note note = card.note();
                    // TODO: undo integration
                    note.flush();
                    // flush card too, in case, did has been changed
                    card.flush();
                    task.doProgress(new TaskData(card, note.stringTags()));
                }

                col.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                col.getDb().getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundUpdateNote - RuntimeException on updating note");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUpdateNote");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
