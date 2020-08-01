package com.ichi2.libanki.sched;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Pair;
import android.widget.Toast;


import com.ichi2.anki.R;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Collection;
import com.ichi2.utils.JSONObject;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;


public abstract class AbstractSched {
    /**
     * Pop the next card from the queue. null if finished.
     */
    protected Collection mCol;
    public abstract Card getCard();
    public abstract void reset();
    /** Ensures that reset is executed before the next card is selected */
    public abstract void deferReset();
    /**
     * @param undoneCard a card undone, send back to the reviewer.*/
    public abstract void deferReset(Card undoneCard);
    public abstract void answerCard(Card card, int ease);
    public abstract int[] counts();
    public abstract int[] counts(Card card);
    /**
     * Return counts over next DAYS. Includes today.
     */
    public abstract int dueForecast();
    public abstract int dueForecast(int days);
    @Consts.CARD_QUEUE
    public abstract int countIdx(Card card);
    public abstract int answerButtons(Card card);
    /**
     * Unbury all buried cards in all decks
     */
    public abstract void unburyCards();
    public abstract void unburyCardsForDeck();
    public abstract void _updateStats(Card card, String type, long cnt);
    public abstract void extendLimits(int newc, int rev);
    /**
     * Returns [deckname, did, rev, lrn, new]
     */
    public abstract List<DeckDueTreeNode> deckDueList();
    /** load the due tree, but halt if deck task is cancelled*/
    public abstract List<DeckDueTreeNode> deckDueTree(CollectionTask collectionTask);
    public abstract List<DeckDueTreeNode> deckDueTree();
    /** New count for a single deck. */
    public abstract int _newForDeck(long did, int lim);
    /** Limit for deck without parent limits. */
    public abstract int _deckNewLimitSingle(Deck g);
    public abstract int totalNewForCurrentDeck();
    public abstract int totalRevForCurrentDeck();
    public abstract Pair<Integer, Integer> _fuzzIvlRange(int ivl);
    /** Rebuild a dynamic deck. */
    public abstract void rebuildDyn();
    public abstract List<Long> rebuildDyn(long did);
    public abstract void emptyDyn(long did);
    public abstract void emptyDyn(long did, String lim);
    public abstract void remFromDyn(long[] cids);
    public abstract DeckConfig _cardConf(Card card);
    public abstract String _deckLimit();
    public abstract void _checkDay();
    public abstract CharSequence finishedMsg(Context context);
    public abstract String _nextDueMsg(Context context);
    /** true if there are any rev cards due. */
    public abstract boolean revDue();
    /** true if there are any new cards due. */
    public abstract boolean newDue();
    /** true if there are cards in learning, with review due the same
     * day, in the selected decks. */
    public abstract boolean hasCardsTodayAfterStudyAheadLimit();
    public abstract boolean haveBuried();
    /**
     * Return the next interval for a card and ease as a string.
     *
     * For a given card and ease, this returns a string that shows when the card will be shown again when the
     * specific ease button (AGAIN, GOOD etc.) is touched. This uses unit symbols like “s” rather than names
     * (“second”), like Anki desktop.
     *
     * @param context The app context, used for localization
     * @param card The card being reviewed
     * @param ease The button number (easy, good etc.)
     * @return A string like “1 min” or “1.7 mo”
     */
    public abstract String nextIvlStr(Context context, Card card, int ease);
    /**
     * Return the next interval for CARD, in seconds.
     */
    public abstract long nextIvl(Card card, int ease);
    /**
     * Suspend cards.
     */
    public abstract void suspendCards(long[] ids);
    /**
     * Unsuspend cards
     */
    public abstract void unsuspendCards(long[] ids);
    public abstract void buryCards(long[] cids);
    @VisibleForTesting
    public abstract void buryCards(long[] cids, boolean manual);
    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    public abstract void buryNote(long nid);
    /** Put cards at the end of the new queue. */
    public abstract void forgetCards(long[] ids);
    /**
     * Put cards in review queue with a new interval in days (min, max).
     *
     * @param ids The list of card ids to be affected
     * @param imin the minimum interval (inclusive)
     * @param imax The maximum interval (inclusive)
     */
    public abstract void reschedCards(long[] ids, int imin, int imax);
    /**
     * Completely reset cards for export.
     */
    public abstract void resetCards(Long[] ids);
    public abstract void sortCards(long[] cids, int start);
    public abstract void sortCards(long[] cids, int start, int step, boolean shuffle, boolean shift);
    public abstract void randomizeCards(long did);
    public abstract void orderCards(long did);
    public abstract void resortConf(DeckConfig conf);
    /**
     * for post-import
     */
    public abstract void maybeRandomizeDeck();
    public abstract void maybeRandomizeDeck(Long did);
    public abstract boolean haveBuried(long did);
    public enum UnburyType {
        ALL,
        MANUAL,
        SIBLINGS;
    }
    public abstract void unburyCardsForDeck(UnburyType type);
    public abstract void unburyCardsForDeck(long did);
    public abstract String getName();
    public abstract int getToday();
    public abstract void setToday(int today);
    public abstract long getDayCutoff();
    public abstract int getReps();
    public abstract void setReps(int reps);
    public abstract int cardCount();
    public abstract int eta(int[] counts);
    /**
     * Return an estimate, in minutes, for how long it will take to complete all the reps in {@code counts}.
     *
     * The estimator builds rates for each queue type by looking at 10 days of history from the revlog table. For
     * efficiency, and to maintain the same rates for a review session, the rates are cached and reused until a
     * reload is forced.
     *
     * Notes:
     * - Because the revlog table does not record deck IDs, the rates cannot be reduced to a single deck and thus cover
     * the whole collection which may be inaccurate for some decks.
     * - There is no efficient way to determine how many lrn cards are generated by each new card. This estimator
     * assumes 1 card is generated as a compromise.
     * - If there is no revlog data to work with, reasonable defaults are chosen as a compromise to predicting 0 minutes.
     *
     * @param counts An array of [new, lrn, rev] counts from the scheduler's counts() method.
     * @param reload Force rebuild of estimator rates using the revlog.
     */
    public abstract int eta(int[] counts, boolean reload);
    public abstract void decrementCounts(Card card);
    public abstract boolean leechActionSuspend(Card card);
    public abstract void setContext(WeakReference<Activity> contextReference);
    public abstract int[] recalculateCounts();
    public abstract void setReportLimit(int reportLimit);


