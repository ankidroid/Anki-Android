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

import timber.log.Timber;

public class Dismiss extends Task {
    private final Card mCard;
    private final Collection.DismissType mType;

    public Dismiss(Card card, Collection.DismissType type) {
        mCard = card;
        mType = type;
    }

    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        AbstractSched sched = col.getSched();
        Note note = mCard.note();
        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                sched.deferReset();
                switch (mType) {
                    case BURY_CARD:
                        // collect undo information
                        col.markUndo(new UndoableBuryCard(note.cards(), mCard.getId()));
                        // then bury
                        sched.buryCards(new long[] { mCard.getId() });
                        break;
                    case BURY_NOTE:
                        // collect undo information
                        col.markUndo(new UndoableBuryNote(note.cards(), mCard.getId()));
                        // then bury
                        sched.buryNote(note.getId());
                        break;
                    case SUSPEND_CARD:
                        // collect undo information
                        col.markUndo(new UndoableSuspendCard(mCard.clone()));
                        // suspend card
                        if (mCard.getQueue() == Consts.QUEUE_TYPE_SUSPENDED) {
                            sched.unsuspendCards(new long[] { mCard.getId() });
                        } else {
                            sched.suspendCards(new long[] { mCard.getId() });
                        }
                        break;
                    case SUSPEND_NOTE: {
                        // collect undo information
                        ArrayList<Card> cards = note.cards();
                        long[] cids = new long[cards.size()];
                        for (int i = 0; i < cards.size(); i++) {
                            cids[i] = cards.get(i).getId();
                        }
                        col.markUndo(new UndoableSuspendNote(cards, mCard.getId()));
                        // suspend note
                        sched.suspendCards(cids);
                        break;
                    }

                    case DELETE_NOTE: {
                        // collect undo information
                        ArrayList<Card> allCs = note.cards();
                        col.markUndo(new UndoableDeleteNote(note, allCs, mCard.getId()));
                        // delete note
                        col.remNotes(new long[] { note.getId() });
                        break;
                    }
                }
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
