package com.ichi2.libanki.sched;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.util.Pair;
import android.widget.Toast;


import com.ichi2.anki.R;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.utils.Time;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;


public abstract class AbstractSched {
    /** Current day. Encoded as number of day since collection creation time.*/
    protected Integer mToday;

    /** Whether the queues has been computed since last reset/change of selected deck */
    protected boolean mHaveQueues;

    /** Number of cards taken obtained from the scheduler today. */
    protected int mReps;

    /** When an action is undone, reset counts need to take the card into account */
    protected Card mUndidCard = null;

    /** Number of new card we expect to see today in selected deck. Number may not be exact due to buried siblings.*/
    protected int mNewCount;

    /** Depending on the scheduler:
     * * number of cards currently in learning or
     * * number of repetition of cards in learning assuming all cards are good*/
    protected int mLrnCount;

    /** Number of rev card we expect to see today in selected deck. Number may not be exact due to buried siblings.*/
    protected int mRevCount;

    // Queues of decks
    /** List of ids of decks which may still contains new cards to see today.
     *
     * Some decks may be missing from the list. It may have been believed that some decks were empty because of same day burying of siblings.
     * So if the list is empty, it should be double checked before asserting that there is no more card.
     * */
    protected LinkedList<Long> mNewDids = new LinkedList<>();
    /** List of ids of decks which may still contains cards in learning from a past day.
     * */
    protected LinkedList<Long> mLrnDids = new LinkedList<>();


    // Queues
    /** The next new cards to see. */
    protected final LinkedList<Long> mNewQueue = new LinkedList<>();
    /** The next cards in same day learning to see. */
    protected final LinkedList<LrnCard> mLrnQueue = new LinkedList<>();
    /** The next cards in learning for more than one day to see. */
    protected final LinkedList<Long> mLrnDayQueue = new LinkedList<>();
    /** The next review cards to see. */
    protected final LinkedList<Long> mRevQueue = new LinkedList<>();
    /** The time at which cards in learning will be considered to be due for the next day */
    protected long mLrnCutoff;

    /** One out of each mNewCardModulus cards seens is a new card. This tries to ensure that new cards are seen regularly.
     * This approximation mostly works if the number of review is greater (at least twice) the number of new cards.*/
    private int mNewCardModulus;

    /** Maximal number of cards to show in card counts. Need to be changed to 1000 when doing sanity check with ankiweb.*/
    protected int mReportLimit;
    /**
     * Pop the next card from the queue. null if finished.
     */
    protected Collection mCol;


    // Not in libanki
    protected WeakReference<Activity> mContextReference;


    protected static class LrnCard implements Comparable<LrnCard> {
        private final long mCid;
        private final long mDue;
        public LrnCard(long due, long cid) {
            mCid = cid;
            mDue = due;
        }
        public long getDue () {
            return mDue;
        }
        public long getId() {
            return mCid;
        }

        @Override
        public int compareTo(LrnCard o) {
            return Long.compare(mDue, o.mDue);
        }
    }

    /**
     * Pop the next card from the queue. null if finished.
     */
    public Card getCard() {
        _checkDay();
        if (!mHaveQueues) {
            reset();
        }
        Card card = _getCard();
        if (card != null) {
            mCol.log(card);
            mReps += 1;
            // In upstream, counts are decremented when the card is
            // gotten; i.e. in _getLrnCard, _getRevCard and
            // _getNewCard. This can not be done anymore since we use
            // those methods to pre-fetch the next card. Instead we
            // decrement the counts here, when the card is returned to
            // the reviewer.
            decrementCounts(card);
            setCurrentCard(card);
            card.startTimer();
        } else {
            discardCurrentCard();
        }
        return card;
    }

    protected abstract Card _getCard();

    public void reset() {
        _updateCutoff();
        _resetLrn();
        _resetRev();
        _resetNew();
        mHaveQueues = true;
        decrementCounts(mUndidCard);
        if (mUndidCard == null) {
            discardCurrentCard();
        } else {
            setCurrentCard(mUndidCard);
        }
        mUndidCard = null;
    }