    /**
     * Holds the data for a single node (row) in the deck due tree (the user-visible list
     * of decks and their counts). A node also contains a list of nodes that refer to the
     * next level of sub-decks for that particular deck (which can be an empty list).
     *
     * The names field is an array of names that build a deck name from a hierarchy (i.e., a nested
     * deck will have an entry for every level of nesting). While the python version interchanges
     * between a string and a list of strings throughout processing, we always use an array for
     * this field and use getNamePart(0) for those cases.
     */
    public class DeckDueTreeNode implements Comparable {
        private final String mName;
        private final String[] mNameComponents;
        private long mDid;
        private int mRevCount;
        private int mLrnCount;
        private int mNewCount;
        @Nullable
        private List<DeckDueTreeNode> mChildren = null;

        public DeckDueTreeNode(String mName, long mDid, int mRevCount, int mLrnCount, int mNewCount) {
            this.mName = mName;
            this.mDid = mDid;
            this.mRevCount = mRevCount;
            this.mLrnCount = mLrnCount;
            this.mNewCount = mNewCount;
            this.mNameComponents = Decks.path(mName);
        }

        /**
         * Sort on the head of the node.
         */
        @Override
        public int compareTo(Object other) {
            DeckDueTreeNode rhs = (DeckDueTreeNode) other;
            int minDepth = Math.min(getDepth(), rhs.getDepth()) + 1;
            // Consider each subdeck name in the ordering
            for (int i = 0; i < minDepth; i++) {
                int cmp = mNameComponents[i].compareTo(rhs.mNameComponents[i]);
                if (cmp == 0) {
                    continue;
                }
                return cmp;
            }
            // If we made it this far then the arrays are of different length. The longer one should
            // always come after since it contains all of the sections of the shorter one inside it
            // (i.e., the short one is an ancestor of the longer one).
            if (rhs.getDepth() > getDepth()) {
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            toString(buf);
            return buf.toString();
        }

        public void toString(StringBuffer buf) {
            for (int i = 0; i < getDepth(); i++ ) {
                buf.append("  ");
            }
            buf.append(String.format(Locale.US, "%s, %d, %d, %d, %d\n",
                    mName, mDid, mRevCount, mLrnCount, mNewCount));
            if (mChildren == null) {
                return;
            }
            for (DeckDueTreeNode children : mChildren) {
                children.toString(buf);
            }
        }

        /**
         * @return The full deck name, e.g. "A::B::C"
         * */
        public String getFullDeckName() {
            return mName;
        }

        /**
         * For deck "A::B::C", `getDeckNameComponent(0)` returns "A",
         * `getDeckNameComponent(1)` returns "B", etc...
         */
        public String getDeckNameComponent(int part) {
            return mNameComponents[part];
        }

        /**
         * The part of the name displayed in deck picker, i.e. the
         * part that does not belong to its parents. E.g.  for deck
         * "A::B::C", returns "C".
         */
        public String getLastDeckNameComponent() {
            return getDeckNameComponent(getDepth());
        }

        public long getDid() {
            return mDid;
        }

        /**
         * @return The depth of a deck. Top level decks have depth 0,
         * their children have depth 1, etc... So "A::B::C" would have
         * depth 2.
         */
        public int getDepth() {
            return mNameComponents.length - 1;
        }

        public int getRevCount() {
            return mRevCount;
        }

        private void limitRevCount(int limit) {
            mRevCount = Math.max(0, Math.min(mRevCount, limit));
        }

        public int getNewCount() {
            return mNewCount;
        }

        private void limitNewCount(int limit) {
            mNewCount = Math.max(0, Math.min(mNewCount, limit));
        }

        public int getLrnCount() {
            return mLrnCount;
        }

        /**
         * @return The children of this deck. Note that they are set
         * in the data structure returned by DeckDueTree but are
         * always empty when the data structure is returned by
         * deckDueList.*/
        public List<DeckDueTreeNode> getChildren() {
            return mChildren;
        }

        /**
         * @return whether this node as any children. */
        public boolean hasChildren() {
            return mChildren != null && !mChildren.isEmpty();
        }

        public void setChildren(@NonNull List<DeckDueTreeNode> children, boolean addRev) {
            mChildren = children;
            // tally up children counts
            for (DeckDueTreeNode ch : children) {
                mLrnCount += ch.getLrnCount();
                mNewCount += ch.getNewCount();
                if (addRev) {
                    mRevCount += ch.getRevCount();
                }
            }
            // limit the counts to the deck's limits
            JSONObject conf = mCol.getDecks().confForDid(mDid);
            if (conf.getInt("dyn") == 0) {
                JSONObject deck = mCol.getDecks().get(mDid);
                limitNewCount(conf.getJSONObject("new").getInt("perDay") - deck.getJSONArray("newToday").getInt(1));
                if (addRev) {
                    limitRevCount(conf.getJSONObject("rev").getInt("perDay") - deck.getJSONArray("revToday").getInt(1));
                }
            }
        }

        @Override
        public int hashCode() {
            int childrenHash = mChildren.hashCode();
            return getFullDeckName().hashCode() + mRevCount + mLrnCount + mNewCount + (int) (childrenHash ^ (childrenHash >>> 32));
        }


        /**
         * Whether both elements have the same structure and numbers.
         * @param object
         * @return
         */
        @Override
        public boolean equals(Object object) {
            if (!(object instanceof DeckDueTreeNode)) {
                return false;
            }
            DeckDueTreeNode tree = (DeckDueTreeNode) object;
            return Decks.equalName(getFullDeckName(), tree.getFullDeckName()) &&
                    mRevCount == tree.mRevCount &&
                    mLrnCount == tree.mLrnCount &&
                    mNewCount == tree.mNewCount &&
                    (mChildren == tree.mChildren || // Would be the case if both are null, or the same pointer
                    mChildren.equals(tree.mChildren))
                    ;
        }
    }


    public interface LimitMethod {
        int operation(Deck g);
    }

    public interface CountMethod {
        int operation(long did, int lim);
    }

    protected static void leech(Card card, Activity activity) {
        if (activity != null) {
            Resources res = activity.getResources();
            final String leechMessage;
            if (card.getQueue() < 0) {
                leechMessage = res.getString(R.string.leech_suspend_notification);
            } else {
                leechMessage = res.getString(R.string.leech_notification);
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, leechMessage, Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            Timber.w("LeechHook :: could not show leech toast as activity was null");
        }
    }

    /**
     * Notifies the scheduler that the provided card is being reviewed. Ensures that a different card is prefetched.
     *
     * @param card the current card in the reviewer
     */
    public abstract void setCurrentCard(@NonNull Card card);
    public abstract void discardCurrentCard();
}
