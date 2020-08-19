package com.ichi2.libanki;

import android.content.res.Resources;
import android.util.Pair;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CardUtils;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.Task;
import com.ichi2.libanki.Collection.DismissType;
import com.ichi2.libanki.sched.AbstractSched;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static com.ichi2.libanki.Collection.DismissType.*;

public abstract class Undoable {
    private final DismissType mDt;
    public static final long MULTI_CARD = -1L;
    public static final long NO_REVIEW = 0L;

    /**
     * For all descendants, we assume that a card/note/object passed as argument is never going to be changed again.
     * It's the caller reponsability to clone the object if necessary.*/
    public Undoable(DismissType dt) {
        mDt = dt;
    }

    public String name(Resources res) {
        return res.getString(mDt.undoNameId);
    }

    public DismissType getDismissType() {
        return mDt;
    }

    /**
     * Return MULTI_CARD when no other action is needed, e.g. for multi card action
     * Return NO_REVIEW when we just need to reset the collection
     * Returned positive integers are card id. Those ids is the card that was discarded and that may be sent back to the reviewer.*/
    public abstract long undo(Collection col);

    public static Undoable revertToProvidedState (DismissType dt, Card card){
        Note note = card.note();
        List<Card> cards = note.cards();
        long cid = card.getId();
        return new Undoable(dt) {
            public long undo(Collection col) {
                Timber.i("Undo: %s", dt);
                for (Card cc : cards) {
                    cc.flush(false);
                }
                return cid;
            }
        };
    }


    public static class Task implements com.ichi2.async.Task<Card, Pair<Boolean, Card[]>> {
        public Pair<Boolean, Card[]> background(CollectionTask<Card, ?> collectionTask) {
            Collection col = collectionTask.getCol();
            AbstractSched sched = col.getSched();
            try {
                col.getDb().getDatabase().beginTransaction();
                Card newCard = null;
                try {
                    long cid = col.undo();
                    if (cid == NO_REVIEW) {
                        // /* card schedule change undone, reset and get
                        // new card */
                        Timber.d("Single card non-review change undo succeeded");
                        col.reset();
                        newCard = sched.getCard();
                    } else if (cid == MULTI_CARD) {
                        /* multi-card action undone, no action to take here */
                        Timber.d("Multi-select undo succeeded");
                    } else {
                        // cid is actually a card id.
                        // a review was undone,
                        /* card review undone, set up to review that card again */
                        Timber.d("Single card review undo succeeded");
                        newCard = col.getCard(cid);
                        newCard.startTimer();
                        col.reset();
                        sched.deferReset(newCard);
                        col.getSched().setCurrentCard(newCard);
                    }
                    // TODO: handle leech undoing properly
                    collectionTask.doProgress(newCard);
                    col.getDb().getDatabase().setTransactionSuccessful();
                } finally {
                    col.getDb().getDatabase().endTransaction();
                }
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUndo - RuntimeException on undoing");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUndo");
                return new Pair(false, null);
            }
            return new Pair(true, null);
        }
    }
}
