package com.ichi2.libanki;

import android.content.res.Resources;

import com.ichi2.anki.R;

import java.util.concurrent.LinkedBlockingDeque;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

/**
 * Undo ********************************************************************* **************************
 */

public class UndoManager {
    private static final int UNDO_SIZE_MAX = 20;
    private LinkedBlockingDeque<Undoable> mUndo;


    // API 21: Use a ConcurrentLinkedDeque
    /* Note from upstream:
     * this data structure is a mess, and will be updated soon
     * in the review case, [1, "Review", [firstReviewedCard, secondReviewedCard, ...], wasLeech]
     * in the checkpoint case, [2, "action name"]
     * wasLeech should have been recorded for each card, not globally
     */
    public void clearUndo() {
        mUndo = new LinkedBlockingDeque<>();
    }


    public void markUndo(@NonNull Undoable undo) {
        Timber.d("markUndo() of type %s", undo.getClass());
        this.mUndo.add(undo);
        while (this.mUndo.size() > UNDO_SIZE_MAX) {
            this.mUndo.removeFirst();
        }
    }


    /** Undo menu item name, or "" if undo unavailable.*/
    @VisibleForTesting
    public @Nullable
    Undoable undoType() {
        if (mUndo.size() > 0) {
            return mUndo.getLast();
        }
        return null;
    }


    public boolean undoAvailable() {
        Timber.d("undoAvailable() undo size: %s", mUndo.size());
        return mUndo.size() > 0;
    }


    public @Nullable Card undo(Collection collection) {
        Undoable lastUndo = mUndo.removeLast();
        Timber.d("undo() of type %s", lastUndo.getClass());
        return lastUndo.undo(collection);
    }


    public String undoName(Resources res) {
        Undoable type = undoType();
        if (type != null) {
            return type.name(res);
        }
        return "";
    }


    public void markReview(Card card, Collection collection) {
        boolean wasLeech = card.note().hasTag("leech");
        Card clonedCard = card.clone();
        markUndo(new UndoReview(wasLeech, clonedCard));
    }


    @VisibleForTesting
    public static class UndoReview extends Undoable {
        private final boolean mWasLeech;
        @NonNull private final Card mClonedCard;
        public UndoReview(boolean wasLeech, @NonNull Card clonedCard) {
            super(R.string.undo_action_review);
            mClonedCard = clonedCard;
            mWasLeech = wasLeech;
        }

        @NonNull
        @Override
        public Card undo(@NonNull Collection col) {
            col.getSched().undoReview(mClonedCard, mWasLeech);
            return mClonedCard;
        }
    }
}
