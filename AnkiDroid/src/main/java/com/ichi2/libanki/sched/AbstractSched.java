package com.ichi2.libanki.sched;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
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
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
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
    protected int mQueueLimit;


    /** The list of parent decks of the current card.
     * Cached for performance .

     Null iff mNextCard is null.*/
    @Nullable
    protected List<Long> mCurrentCardParentsDid;


    /**
     * The card currently being reviewed.
     *
     * Must not be returned during prefetching (as it is currently shown)
     */
    protected Card mCurrentCard;

    /**
     * Pop the next card from the queue. null if finished.
     */
    protected Collection mCol;

    // Default limit for dynamic deck
    protected int mDynReportLimit;


    // Seconds at which we change day
    public long mDayCutoff;


    protected boolean mHaveCustomStudy = true;

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


    protected Card _getNewCard() {
        if (_fillNew()) {
            // mNewCount -= 1; see decrementCounts()
            return mCol.getCard(mNewQueue.remove());
        }
        return null;
    }


    private boolean _fillNew() {
        return _fillNew(false);
    }

    private boolean _fillNew(boolean allowSibling) {
        if (!mNewQueue.isEmpty()) {
            return true;
        }
        if (mNewCount == 0) {
            return false;
        }
        SupportSQLiteDatabase db = mCol.getDb().getDatabase();
        while (!mNewDids.isEmpty()) {
            long did = mNewDids.getFirst();
            int lim = Math.min(mQueueLimit, _deckNewLimit(did));
            Cursor cur = null;
            if (lim != 0) {
                mNewQueue.clear();
                try {
                    /* Difference with upstream: we take current card into account.
                     *
                     * When current card is answered, the card is not due anymore, so does not belong to the queue.
                     * Furthermore, _burySiblings ensure that the siblings of the current cards are removed from the
                     * queue to ensure same day spacing. We simulate this action by ensuring that those siblings are not
                     * filled, except if we know there are cards and we didn't find any non-sibling card. This way, the
                     * queue is not empty if it should not be empty (important for the conditional belows), but the
                     * front of the queue contains distinct card.
                     */
                    // fill the queue with the current did
                    String idName = (allowSibling) ? "id": "nid";
                    long id = (allowSibling) ? currentCardId(): currentCardNid();
                    cur = db.query("SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " AND " + idName + "!= ? ORDER BY due, ord LIMIT ?",
                            new Object[]{did, id, lim});
                    while (cur.moveToNext()) {
                        mNewQueue.add(cur.getLong(0));
                    }
                } finally {
                    if (cur != null && !cur.isClosed()) {
                        cur.close();
                    }
                }
                if (!mNewQueue.isEmpty()) {
                    // Note: libanki reverses mNewQueue and returns the last element in _getNewCard().
                    // AnkiDroid differs by leaving the queue intact and returning the *first* element
                    // in _getNewCard().
                    return true;
                }
            }
            // nothing left in the deck; move to next
            mNewDids.remove();
        }
        // if we didn't get a card, since the count is non-zero, we
        // need to check again for any cards that were removed
        // from the queue but not buried
        _resetNew(mCurrentCard);
        return _fillNew(true);
    }

    protected void _resetNewCount() {
        mNewCount = _walkingCount((Deck g) -> _deckNewLimitSingle(g),
                (long did, int lim) -> _cntFnNew(did, lim));
    }


    protected int _deckNewLimit(long did) {
        return _deckNewLimit(did, null);
    }


    protected int _deckNewLimit(long did, LimitMethod fn) {
        if (fn == null) {
            fn = (g -> _deckNewLimitSingle(g));
        }
        List<Deck> decks = mCol.getDecks().parents(did);
        decks.add(mCol.getDecks().get(did));
        int lim = -1;
        // for the deck and each of its parents
        int rem = 0;
        for (Deck g : decks) {
            rem = fn.operation(g);
            if (lim == -1) {
                lim = rem;
            } else {
                lim = Math.min(rem, lim);
            }
        }
        return lim;
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


    protected Card _getLrnCard() {
        return _getLrnCard(false);
    }


    protected Card _getLrnDayCard() {
        if (_fillLrnDay()) {
            // mLrnCount -= 1; see decrementCounts()
            return mCol.getCard(mLrnDayQueue.remove());
        }
        return null;
    }


    // daily learning
    protected boolean _fillLrnDay() {
        if (mLrnCount == 0) {
            return false;
        }
        if (!mLrnDayQueue.isEmpty()) {
            return true;
        }
        SupportSQLiteDatabase db = mCol.getDb().getDatabase();
        while (!mLrnDids.isEmpty()) {
            long did = mLrnDids.getFirst();
            // fill the queue with the current did
            mLrnDayQueue.clear();
            Cursor cur = null;
            try {
                /* Difference with upstream:
                 * Current card can't come in the queue.
                 *
                 * In standard usage, a card is not requested before
                 * the previous card is marked as reviewed. However,
                 * if we decide to query a second card sooner, we
                 * don't want to get the same card a second time. This
                 * simulate _getLrnDayCard which did remove the card
                 * from the queue.
                 */
                cur = db.query(
                        "SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ? and id != ? LIMIT ?",
                        new Object[] {did, mToday, currentCardId(), mQueueLimit});
                while (cur.moveToNext()) {
                    mLrnDayQueue.add(cur.getLong(0));
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            if (!mLrnDayQueue.isEmpty()) {
                // order
                Random r = new Random();
                r.setSeed(mToday);
                Collections.shuffle(mLrnDayQueue, r);
                // is the current did empty?
                if (mLrnDayQueue.size() < mQueueLimit) {
                    mLrnDids.remove();
                }
                return true;
            }
            // nothing left in the deck; move to next
            mLrnDids.remove();
        }
        return false;
    }

    protected Card _getRevCard() {
        if (_fillRev()) {
            // mRevCount -= 1; see decrementCounts()
            return mCol.getCard(mRevQueue.remove());
        } else {
            return null;
        }
    }


    protected boolean _fillRev() {
        return _fillRev(false);
    }


    protected abstract boolean _fillRev(boolean allowSibling);
    public abstract void answerCard(Card card, int ease);


    /**
     * Number of new cards, review cards and cards in learning, ignoring `card`
     */
    public int[] counts() {
        if (!mHaveQueues) {
            reset();
        }
        return new int[] {mNewCount, mLrnCount, mRevCount};
    }


    /**
     * Number of new cards, review cards and cards in learning, ignoring `card`
     *
     * If card is in learning, counts change differently in V1 and V2. It counts steps or number of cards. */
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

    /** In which element of the array counts() this card should be sorted. In V2, there are two kinds of queue going to lrn count */
    @Consts.CARD_QUEUE
    public abstract int countIdx(Card card);
    /** Number of buttons to show the user for this card. The number depends of the queue and the scheduler version*/
    public abstract int answerButtons(Card card);
    /**
     * Unbury all buried cards in all decks
     *
     * In V1/V2 the lrn cards differ
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
     * The way rev cards are counted depends on the scheduler version
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
    public int _newForDeck(long did, int lim) {
        if (lim == 0) {
            return 0;
        }
        lim = Math.min(lim, mReportLimit);
        return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT ?)",
                did, lim);
    }

    /* Limit for deck without parent limits. */
    public int _deckNewLimitSingle(Deck g) {
        if (g.getInt("dyn") != 0) {
            return mDynReportLimit;
        }
        long did = g.getLong("id");
        DeckConfig c = mCol.getDecks().confForDid(did);
        int lim = Math.max(0, c.getJSONObject("new").getInt("perDay") - g.getJSONArray("newToday").getInt(1));
        if (currentCardIsInQueueWithDeck(Consts.QUEUE_TYPE_NEW, did)) {
            lim--;
        }
        return lim;
    }

    public int totalNewForCurrentDeck() {
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT ?)",
                mReportLimit);
    }


    public int totalRevForCurrentDeck() {
        return mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + _deckLimit() + "  AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
                mToday, mReportLimit);
    }


    public Pair<Integer, Integer> _fuzzIvlRange(int ivl) {
        int fuzz;
        if (ivl < 2) {
            return new Pair<>(1, 1);
        } else if (ivl == 2) {
            return new Pair<>(2, 3);
        } else if (ivl < 7) {
            fuzz = (int)(ivl * 0.25);
        } else if (ivl < 30) {
            fuzz = Math.max(2, (int)(ivl * 0.15));
        } else {
            fuzz = Math.max(4, (int)(ivl * 0.05));
        }
        // fuzz at least a day
        fuzz = Math.max(fuzz, 1);
        return new Pair<>(ivl - fuzz, ivl + fuzz);
    }
    /** Rebuild a dynamic deck. */
    public void rebuildDyn() {
        rebuildDyn(0);
    }
    public abstract List<Long> rebuildDyn(long did);


    public void emptyDyn(long did) {
        emptyDyn(did, null);
    }


    public abstract void emptyDyn(long did, String lim);


    public void remFromDyn(long[] cids) {
        emptyDyn(0, "id IN " + Utils.ids2str(cids) + " AND odid");
    }


    public DeckConfig _cardConf(Card card) {
        return mCol.getDecks().confForDid(card.getDid());
    }


    public String _deckLimit() {
        return Utils.ids2str(mCol.getDecks().active());
    }


    public abstract void _checkDay();

    public CharSequence finishedMsg(Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(context.getString(R.string.studyoptions_congrats_finished));
        StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
        sb.setSpan(boldSpan, 0, sb.length(), 0);
        sb.append(_nextDueMsg(context));
        // sb.append("\n\n");
        // sb.append(_tomorrowDueMsg(context));
        return sb;
    }


    public String _nextDueMsg(Context context) {
        StringBuilder sb = new StringBuilder();
        if (revDue()) {
            sb.append("\n\n");
            sb.append(context.getString(R.string.studyoptions_congrats_more_rev));
        }
        if (newDue()) {
            sb.append("\n\n");
            sb.append(context.getString(R.string.studyoptions_congrats_more_new));
        }
        if (haveBuried()) {
            String now;
            if (mHaveCustomStudy) {
                now = " " + context.getString(R.string.sched_unbury_action);
            } else {
                now = "";
            }
            sb.append("\n\n");
            sb.append("" + context.getString(R.string.sched_has_buried) + now);
        }
        if (mHaveCustomStudy && mCol.getDecks().current().getInt("dyn") == 0) {
            sb.append("\n\n");
            sb.append(context.getString(R.string.studyoptions_congrats_custom));
        }
        return sb.toString();
    }


    /** true if there are any rev cards due. */
    public boolean revDue() {
        return mCol.getDb()
                .queryScalar(
                        "SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ?"
                                + " LIMIT 1",
                        mToday) != 0;
    }


    /** true if there are any new cards due. */
    public boolean newDue() {
        return mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT 1") != 0;
    }


    /** true if there are cards in learning, with review due the same
     * day, in the selected decks. */
    /* not in upstream anki. As revDue and newDue, it's used to check
     * what to do when a deck is selected in deck picker. When this
     * method is called, we already know that no cards is due
     * immedietly. It answers whether cards will be due later in the
     * same deck. */
    public boolean hasCardsTodayAfterStudyAheadLimit() {
        return mCol.getDb().queryScalar(
                "SELECT 1 FROM cards WHERE did IN " + _deckLimit()
                        + " AND queue = " + Consts.QUEUE_TYPE_LRN + " LIMIT 1") != 0;
    }


    // In V2 we consider two different kind of buried
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
    public String nextIvlStr(Context context, Card card, int ease) {
        long ivl = nextIvl(card, ease);
        if (ivl == 0) {
            return context.getString(R.string.sched_end);
        }
        String s = Utils.timeQuantityNextIvl(context, ivl);
        if (ivl < mCol.getConf().getInt("collapseTime")) {
            s = context.getString(R.string.less_than_time, s);
        }
        return s;
    }


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
    public abstract void buryCards(long[] cids, boolean manual);
    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    public void buryNote(long nid) {
        long[] cids = Utils.collection2Array(mCol.getDb().queryLongList(
                "SELECT id FROM cards WHERE nid = ? AND queue >= " + Consts.CARD_TYPE_NEW, nid));
        buryCards(cids);
    }

    /** Put cards at the end of the new queue. */
    public void forgetCards(long[] ids) {
        remFromDyn(ids);
        mCol.getDb().execute("update cards set type=" + Consts.CARD_TYPE_NEW + ",queue=" + Consts.QUEUE_TYPE_NEW + ",ivl=0,due=0,odue=0,factor="+Consts.STARTING_FACTOR +
                " where id in " + Utils.ids2str(ids));
        int pmax = mCol.getDb().queryScalar("SELECT max(due) FROM cards WHERE type=" + Consts.CARD_TYPE_NEW + "");
        // takes care of mod + usn
        sortCards(ids, pmax + 1);
        mCol.log(ids);
    }


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
    public void resetCards(Long[] ids) {
        long[] nonNew = Utils.collection2Array(mCol.getDb().queryLongList(
                "select id from cards where id in " + Utils.ids2str(ids) + " and (queue != " + Consts.QUEUE_TYPE_NEW + " or type != " + Consts.CARD_TYPE_NEW + ")"));
        mCol.getDb().execute("update cards set reps=0, lapses=0 where id in " + Utils.ids2str(nonNew));
        forgetCards(nonNew);
        mCol.log((Object[]) ids);
    }


    public void sortCards(long[] cids, int start) {
        sortCards(cids, start, 1, false, false);
    }


    public abstract void sortCards(long[] cids, int start, int step, boolean shuffle, boolean shift);


    public void randomizeCards(long did) {
        List<Long> cids = mCol.getDb().queryLongList("select id from cards where did = ?", did);
        sortCards(Utils.toPrimitive(cids), 1, 1, true, false);
    }


    public void orderCards(long did) {
        List<Long> cids = mCol.getDb().queryLongList("SELECT id FROM cards WHERE did = ? ORDER BY nid", did);
        sortCards(Utils.toPrimitive(cids), 1, 1, false, false);
    }


    public void resortConf(DeckConfig conf) {
        List<Long> dids = mCol.getDecks().didsForConf(conf);
        for (long did : dids) {
            if (conf.getJSONObject("new").getLong("order") == 0) {
                randomizeCards(did);
            } else {
                orderCards(did);
            }
        }
    }


    /**
     * for post-import
     */
    public void maybeRandomizeDeck() {
        maybeRandomizeDeck(null);
    }


    public abstract void maybeRandomizeDeck(Long did);
    // two kinds of buried exists in V2 only
    public abstract boolean haveBuried(long did);
    public enum UnburyType {
        ALL,
        MANUAL,
        SIBLINGS;
    }
    public abstract void unburyCardsForDeck(UnburyType type);
    public abstract void unburyCardsForDeck(long did);
    public abstract String getName();


    public int getToday() {
        return mToday;
    }



    public void setToday(int today) {
        mToday = today;
    }


    public long getDayCutoff() {
        return mDayCutoff;
    }


    public int getReps(){
        return mReps;
    }


    public void setReps(int reps){
        mReps = reps;
    }


    public int cardCount() {
        String dids = _deckLimit();
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE did IN " + dids);
    }


    public int eta(int[] counts) {
        return eta(counts, true);
    }


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
        int spread;
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



    /**
     @return The id of the card currently in the reviewer. 0 if no
     such card.
     */
    protected long currentCardId() {
        if (mCurrentCard == null) {
            /* This method is used to ensure that query don't return current card. Since 0 is not a valid nid, all cards
            will have a nid distinct from 0. As it is used in sql statement, it is not possible to just use a function
            areSiblings()*/
            return 0;
        }
        return mCurrentCard.getId();
    }


    /**
     @return The id of the note currently in the reviewer. 0 if no
     such card.
     */
    protected long currentCardNid() {
        Card currentCard = mCurrentCard;
        /* mCurrentCard may be set to null when the reviewer gets closed. So we copy it to be sure to avoid
           NullPointerException */
        if (mCurrentCard == null) {
            /* This method is used to determine whether two cards are siblings. Since 0 is not a valid nid, all cards
            will have a nid distinct from 0. As it is used in sql statement, it is not possible to just use a function
            areSiblings()*/
            return 0;
        }
        return currentCard.getNid();
    }


    protected abstract Card _getLrnCard(boolean collapse);


    protected boolean currentCardIsInQueueWithDeck(int queue, long did) {
        // mCurrentCard may be set to null when the reviewer gets closed. So we copy it to be sure to avoid NullPointerException
        Card currentCard = mCurrentCard;
        List<Long> currentCardParentsDid = mCurrentCardParentsDid;
        return currentCard != null && currentCard.getQueue() == queue && currentCardParentsDid != null && currentCardParentsDid.contains(did);
    }
}