    // Sched V1 also reset the list of rev deck.
    protected void _resetRev() {
        _resetRevCount();
        mRevQueue.clear();
    }

    /** Number of review cards in current selected deck
     * In sched V2 only current limit is applied. In sched V1, limit is applied subdeck by subdeck. */
    protected abstract void _resetRevCount();


    // In sched V2 only, the lrn cutoff is updated
    protected abstract void _resetLrn();
    protected abstract void _updateCutoff();


    protected void _resetNewCount() {
        mNewCount = _walkingCount((Deck g) -> _deckNewLimitSingle(g),
                (long did, int lim) -> _cntFnNew(did, lim));
    }

    /** Same as _resetNew, but assume discardCard is currently in the reviewer and so don't conunt it.*/
    protected void _resetNew(@Nullable Card discardCard) {
        _resetNew();
        if (discardCard != null && discardCard.getQueue() == Consts.QUEUE_TYPE_NEW) {
            mNewCount--;
        }
    }

    protected abstract int _cntFnNew(long did, int lim);

    /** Ensures that reset is executed before the next card is selected
     *  @param undidCard a card undone, send back to the reviewer.*/
    public void deferReset(Card undidCard){
        mHaveQueues = false;
        mUndidCard = undidCard;
    }


    public void deferReset(){
        deferReset(null);
    }

    protected int _walkingCount(LimitMethod limFn, CountMethod cntFn) {
        int tot = 0;
        HashMap<Long, Integer> pcounts = new HashMap<>();
        // for each of the active decks
        for (long did : mCol.getDecks().active()) {
            // get the individual deck's limit
            int lim = limFn.operation(mCol.getDecks().get(did));
            if (lim == 0) {
                continue;
            }
            // check the parents
            List<Deck> parents = mCol.getDecks().parents(did);
            for (Deck p : parents) {
                // add if missing
                long id = p.getLong("id");
                if (!pcounts.containsKey(id)) {
                    pcounts.put(id, limFn.operation(p));
                }
                // take minimum of child and parent
                lim = Math.min(pcounts.get(id), lim);
            }
            // see how many cards we actually have
            int cnt = cntFn.operation(did, lim);
            // if non-zero, decrement from parents counts
            for (Deck p : parents) {
                long id = p.getLong("id");
                pcounts.put(id, pcounts.get(id) - cnt);
            }
            // we may also be a parent
            pcounts.put(did, lim - cnt);
            // and add to running total
            tot += cnt;
        }
        return tot;
    }


    /**
     * @param undoneCard a card undone, send back to the reviewer.*/
    public abstract void deferReset(Card undoneCard);
    public abstract void answerCard(Card card, @Consts.BUTTON_TYPE int ease);
    public abstract int[] counts();
    public abstract int[] counts(Card card);
    /**
     * Return counts over next DAYS. Includes today.
     */
    public int dueForecast() {
        return dueForecast(7);
    }

    public int dueForecast(int days) {
        // TODO:...
        return 0;
    }

    @Consts.CARD_QUEUE
    public abstract int countIdx(Card card);
    public abstract int answerButtons(Card card);
    /**
     * Unbury all buried cards in all decks
     */
    public abstract void unburyCards();
    public abstract void unburyCardsForDeck();


    public void _updateStats(Card card, String type, long cnt) {
        String key = type + "Today";
        long did = card.getDid();
        List<Deck> list = mCol.getDecks().parents(did);
        list.add(mCol.getDecks().get(did));
        for (Deck g : list) {
            JSONArray a = g.getJSONArray(key);
            // add
            a.put(1, a.getLong(1) + cnt);
            mCol.getDecks().save(g);
        }
    }

