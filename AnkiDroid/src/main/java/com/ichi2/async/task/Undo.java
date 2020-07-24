package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sched.AbstractSched;

import timber.log.Timber;

public class Undo extends Task {
    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        try {
            col.getDb().getDatabase().beginTransaction();
            Card newCard = null;
            try {
                long cid = col.undo();
                if (cid == 0) {
                    // /* card schedule change undone, reset and get
                    // new card */
                    Timber.d("Single card non-review change undo succeeded");
                    col.reset();
                    newCard = sched.getCard();
                } else if (cid > 0) {
                    // a review was undone,
                     /* card review undone, set up to review that card again */
                    Timber.d("Single card review undo succeeded");
                    newCard = col.getCard(cid);
                    newCard.startTimer();
                    col.reset();
                    sched.deferReset(newCard);
                    col.getSched().setCurrentCard(newCard);
                } else {
                    // cid < 0
                    /* multi-card action undone, no action to take here */
                    Timber.d("Multi-select undo succeeded");
                }
                // TODO: handle leech undoing properly
                task.doProgress(new TaskData(newCard, 0));
                col.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                col.getDb().getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundUndo - RuntimeException on undoing");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUndo");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
