package com.ichi2.libanki;

import android.content.res.Resources;

import com.ichi2.anki.CardUtils;
import com.ichi2.libanki.Collection.DismissType;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import timber.log.Timber;

import static com.ichi2.libanki.Collection.DismissType.*;

public abstract class Undoable {
    private final DismissType mDt;
    public static final long MULTI_CARD = -1L;
    public static final long NOT_UNDONE = -2L;
    public static final long NO_REVIEW = 0L;
    private Boolean mStarted = false;

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
     * Return NOT_UNDONE if the undo was already started and so is not executed here
     * Returned positive integers are card id. Those ids is the card that was discarded and that may be sent back to the reviewer.*/
    public long undo(Collection col){
        synchronized (mStarted) {
            if (mStarted) {
                return NOT_UNDONE;
            }
            mStarted = true;
        }
        return actualUndo(col);
    }
    /**Actually undo. It assumes the action was not already undone. Return same as undo, but can't return NOT_UNDONE */
    protected abstract long actualUndo(Collection col);

    public boolean isStarted() {
        return mStarted;
    }
    public static Undoable revertToProvidedState (DismissType dt, Card card){
        Note note = card.note();
        List<Card> cards = note.cards();
        long cid = card.getId();
        return new Undoable(dt) {
            protected long actualUndo(Collection col) {
                Timber.i("Undo: %s", dt);
                for (Card cc : cards) {
                    cc.flush(false);
                }
                return cid;
            }
        };
    }

    public @NonNull String getName(Resources res) {
        return res.getString(mDt.undoNameId);
    }

    /** Whether this Undoable undo a review. Reviews need special care when undone*/
    public boolean isReview() {
        return false;
    }
}