    public abstract void extendLimits(int newc, int rev);
    /**
     * Returns [deckname, did, rev, lrn, new]
     */
    public List<DeckDueTreeNode> deckDueList() {
        return deckDueList(null);
    }
    /**
     * Returns [deckname, did, rev, lrn, new]
     *
     * Return nulls when deck task is cancelled.
     */
    protected abstract List<DeckDueTreeNode> deckDueList(CollectionTask collectionTask);
    /** load the due tree, but halt if deck task is cancelled*/



    public List<DeckDueTreeNode> deckDueTree() {
        return deckDueTree(null);
    }

    public List<DeckDueTreeNode> deckDueTree(CollectionTask collectionTask) {
        List<DeckDueTreeNode> deckDueTree = deckDueList(collectionTask);
        if (deckDueTree == null) {
            return null;
        }
        return _groupChildren(deckDueTree, true);
    }




    private List<DeckDueTreeNode> _groupChildren(List<DeckDueTreeNode> grps, boolean checkDone) {
        // sort based on name's components
        Collections.sort(grps);
        // then run main function
        return _groupChildrenMain(grps, checkDone);
    }


    protected List<DeckDueTreeNode> _groupChildrenMain(List<DeckDueTreeNode> grps, boolean checkDone) {
        return _groupChildrenMain(grps, 0, checkDone);
    }

    /**
     @return the tree structure of all decks from @grps, starting
     at specified depth.

     @param grps a list of decks of dept at least depth, having all
     the same first depth name elements, sorted in deck order.
     @param depth The depth of the tree we are creating
     @param checkDone whether the set of deck was checked. If
     false, we can't assume all decks have parents and that there
     is no duplicate. Instead, we'll ignore problems.
     */
    protected List<DeckDueTreeNode> _groupChildrenMain(List<DeckDueTreeNode> grps, int depth, boolean checkDone) {
        List<DeckDueTreeNode> tree = new ArrayList<>();
        // group and recurse
        ListIterator<DeckDueTreeNode> it = grps.listIterator();
        while (it.hasNext()) {
            DeckDueTreeNode node = it.next();
            String head = node.getDeckNameComponent(depth);
            List<DeckDueTreeNode> children  = new ArrayList<>();
            /* Compose the "children" node list. The children is a
             * list of all the nodes that proceed the current one that
             * contain the same at depth `depth`, except for the
             * current one itself.  I.e., they are subdecks that stem
             * from this node.  This is our version of python's
             * itertools.groupby. */
            if (!checkDone && node.getDepth() != depth) {
                JSONObject deck = mCol.getDecks().get(node.getDid());
                Timber.d("Deck %s (%d)'s parent is missing. Ignoring for quick display.", deck.getString("name"), node.getDid());
                continue;
            }
            while (it.hasNext()) {
                DeckDueTreeNode next = it.next();
                if (head.equals(next.getDeckNameComponent(depth))) {
                    // Same head - add to tail of current head.
                    if (!checkDone && next.getDepth() == depth) {
                        JSONObject deck = mCol.getDecks().get(next.getDid());
                        Timber.d("Deck %s (%d)'s is a duplicate name. Ignoring for quick display.", deck.getString("name"), next.getDid());
                        continue;
                    }
                    children.add(next);
                } else {
                    // We've iterated past this head, so step back in order to use this node as the
                    // head in the next iteration of the outer loop.
                    it.previous();
                    break;
                }
            }
            // the children set contains direct children but not the children of children...
            node.setChildren(_groupChildrenMain(children, depth + 1, checkDone), "std".equals(getName()));
            tree.add(node);
        }
        return tree;
    }

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
    public abstract String nextIvlStr(Context context, Card card, @Consts.BUTTON_TYPE int ease);
    /**
     * Return the next interval for CARD, in seconds.
     */
    public abstract long nextIvl(Card card, @Consts.BUTTON_TYPE int ease);

    protected abstract String queueIsBuriedSnippet();
    protected abstract String _restoreQueueSnippet();
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

