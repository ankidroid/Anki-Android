/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>                        *
 * Copyright (c) 2018 Chris Williams <chris@chrispwill.com>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General private License as published by the Free Software       *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General private License for more details.            *
 *                                                                                      *
 * You should have received a copy of the GNU General private License along with        *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki.sched;

import android.app.Activity;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import com.ichi2.anki.R;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.hooks.Hooks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Random;

import androidx.annotation.NonNull;
import timber.log.Timber;

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
                    "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.AvoidBranchingStatementAsLastInLoop",
                    "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt"})
public class SchedV2 extends AbstractSched {



    // Not in libanki
    private static final int[] FACTOR_ADDITION_VALUES = { -150, 0, 150 };

    private String mName = "std2";
    private boolean mHaveCustomStudy = true;

    protected Collection mCol;
    protected int mQueueLimit;
    protected int mReportLimit;
    private int mDynReportLimit;
    protected int mReps;
    protected boolean mHaveQueues;
    protected Integer mToday;
    public long mDayCutoff;
    private long mLrnCutoff;

    protected int mNewCount;
    protected int mLrnCount;
    protected int mRevCount;

    private int mNewCardModulus;

    private double[] mEtaCache = new double[] { -1, -1, -1, -1, -1, -1 };

    // Queues
    protected final LinkedList<Long> mNewQueue = new LinkedList<>();
    protected final LinkedList<long[]> mLrnQueue = new LinkedList<>();
    protected final LinkedList<Long> mLrnDayQueue = new LinkedList<>();
    protected final LinkedList<Long> mRevQueue = new LinkedList<>();

    private LinkedList<Long> mNewDids = new LinkedList<>();
    protected LinkedList<Long> mLrnDids = new LinkedList<>();

    // Not in libanki
    protected WeakReference<Activity> mContextReference;


    /**
     * card types: 0=new, 1=lrn, 2=rev, 3=relrn
     * queue types: 0=new, 1=(re)lrn, 2=rev, 3=day (re)lrn,
     *   4=preview, -1=suspended, -2=sibling buried, -3=manually buried
     * revlog types: 0=lrn, 1=rev, 2=relrn, 3=early review
     * positive revlog intervals are in days (rev), negative in seconds (lrn)
     * odue/odid store original due/did when cards moved to filtered deck
     *
     */

