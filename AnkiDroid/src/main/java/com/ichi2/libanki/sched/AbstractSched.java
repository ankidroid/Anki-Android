package com.ichi2.libanki.sched;

import android.app.Activity;
import android.content.Context;


import com.ichi2.libanki.Card;
import com.ichi2.utils.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public abstract class AbstractSched {
    public abstract Card getCard();
    public abstract void reset();
    public abstract void answerCard(Card card, int ease);
    public abstract int[] counts();
    public abstract int[] counts(Card card);
    public abstract int dueForecast();
    public abstract int dueForecast(int days);
    public abstract int countIdx(Card card);
    public abstract int answerButtons(Card card);
    public abstract void unburyCards();
    public abstract void unburyCardsForDeck();
    public abstract void _updateStats(Card card, String type, long cnt);
    public abstract void extendLimits(int newc, int rev);
    public abstract List<DeckDueTreeNode> deckDueList();
    public abstract List<DeckDueTreeNode> deckDueTree();
    public abstract int _newForDeck(long did, int lim);
    public abstract int _deckNewLimitSingle(JSONObject g);
    public abstract int totalNewForCurrentDeck();
    public abstract int totalRevForCurrentDeck();
    public abstract int[] _fuzzedIvlRange(int ivl);
    public abstract void rebuildDyn();
    public abstract List<Long> rebuildDyn(long did);
    public abstract void emptyDyn(long did);
    public abstract void emptyDyn(long did, String lim);
    public abstract void remFromDyn(long[] cids);
    public abstract JSONObject _cardConf(Card card);
    public abstract String _deckLimit();
    public abstract void _checkDay();
    public abstract CharSequence finishedMsg(Context context);
    public abstract String _nextDueMsg(Context context);
    public abstract boolean revDue();
    public abstract boolean newDue();
    public abstract boolean haveBuried();
    public abstract String nextIvlStr(Context context, Card card, int ease);
    public abstract long nextIvl(Card card, int ease);
    public abstract void suspendCards(long[] ids);
    public abstract void unsuspendCards(long[] ids);
    public abstract void buryCards(long[] cids);
    public abstract void buryNote(long nid);
    public abstract void forgetCards(long[] ids);
    public abstract void reschedCards(long[] ids, int imin, int imax);
    public abstract void resetCards(Long[] ids);
    public abstract void sortCards(long[] cids, int start);
    public abstract void sortCards(long[] cids, int start, int step, boolean shuffle, boolean shift);
    public abstract void randomizeCards(long did);
    public abstract void orderCards(long did);
    public abstract void resortConf(JSONObject conf);
    public abstract void maybeRandomizeDeck();
    public abstract void maybeRandomizeDeck(Long did);
    public abstract boolean haveBuried(long did);
    public abstract void unburyCardsForDeck(long did);
    public abstract String getName();
    public abstract int getToday();
    public abstract void setToday(int today);
    public abstract long getDayCutoff();
    public abstract int getReps();
    public abstract void setReps(int reps);
    public abstract int cardCount();
    public abstract int eta(int[] counts);
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
     * this field and use names[0] for those cases.
     */
    public class DeckDueTreeNode implements Comparable {
        public String[] names;
        public long did;
        public int depth;
        public int revCount;
        public int lrnCount;
        public int newCount;
        public List<DeckDueTreeNode> children = new ArrayList<>();

        public DeckDueTreeNode(String[] names, long did, int revCount, int lrnCount, int newCount) {
            this.names = names;
            this.did = did;
            this.revCount = revCount;
            this.lrnCount = lrnCount;
            this.newCount = newCount;
        }

        public DeckDueTreeNode(String name, long did, int revCount, int lrnCount, int newCount) {
            this(new String[]{name}, did, revCount, lrnCount, newCount);
        }

        public DeckDueTreeNode(String name, long did, int revCount, int lrnCount, int newCount,
                               List<DeckDueTreeNode> children) {
            this(new String[]{name}, did, revCount, lrnCount, newCount);
            this.children = children;
        }

        /**
         * Sort on the head of the node.
         */
        @Override
        public int compareTo(Object other) {
            DeckDueTreeNode rhs = (DeckDueTreeNode) other;
            // Consider each subdeck name in the ordering
            for (int i = 0; i < names.length && i < rhs.names.length; i++) {
                int cmp = names[i].compareTo(rhs.names[i]);
                if (cmp == 0) {
                    continue;
                }
                return cmp;
            }
            // If we made it this far then the arrays are of different length. The longer one should
            // always come after since it contains all of the sections of the shorter one inside it
            // (i.e., the short one is an ancestor of the longer one).
            if (rhs.names.length > names.length) {
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s, %d, %d, %d, %d, %d, %s",
                    Arrays.toString(names), did, depth, revCount, lrnCount, newCount, children);
        }
    }

    public interface LimitMethod {
        int operation(JSONObject g);
    }

    public interface CountMethod {
        int operation(long did, int lim);
    }
}