    /**
     * This is used when card is currently in the reviewer, to adapt the counts by removing this card from it.
     * The difference between both scheduler appears for cards in learning. In sched v1, the number of steps is counted.
     * In v2, the number of cards is counted*/
    public abstract void decrementCounts(Card card);
    public abstract boolean leechActionSuspend(Card card);


    public void setContext(WeakReference<Activity> contextReference) {
        mContextReference = contextReference;
    }

    /** not in libAnki. Added due to #5666: inconsistent selected deck card counts on sync */
    public int[] recalculateCounts() {
        _updateLrnCutoff(true);
        _resetLrnCount();
        _resetNewCount();
        _resetRevCount();
        return new int[] { mNewCount, mLrnCount, mRevCount };
    }
    
    protected abstract void _resetLrnCount();

    public void setReportLimit(int reportLimit) {
        this.mReportLimit = reportLimit;
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


    private void _resetNew() {
        _resetNewCount();
        mNewDids = new LinkedList<>(mCol.getDecks().active());
        mNewQueue.clear();
        _updateNewCardRatio();
    }


    private void _updateNewCardRatio() {
        if (mCol.getConf().getInt("newSpread") == Consts.NEW_CARDS_DISTRIBUTE) {
            if (mNewCount != 0) {
                mNewCardModulus = (mNewCount + mRevCount) / mNewCount;
                // if there are cards to review, ensure modulo >= 2
                if (mRevCount != 0) {
                    mNewCardModulus = Math.max(2, mNewCardModulus);
                }
                return;
            }
        }
        mNewCardModulus = 0;
    }


    /**
     * @return True if it's time to display a new card when distributing.
     */
    protected boolean _timeForNewCard() {
        if (mNewCount == 0) {
            return false;
        }
        @Consts.NEW_CARD_ORDER int spread;
        spread = mCol.getConf().getInt("newSpread");
        if (spread == Consts.NEW_CARDS_LAST) {
            return false;
        } else if (spread == Consts.NEW_CARDS_FIRST) {
            return true;
        } else if (mNewCardModulus != 0) {
            return (mReps != 0 && (mReps % mNewCardModulus == 0));
        } else {
            return false;
        }
    }
    protected abstract boolean _updateLrnCutoff(boolean force);

    /** A conf object for this card. The delay for cards in filtered deck is the only difference between V1 and V2 */
    protected JSONObject _revConf(Card card) {
        DeckConfig conf = _cardConf(card);
        // normal deck
        if (card.getODid() == 0) {
            return conf.getJSONObject("rev");
        }
        // dynamic deck
        return mCol.getDecks().confForDid(card.getODid()).getJSONObject("rev");
    }

    protected abstract JSONObject _newConf(Card card);

    /**
     * Sibling spacing
     * ********************
     */

    protected void _burySiblings(Card card) {
        ArrayList<Long> toBury = new ArrayList<>();
        JSONObject nconf = _newConf(card);
        boolean buryNew = nconf.optBoolean("bury", true);
        JSONObject rconf = _revConf(card);
        boolean buryRev = rconf.optBoolean("bury", true);
        // loop through and remove from queues
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().query(
                    "select id, queue from cards where nid=? and id!=? "+
                            "and (queue=" + Consts.QUEUE_TYPE_NEW + " or (queue=" + Consts.QUEUE_TYPE_REV + " and due<=?))",
                    new Object[] {card.getNid(), card.getId(), mToday});
            while (cur.moveToNext()) {
                long cid = cur.getLong(0);
                int queue = cur.getInt(1);
                List<Long> queue_object;
                if (queue == Consts.QUEUE_TYPE_REV) {
                    queue_object = mRevQueue;
                    if (buryRev) {
                        toBury.add(cid);
                    }
                } else {
                    queue_object = mNewQueue;
                    if (buryNew) {
                        toBury.add(cid);
                    }
                }
                // even if burying disabled, we still discard to give
                // same-day spacing
                queue_object.remove(cid);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // then bury
        if (!toBury.isEmpty()) {
            buryCards(Utils.collection2Array(toBury),false);
        }
    }
}
