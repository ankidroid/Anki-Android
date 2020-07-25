package com.ichi2.async.task;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;
import static com.ichi2.libanki.Undoable.*;
import com.ichi2.libanki.sched.AbstractSched;

import java.util.ArrayList;

import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

public abstract class Dismiss extends Task {
    private final Card mCard;
    private final Collection.DismissType mType;

    protected Dismiss(Card card, Collection.DismissType type) {
        mCard = card;
        mType = type;
    }

    @VisibleForTesting
    public Collection.DismissType getType() {
        return mType;
    }

    protected Card getCard() {
        return mCard;
    }

    protected abstract void actualBackground(Collection col);

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                sched.deferReset();
                actualBackground(col);
                // With sHadCardQueue set, getCard() resets the scheduler prior to getting the next card
                task.doProgress(new TaskData(col.getSched().getCard(), 0));
                col.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                col.getDb().getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundDismissNote - RuntimeException on dismissing note, dismiss type %s", mType);
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundDismissNote");
            return new TaskData(false);
        }
        return new TaskData(true);
    }
}
