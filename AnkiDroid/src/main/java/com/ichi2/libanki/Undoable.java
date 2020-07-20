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
     * Return -1 when no other action is needed, e.g. for multi card action
     * Return 0 when we just need to reset the collection
     * Returned positive integers are card id. Those ids is the card that was discarded and that may be sent back to the reviewer.*/
    public abstract long undo(Collection col);

    public static class UndoableReview extends Undoable {
        private final Card mCard;
        private final boolean mWasLeech;
        public UndoableReview(Card card, boolean wasLeach) {
            super(REVIEW);
            mCard = card.clone();
            mWasLeech = wasLeach;
        }

        public long undo(Collection col) {
            // remove leech tag if it didn't have it before
            if (!mWasLeech && mCard.note().hasTag("leech")) {
                mCard.note().delTag("leech");
                mCard.note().flush();
            }
            Timber.i("Undo Review of card %d, leech: %b", mCard.getId(), mWasLeech);
            // write old data
            mCard.flush(false);
            // and delete revlog entry
            long last = col.getDb().queryLongScalar("SELECT id FROM revlog WHERE cid = ? ORDER BY id DESC LIMIT 1", new Object [] {mCard.getId()});
            col.getDb().execute("DELETE FROM revlog WHERE id = " + last);
            // restore any siblings
            col.getDb().execute("update cards set queue=type,mod=?,usn=? where queue=" + Consts.QUEUE_TYPE_SIBLING_BURIED + " and nid=?",
                        new Object[]{Utils.intTime(), col.usn(), mCard.getNid()});
            // and finally, update daily count
            @Consts.CARD_QUEUE int n = mCard.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN ? Consts.QUEUE_TYPE_LRN : mCard.getQueue();
            String type = (new String[]{"new", "lrn", "rev"})[n];
            col.getSched()._updateStats(mCard, type, -1);
            col.getSched().setReps(col.getSched().getReps() - 1);
            return mCard.getId();
        }
    }

    public static class UndoableBuryCard extends Undoable {
        private final List<Card> mCards;
        private final long mCid;
        public UndoableBuryCard(List<Card> cards, long cid) {
            super(BURY_CARD);
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

    public static class UndoableBuryNote extends Undoable {
        private final long mCid;
        private final List<Card> mCards;
        public UndoableBuryNote(List<Card> cards, long cid) {
            super(BURY_NOTE);
            mCid = cid;
            mCards = cards;
        }

        public long undo(Collection col) {
            Timber.i("UNDO: Burying notes");
            for (Card cc : mCards) {
                cc.flush(false);
            }
            return mCid;
        }
    }

    public static class UndoableSuspendCard extends Undoable {
        private Card mSuspendedCard;
        public UndoableSuspendCard(Card card) {
            super(SUSPEND_CARD);
            mSuspendedCard = card.clone();
        }

        public long undo(Collection col) {
            Timber.i("UNDO: Suspend Card %d", mSuspendedCard.getId());
            mSuspendedCard.flush(false);
            return mSuspendedCard.getId();
        }
    }

    public static class UndoableSuspendCardMulti extends Undoable {
        private final Card[] mCards;
        private final boolean[] mOriginalSuspended;
        public UndoableSuspendCardMulti(Card[] cards, boolean[] originalSuspended) {
            super(SUSPEND_CARD_MULTI);
            mCards = cards;
            mOriginalSuspended = originalSuspended;
        }

        public long undo(Collection col) {
            Timber.i("Undo: Suspend multiple cards");
            List<Long> toSuspendIds = new ArrayList<>();
            List<Long> toUnsuspendIds = new ArrayList<>();
            for (int i = 0; i < mCards.length; i++) {
                Card card = mCards[i];
                if (mOriginalSuspended[i]) {
                    toSuspendIds.add(card.getId());
                } else {
                    toUnsuspendIds.add(card.getId());
                }
            }

            // unboxing
            long[] toSuspendIdsArray = new long[toSuspendIds.size()];
            long[] toUnsuspendIdsArray = new long[toUnsuspendIds.size()];
            for (int i = 0; i < toSuspendIds.size(); i++) {
                toSuspendIdsArray[i] = toSuspendIds.get(i);
            }
            for (int i = 0; i < toUnsuspendIds.size(); i++) {
                toUnsuspendIdsArray[i] = toUnsuspendIds.get(i);
            }

            col.getSched().suspendCards(toSuspendIdsArray);
            col.getSched().unsuspendCards(toUnsuspendIdsArray);

            return -1;  // don't fetch new card

        }
    }

    public static class UndoableSuspendNote extends Undoable {
        private final List<Card> mCards;
        private final long mCid;
        public UndoableSuspendNote(List<Card> cards, long cid) {
            super(SUSPEND_NOTE);
            mCards = cards;
            mCid = cid;
        }

        public long undo(Collection col) {
            Timber.i("Undo: Suspend note");
            for (Card ccc : mCards) {
                ccc.flush(false);
            }
            return mCid;
        }
    }

    public static class UndoableDeleteNote extends Undoable {
        private final Note mNote;
        private final List<Card> mCards;
        private final long mCid;
        public UndoableDeleteNote(Note note, List<Card> cards, long cid) {
            super(DELETE_NOTE);
            mNote = note;
            mCards = cards;
            mCid = cid;
        }

        public long undo(Collection col) {
            Timber.i("Undo: Delete note");
            ArrayList<Long> ids = new ArrayList<>();
            mNote.flush(mNote.getMod(), false);
            ids.add(mNote.getId());
            for (Card c : mCards) {
                c.flush(false);
                ids.add(c.getId());
            }
            col.getDb().execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(Utils.arrayList2array(ids)));
            return mCid;
        }
    }

    public static class UndoableDeleteNoteMulti extends Undoable {
        private final List<Card> mAllCards;
        private final Note[] mNotes;
        public UndoableDeleteNoteMulti(Note[] notes, List<Card> allCards) {
            super(DELETE_NOTE_MULTI);
            mNotes = notes;
            mAllCards = allCards;
        }

        public long undo(Collection col) {
            Timber.i("Undo: Delete notes");
            // undo all of these at once instead of one-by-one
            ArrayList<Long> ids = new ArrayList<>();
            for (Note n : mNotes) {
                n.flush(n.getMod(), false);
                ids.add(n.getId());
            }
            for (Card c : mAllCards) {
                c.flush(false);
                ids.add(c.getId());
            }
            col.getDb().execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(Utils.arrayList2array(ids)));
            return -1;  // don't fetch new card

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
            return -1;  // don't fetch new card

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
            return -1;  // don't fetch new card
        }
    }

    public static class UndoableFlag extends Undoable {
        public UndoableFlag(Collection col) {
            super(FLAG);
        }

        public long undo(Collection col) {
            Timber.d("Not implemented.");
            return 0;
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
            return 0;
        }
    }
}
