package com.ichi2.libanki;

import android.content.res.Resources;

import com.ichi2.anki.CardUtils;
import com.ichi2.libanki.Collection.DismissType;

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



    public static class UndoableFlushAll extends Undoable {
        private final List<Card> mCards;
        private final long mCid;
        public UndoableFlushAll(DismissType dt, List<Card> cards, long cid) {
            super(dt);
            mCards = cards;
            mCid = cid;
        }

        public long undo(Collection col) {
            Timber.i("Undo: Bury Card");
            for (Card cc : mCards) {
                cc.flush(false);
            }
            return mCid;
        }
    }

    public static class UndoableChangeDeckMulti extends Undoable {
        private final Card[] mCards;
        private final long[] mOriginalDid;
        public UndoableChangeDeckMulti(Card[]cards, long[] originalDid) {
            super(CHANGE_DECK_MULTI);
            mCards = cards;
            mOriginalDid = originalDid;
        }

        public long undo(Collection col) {
            Timber.i("Undo: Change Decks");
            // move cards to original deck
            for (int i = 0; i < mCards.length; i++) {
                Card card = mCards[i];
                card.load();
                card.setDid(mOriginalDid[i]);
                Note note = card.note();
                note.flush();
                card.flush();
            }
            return MULTI_CARD;  // don't fetch new card

        }
    }

    public static class UndoableMarkNoteMulti extends Undoable {
        private final List<Note> mOriginalMarked;
        private final List<Note> mOriginalUnmarked;
        public UndoableMarkNoteMulti(List<Note> originalMarked, List<Note> originalUnmarked) {
            super(MARK_NOTE_MULTI);
            mOriginalMarked = originalMarked;
            mOriginalUnmarked = originalUnmarked;
        }

        public long undo(Collection col) {
            Timber.i("Undo: Mark notes");
            CardUtils.markAll(mOriginalMarked, true);
            CardUtils.markAll(mOriginalUnmarked, false);
            return MULTI_CARD;  // don't fetch new card
        }
    }

    public static class UndoableFlag extends Undoable {
        public UndoableFlag(Collection col) {
            super(FLAG);
        }

        public long undo(Collection col) {
            Timber.d("Not implemented.");
            return NO_REVIEW;
        }
    }

    public static class UndoableRepositionRescheduleResetCards extends Undoable {
        private final Card[] mCards;
        public UndoableRepositionRescheduleResetCards(DismissType dt, Card[] cards) {
            super(dt);
            mCards = cards;
        }

        public long undo(Collection col) {
            Timber.i("Undoing action of type %s on %d cards", getDismissType(), mCards.length);
            for (int i = 0; i < mCards.length; i++) {
                Card card = mCards[i];
                card.flush(false);
            }
            return NO_REVIEW;
        }
    }
}