    public SchedV2(Collection col) {
        super();
        mCol = col;
        mQueueLimit = 50;
        mReportLimit = 99999;
        mDynReportLimit = 99999;
        mReps = 0;
        mToday = null;
        mHaveQueues = false;
        mLrnCutoff = 0;
        _updateCutoff();
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
            card.startTimer();
        }
        return card;
    }

    /** Ensures that reset is executed before the next card is selected */
    public void deferReset(){
        mHaveQueues = false;
    }

    public void reset() {
        _updateCutoff();
        _resetLrn();
        _resetRev();
        _resetNew();
        mHaveQueues = true;
    }


    public void answerCard(Card card, int ease) {
        mCol.log();
        mCol.markReview(card);
        _burySiblings(card);

        _answerCard(card, ease);

        _updateStats(card, "time", card.timeTaken());
        card.setMod(Utils.intTime());
        card.setUsn(mCol.usn());
        card.flushSched();
    }


    public void _answerCard(Card card, int ease) {
        if (_previewingCard(card)) {
            _answerCardPreview(card, ease);
            return;
        }

        card.setReps(card.getReps() + 1);

        if (card.getQueue() == Consts.QUEUE_TYPE_NEW) {
            // came from the new queue, move to learning
            card.setQueue(Consts.QUEUE_TYPE_LRN);
            card.setType(Consts.CARD_TYPE_LRN);
            // init reps to graduation
            card.setLeft(_startingLeft(card));
            // update daily limit
            _updateStats(card, "new");
        }
        if (card.getQueue() == Consts.QUEUE_TYPE_LRN || card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            _answerLrnCard(card, ease);
        } else if (card.getQueue() == Consts.QUEUE_TYPE_REV) {
            _answerRevCard(card, ease);
            // Update daily limit
            _updateStats(card, "rev");
        } else {
            throw new RuntimeException("Invalid queue");
        }

        // once a card has been answered once, the original due date
        // no longer applies
        if (card.getODue() > 0) {
            card.setODue(0);
        }
    }


    public void _answerCardPreview(Card card, int ease) {
        if (ease == Consts.BUTTON_ONE) {
            // Repeat after delay
            card.setQueue(Consts.QUEUE_TYPE_PREVIEW);
            card.setDue(Utils.intTime() + _previewDelay(card));
            mLrnCount += 1;
        } else if (ease == Consts.BUTTON_TWO) {
            // Restore original card state and remove from filtered deck
            _restorePreviewCard(card);
            _removeFromFiltered(card);
        } else {
            // This is in place of the assert
            throw new RuntimeException("Invalid ease");
        }
    }


    public int[] counts() {
        return new int[] {mNewCount, mLrnCount, mRevCount};
    }


    public int[] counts(@NonNull Card card) {
        int[] counts = counts();
        int idx = countIdx(card);
        counts[idx] += 1;
        return counts;
    }


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


    public int countIdx(Card card) {
        if (card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN || card.getQueue() == Consts.QUEUE_TYPE_PREVIEW) {
            return Consts.QUEUE_TYPE_LRN;
        }
        return card.getQueue();
    }


    public int answerButtons(Card card) {
        JSONObject conf = _cardConf(card);
        if (card.getODid() != 0 && !conf.getBoolean("resched")) {
            return 2;
        }
        return 4;
    }


    /**
     * Rev/lrn/time daily stats *************************************************
     * **********************************************
     */

    protected void _updateStats(Card card, String type) {
        _updateStats(card, type, 1);
    }


    public void _updateStats(Card card, String type, long cnt) {
        String key = type + "Today";
        long did = card.getDid();
        List<JSONObject> list = mCol.getDecks().parents(did);
        list.add(mCol.getDecks().get(did));
        for (JSONObject g : list) {
            JSONArray a = g.getJSONArray(key);
            // add
            a.put(1, a.getLong(1) + cnt);
            mCol.getDecks().save(g);
        }
    }


    public void extendLimits(int newc, int rev) {
        JSONObject cur = mCol.getDecks().current();
        ArrayList<JSONObject> decks = new ArrayList<>();
        decks.add(cur);
        decks.addAll(mCol.getDecks().parents(cur.getLong("id")));
        for (long did : mCol.getDecks().children(cur.getLong("id")).values()) {
            decks.add(mCol.getDecks().get(did));
        }
        for (JSONObject g : decks) {
            // add
            JSONArray ja = g.getJSONArray("newToday");
            ja.put(1, ja.getInt(1) - newc);
            g.put("newToday", ja);
            ja = g.getJSONArray("revToday");
            ja.put(1, ja.getInt(1) - rev);
            g.put("revToday", ja);
            mCol.getDecks().save(g);
        }
    }


    protected int _walkingCount(LimitMethod limFn, CountMethod cntFn) {
        int tot = 0;
        HashMap<Long, Integer> pcounts = new HashMap<>();
        // for each of the active decks
        HashMap<String, JSONObject> nameMap = mCol.getDecks().nameMap();
        for (long did : mCol.getDecks().active()) {
            // get the individual deck's limit
            int lim = limFn.operation(mCol.getDecks().get(did));
            if (lim == 0) {
                continue;
            }
            // check the parents
            List<JSONObject> parents = mCol.getDecks().parents(did, nameMap);
            for (JSONObject p : parents) {
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
            for (JSONObject p : parents) {
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
     * Deck list **************************************************************** *******************************
     */


    /**
     * Returns [deckname, did, rev, lrn, new]
     *
     * Return nulls when deck task is cancelled.
     */
    public List<DeckDueTreeNode> deckDueList() {
        return deckDueList(null);
    }

    public List<DeckDueTreeNode> deckDueList(CollectionTask collectionTask) {
        _checkDay();
        mCol.getDecks().checkIntegrity();
        ArrayList<JSONObject> decks = mCol.getDecks().allSorted();
        HashMap<String, Integer[]> lims = new HashMap<>();
        ArrayList<DeckDueTreeNode> data = new ArrayList<>();
        HashMap<Long, HashMap> childMap = mCol.getDecks().childMap();
        for (JSONObject deck : decks) {
            if (collectionTask != null && collectionTask.isCancelled()) {
                return null;
            }
            String p = Decks.parent(deck.getString("name"));
            // new
            int nlim = _deckNewLimitSingle(deck);
            if (!TextUtils.isEmpty(p)) {
                nlim = Math.min(nlim, lims.get(p)[0]);
            }
            int _new = _newForDeck(deck.getLong("id"), nlim);
            // learning
            int lrn = _lrnForDeck(deck.getLong("id"));
            // reviews
            Integer plim;
            if (!TextUtils.isEmpty(p)) {
                plim = lims.get(p)[1];
            } else {
                plim = null;
            }
            int rlim = _deckRevLimitSingle(deck, plim);
            int rev = _revForDeck(deck.getLong("id"), rlim, childMap);
            // save to list
            data.add(new DeckDueTreeNode(deck.getString("name"), deck.getLong("id"), rev, lrn, _new));
            // add deck as a parent
            lims.put(deck.getString("name"), new Integer[]{nlim, rlim});
        }
        return data;
    }


    public List<DeckDueTreeNode> deckDueTree() {
        return deckDueTree(null);
    }

    public List<DeckDueTreeNode> deckDueTree(CollectionTask collectionTask) {
        List<DeckDueTreeNode> deckDueTree = deckDueList(collectionTask);
        if (deckDueTree == null) {
            return null;
        }
        return _groupChildren(deckDueTree);
    }


    private List<DeckDueTreeNode> _groupChildren(List<DeckDueTreeNode> grps) {
        // first, split the group names into components
        for (DeckDueTreeNode g : grps) {
            g.names = Decks.path(g.names[0]);
        }
        // and sort based on those components
        Collections.sort(grps);
        // then run main function
        return _groupChildrenMain(grps);
    }


    protected List<DeckDueTreeNode> _groupChildrenMain(List<DeckDueTreeNode> grps) {
        List<DeckDueTreeNode> tree = new ArrayList<>();
        // group and recurse
        ListIterator<DeckDueTreeNode> it = grps.listIterator();
        while (it.hasNext()) {
            DeckDueTreeNode node = it.next();
            String head = node.names[0];
            // Compose the "tail" node list. The tail is a list of all the nodes that proceed
            // the current one that contain the same name[0]. I.e., they are subdecks that stem
            // from this node. This is our version of python's itertools.groupby.
            List<DeckDueTreeNode> tail  = new ArrayList<>();
            tail.add(node);
            while (it.hasNext()) {
                DeckDueTreeNode next = it.next();
                if (head.equals(next.names[0])) {
                    // Same head - add to tail of current head.
                    tail.add(next);
                } else {
                    // We've iterated past this head, so step back in order to use this node as the
                    // head in the next iteration of the outer loop.
                    it.previous();
                    break;
                }
            }
            Long did = null;
            int rev = 0;
            int _new = 0;
            int lrn = 0;
            List<DeckDueTreeNode> children = new ArrayList<>();
            for (DeckDueTreeNode c : tail) {
                if (c.names.length == 1) {
                    // current node
                    did = c.did;
                    rev += c.revCount;
                    lrn += c.lrnCount;
                    _new += c.newCount;
                } else {
                    // set new string to tail
                    String[] newTail = new String[c.names.length-1];
                    System.arraycopy(c.names, 1, newTail, 0, c.names.length-1);
                    c.names = newTail;
                    children.add(c);
                }
            }
            children = _groupChildrenMain(children);
            // tally up children counts
            for (DeckDueTreeNode ch : children) {
                lrn +=  ch.lrnCount;
                _new += ch.newCount;
            }
            // limit the counts to the deck's limits
            JSONObject conf = mCol.getDecks().confForDid(did);
            JSONObject deck = mCol.getDecks().get(did);
            if (conf.getInt("dyn") == 0) {
                _new = Math.max(0, Math.min(_new, conf.getJSONObject("new").getInt("perDay") - deck.getJSONArray("newToday").getInt(1)));
            }
            tree.add(new DeckDueTreeNode(head, did, rev, lrn, _new, children));
        }
        return tree;
    }


    /**
     * Getting the next card ****************************************************
     * *******************************************
     */

    /**
     * Return the next due card, or null.
     */
    protected Card _getCard() {
        // learning card due?
        Card c = _getLrnCard();
        if (c != null) {
            return c;
        }
        // new first, or time for one?
        if (_timeForNewCard()) {
            c = _getNewCard();
            if (c != null) {
                return c;
            }
        }
        // Day learning first and card due?
        boolean dayLearnFirst = mCol.getConf().optBoolean("dayLearnFirst", false);
        if (dayLearnFirst) {
            c = _getLrnDayCard();
            if (c != null) {
                return c;
            }
        }
        // Card due for review?
        c = _getRevCard();
        if (c != null) {
            return c;
        }
        // day learning card due?
        if (!dayLearnFirst) {
            c = _getLrnDayCard();
            if (c != null) {
                return c;
            }
        }
        // New cards left?
        c = _getNewCard();
        if (c != null) {
            return c;
        }
        // collapse or finish
        return _getLrnCard(true);
    }


    /**
     * New cards **************************************************************** *******************************
     */

    protected void _resetNewCount() {
        mNewCount = _walkingCount((JSONObject g) -> _deckNewLimitSingle(g),
                                  (long did, int lim) -> _cntFnNew(did, lim));
    }


    // Used as an argument for _walkingCount() in _resetNewCount() above
    @SuppressWarnings("unused")
    protected int _cntFnNew(long did, int lim) {
        return mCol.getDb().queryScalar(
                "SELECT count() FROM (SELECT 1 FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT ?)",
                new Object[]{did, lim});
    }


    private void _resetNew() {
        _resetNewCount();
        mNewDids = new LinkedList<>(mCol.getDecks().active());
        mNewQueue.clear();
        _updateNewCardRatio();
    }


    private boolean _fillNew() {
        if (!mNewQueue.isEmpty()) {
            return true;
        }
        if (mNewCount == 0) {
            return false;
        }
        while (!mNewDids.isEmpty()) {
            long did = mNewDids.getFirst();
            int lim = Math.min(mQueueLimit, _deckNewLimit(did));
            Cursor cur = null;
            if (lim != 0) {
                mNewQueue.clear();
                try {
                    // fill the queue with the current did
                    cur = mCol
                            .getDb()
                            .getDatabase()
                            .query("SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " order by due, ord LIMIT ?",
                                    new Object[]{did, lim});
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
        if (mNewCount != 0) {
            // if we didn't get a card but the count is non-zero,
            // we need to check again for any cards that were
            // removed from the queue but not buried
            _resetNew();
            return _fillNew();
        }
        return false;
    }


    protected Card _getNewCard() {
        if (_fillNew()) {
            mNewCount -= 1;
            return mCol.getCard(mNewQueue.remove());
        }
        return null;
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


    protected int _deckNewLimit(long did) {
        return _deckNewLimit(did, null);
    }


    protected int _deckNewLimit(long did, LimitMethod fn) {
        if (fn == null) {
            fn = (g -> _deckNewLimitSingle(g));
        }
        List<JSONObject> decks = mCol.getDecks().parents(did);
        decks.add(mCol.getDecks().get(did));
        int lim = -1;
        // for the deck and each of its parents
        int rem = 0;
        for (JSONObject g : decks) {
            rem = fn.operation(g);
            if (lim == -1) {
                lim = rem;
            } else {
                lim = Math.min(rem, lim);
            }
        }
        return lim;
    }


    /** New count for a single deck. */
    public int _newForDeck(long did, int lim) {
        if (lim == 0) {
            return 0;
        }
        lim = Math.min(lim, mReportLimit);
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT ?)",
                                        new Object[] {did, lim});
    }


    /** Limit for deck without parent limits. */
    public int _deckNewLimitSingle(JSONObject g) {
        if (g.getInt("dyn") != 0) {
            return mDynReportLimit;
        }
        JSONObject c = mCol.getDecks().confForDid(g.getLong("id"));
        return Math.max(0, c.getJSONObject("new").getInt("perDay") - g.getJSONArray("newToday").getInt(1));
    }

    public int totalNewForCurrentDeck() {
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT ?)",
                                        new Object[] {mReportLimit});
    }

    /**
     * Learning queues *********************************************************** ************************************
     */

    private boolean _updateLrnCutoff(boolean force) {
        long nextCutoff = Utils.intTime() + mCol.getConf().getInt("collapseTime");
        if (nextCutoff - mLrnCutoff > 60 || force) {
            mLrnCutoff = nextCutoff;
            return true;
        }
        return false;
    }


    private void _maybeResetLrn(boolean force) {
        if (_updateLrnCutoff(force)) {
            _resetLrn();
        }
    }


    protected void _resetLrnCount() {
        // sub-day
        mLrnCount = mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit()
                + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ?", new Object[] {mLrnCutoff});

        // day
        mLrnCount += mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ?",
                new Object[] {mToday});

        // previews
        mLrnCount += mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_PREVIEW);
    }


    protected void _resetLrn() {
        _updateLrnCutoff(true);
        _resetLrnCount();
        mLrnQueue.clear();
        mLrnDayQueue.clear();
        mLrnDids = mCol.getDecks().active();
    }


    // sub-day learning
    protected boolean _fillLrn() {
        if (mLrnCount == 0) {
            return false;
        }
        if (!mLrnQueue.isEmpty()) {
            return true;
        }
        long cutoff = 0;
        cutoff = Utils.intTime() + mCol.getConf().getLong("collapseTime");
        Cursor cur = null;
        mLrnQueue.clear();
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .query(
                            "SELECT due, id FROM cards WHERE did IN " + _deckLimit() + " AND queue IN (" + Consts.QUEUE_TYPE_LRN + ", " + Consts.QUEUE_TYPE_PREVIEW + ") AND due < ?"
                            + " LIMIT ?", new Object[] { cutoff, mReportLimit});
            while (cur.moveToNext()) {
                mLrnQueue.add(new long[] { cur.getLong(0), cur.getLong(1) });
            }
            // as it arrives sorted by did first, we need to sort it
            Collections.sort(mLrnQueue, new Comparator<long[]>() {
                @Override
                public int compare(long[] lhs, long[] rhs) {
                    return Long.compare(lhs[0], rhs[0]);
                }
            });
            return !mLrnQueue.isEmpty();
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    protected Card _getLrnCard() {
        return _getLrnCard(false);
    }


    protected Card _getLrnCard(boolean collapse) {
        _maybeResetLrn(collapse && mLrnCount == 0);
        if (_fillLrn()) {
            double cutoff = Utils.now();
            if (collapse) {
                cutoff += mCol.getConf().getInt("collapseTime");
            }
            if (mLrnQueue.getFirst()[0] < cutoff) {
                long id = mLrnQueue.remove()[1];
                Card card = mCol.getCard(id);
                mLrnCount -= 1;
                return card;
            }
        }
        return null;
    }


    // daily learning
    private boolean _fillLrnDay() {
        if (mLrnCount == 0) {
            return false;
        }
        if (!mLrnDayQueue.isEmpty()) {
            return true;
        }
        while (!mLrnDids.isEmpty()) {
            long did = mLrnDids.getFirst();
            // fill the queue with the current did
            mLrnDayQueue.clear();
            Cursor cur = null;
            try {
                cur = mCol
                        .getDb()
                        .getDatabase()
                        .query(
                                "SELECT id FROM cards WHERE did = ? AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ? LIMIT ?",
                                new Object[] {did, mToday, mQueueLimit});
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


    protected Card _getLrnDayCard() {
        if (_fillLrnDay()) {
            mLrnCount -= 1;
            return mCol.getCard(mLrnDayQueue.remove());
        }
        return null;
    }


    protected void _answerLrnCard(Card card, int ease) {
        JSONObject conf = _lrnConf(card);
        int type;
        if (card.getType() == Consts.CARD_TYPE_REV || card.getType() == Consts.CARD_TYPE_RELEARNING) {
            type = Consts.CARD_TYPE_REV;
        } else {
            type = 0;
        }

        // lrnCount was decremented once when card was fetched
        int lastLeft = card.getLeft();
        boolean leaving = false;

        // immediate graduate?
        if (ease == Consts.BUTTON_FOUR) {
            _rescheduleAsRev(card, conf, true);
            leaving = true;
        // next step?
        } else if (ease == Consts.BUTTON_THREE) {
            // graduation time?
            if ((card.getLeft() % 1000) - 1 <= 0) {
                _rescheduleAsRev(card, conf, false);
                leaving = true;
            } else {
                _moveToNextStep(card, conf);
            }
        } else if (ease == Consts.BUTTON_TWO) {
            _repeatStep(card, conf);
        } else {
            // move back to first step
            _moveToFirstStep(card, conf);
        }
        _logLrn(card, ease, conf, leaving, type, lastLeft);
    }


    protected void _updateRevIvlOnFail(Card card, JSONObject conf) {
        card.setLastIvl(card.getIvl());
        card.setIvl(_lapseIvl(card, conf));
    }


    private int _moveToFirstStep(Card card, JSONObject conf) {
        card.setLeft(_startingLeft(card));

        // relearning card?
        if (card.getType() == Consts.CARD_TYPE_RELEARNING) {
            _updateRevIvlOnFail(card, conf);
        }

        return _rescheduleLrnCard(card, conf);
    }


    private void _moveToNextStep(Card card, JSONObject conf) {
        // decrement real left count and recalculate left today
        int left = (card.getLeft() % 1000) - 1;
        card.setLeft(_leftToday(conf.getJSONArray("delays"), left) * 1000 + left);

        _rescheduleLrnCard(card, conf);
    }


    private void _repeatStep(Card card, JSONObject conf) {
        int delay = _delayForRepeatingGrade(conf, card.getLeft());
        _rescheduleLrnCard(card, conf, delay);
    }


    private int _rescheduleLrnCard(Card card, JSONObject conf) {
        return _rescheduleLrnCard(card, conf, null);
    }


    private int _rescheduleLrnCard(Card card, JSONObject conf, Integer delay) {
        // normal delay for the current step?
        if (delay == null) {
            delay = _delayForGrade(conf, card.getLeft());
        }
        card.setDue(Utils.intTime() + delay);

        // due today?
        if (card.getDue() < mDayCutoff) {
            // Add some randomness, up to 5 minutes or 25%
            int maxExtra = (int) Math.min(300, (int)(delay * 0.25));
            int fuzz = new Random().nextInt(maxExtra);
            card.setDue(Math.min(mDayCutoff - 1, card.getDue() + fuzz));
            card.setQueue(Consts.QUEUE_TYPE_LRN);
            if (card.getDue() < (Utils.intTime() + mCol.getConf().getInt("collapseTime"))) {
                mLrnCount += 1;
                // if the queue is not empty and there's nothing else to do, make
                // sure we don't put it at the head of the queue and end up showing
                // it twice in a row
                if (!mLrnQueue.isEmpty() && mRevCount == 0 && mNewCount == 0) {
                    long smallestDue = mLrnQueue.getFirst()[0];
                    card.setDue(Math.max(card.getDue(), smallestDue + 1));
                }
                _sortIntoLrn(card.getDue(), card.getId());
            }
        } else {
            // the card is due in one or more days, so we need to use the day learn queue
            long ahead = ((card.getDue() - mDayCutoff) / 86400) + 1;
            card.setDue(mToday + ahead);
            card.setQueue(Consts.QUEUE_TYPE_DAY_LEARN_RELEARN);
        }
        return delay;
    }


    protected int _delayForGrade(JSONObject conf, int left) {
        left = left % 1000;
        try {
            double delay;
            JSONArray ja = conf.getJSONArray("delays");
            int len = ja.length();
            try {
                delay = ja.getDouble(len - left);
            } catch (JSONException e) {
                if (conf.getJSONArray("delays").length() > 0) {
                    delay = conf.getJSONArray("delays").getDouble(0);
                } else {
                    // user deleted final step; use dummy value
                    delay = 1.0;
                }
            }
            return (int) (delay * 60.0);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private int  _delayForRepeatingGrade(JSONObject conf, int left) {
        // halfway between last and  next
        int delay1 = _delayForGrade(conf, left);
        int delay2;
        if (conf.getJSONArray("delays").length() > 1) {
            delay2 = _delayForGrade(conf, left - 1);
        } else {
            delay2 = delay1 * 2;
        }
        int avg = (delay1 + Math.max(delay1, delay2)) / 2;
        return avg;
    }


    protected JSONObject _lrnConf(Card card) {
        if (card.getType() == Consts.CARD_TYPE_REV || card.getType() == Consts.CARD_TYPE_RELEARNING) {
            return _lapseConf(card);
        } else {
            return _newConf(card);
        }
    }


    protected void _rescheduleAsRev(Card card, JSONObject conf, boolean early) {
        boolean lapse = (card.getType() == Consts.CARD_TYPE_REV || card.getType() == Consts.CARD_TYPE_RELEARNING);
        if (lapse) {
            _rescheduleGraduatingLapse(card);
        } else {
            _rescheduleNew(card, conf, early);
        }
        // if we were dynamic, graduating means moving back to the old deck
        if (card.getODid() != 0) {
            _removeFromFiltered(card);
        }
    }

    private void _rescheduleGraduatingLapse(Card card) {
        card.setDue(mToday + card.getIvl());
        card.setQueue(Consts.QUEUE_TYPE_REV);
        card.setType(Consts.CARD_TYPE_REV);
    }


    protected int _startingLeft(Card card) {
        JSONObject conf;
        if (card.getType() == Consts.CARD_TYPE_RELEARNING) {
        conf = _lapseConf(card);
        } else {
            conf = _lrnConf(card);
        }
        int tot = conf.getJSONArray("delays").length();
        int tod = _leftToday(conf.getJSONArray("delays"), tot);
        return tot + tod * 1000;
    }


    /** the number of steps that can be completed by the day cutoff */
    protected int _leftToday(JSONArray delays, int left) {
        return _leftToday(delays, left, 0);
    }


    private int _leftToday(JSONArray delays, int left, long now) {
        if (now == 0) {
            now = Utils.intTime();
        }
        int ok = 0;
        int offset = Math.min(left, delays.length());
        for (int i = 0; i < offset; i++) {
            now += (int) (delays.getDouble(delays.length() - offset + i) * 60.0);
            if (now > mDayCutoff) {
                break;
            }
            ok = i;
        }
        return ok + 1;
    }


    private int _graduatingIvl(Card card, JSONObject conf, boolean early) {
        return _graduatingIvl(card, conf, early, true);
    }


    private int _graduatingIvl(Card card, JSONObject conf, boolean early, boolean fuzz) {
        if (card.getType() == Consts.CARD_TYPE_REV || card.getType() == Consts.CARD_TYPE_RELEARNING) {
            return card.getIvl();
        }
        int ideal;
        JSONArray ja;
        ja = conf.getJSONArray("ints");
        if (!early) {
            // graduate
            ideal = ja.getInt(0);
        } else {
            // early remove
            ideal = ja.getInt(1);
        }
        if (fuzz) {
            ideal = _fuzzedIvl(ideal);
        }
        return ideal;
    }


    /** Reschedule a new card that's graduated for the first time. */
    private void _rescheduleNew(Card card, JSONObject conf, boolean early) {
        card.setIvl(_graduatingIvl(card, conf, early));
        card.setDue(mToday + card.getIvl());
        card.setFactor(conf.getInt("initialFactor"));
        card.setType(Consts.CARD_TYPE_REV);
        card.setQueue(Consts.QUEUE_TYPE_REV);
    }


    private void _logLrn(Card card, int ease, JSONObject conf, boolean leaving, int type, int lastLeft) {
        int lastIvl = -(_delayForGrade(conf, lastLeft));
        int ivl = leaving ? card.getIvl() : -(_delayForGrade(conf, card.getLeft()));
        log(card.getId(), mCol.usn(), ease, ivl, lastIvl, card.getFactor(), card.timeTaken(), type);
    }


    private void log(long id, int usn, int ease, int ivl, int lastIvl, int factor, int timeTaken, int type) {
        try {
            mCol.getDb().execute("INSERT INTO revlog VALUES (?,?,?,?,?,?,?,?,?)",
                    new Object[]{Utils.now() * 1000, id, usn, ease, ivl, lastIvl, factor, timeTaken, type});
        } catch (SQLiteConstraintException e) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
            }
            log(id, usn, ease, ivl, lastIvl, factor, timeTaken, type);
        }
    }


    private int _lrnForDeck(long did) {
        try {
            int cnt = mCol.getDb().queryScalar(
                    "SELECT count() FROM (SELECT null FROM cards WHERE did = ?"
                            + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < ?"
                            + " LIMIT ?)",
                    new Object[] {did, (Utils.intTime() + mCol.getConf().getInt("collapseTime")), mReportLimit});
            return cnt + mCol.getDb().queryScalar(
                    "SELECT count() FROM (SELECT null FROM cards WHERE did = ?"
                            + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= ?"
                            + " LIMIT ?)",
                    new Object[] {did, mToday, mReportLimit});
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Reviews ****************************************************************** *****************************
     */

    private int _currentRevLimit() {
        JSONObject d = mCol.getDecks().get(mCol.getDecks().selected(), false);
        return _deckRevLimitSingle(d);
    }


    protected int _deckRevLimitSingle(JSONObject d) {
        return _deckRevLimitSingle(d, null);
    }


    private int _deckRevLimitSingle(JSONObject d, Integer parentLimit) {
        // invalid deck selected?
        if (d == null) {
            return 0;
        }
        if (d.getInt("dyn") != 0) {
            return mDynReportLimit;
        }
        JSONObject c = mCol.getDecks().confForDid(d.getLong("id"));
        int lim = Math.max(0, c.getJSONObject("rev").getInt("perDay") - d.getJSONArray("revToday").getInt(1));

        if (parentLimit != null) {
            return Math.min(parentLimit, lim);
        } else if (!d.getString("name").contains("::")) {
            return lim;
        } else {
            for (JSONObject parent : mCol.getDecks().parents(d.getLong("id"))) {
                // pass in dummy parentLimit so we don't do parent lookup again
                lim = Math.min(lim, _deckRevLimitSingle(parent, lim));
            }
            return lim;
        }
    }


    protected int _revForDeck(long did, int lim, HashMap<Long, HashMap> childMap) {
        List<Long> dids = mCol.getDecks().childDids(did, childMap);
        dids.add(0, did);
        lim = Math.min(lim, mReportLimit);
        return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did in " + Utils.ids2str(dids) + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
                                        new Object[] {mToday, lim});
    }


    protected void _resetRevCount() {
        int lim = _currentRevLimit();
        mRevCount = mCol.getDb().queryScalar("SELECT count() FROM (SELECT id FROM cards WHERE did in " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
                                             new Object[]{mToday, lim});
    }


    protected void _resetRev() {
        _resetRevCount();
        mRevQueue.clear();
    }


    protected boolean _fillRev() {
        if (!mRevQueue.isEmpty()) {
            return true;
        }
        if (mRevCount == 0) {
            return false;
        }
        int lim = Math.min(mQueueLimit, _currentRevLimit());
        if (lim != 0) {
            Cursor cur = null;
            mRevQueue.clear();
            // fill the queue with the current did
            try {
                cur = mCol
                        .getDb()
                        .getDatabase()
                        .query(
                                "SELECT id FROM cards WHERE did in " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? "
                                        + " ORDER BY due, random() LIMIT ?",
                                new Object[] {mToday, lim});
                while (cur.moveToNext()) {
                    mRevQueue.add(cur.getLong(0));
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            if (!mRevQueue.isEmpty()) {
                // preserve order
                // Note: libanki reverses mRevQueue and returns the last element in _getRevCard().
                // AnkiDroid differs by leaving the queue intact and returning the *first* element
                // in _getRevCard().
                return true;
            }
        }
        if (mRevCount != 0) {
            // if we didn't get a card but the count is non-zero,
            // we need to check again for any cards that were
            // removed from the queue but not buried
            _resetRev();
            return _fillRev();
        }
        return false;
    }


    protected Card _getRevCard() {
        if (_fillRev()) {
            mRevCount -= 1;
            return mCol.getCard(mRevQueue.remove());
        } else {
            return null;
        }
    }


    public int totalRevForCurrentDeck() {
        return mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + _deckLimit() + "  AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= ? LIMIT ?)",
                new Object[]{mToday, mReportLimit});
    }


    /**
     * Answering a review card **************************************************
     * *********************************************
     */

    protected void _answerRevCard(Card card, int ease) {
        int delay = 0;
        boolean early = card.getODid() != 0 && (card.getODue() > mToday);
        int type = early ? 3 : 1;
        if (ease == Consts.BUTTON_ONE) {
            delay = _rescheduleLapse(card);
        } else {
            _rescheduleRev(card, ease, early);
        }
        _logRev(card, ease, delay, type);
    }


    protected int _rescheduleLapse(Card card) {
        JSONObject conf;
        conf = _lapseConf(card);
        card.setLapses(card.getLapses() + 1);
        card.setFactor(Math.max(1300, card.getFactor() - 200));
        int delay;
         boolean suspended = _checkLeech(card, conf) && card.getQueue() == Consts.QUEUE_TYPE_SUSPENDED;
        if (conf.getJSONArray("delays").length() != 0 && !suspended) {
            card.setType(Consts.CARD_TYPE_RELEARNING);
            delay = _moveToFirstStep(card, conf);
        } else {
            // no relearning steps
            _updateRevIvlOnFail(card, conf);
            _rescheduleAsRev(card, conf, false);
            // need to reset the queue after rescheduling
            if (suspended) {
                card.setQueue(Consts.QUEUE_TYPE_SUSPENDED);
            }
            delay = 0;
        }

        return delay;
    }


    private int _lapseIvl(Card card, JSONObject conf) {
        int ivl = Math.max(1, Math.max(conf.getInt("minInt"), (int)(card.getIvl() * conf.getDouble("mult"))));
        return ivl;
    }


    protected void _rescheduleRev(Card card, int ease, boolean early) {
        // update interval
        card.setLastIvl(card.getIvl());
        if (early) {
            _updateEarlyRevIvl(card, ease);
        } else {
            _updateRevIvl(card, ease);
        }

        // then the rest
        card.setFactor(Math.max(1300, card.getFactor() + FACTOR_ADDITION_VALUES[ease - 2]));
        card.setDue(mToday + card.getIvl());

        // card leaves filtered deck
        _removeFromFiltered(card);
    }


    protected void _logRev(Card card, int ease, int delay, int type) {
        log(card.getId(), mCol.usn(), ease, ((delay != 0) ? (-delay) : card.getIvl()), card.getLastIvl(),
                card.getFactor(), card.timeTaken(), type);
    }


    /**
     * Interval management ******************************************************
     * *****************************************
     */

    /**
     * Next interval for CARD, given EASE.
     */
    protected int _nextRevIvl(Card card, int ease, boolean fuzz) {
        long delay = _daysLate(card);
        JSONObject conf = _revConf(card);
        double fct = card.getFactor() / 1000.0;
        double hardFactor = conf.optDouble("hardFactor", 1.2);
        int hardMin;
        if (hardFactor > 1) {
            hardMin = card.getIvl();
        } else {
            hardMin = 0;
        }

        int ivl2 = _constrainedIvl(card.getIvl() * hardFactor, conf, hardMin, fuzz);
        if (ease == Consts.BUTTON_TWO) {
            return ivl2;
        }

        int ivl3 = _constrainedIvl((card.getIvl() + delay / 2) * fct, conf, ivl2, fuzz);
        if (ease == Consts.BUTTON_THREE) {
            return ivl3;
        }

        int ivl4 = _constrainedIvl((
                                    (card.getIvl() + delay) * fct * conf.getDouble("ease4")), conf, ivl3, fuzz);
        return ivl4;
    }

    protected int _fuzzedIvl(int ivl) {
        int[] minMax = _fuzzedIvlRange(ivl);
        // Anki's python uses random.randint(a, b) which returns x in [a, b] while the eq Random().nextInt(a, b)
        // returns x in [0, b-a), hence the +1 diff with libanki
        return (new Random().nextInt(minMax[1] - minMax[0] + 1)) + minMax[0];
    }


    public int[] _fuzzedIvlRange(int ivl) {
        int fuzz;
        if (ivl < 2) {
            return new int[]{1, 1};
        } else if (ivl == 2) {
            return new int[]{2, 3};
        } else if (ivl < 7) {
            fuzz = (int)(ivl * 0.25);
        } else if (ivl < 30) {
            fuzz = Math.max(2, (int)(ivl * 0.15));
        } else {
            fuzz = Math.max(4, (int)(ivl * 0.05));
        }
        // fuzz at least a day
        fuzz = Math.max(fuzz, 1);
        return new int[]{ivl - fuzz, ivl + fuzz};
    }


    protected int _constrainedIvl(double ivl, JSONObject conf, double prev, boolean fuzz) {
        int newIvl = (int) (ivl * conf.optDouble("ivlFct", 1));
        if (fuzz) {
            newIvl = _fuzzedIvl(newIvl);
        }

        newIvl = (int) Math.max(Math.max(newIvl, prev + 1), 1);
        newIvl = Math.min(newIvl, conf.getInt("maxIvl"));

        return newIvl;
    }


    /**
     * Number of days later than scheduled.
     */
    protected long _daysLate(Card card) {
        long due = card.getODid() != 0 ? card.getODue() : card.getDue();
        return Math.max(0, mToday - due);
    }


    protected void _updateRevIvl(Card card, int ease) {
        card.setIvl(_nextRevIvl(card, ease, true));
    }


    private void _updateEarlyRevIvl(Card card, int ease) {
        card.setIvl(_earlyReviewIvl(card, ease));
    }


    /** next interval for card when answered early+correctly */
    private int _earlyReviewIvl(Card card, int ease) {
        if (card.getODid() == 0 || card.getType() != Consts.CARD_TYPE_REV || card.getFactor() == 0) {
            throw new RuntimeException("Unexpected card parameters");
        }
        if (ease <= 1) {
            throw new RuntimeException("Ease must be greater than 1");
        }

        long elapsed = card.getIvl() - (card.getODue() - mToday);

        JSONObject conf = _revConf(card);

        double easyBonus = 1;
        // early 3/4 reviews shouldn't decrease previous interval
        double minNewIvl = 1;

        double factor;
        if (ease == Consts.BUTTON_TWO)  {
            factor = conf.optDouble("hardFactor", 1.2);
            // hard cards shouldn't have their interval decreased by more than 50%
            // of the normal factor
            minNewIvl = factor / 2;
        } else if (ease == 3) {
            factor = card.getFactor() / 1000;
        } else { // ease == 4
            factor = card.getFactor() / 1000;
            double ease4 = conf.getDouble("ease4");
            // 1.3 -> 1.15
            easyBonus = ease4 - (ease4 - 1)/2;
        }

        double ivl = Math.max(elapsed * factor, 1);

        // cap interval decreases
        ivl = Math.max(card.getIvl() * minNewIvl, ivl) * easyBonus;

        return _constrainedIvl(ivl, conf, 0, false);
    }


    /**
     * Dynamic deck handling ******************************************************************
     * *****************************
     */

    /** Rebuild a dynamic deck. */
    public void rebuildDyn() {
        rebuildDyn(0);
    }


    // Note: The original returns an integer result. We return List<Long> with that number to satisfy the
    // interface requirements. The result isn't used anywhere so this isn't a problem.
    public List<Long> rebuildDyn(long did) {
        if (did == 0) {
            did = mCol.getDecks().selected();
        }
        JSONObject deck = mCol.getDecks().get(did);
        if (deck.getInt("dyn") == 0) {
            Timber.e("error: deck is not a filtered deck");
            return null;
        }
        // move any existing cards back first, then fill
        emptyDyn(did);
        int cnt = _fillDyn(deck);
        if (cnt == 0) {
            return null;
        }
        // and change to our new deck
        mCol.getDecks().select(did);
        return Collections.singletonList((long)cnt);
    }


    private int _fillDyn(JSONObject deck) {
        int start = -100000;
        int total = 0;
        JSONArray terms;
        List<Long> ids;
        terms = deck.getJSONArray("terms");
        for (int i = 0; i < terms.length(); i++) {
            JSONArray term = terms.getJSONArray(i);
            String search = term.getString(0);
            int limit = term.getInt(1);
            int order = term.getInt(2);

            String orderlimit = _dynOrder(order, limit);
            if (!TextUtils.isEmpty(search.trim())) {
                search = String.format(Locale.US, "(%s)", search);
            }
            search = String.format(Locale.US, "%s -is:suspended -is:buried -deck:filtered", search);
            ids = mCol.findCards(search, orderlimit);
            if (ids.isEmpty()) {
                return total;
            }
            // move the cards over
            mCol.log(deck.getLong("id"), ids);
            _moveToDyn(deck.getLong("id"), ids, start + total);
            total += ids.size();
        }
        return total;
    }


    public void emptyDyn(long did) {
        emptyDyn(did, null);
    }


    public void emptyDyn(long did, String lim) {
        if (lim == null) {
            lim = "did = " + did;
        }
        mCol.log(mCol.getDb().queryColumn(Long.class, "select id from cards where " + lim, 0));

        mCol.getDb().execute(
                "update cards set did = odid, " + _restoreQueueSnippet() +
                ", due = (case when odue>0 then odue else due end), odue = 0, odid = 0, usn = ? where " + lim,
                new Object[] { mCol.usn() });
    }


    public void remFromDyn(long[] cids) {
        emptyDyn(0, "id IN " + Utils.ids2str(cids) + " AND odid");
    }


    /**
     * Generates the required SQL for order by and limit clauses, for dynamic decks.
     *
     * @param o deck["order"]
     * @param l deck["limit"]
     * @return The generated SQL to be suffixed to "select ... from ... order by "
     */
    protected String _dynOrder(int o, int l) {
        String t;
        switch (o) {
            case Consts.DYN_OLDEST:
                t = "c.mod";
                break;
            case Consts.DYN_RANDOM:
                t = "random()";
                break;
            case Consts.DYN_SMALLINT:
                t = "ivl";
                break;
            case Consts.DYN_BIGINT:
                t = "ivl desc";
                break;
            case Consts.DYN_LAPSES:
                t = "lapses desc";
                break;
            case Consts.DYN_ADDED:
                t = "n.id";
                break;
            case Consts.DYN_REVADDED:
                t = "n.id desc";
                break;
            case Consts.DYN_DUE:
                t = "c.due";
                break;
            case Consts.DYN_DUEPRIORITY:
                t = String.format(Locale.US,
                        "(case when queue=" + Consts.QUEUE_TYPE_REV + " and due <= %d then (ivl / cast(%d-due+0.001 as real)) else 100000+due end)",
                        mToday, mToday);
                break;
            default:
                // if we don't understand the term, default to due order
                t = "c.due";
                break;
        }
        return t + " limit " + l;
    }


    protected void _moveToDyn(long did, List<Long> ids, int start) {
        JSONObject deck = mCol.getDecks().get(did);
        ArrayList<Object[]> data = new ArrayList<>();
        int u = mCol.usn();
        int due = start;
        for (Long id : ids) {
            data.add(new Object[] {
                did, due, u, id
            });
            due += 1;
        }
        String queue = "";
        if (!deck.getBoolean("resched")) {
            queue = ", queue = " + Consts.QUEUE_TYPE_REV + "";
        }

        mCol.getDb().executeMany(
                "UPDATE cards SET odid = did, " +
                        "odue = due, did = ?, due = (case when due <= 0 then due else ? end), usn = ? " + queue + " WHERE id = ?", data);
    }


    private void _removeFromFiltered(Card card) {
        if (card.getODid() != 0) {
            card.setDid(card.getODid());
            card.setODue(0);
            card.setODid(0);
        }
    }


    private void _restorePreviewCard(Card card) {
        if (card.getODid() == 0) {
            throw new RuntimeException("ODid wasn't set");
        }

        card.setDue(card.getODue());

        // learning and relearning cards may be seconds-based or day-based;
        // other types map directly to queues
        if (card.getType() == Consts.CARD_TYPE_LRN || card.getType() == Consts.CARD_TYPE_RELEARNING) {
            if (card.getODue() > 1000000000) {
                card.setQueue(Consts.QUEUE_TYPE_LRN);
            } else {
                card.setQueue(Consts.QUEUE_TYPE_DAY_LEARN_RELEARN);
            }
        } else {
            card.setQueue(card.getType());
        }
    }


    /**
     * Leeches ****************************************************************** *****************************
     */

    /** Leech handler. True if card was a leech. */
    protected boolean _checkLeech(Card card, JSONObject conf) {
        int lf;
        lf = conf.getInt("leechFails");
        if (lf == 0) {
            return false;
        }
        // if over threshold or every half threshold reps after that
        if (card.getLapses() >= lf && (card.getLapses() - lf) % Math.max(lf / 2, 1) == 0) {
            // add a leech tag
            Note n = card.note();
            n.addTag("leech");
            n.flush();
            // handle
            if (conf.getInt("leechAction") == Consts.LEECH_SUSPEND) {
                card.setQueue(Consts.QUEUE_TYPE_SUSPENDED);
            }
            // notify UI
            if (mContextReference != null) {
                Context context = mContextReference.get();
                Hooks.getInstance(context).runHook("leech", card, context);
            }
            return true;
        }
        return false;
    }


    /**
     * Tools ******************************************************************** ***************************
     */

    public JSONObject _cardConf(Card card) {
        return mCol.getDecks().confForDid(card.getDid());
    }


    protected JSONObject _newConf(Card card) {
        JSONObject conf = _cardConf(card);
        // normal deck
        if (card.getODid() == 0) {
            return conf.getJSONObject("new");
        }
        // dynamic deck; override some attributes, use original deck for others
        JSONObject oconf = mCol.getDecks().confForDid(card.getODid());
        JSONObject dict = new JSONObject();
        // original deck
        dict.put("ints", oconf.getJSONObject("new").getJSONArray("ints"));
        dict.put("initialFactor", oconf.getJSONObject("new").getInt("initialFactor"));
        dict.put("bury", oconf.getJSONObject("new").optBoolean("bury", true));
        dict.put("delays", oconf.getJSONObject("new").getJSONArray("delays"));
        // overrides
        dict.put("separate", conf.getBoolean("separate"));
        dict.put("order", Consts.NEW_CARDS_DUE);
        dict.put("perDay", mReportLimit);
        return dict;
    }


    protected JSONObject _lapseConf(Card card) {
        JSONObject conf = _cardConf(card);
        // normal deck
        if (card.getODid() == 0) {
            return conf.getJSONObject("lapse");
        }
        // dynamic deck; override some attributes, use original deck for others
        JSONObject oconf = mCol.getDecks().confForDid(card.getODid());
        JSONObject dict = new JSONObject();
        // original deck
        dict.put("minInt", oconf.getJSONObject("lapse").getInt("minInt"));
        dict.put("leechFails", oconf.getJSONObject("lapse").getInt("leechFails"));
        dict.put("leechAction", oconf.getJSONObject("lapse").getInt("leechAction"));
        dict.put("mult", oconf.getJSONObject("lapse").getDouble("mult"));
        dict.put("delays", oconf.getJSONObject("new").getJSONArray("delays"));
        // overrides
        dict.put("resched", conf.getBoolean("resched"));
        return dict;
    }


    protected JSONObject _revConf(Card card) {
        JSONObject conf = _cardConf(card);
        // normal deck
        if (card.getODid() == 0) {
            return conf.getJSONObject("rev");
        }
        // dynamic deck
        return mCol.getDecks().confForDid(card.getODid()).getJSONObject("rev");
    }


    public String _deckLimit() {
        return Utils.ids2str(mCol.getDecks().active());
    }


    private boolean _previewingCard(Card card) {
        JSONObject conf = _cardConf(card);

        return conf.getInt("dyn") != 0 && !conf.getBoolean("resched");
    }


    private int _previewDelay(Card card) {
        return _cardConf(card).optInt("previewDelay", 10) * 60;
    }


    /**
     * Daily cutoff ************************************************************* **********************************
     * This function uses GregorianCalendar so as to be sensitive to leap years, daylight savings, etc.
     */

    protected void _updateCutoff() {
        Integer oldToday = mToday == null ? 0 : mToday;
        // days since col created
        mToday = _daysSinceCreation();
        // end of day cutoff
        mDayCutoff = _dayCutoff();
        if (oldToday != mToday) {
            mCol.log(mToday, mDayCutoff);
        }
        // update all daily counts, but don't save decks to prevent needless conflicts. we'll save on card answer
        // instead
        for (JSONObject deck : mCol.getDecks().all()) {
            update(deck);
        }
        // unbury if the day has rolled over
        int unburied = mCol.getConf().optInt("lastUnburied", 0);
        if (unburied < mToday) {
            unburyCards();
            mCol.getConf().put("lastUnburied", mToday);
        }
    }


    private long _dayCutoff() {
        int rolloverTime = mCol.getConf().optInt("rollover", 4);
        if (rolloverTime < 0) {
            rolloverTime = 24 + rolloverTime;
        }
        Calendar date = Calendar.getInstance();
        date.setTime(new Date());
        date.set(Calendar.HOUR_OF_DAY, rolloverTime);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        Calendar today = Calendar.getInstance();
        today.setTime(new Date());
        if (date.before(today)) {
            date.add(Calendar.DAY_OF_MONTH, 1);
        }

        return date.getTimeInMillis() / 1000;
    }


    private int _daysSinceCreation() {
        Date startDate = new Date(mCol.getCrt() * 1000);
        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        c.set(Calendar.HOUR, mCol.getConf().optInt("rollover", 4));
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return (int) ((new Date().getTime() - c.getTimeInMillis()) / 1000) / 86400;
    }


    protected void update(JSONObject g) {
        for (String t : new String[] { "new", "rev", "lrn", "time" }) {
            String key = t + "Today";
            if (g.getJSONArray(key).getInt(0) != mToday) {
                JSONArray ja = new JSONArray();
                ja.put(mToday);
                ja.put(0);
                g.put(key, ja);
            }
        }
    }


    public void _checkDay() {
        // check if the day has rolled over
        if (Utils.now() > mDayCutoff) {
            reset();
        }
    }


    /**
     * Deck finished state ******************************************************
     * *****************************************
     */

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
                        new Object[] {mToday}) != 0;
    }


    /** true if there are any new cards due. */
    public boolean newDue() {
        return mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT 1") != 0;
    }


    public boolean haveBuriedSiblings() {
        return haveBuriedSiblings(mCol.getDecks().active());
    }


    private boolean haveBuriedSiblings(List<Long> allDecks) {
        // Refactored to allow querying an arbitrary deck
        String sdids = Utils.ids2str(allDecks);
        int cnt = mCol.getDb().queryScalar(
                "select 1 from cards where queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED + " and did in " + sdids + " limit 1");
        return cnt != 0;
    }


    public boolean haveManuallyBuried() {
        return haveManuallyBuried(mCol.getDecks().active());
    }


    private boolean haveManuallyBuried(List<Long> allDecks) {
        // Refactored to allow querying an arbitrary deck
        String sdids = Utils.ids2str(allDecks);
        int cnt = mCol.getDb().queryScalar(
                "select 1 from cards where queue = " + Consts.QUEUE_TYPE_MANUALLY_BURIED + " and did in " + sdids + " limit 1");
        return cnt != 0;
    }


    public boolean haveBuried() {
        return haveManuallyBuried() || haveBuriedSiblings();
    }


    /**
     * Next time reports ********************************************************
     * ***************************************
     */

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
        String s = Utils.timeQuantity(context, ivl);
        if (ivl < mCol.getConf().getInt("collapseTime")) {
            s = context.getString(R.string.less_than_time, s);
        }
        return s;
    }


    /**
     * Return the next interval for CARD, in seconds.
     */
    public long nextIvl(Card card, int ease) {
        // preview mode?
        if (_previewingCard(card)) {
            if (ease == Consts.BUTTON_ONE) {
                return _previewDelay(card);
            }
            return 0;
        }
        // (re)learning?
        if (card.getQueue() == Consts.QUEUE_TYPE_NEW || card.getQueue() == Consts.QUEUE_TYPE_LRN || card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            return _nextLrnIvl(card, ease);
        } else if (ease == Consts.BUTTON_ONE) {
            // lapse
            JSONObject conf = _lapseConf(card);
            if (conf.getJSONArray("delays").length() > 0) {
                return (long) (conf.getJSONArray("delays").getDouble(0) * 60.0);
            }
            return _lapseIvl(card, conf) * 86400L;
        } else {
            // review
            boolean early = card.getODid() != 0 && (card.getODue() > mToday);
            if (early) {
                return _earlyReviewIvl(card, ease) * 86400L;
            } else {
                return _nextRevIvl(card, ease, false) * 86400L;
            }
        }
    }


    // this isn't easily extracted from the learn code
    protected long _nextLrnIvl(Card card, int ease) {
        if (card.getQueue() == Consts.QUEUE_TYPE_NEW) {
            card.setLeft(_startingLeft(card));
        }
        JSONObject conf = _lrnConf(card);
        if (ease == Consts.BUTTON_ONE) {
            // fail
            return _delayForGrade(conf, conf.getJSONArray("delays").length());
        } else if (ease == Consts.BUTTON_TWO) {
            return _delayForRepeatingGrade(conf, card.getLeft());
        } else if (ease == Consts.BUTTON_FOUR) {
            return _graduatingIvl(card, conf, true, false) * 86400L;
        } else { // ease == 3
            int left = card.getLeft() % 1000 - 1;
            if (left <= 0) {
                // graduate
                return _graduatingIvl(card, conf, false, false) * 86400L;
            } else {
                return _delayForGrade(conf, left);
            }
        }
    }


    /**
     * Suspending & burying ********************************************************** ********************************
     */

    /**
     * learning and relearning cards may be seconds-based or day-based;
     * other types map directly to queues
     */
    private String _restoreQueueSnippet() {
        return "queue = (case when type in (" + Consts.CARD_TYPE_LRN + "," + Consts.CARD_TYPE_RELEARNING + ") then\n" +
                "  (case when (case when odue then odue else due end) > 1000000000 then 1 else " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " end)\n" +
                "else\n" +
                "  type\n" +
                "end)  ";
    }

    /**
     * Suspend cards.
     */
    public void suspendCards(long[] ids) {
        mCol.log(ids);
        mCol.getDb().execute(
                "UPDATE cards SET queue = " + Consts.QUEUE_TYPE_SUSPENDED + ", mod = ?, usn = ? WHERE id IN "
                        + Utils.ids2str(ids),
                new Object[] {Utils.intTime(), mCol.usn()});
    }


    /**
     * Unsuspend cards
     */
    public void unsuspendCards(long[] ids) {
        mCol.log(ids);
        mCol.getDb().execute(
                "UPDATE cards SET " + _restoreQueueSnippet() + ", mod = ?, usn = ?"
                        + " WHERE queue = " + Consts.QUEUE_TYPE_SUSPENDED + " AND id IN " + Utils.ids2str(ids),
                new Object[] {Utils.intTime(), mCol.usn()});
    }


    public void buryCards(long[] cids) {
        buryCards(cids, true);
    }


    public void buryCards(long[] cids, boolean manual) {
        int queue = manual ? Consts.QUEUE_TYPE_MANUALLY_BURIED : Consts.QUEUE_TYPE_SIBLING_BURIED;
        mCol.log(cids);
        mCol.getDb().execute("update cards set queue=?,mod=?,usn=? where id in " + Utils.ids2str(cids),
                new Object[]{queue, Utils.now(), mCol.usn()});
    }


    /**
     * Unbury all buried cards in all decks
     */
    public void unburyCards() {
        mCol.log(mCol.getDb().queryColumn( Long.class,"select id from cards where queue in (" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", " + Consts.QUEUE_TYPE_MANUALLY_BURIED + ")", 0));
        mCol.getDb().execute("update cards set " + _restoreQueueSnippet() + " where queue in (" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", " + Consts.QUEUE_TYPE_MANUALLY_BURIED + ")");
    }


    public void unburyCardsForDeck() {
        unburyCardsForDeck("all");
    }


    public void unburyCardsForDeck(String type) {
        unburyCardsForDeck(type, null);
    }

    public void unburyCardsForDeck(String type, List<Long> allDecks) {
        String queue;
        if ("all".equals(type)) {
            queue = "queue in (" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", " + Consts.QUEUE_TYPE_MANUALLY_BURIED + ")";
        } else if ("manual".equals(type)) {
            queue = "queue = " + Consts.QUEUE_TYPE_MANUALLY_BURIED;
        } else if ("siblings".equals(type)) {
            queue = "queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED;
        } else {
            throw new RuntimeException("unknown type");
        }

        String sids = Utils.ids2str(allDecks != null ? allDecks : mCol.getDecks().active());

        mCol.log(mCol.getDb().queryColumn(Long.class,"select id from cards where " + queue + " and did in " + sids, 0));
        mCol.getDb().execute("update cards set mod=?,usn=?, " + _restoreQueueSnippet() + " where " + queue + " and did in " + sids,
                new Object[]{Utils.intTime(), mCol.usn()});
    }


    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    public void buryNote(long nid) {
        long[] cids = Utils.arrayList2array(mCol.getDb().queryColumn(Long.class,
                "SELECT id FROM cards WHERE nid = ? AND queue >= " + Consts.CARD_TYPE_NEW, 0,
                new Object[] {nid}));
        buryCards(cids);
    }

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
                if (queue == Consts.QUEUE_TYPE_REV) {
                    if (buryRev) {
                        toBury.add(cid);
                    }
                    // if bury disabled, we still discard to give same-day spacing
                    mRevQueue.remove(cid);
                } else {
                    // if bury is disabled, we still discard to give same-day spacing
                    if (buryNew) {
                        toBury.add(cid);
                    }
                    mNewQueue.remove(cid);
                }
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // then bury
        if (!toBury.isEmpty()) {
            buryCards(Utils.arrayList2array(toBury),false);
        }
    }


    /**
     * Resetting **************************************************************** *******************************
     */

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
    public void reschedCards(long[] ids, int imin, int imax) {
        ArrayList<Object[]> d = new ArrayList<>();
        int t = mToday;
        long mod = Utils.intTime();
        Random rnd = new Random();
        for (long id : ids) {
            int r = rnd.nextInt(imax - imin + 1) + imin;
            d.add(new Object[] { Math.max(1, r), r + t, mCol.usn(), mod, Consts.STARTING_FACTOR, id });
        }
        remFromDyn(ids);
        mCol.getDb().executeMany(
                "update cards set type=" + Consts.CARD_TYPE_REV + ",queue=" + Consts.QUEUE_TYPE_REV + ",ivl=?,due=?,odue=0, " +
                        "usn=?,mod=?,factor=? where id=?", d);
        mCol.log(ids);
    }


    /**
     * Completely reset cards for export.
     */
    public void resetCards(Long[] ids) {
        long[] nonNew = Utils.arrayList2array(mCol.getDb().queryColumn(Long.class,
                "select id from cards where id in " + Utils.ids2str(ids) + " and (queue != " + Consts.QUEUE_TYPE_NEW + " or type != " + Consts.CARD_TYPE_NEW + ")", 0));
        mCol.getDb().execute("update cards set reps=0, lapses=0 where id in " + Utils.ids2str(nonNew));
        forgetCards(nonNew);
        mCol.log((Object[]) ids);
    }


    /**
     * Repositioning new cards **************************************************
     * *********************************************
     */

    public void sortCards(long[] cids, int start) {
        sortCards(cids, start, 1, false, false);
    }


    public void sortCards(long[] cids, int start, int step, boolean shuffle, boolean shift) {
        String scids = Utils.ids2str(cids);
        long now = Utils.intTime();
        ArrayList<Long> nids = new ArrayList<>();
        for (long id : cids) {
            long nid = mCol.getDb().queryLongScalar("SELECT nid FROM cards WHERE id = ?",
                                                    new Object[] {id});
            if (!nids.contains(nid)) {
                nids.add(nid);
            }
        }
        if (nids.isEmpty()) {
            // no new cards
            return;
        }
        // determine nid ordering
        HashMap<Long, Long> due = new HashMap<>();
        if (shuffle) {
            Collections.shuffle(nids);
        }
        for (int c = 0; c < nids.size(); c++) {
            due.put(nids.get(c), (long) (start + c * step));
        }
        int high = start + step * (nids.size() - 1);
        // shift?
        if (shift) {
            int low = mCol.getDb().queryScalar(
                    "SELECT min(due) FROM cards WHERE due >= ? AND type = " + Consts.CARD_TYPE_NEW + " AND id NOT IN " + scids,
                    new Object[] {start});
            if (low != 0) {
                int shiftby = high - low + 1;
                mCol.getDb().execute(
                        "UPDATE cards SET mod = ?, usn = ?, due = due + ?"
                                + " WHERE id NOT IN " + scids + " AND due >= ? AND queue = " + Consts.QUEUE_TYPE_NEW,
                        new Object[]{now, mCol.usn(), shiftby, low});
            }
        }
        // reorder cards
        ArrayList<Object[]> d = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase()
                    .query("SELECT id, nid FROM cards WHERE type = " + Consts.CARD_TYPE_NEW + " AND id IN " + scids, null);
            while (cur.moveToNext()) {
                long nid = cur.getLong(1);
                d.add(new Object[] { due.get(nid), now, mCol.usn(), cur.getLong(0) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        mCol.getDb().executeMany("UPDATE cards SET due = ?, mod = ?, usn = ? WHERE id = ?", d);
    }


    public void randomizeCards(long did) {
        List<Long> cids = mCol.getDb().queryColumn(Long.class, "select id from cards where did = ?", 0,
                                                   new Object[]{did});
        sortCards(Utils.toPrimitive(cids), 1, 1, true, false);
    }


    public void orderCards(long did) {
        List<Long> cids = mCol.getDb().queryColumn(Long.class, "SELECT id FROM cards WHERE did = ? ORDER BY nid", 0,
                                                   new Object[]{did});
        sortCards(Utils.toPrimitive(cids), 1, 1, false, false);
    }


    public void resortConf(JSONObject conf) {
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

    public void maybeRandomizeDeck(Long did) {
        if (did == null) {
            did = mCol.getDecks().selected();
        }
        JSONObject conf = mCol.getDecks().confForDid(did);
        // in order due?
        if (conf.getJSONObject("new").getInt("order") == Consts.NEW_CARDS_RANDOM) {
            randomizeCards(did);
        }
    }


    /**
     * Changing scheduler versions **************************************************
     * *********************************************
     */

    private void _emptyAllFiltered() {
        mCol.getDb().execute("update cards set did = odid, queue = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.QUEUE_TYPE_NEW + " when type = " + Consts.CARD_TYPE_RELEARNING + " then " + Consts.QUEUE_TYPE_REV + " else type end), type = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.CARD_TYPE_NEW + " when type = " + Consts.CARD_TYPE_RELEARNING + " then " + Consts.CARD_TYPE_REV + " else type end), due = odue, odue = 0, odid = 0, usn = ? where odid != 0",
                             new Object[]{mCol.usn()});
    }


    private void _removeAllFromLearning() {
        _removeAllFromLearning(2);
    }

    private void _removeAllFromLearning(int schedVer) {
        // remove review cards from relearning
        if (schedVer == 1) {
            mCol.getDb().execute("update cards set due = odue, queue = " + Consts.QUEUE_TYPE_REV + ", type = " + Consts.CARD_TYPE_REV + ", mod = ?, usn = ?, odue = 0 where queue in (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and type in (" + Consts.CARD_TYPE_REV + "," + Consts.CARD_TYPE_RELEARNING + ")",
                                 new Object[] {Utils.intTime(), mCol.usn()});
        } else {
            mCol.getDb().execute("update cards set due = ?+ivl, queue = " + Consts.QUEUE_TYPE_REV + ", type = " + Consts.CARD_TYPE_REV + ", mod = ?, usn = ?, odue = 0 where queue in (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and type in (" + Consts.CARD_TYPE_REV + "," + Consts.CARD_TYPE_RELEARNING + ")",
                                 new Object[] {mToday, Utils.intTime(), mCol.usn()});
        }


        // remove new cards from learning
        forgetCards(Utils.arrayList2array(mCol.getDb().queryColumn(Long.class, "select id from cards where queue in (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")", 0)));
    }


    // v1 doesn't support buried/suspended (re)learning cards
    private void _resetSuspendedLearning() {
        mCol.getDb().execute("update cards set type = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.CARD_TYPE_NEW + " when type in (" + Consts.CARD_TYPE_REV + ", " + Consts.CARD_TYPE_RELEARNING + ") then " + Consts.CARD_TYPE_REV + " else type end), due = (case when odue then odue else due end), odue = 0, mod = ?, usn = ? where queue < 0",
                             new Object[] {Utils.intTime(), mCol.usn()});
    }


    // no 'manually buried' queue in v1
    private void _moveManuallyBuried() {
        mCol.getDb().execute("update cards set queue=" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", mod=? where queue=" + Consts.QUEUE_TYPE_MANUALLY_BURIED,
                             new Object[] {Utils.intTime()});
    }

    // adding 'hard' in v2 scheduler means old ease entries need shifting
    // up or down
    private void _remapLearningAnswers(String sql) {
        mCol.getDb().execute("update revlog set " + sql + " and type in (" + Consts.REVLOG_LRN + ", " + Consts.REVLOG_RELRN + ")");
    }

    public void moveToV1() {
        _emptyAllFiltered();
        _removeAllFromLearning();

        _moveManuallyBuried();
        _resetSuspendedLearning();
        _remapLearningAnswers("ease=ease-1 where ease in (" + Consts.BUTTON_THREE + "," + Consts.BUTTON_FOUR + ")");
    }


    public void moveToV2() {
        _emptyAllFiltered();
        _removeAllFromLearning(1);
        _remapLearningAnswers("ease=ease+1 where ease in (" + Consts.BUTTON_TWO + "," + Consts.BUTTON_THREE + ")");
    }


    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    public boolean haveBuried(long did) {
        List<Long> all = new ArrayList<>(mCol.getDecks().children(did).values());
        all.add(did);
        return haveBuriedSiblings(all) || haveManuallyBuried(all);
    }

    public void unburyCardsForDeck(long did) {
        List<Long> all = new ArrayList<>(mCol.getDecks().children(did).values());
        all.add(did);
        unburyCardsForDeck("all", all);
    }


    public String getName() {
        return mName;
    }


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


    /**
     * Counts
     */

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
    public int eta(int[] counts, boolean reload) {
        double newRate;
        double newTime;
        double revRate;
        double revTime;
        double relrnRate;
        double relrnTime;

        if (reload || mEtaCache[0] == -1) {
            Cursor cur = null;
            try {
                cur = mCol
                        .getDb()
                        .getDatabase()
                        .query("select "
                                + "avg(case when type = " + Consts.CARD_TYPE_NEW + " then case when ease > 1 then 1.0 else 0.0 end else null end) as newRate, avg(case when type = " + Consts.CARD_TYPE_NEW + " then time else null end) as newTime, "
                                + "avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING + ") then case when ease > 1 then 1.0 else 0.0 end else null end) as revRate, avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING + ") then time else null end) as revTime, "
                                + "avg(case when type = " + Consts.CARD_TYPE_REV + " then case when ease > 1 then 1.0 else 0.0 end else null end) as relrnRate, avg(case when type = " + Consts.CARD_TYPE_REV + " then time else null end) as relrnTime "
                                + "from revlog where id > "
                                + "?",
                               new Object[] {(mCol.getSched().getDayCutoff() - (10 * 86400)) * 1000});
                if (!cur.moveToFirst()) {
                    return -1;
                }

                newRate = cur.getDouble(0);
                newTime = cur.getDouble(1);
                revRate = cur.getDouble(2);
                revTime = cur.getDouble(3);
                relrnRate = cur.getDouble(4);
                relrnTime = cur.getDouble(5);

                if (!cur.isClosed()) {
                    cur.close();
                }

            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }

            // If the collection has no revlog data to work with, assume a 20 second average rep for that type
            newTime = newTime == 0 ? 20000 : newTime;
            revTime = revTime == 0 ? 20000 : revTime;
            relrnTime = relrnTime == 0 ? 20000 : relrnTime;
            // And a 100% success rate
            newRate = newRate == 0 ? 1 : newRate;
            revRate = revRate == 0 ? 1 : revRate;
            relrnRate = relrnRate == 0 ? 1 : relrnRate;

            mEtaCache[0] = newRate;
            mEtaCache[1] = newTime;
            mEtaCache[2] = revRate;
            mEtaCache[3] = revTime;
            mEtaCache[4] = relrnRate;
            mEtaCache[5] = relrnTime;

        } else {
            newRate = mEtaCache[0];
            newTime = mEtaCache[1];
            revRate= mEtaCache[2];
            revTime = mEtaCache[3];
            relrnRate = mEtaCache[4];
            relrnTime = mEtaCache[5];
        }

        // Calculate the total time for each queue based on the historical average duration per rep
        double newTotal = newTime * counts[0];
        double relrnTotal = relrnTime * counts[1];
        double revTotal = revTime * counts[2];

        // Now we have to predict how many additional relrn cards are going to be generated while reviewing the above
        // queues, and how many relrn cards *those* reps will generate (and so on, until 0).

        // Every queue has a failure rate, and each failure will become a relrn
        int toRelrn = counts[0]; // Assume every new card becomes 1 relrn
        toRelrn += Math.ceil((1 - relrnRate) * counts[1]);
        toRelrn += Math.ceil((1 - revRate) * counts[2]);

        // Use the accuracy rate of the relrn queue to estimate how many reps we will end up with if the cards
        // currently in relrn continue to fail at that rate. Loop through the failures of the failures until we end up
        // with no predicted failures left.

        // Cap the lower end of the success rate to ensure the loop ends (it could be 0 if no revlog history, or
        // negative for other reasons). 5% seems reasonable to ensure the loop doesn't iterate too much.
        relrnRate = relrnRate < 0.05 ? 0.05 : relrnRate;
        int futureReps = 0;
        do {
            // Truncation ensures the failure rate always decreases
            int failures = (int) ((1 - relrnRate) * toRelrn);
            futureReps += failures;
            toRelrn = failures;
        } while (toRelrn > 1);
        double futureRelrnTotal = relrnTime * futureReps;

        return (int) Math.round((newTotal + relrnTotal + revTotal + futureRelrnTotal) / 60000);
    }


    public void decrementCounts(Card card) {
        switch (card.getQueue()) {
        case Consts.QUEUE_TYPE_NEW:
            mNewCount--;
            break;
        case Consts.QUEUE_TYPE_LRN:
            mLrnCount -= card.getLeft() / 1000;
            break;
        case Consts.QUEUE_TYPE_REV:
            mRevCount--;
            break;
        case Consts.QUEUE_TYPE_DAY_LEARN_RELEARN:
            mLrnCount--;
            break;
        }
    }


    /**
     * Sorts a card into the lrn queue LIBANKI: not in libanki
     */
    protected void _sortIntoLrn(long due, long id) {
        Iterator i = mLrnQueue.listIterator();
        int idx = 0;
        while (i.hasNext()) {
            if (((long[]) i.next())[0] > due) {
                break;
            } else {
                idx++;
            }
        }
        mLrnQueue.add(idx, new long[] { due, id });
    }


    public boolean leechActionSuspend(Card card) {
        JSONObject conf;
        conf = _cardConf(card).getJSONObject("lapse");
        return conf.getInt("leechAction") == Consts.LEECH_SUSPEND;
    }


    public void setContext(WeakReference<Activity> contextReference) {
        mContextReference = contextReference;
    }

    /** not in libAnki. Added due to #5666: inconsistent selected deck card counts on sync */
    @Override
    public int[] recalculateCounts() {
        _resetLrnCount();
        _resetNewCount();
        _resetRevCount();
        return new int[] { mNewCount, mLrnCount, mRevCount };
    }

    @Override
    public void setReportLimit(int reportLimit) {
        this.mReportLimit = reportLimit;
    }

    /** End #5666 */
}
