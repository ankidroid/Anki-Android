package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.sched.AbstractSched;

import timber.log.Timber;

public class UpdateNote extends Task {
    private final Card mEditCard;
    private final boolean mFromReviewer;

    public UpdateNote(Card editCard, boolean fromReviewer) {
        mEditCard = editCard;
        mFromReviewer = fromReviewer;
    }

    public TaskData background(CollectionTask task) {
        Timber.d("doInBackgroundUpdateNote");
        Collection col = task.getCol();
        // Save the note
        AbstractSched sched = col.getSched();
        Note editNote = mEditCard.note();

        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                // TODO: undo integration
                editNote.flush();
                // flush card too, in case, did has been changed
                mEditCard.flush();
                if (mFromReviewer) {
                    Card newCard;
                    if (col.getDecks().active().contains(mEditCard.getDid())) {
                        newCard = mEditCard;
                        newCard.load();
                        // reload qa-cache
                        newCard.q(true);
                    } else {
                        newCard = sched.getCard();
                    }
                    task.doProgress(new TaskData(newCard));
                } else {
                    task.doProgress(new TaskData(mEditCard, editNote.stringTags()));
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
