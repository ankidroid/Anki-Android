/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki;

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
import com.ichi2.libanki.hooks.Hooks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Random;

import timber.log.Timber;

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
                    "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.AvoidBranchingStatementAsLastInLoop",
                    "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt"})
public class Sched extends AbstractSched {



    // Not in libanki
    private static final int[] FACTOR_ADDITION_VALUES = { -150, 0, 150 };

    private String mName = "std";
    private boolean mHaveCustomStudy = true;
    private boolean mSpreadRev = true;

    private Collection mCol;
    private int mQueueLimit;
    private int mReportLimit;
    private int mReps;
    private boolean mHaveQueues;
    private int mToday;
    public long mDayCutoff;

    private int mNewCount;
    private int mLrnCount;
    private int mRevCount;

    private int mNewCardModulus;

    private double[] mEtaCache = new double[] { -1, -1, -1, -1, -1, -1 };

    // Queues
    private final LinkedList<Long> mNewQueue = new LinkedList<>();
    private final LinkedList<long[]> mLrnQueue = new LinkedList<>();
    private final LinkedList<Long> mLrnDayQueue = new LinkedList<>();
    private final LinkedList<Long> mRevQueue = new LinkedList<>();

    private LinkedList<Long> mNewDids;
    private LinkedList<Long> mLrnDids;
    private LinkedList<Long> mRevDids;

    // Not in libanki
    private WeakReference<Activity> mContextReference;

    /**
     * queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried
     * revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram
     * positive revlog intervals are in days (rev), negative in seconds (lrn)
     */

    public Sched(Collection col) {
        mCol = col;
        mQueueLimit = 50;
        mReportLimit = 99999;
        mReps = 0;
        mHaveQueues = false;
        _updateCutoff();
    }


    /**
     * Pop the next card from the queue. None if finished.
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
            return card;
        }
        return null;
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
        card.setReps(card.getReps() + 1);
        // former is for logging new cards, latter also covers filt. decks
        card.setWasNew((card.getType() == Consts.CARD_TYPE_NEW));
        boolean wasNewQ = (card.getQueue() == Consts.QUEUE_TYPE_NEW);
        if (wasNewQ) {
            // came from the new queue, move to learning
            card.setQueue(Consts.QUEUE_TYPE_LRN);
            // if it was a new card, it's now a learning card
            if (card.getType() == Consts.CARD_TYPE_NEW) {
                card.setType(Consts.QUEUE_TYPE_LRN);
            }
            // init reps to graduation
            card.setLeft(_startingLeft(card));
            // dynamic?
            if (card.getODid() != 0 && card.getType() == Consts.CARD_TYPE_REV) {
                if (_resched(card)) {
                    // reviews get their ivl boosted on first sight
                    card.setIvl(_dynIvlBoost(card));
                    card.setODue(mToday + card.getIvl());
                }
            }
            _updateStats(card, "new");
        }
        if (card.getQueue() == Consts.QUEUE_TYPE_LRN || card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            _answerLrnCard(card, ease);
            if (!wasNewQ) {
                _updateStats(card, "lrn");
            }
        } else if (card.getQueue() == Consts.QUEUE_TYPE_REV) {
            _answerRevCard(card, ease);
            _updateStats(card, "rev");
        } else {
            throw new RuntimeException("Invalid queue");
        }
        _updateStats(card, "time", card.timeTaken());
        card.setMod(Utils.intTime());
        card.setUsn(mCol.usn());
        card.flushSched();
    }


    public int[] counts() {
        return counts(null);
    }


    public int[] counts(Card card) {
        int[] counts = {mNewCount, mLrnCount, mRevCount};
        if (card != null) {
            int idx = countIdx(card);
            if (idx == 1) {
                counts[1] += card.getLeft() / 1000;
            } else {
                counts[idx] += 1;
            }
        }
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
        if (card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            return 1;
        }
        return card.getQueue();
    }


    public int answerButtons(Card card) {
        if (card.getODue() != 0) {
            // normal review in dyn deck?
            if (card.getODid() != 0 && card.getQueue() == Consts.QUEUE_TYPE_REV) {
                return 4;
            }
            JSONObject conf = _lrnConf(card);
            if (card.getType() == Consts.CARD_TYPE_NEW || card.getType() == Consts.CARD_TYPE_LRN || conf.getJSONArray("delays").length() > 1) {
                return 3;
            }
            return 2;
        } else if (card.getQueue() == Consts.QUEUE_TYPE_REV) {
            return 4;
        } else {
            return 3;
        }
    }


    /*
     * Unbury cards.
     */
    public void unburyCards() {
        mCol.getConf().put("lastUnburied", mToday);
        mCol.log(mCol.getDb().queryColumn(Long.class, "select id from cards where queue = "+ Consts.QUEUE_TYPE_SIBLING_BURIED , 0));
        mCol.getDb().execute("update cards set queue=type where queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED);
    }


    public void unburyCardsForDeck() {
        unburyCardsForDeck(mCol.getDecks().active());
    }

    private void unburyCardsForDeck(List<Long> allDecks) {
        // Refactored to allow unburying an arbitrary deck
        String sids = Utils.ids2str(allDecks);
        mCol.log(mCol.getDb().queryColumn(Long.class, "select id from cards where queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED + " and did in " + sids, 0));
        mCol.getDb().execute("update cards set mod=?,usn=?,queue=type where queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED + " and did in " + sids,
                new Object[] { Utils.intTime(), mCol.usn() });
    }

    /**
     * Rev/lrn/time daily stats *************************************************
     * **********************************************
     */

    private void _updateStats(Card card, String type) {
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


    private int _walkingCount(Method limFn, Method cntFn) {
        int tot = 0;
        HashMap<Long, Integer> pcounts = new HashMap<>();
        // for each of the active decks
        try {
            for (long did : mCol.getDecks().active()) {
                // get the individual deck's limit
                int lim = (Integer)limFn.invoke(Sched.this, mCol.getDecks().get(did));
                if (lim == 0) {
                    continue;
                }
                // check the parents
                List<JSONObject> parents = mCol.getDecks().parents(did);
                for (JSONObject p : parents) {
                    // add if missing
                    long id = p.getLong("id");
                    if (!pcounts.containsKey(id)) {
                        pcounts.put(id, (Integer)limFn.invoke(Sched.this, p));
                    }
                    // take minimum of child and parent
                    lim = Math.min(pcounts.get(id), lim);
                }
                // see how many cards we actually have
                int cnt = (Integer)cntFn.invoke(Sched.this, did, lim);
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
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return tot;
    }


    /**
     * Deck list **************************************************************** *******************************
     */


    /**
     * Returns [deckname, did, rev, lrn, new]
     */
    public List<DeckDueTreeNode> deckDueList() {
        _checkDay();
        mCol.getDecks().checkIntegrity();
        ArrayList<JSONObject> decks = mCol.getDecks().allSorted();
        HashMap<String, Integer[]> lims = new HashMap<>();
        ArrayList<DeckDueTreeNode> data = new ArrayList<>();
        for (JSONObject deck : decks) {
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
            int rlim = _deckRevLimitSingle(deck);
            if (!TextUtils.isEmpty(p)) {
                rlim = Math.min(rlim, lims.get(p)[1]);
            }
            int rev = _revForDeck(deck.getLong("id"), rlim);
            // save to list
            data.add(new DeckDueTreeNode(deck.getString("name"), deck.getLong("id"), rev, lrn, _new));
            // add deck as a parent
            lims.put(deck.getString("name"), new Integer[]{nlim, rlim});
        }
        return data;
    }


    public List<DeckDueTreeNode> deckDueTree() {
        return _groupChildren(deckDueList());
    }


    private List<DeckDueTreeNode> _groupChildren(List<DeckDueTreeNode> grps) {
        // first, split the group names into components
        for (DeckDueTreeNode g : grps) {
            g.names = g.names[0].split("::", -1);
        }
        // and sort based on those components
        Collections.sort(grps);
        // then run main function
        return _groupChildrenMain(grps);
    }


    private List<DeckDueTreeNode> _groupChildrenMain(List<DeckDueTreeNode> grps) {
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
                rev +=  ch.revCount;
                lrn +=  ch.lrnCount;
                _new += ch.newCount;
            }
            // limit the counts to the deck's limits
            JSONObject conf = mCol.getDecks().confForDid(did);
            JSONObject deck = mCol.getDecks().get(did);
            if (conf.getInt("dyn") == 0) {
                rev = Math.max(0, Math.min(rev, conf.getJSONObject("rev").getInt("perDay") - deck.getJSONArray("revToday").getInt(1)));
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
    private Card _getCard() {
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
        // Card due for review?
        c = _getRevCard();
        if (c != null) {
            return c;
        }
        // day learning card due?
        c = _getLrnDayCard();
        if (c != null) {
            return c;
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

    private void _resetNewCount() {
        try {
            mNewCount = _walkingCount(Sched.class.getDeclaredMethod("_deckNewLimitSingle", JSONObject.class),
                    Sched.class.getDeclaredMethod("_cntFnNew", long.class, int.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    // Used as an argument for _walkingCount() in _resetNewCount() above
    @SuppressWarnings("unused")
    private int _cntFnNew(long did, int lim) {
        return mCol.getDb().queryScalar(
                "SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT " + lim + ")");
    }


    private void _resetNew() {
        _resetNewCount();
        mNewDids = new LinkedList<>(mCol.getDecks().active());
        mNewQueue.clear();
        _updateNewCardRatio();
    }


    private boolean _fillNew() {
        if (mNewQueue.size() > 0) {
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
                            .query("SELECT id FROM cards WHERE did = " + did + " AND queue = " + Consts.QUEUE_TYPE_NEW + " order by due, ord LIMIT " + lim,
                                    null);
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


    private Card _getNewCard() {
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
    private boolean _timeForNewCard() {
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


    private int _deckNewLimit(long did) {
        return _deckNewLimit(did, null);
    }


    private int _deckNewLimit(long did, Method fn) {
        try {
            if (fn == null) {
                fn = Sched.class.getDeclaredMethod("_deckNewLimitSingle", JSONObject.class);
            }
            List<JSONObject> decks = mCol.getDecks().parents(did);
            decks.add(mCol.getDecks().get(did));
            int lim = -1;
            // for the deck and each of its parents
            int rem = 0;
            for (JSONObject g : decks) {
                rem = (Integer) fn.invoke(Sched.this, g);
                if (lim == -1) {
                    lim = rem;
                } else {
                    lim = Math.min(rem, lim);
                }
            }
            return lim;
        } catch (IllegalArgumentException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    /* New count for a single deck. */
    public int _newForDeck(long did, int lim) {
    	if (lim == 0) {
    		return 0;
    	}
    	lim = Math.min(lim, mReportLimit);
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT " + lim + ")");
    }


    /* Limit for deck without parent limits. */
    public int _deckNewLimitSingle(JSONObject g) {
        if (g.getInt("dyn") != 0) {
            return mReportLimit;
        }
        JSONObject c = mCol.getDecks().confForDid(g.getLong("id"));
        return Math.max(0, c.getJSONObject("new").getInt("perDay") - g.getJSONArray("newToday").getInt(1));
    }

    public int totalNewForCurrentDeck() {
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + Utils.ids2str(mCol.getDecks().active()) + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT " + mReportLimit + ")");
    }

    /**
     * Learning queues *********************************************************** ************************************
     */

    private void _resetLrnCount() {
        // sub-day
        mLrnCount = mCol.getDb().queryScalar(
                "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did IN " + _deckLimit()
                + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < " + mDayCutoff + " LIMIT " + mReportLimit + ")");

        // day
        mLrnCount += mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= " + mToday
                        + " LIMIT " + mReportLimit);
    }


    private void _resetLrn() {
        _resetLrnCount();
        mLrnQueue.clear();
        mLrnDayQueue.clear();
        mLrnDids = mCol.getDecks().active();
    }


    // sub-day learning
    private boolean _fillLrn() {
        if (mLrnCount == 0) {
            return false;
        }
        if (!mLrnQueue.isEmpty()) {
            return true;
        }
        Cursor cur = null;
        mLrnQueue.clear();
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .query(
                            "SELECT due, id FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < "
                                    + mDayCutoff + " LIMIT " + mReportLimit, null);
            while (cur.moveToNext()) {
                mLrnQueue.add(new long[] { cur.getLong(0), cur.getLong(1) });
            }
            // as it arrives sorted by did first, we need to sort it
            Collections.sort(mLrnQueue, new Comparator<long[]>() {
                @Override
                public int compare(long[] lhs, long[] rhs) {
                    return Long.valueOf(lhs[0]).compareTo(rhs[0]);
                }
            });
            return !mLrnQueue.isEmpty();
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    private Card _getLrnCard() {
        return _getLrnCard(false);
    }


    private Card _getLrnCard(boolean collapse) {
        if (_fillLrn()) {
            double cutoff = Utils.now();
            if (collapse) {
                cutoff += mCol.getConf().getInt("collapseTime");
            }
            if (mLrnQueue.getFirst()[0] < cutoff) {
                long id = mLrnQueue.remove()[1];
                Card card = mCol.getCard(id);
                mLrnCount -= card.getLeft() / 1000;
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
        while (mLrnDids.size() > 0) {
            long did = mLrnDids.getFirst();
            // fill the queue with the current did
            mLrnDayQueue.clear();
            Cursor cur = null;
            try {
                cur = mCol
                        .getDb()
                        .getDatabase()
                        .query(
                                "SELECT id FROM cards WHERE did = " + did + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= " + mToday
                                        + " LIMIT " + mQueueLimit, null);
                while (cur.moveToNext()) {
                    mLrnDayQueue.add(cur.getLong(0));
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            if (mLrnDayQueue.size() > 0) {
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


    private Card _getLrnDayCard() {
        if (_fillLrnDay()) {
            mLrnCount -= 1;
            return mCol.getCard(mLrnDayQueue.remove());
        }
        return null;
    }


    /**
     * @param ease 1=no, 2=yes, 3=remove
     */
    private void _answerLrnCard(Card card, int ease) {
        JSONObject conf = _lrnConf(card);
        int type;
        if (card.getODid() != 0 && !card.getWasNew()) {
            type = Consts.CARD_TYPE_RELEARNING;
        } else if (card.getType() == Consts.CARD_TYPE_REV) {
            type = Consts.CARD_TYPE_REV;
        } else {
            type = Consts.CARD_TYPE_NEW;
        }
        boolean leaving = false;
        // lrnCount was decremented once when card was fetched
        int lastLeft = card.getLeft();
        // immediate graduate?
        if (ease == Consts.BUTTON_THREE) {
            _rescheduleAsRev(card, conf, true);
            leaving = true;
            // graduation time?
        } else if (ease == Consts.BUTTON_TWO && (card.getLeft() % 1000) - 1 <= 0) {
            _rescheduleAsRev(card, conf, false);
            leaving = true;
        } else {
            // one step towards graduation
            if (ease == Consts.BUTTON_TWO) {
                // decrement real left count and recalculate left today
                int left = (card.getLeft() % 1000) - 1;
                card.setLeft(_leftToday(conf.getJSONArray("delays"), left) * 1000 + left);
                // failed
            } else {
                card.setLeft(_startingLeft(card));
                boolean resched = _resched(card);
                if (conf.has("mult") && resched) {
                    // review that's lapsed
                    card.setIvl(Math.max(Math.max(1, (int) (card.getIvl() * conf.getDouble("mult"))), conf.getInt("minInt")));
                } else {
                    // new card; no ivl adjustment
                    // pass
                }
                if (resched && card.getODid() != 0) {
                    card.setODue(mToday + 1);
                }
            }
            int delay = _delayForGrade(conf, card.getLeft());
            if (card.getDue() < Utils.now()) {
                // not collapsed; add some randomness
                delay *= Utils.randomFloatInRange(1f, 1.25f);
            }
            card.setDue((int) (Utils.now() + delay));

            // due today?
            if (card.getDue() < mDayCutoff) {
                mLrnCount += card.getLeft() / 1000;
                // if the queue is not empty and there's nothing else to do, make
                // sure we don't put it at the head of the queue and end up showing
                // it twice in a row
                card.setQueue(Consts.QUEUE_TYPE_LRN);
                if (!mLrnQueue.isEmpty() && mRevCount == 0 && mNewCount == 0) {
                    long smallestDue = mLrnQueue.getFirst()[0];
                    card.setDue(Math.max(card.getDue(), smallestDue + 1));
                }
                _sortIntoLrn(card.getDue(), card.getId());
            } else {
                // the card is due in one or more days, so we need to use the day learn queue
                long ahead = ((card.getDue() - mDayCutoff) / 86400) + 1;
                card.setDue(mToday + ahead);
                card.setQueue(Consts.QUEUE_TYPE_DAY_LEARN_RELEARN);
            }
        }
        _logLrn(card, ease, conf, leaving, type, lastLeft);
    }


    private int _delayForGrade(JSONObject conf, int left) {
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


    private JSONObject _lrnConf(Card card) {
        if (card.getType() == Consts.CARD_TYPE_REV) {
            return _lapseConf(card);
        } else {
            return _newConf(card);
        }
    }


    private void _rescheduleAsRev(Card card, JSONObject conf, boolean early) {
        boolean lapse = (card.getType() == Consts.CARD_TYPE_REV);
        if (lapse) {
            if (_resched(card)) {
                card.setDue(Math.max(mToday + 1, card.getODue()));
            } else {
                card.setDue(card.getODue());
            }
            card.setODue(0);
        } else {
            _rescheduleNew(card, conf, early);
        }
        card.setQueue(Consts.QUEUE_TYPE_REV);
        card.setType(Consts.CARD_TYPE_REV);
        // if we were dynamic, graduating means moving back to the old deck
        boolean resched = _resched(card);
        if (card.getODid() != 0) {
            card.setDid(card.getODid());
            card.setODue(0);
            card.setODid(0);
            // if rescheduling is off, it needs to be set back to a new card
            if (!resched && !lapse) {
                card.setType(Consts.CARD_TYPE_NEW);
                card.setQueue(Consts.QUEUE_TYPE_NEW);
                card.setDue(mCol.nextID("pos"));
            }
        }
    }


    private int _startingLeft(Card card) {
        JSONObject conf;
    	if (card.getType() == Consts.CARD_TYPE_REV) {
    		conf = _lapseConf(card);
    	} else {
    		conf = _lrnConf(card);
    	}
        int tot = conf.getJSONArray("delays").length();
        int tod = _leftToday(conf.getJSONArray("delays"), tot);
        return tot + tod * 1000;
    }


    /* the number of steps that can be completed by the day cutoff */
    private int _leftToday(JSONArray delays, int left) {
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


    private int _graduatingIvl(Card card, JSONObject conf, boolean early, boolean adj) {
        if (card.getType() == Consts.CARD_TYPE_REV) {
            // lapsed card being relearnt
            if (card.getODid() != 0) {
                if (conf.getBoolean("resched")) {
                    return _dynIvlBoost(card);
                }
            }
            return card.getIvl();
        }
        int ideal;
        JSONArray ja;
        ja = conf.getJSONArray("ints");
        if (!early) {
            // graduate
            ideal = ja.getInt(0);
        } else {
            ideal = ja.getInt(1);
        }
        if (adj) {
            return _adjRevIvl(card, ideal);
        } else {
            return ideal;
        }
    }


    /* Reschedule a new card that's graduated for the first time. */
    private void _rescheduleNew(Card card, JSONObject conf, boolean early) {
        card.setIvl(_graduatingIvl(card, conf, early));
        card.setDue(mToday + card.getIvl());
        card.setFactor(conf.getInt("initialFactor"));
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


    private void removeLrn() {
    	removeLrn(null);
    }

    /* Remove cards from the learning queues. */
    private void removeLrn(long[] ids) {
        String extra;
        if (ids != null && ids.length > 0) {
            extra = " AND id IN " + Utils.ids2str(ids);
        } else {
            // benchmarks indicate it's about 10x faster to search all decks with the index than scan the table
            extra = " AND did IN " + Utils.ids2str(mCol.getDecks().allIds());
        }
        // review cards in relearning
        mCol.getDb().execute(
                "update cards set due = odue, queue = " + Consts.QUEUE_TYPE_REV + ", mod = " + Utils.intTime() +
                ", usn = " + mCol.usn() + ", odue = 0 where queue IN (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and type = " + Consts.CARD_TYPE_REV + " " + extra);
        // new cards in learning
        forgetCards(Utils.arrayList2array(mCol.getDb().queryColumn(Long.class, "SELECT id FROM cards WHERE queue IN (" + Consts.QUEUE_TYPE_LRN + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") " + extra, 0)));
    }


    private int _lrnForDeck(long did) {
        try {
            int cnt = mCol.getDb().queryScalar(
                    "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did = " + did
                            + " AND queue = " + Consts.QUEUE_TYPE_LRN + " AND due < " + (Utils.intTime() + mCol.getConf().getInt("collapseTime"))
                            + " LIMIT " + mReportLimit + ")");
            return cnt + mCol.getDb().queryScalar(
                    "SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did
                            + " AND queue = " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + " AND due <= " + mToday
                            + " LIMIT " + mReportLimit + ")");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Reviews ****************************************************************** *****************************
     */

    private int _deckRevLimit(long did) {
        try {
            return _deckNewLimit(did, Sched.class.getDeclaredMethod("_deckRevLimitSingle", JSONObject.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    private int _deckRevLimitSingle(JSONObject d) {
        if (d.getInt("dyn") != 0) {
            return mReportLimit;
        }
        JSONObject c = mCol.getDecks().confForDid(d.getLong("id"));
        return Math.max(0, c.getJSONObject("rev").getInt("perDay") - d.getJSONArray("revToday").getInt(1));
    }


    private int _revForDeck(long did, int lim) {
    	lim = Math.min(lim, mReportLimit);
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= " + mToday + " LIMIT " + lim + ")");
    }


    private void _resetRevCount() {
        try {
            mRevCount = _walkingCount(Sched.class.getDeclaredMethod("_deckRevLimitSingle", JSONObject.class),
                    Sched.class.getDeclaredMethod("_cntFnRev", long.class, int.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    // Dynamically invoked in _walkingCount, passed as a parameter in _resetRevCount
    @SuppressWarnings("unused")
    private int _cntFnRev(long did, int lim) {
        return mCol.getDb().queryScalar(
                "SELECT count() FROM (SELECT id FROM cards WHERE did = " + did + " AND queue = " + Consts.QUEUE_TYPE_REV + " and due <= " + mToday
                        + " LIMIT " + lim + ")");
    }


    private void _resetRev() {
        _resetRevCount();
        mRevQueue.clear();
        mRevDids = mCol.getDecks().active();
    }


    private boolean _fillRev() {
        if (!mRevQueue.isEmpty()) {
            return true;
        }
        if (mRevCount == 0) {
            return false;
        }
        while (mRevDids.size() > 0) {
            long did = mRevDids.getFirst();
            int lim = Math.min(mQueueLimit, _deckRevLimit(did));
            Cursor cur = null;
            if (lim != 0) {
                mRevQueue.clear();
                // fill the queue with the current did
                try {
                    cur = mCol
                            .getDb()
                            .getDatabase()
                            .query(
                                    "SELECT id FROM cards WHERE did = " + did + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= " + mToday
                                            + " LIMIT " + lim, null);
                    while (cur.moveToNext()) {
                        mRevQueue.add(cur.getLong(0));
                    }
                } finally {
                    if (cur != null && !cur.isClosed()) {
                        cur.close();
                    }
                }
                if (!mRevQueue.isEmpty()) {
                    // ordering
                    if (mCol.getDecks().get(did).getInt("dyn") != 0) {
                        // dynamic decks need due order preserved
                        // Note: libanki reverses mRevQueue and returns the last element in _getRevCard().
                        // AnkiDroid differs by leaving the queue intact and returning the *first* element
                        // in _getRevCard().
                    } else {
                        Random r = new Random();
                        r.setSeed(mToday);
                        Collections.shuffle(mRevQueue, r);
                    }
                    // is the current did empty?
                    if (mRevQueue.size() < lim) {
                        mRevDids.remove();
                    }
                    return true;
                }
            }
            // nothing left in the deck; move to next
            mRevDids.remove();
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


    private Card _getRevCard() {
        if (_fillRev()) {
            mRevCount -= 1;
            return mCol.getCard(mRevQueue.remove());
        } else {
            return null;
        }
    }


    public int totalRevForCurrentDeck() {
        return mCol.getDb().queryScalar(String.format(Locale.US,
        		"SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN %s AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= %d LIMIT %s)",
        		Utils.ids2str(mCol.getDecks().active()), mToday, mReportLimit));
    }


    /**
     * Answering a review card **************************************************
     * *********************************************
     */

    private void _answerRevCard(Card card, int ease) {
        int delay = 0;
        if (ease == Consts.BUTTON_ONE) {
            delay = _rescheduleLapse(card);
        } else {
            _rescheduleRev(card, ease);
        }
        _logRev(card, ease, delay);
    }


    private int _rescheduleLapse(Card card) {
        JSONObject conf;
        conf = _lapseConf(card);
        card.setLastIvl(card.getIvl());
        if (_resched(card)) {
            card.setLapses(card.getLapses() + 1);
            card.setIvl(_nextLapseIvl(card, conf));
            card.setFactor(Math.max(1300, card.getFactor() - 200));
            card.setDue(mToday + card.getIvl());
            // if it's a filtered deck, update odue as well
            if (card.getODid() != 0) {
                card.setODue(card.getDue());
            }
        }
        // if suspended as a leech, nothing to do
        int delay = 0;
        if (_checkLeech(card, conf) && card.getQueue() == Consts.QUEUE_TYPE_SUSPENDED) {
            return delay;
        }
        // if no relearning steps, nothing to do
        if (conf.getJSONArray("delays").length() == 0) {
            return delay;
        }
        // record rev due date for later
        if (card.getODue() == 0) {
            card.setODue(card.getDue());
        }
        delay = _delayForGrade(conf, 0);
        card.setDue((long) (delay + Utils.now()));
        card.setLeft(_startingLeft(card));
        // queue 1
        if (card.getDue() < mDayCutoff) {
            mLrnCount += card.getLeft() / 1000;
            card.setQueue(Consts.QUEUE_TYPE_LRN);
            _sortIntoLrn(card.getDue(), card.getId());
        } else {
            // day learn queue
            long ahead = ((card.getDue() - mDayCutoff) / 86400) + 1;
            card.setDue(mToday + ahead);
            card.setQueue(Consts.QUEUE_TYPE_DAY_LEARN_RELEARN);
        }
        return delay;
    }


    private int _nextLapseIvl(Card card, JSONObject conf) {
        return Math.max(conf.getInt("minInt"), (int)(card.getIvl() * conf.getDouble("mult")));
    }


    private void _rescheduleRev(Card card, int ease) {
        // update interval
        card.setLastIvl(card.getIvl());
        if (_resched(card)) {
            _updateRevIvl(card, ease);
            // then the rest
            card.setFactor(Math.max(1300, card.getFactor() + FACTOR_ADDITION_VALUES[ease - 2]));
            card.setDue(mToday + card.getIvl());
        } else {
            card.setDue(card.getODue());
        }
        if (card.getODid() != 0) {
            card.setDid(card.getODid());
            card.setODid(0);
            card.setODue(0);
        }
    }


    private void _logRev(Card card, int ease, int delay) {
        log(card.getId(), mCol.usn(), ease, ((delay != 0) ? (-delay) : card.getIvl()), card.getLastIvl(),
                card.getFactor(), card.timeTaken(), 1);
    }


    /**
     * Interval management ******************************************************
     * *****************************************
     */

    /**
     * Ideal next interval for CARD, given EASE.
     */
    private int _nextRevIvl(Card card, int ease) {
        long delay = _daysLate(card);
        int interval = 0;
        JSONObject conf = _revConf(card);
        double fct = card.getFactor() / 1000.0;
        int ivl2 = _constrainedIvl((int)((card.getIvl() + delay/4) * 1.2), conf, card.getIvl());
        int ivl3 = _constrainedIvl((int)((card.getIvl() + delay/2) * fct), conf, ivl2);
        int ivl4 = _constrainedIvl((int)((card.getIvl() + delay) * fct * conf.getDouble("ease4")), conf, ivl3);
        if (ease == Consts.BUTTON_TWO) {
            interval = ivl2;
        } else if (ease == Consts.BUTTON_THREE) {
            interval = ivl3;
        } else if (ease == Consts.BUTTON_FOUR) {
            interval = ivl4;
        }
        // interval capped?
        return Math.min(interval, conf.getInt("maxIvl"));
    }

    private int _fuzzedIvl(int ivl) {
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


    /** Integer interval after interval factor and prev+1 constraints applied */
    private int _constrainedIvl(int ivl, JSONObject conf, double prev) {
    	double newIvl = ivl;
    	newIvl = ivl * conf.optDouble("ivlFct",1.0);
        return (int) Math.max(newIvl, prev + 1);
    }


    /**
     * Number of days later than scheduled.
     */
    private long _daysLate(Card card) {
        long due = card.getODid() != 0 ? card.getODue() : card.getDue();
        return Math.max(0, mToday - due);
    }


    private void _updateRevIvl(Card card, int ease) {
        try {
            int idealIvl = _nextRevIvl(card, ease);
            JSONObject conf = _revConf(card);
            card.setIvl(Math.min(
                    Math.max(_adjRevIvl(card, idealIvl), card.getIvl() + 1),
                    conf.getInt("maxIvl")));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    @SuppressWarnings("PMD.UnusedFormalParameter") // it's unused upstream as well
    private int _adjRevIvl(Card card, int idealIvl) {
        if (mSpreadRev) {
            idealIvl = _fuzzedIvl(idealIvl);
        }
        return idealIvl;
    }


    /**
     * Dynamic deck handling ******************************************************************
     * *****************************
     */

    /* Rebuild a dynamic deck. */
    public void rebuildDyn() {
        rebuildDyn(0);
    }


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
        List<Long> ids = _fillDyn(deck);
        if (ids.isEmpty()) {
            return null;
        }
        // and change to our new deck
        mCol.getDecks().select(did);
        return ids;
    }


    private List<Long> _fillDyn(JSONObject deck) {
        JSONArray terms;
        List<Long> ids;
        terms = deck.getJSONArray("terms").getJSONArray(0);
        String search = terms.getString(0);
        int limit = terms.getInt(1);
        int order = terms.getInt(2);
        String orderlimit = _dynOrder(order, limit);
        if (!TextUtils.isEmpty(search.trim())) {
            search = String.format(Locale.US, "(%s)", search);
        }
        search = String.format(Locale.US, "%s -is:suspended -is:buried -deck:filtered", search);
        ids = mCol.findCards(search, orderlimit);
        if (ids.isEmpty()) {
            return ids;
        }
        // move the cards over
        mCol.log(deck.getLong("id"), ids);
        _moveToDyn(deck.getLong("id"), ids);
        return ids;
    }


    public void emptyDyn(long did) {
        emptyDyn(did, null);
    }


    public void emptyDyn(long did, String lim) {
        if (lim == null) {
            lim = "did = " + did;
        }
        mCol.log(mCol.getDb().queryColumn(Long.class, "select id from cards where " + lim, 0));
        // move out of cram queue
        mCol.getDb().execute(
                "update cards set did = odid, queue = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.QUEUE_TYPE_NEW + " " +
                "else type end), type = (case when type = " + Consts.CARD_TYPE_LRN + " then " + Consts.CARD_TYPE_NEW + " else type end), " +
                "due = odue, odue = 0, odid = 0, usn = ? where " + lim,
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
    private String _dynOrder(int o, int l) {
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


    private void _moveToDyn(long did, List<Long> ids) {
        ArrayList<Object[]> data = new ArrayList<>();
        //long t = Utils.intTime(); // unused variable present (and unused) upstream
        int u = mCol.usn();
        for (long c = 0; c < ids.size(); c++) {
            // start at -100000 so that reviews are all due
            data.add(new Object[] { did, -100000 + c, u, ids.get((int) c) });
        }
        // due reviews stay in the review queue. careful: can't use "odid or did", as sqlite converts to boolean
        String queue = "(CASE WHEN type = " + Consts.CARD_TYPE_REV + " AND (CASE WHEN odue THEN odue <= " + mToday +
                " ELSE due <= " + mToday + " END) THEN " + Consts.QUEUE_TYPE_REV + " ELSE " + Consts.QUEUE_TYPE_NEW + " END)";
        mCol.getDb().executeMany(
                "UPDATE cards SET odid = (CASE WHEN odid THEN odid ELSE did END), " +
                        "odue = (CASE WHEN odue THEN odue ELSE due END), did = ?, queue = " +
                        queue + ", due = ?, usn = ? WHERE id = ?", data);
    }


    private int _dynIvlBoost(Card card) {
        if (card.getODid() == 0 || card.getType() != Consts.CARD_TYPE_REV || card.getFactor() == 0) {
            Timber.e("error: deck is not a filtered deck");
            return 0;
        }
        long elapsed = card.getIvl() - (card.getODue() - mToday);
        double factor = ((card.getFactor() / 1000.0) + 1.2) / 2.0;
        int ivl = Math.max(1, Math.max(card.getIvl(), (int) (elapsed * factor)));
        JSONObject conf = _revConf(card);
        return Math.min(conf.getInt("maxIvl"), ivl);
    }


    /**
     * Leeches ****************************************************************** *****************************
     */

    /** Leech handler. True if card was a leech. */
    private boolean _checkLeech(Card card, JSONObject conf) {
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
                // if it has an old due, remove it from cram/relearning
                if (card.getODue() != 0) {
                    card.setDue(card.getODue());
                }
                if (card.getODid() != 0) {
                    card.setDid(card.getODid());
                }
                card.setODue(0);
                card.setODid(0);
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


    private JSONObject _newConf(Card card) {
        JSONObject conf = _cardConf(card);
        // normal deck
        if (card.getODid() == 0) {
            return conf.getJSONObject("new");
        }
        // dynamic deck; override some attributes, use original deck for others
        JSONObject oconf = mCol.getDecks().confForDid(card.getODid());
        JSONArray delays = conf.optJSONArray("delays");
        if (delays == null) {
            delays = oconf.getJSONObject("new").getJSONArray("delays");
        }
        JSONObject dict = new JSONObject();
        // original deck
        dict.put("ints", oconf.getJSONObject("new").getJSONArray("ints"));
        dict.put("initialFactor", oconf.getJSONObject("new").getInt("initialFactor"));
        dict.put("bury", oconf.getJSONObject("new").optBoolean("bury", true));
        // overrides
        dict.put("delays", delays);
        dict.put("separate", conf.getBoolean("separate"));
        dict.put("order", Consts.NEW_CARDS_DUE);
        dict.put("perDay", mReportLimit);
        return dict;
    }


    private JSONObject _lapseConf(Card card) {
        JSONObject conf = _cardConf(card);
        // normal deck
        if (card.getODid() == 0) {
            return conf.getJSONObject("lapse");
        }
        // dynamic deck; override some attributes, use original deck for others
        JSONObject oconf = mCol.getDecks().confForDid(card.getODid());
        JSONArray delays = conf.optJSONArray("delays");
        if (delays == null) {
            delays = oconf.getJSONObject("lapse").getJSONArray("delays");
        }
        JSONObject dict = new JSONObject();
        // original deck
        dict.put("minInt", oconf.getJSONObject("lapse").getInt("minInt"));
        dict.put("leechFails", oconf.getJSONObject("lapse").getInt("leechFails"));
        dict.put("leechAction", oconf.getJSONObject("lapse").getInt("leechAction"));
        dict.put("mult", oconf.getJSONObject("lapse").getDouble("mult"));
        // overrides
        dict.put("delays", delays);
        dict.put("resched", conf.getBoolean("resched"));
        return dict;
    }


    private JSONObject _revConf(Card card) {
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


    private boolean _resched(Card card) {
        JSONObject conf = _cardConf(card);
        if (conf.getInt("dyn") == 0) {
            return true;
        }
        return conf.getBoolean("resched");
    }


    /**
     * Daily cutoff ************************************************************* **********************************
     * This function uses GregorianCalendar so as to be sensitive to leap years, daylight savings, etc.
     */

    private void _updateCutoff() {
        int oldToday = mToday;
        // days since col created
        mToday = (int) ((Utils.now() - mCol.getCrt()) / 86400);
        // end of day cutoff
        mDayCutoff = mCol.getCrt() + ((mToday + 1) * 86400);
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
        }
    }


    private void update(JSONObject g) {
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
                        "SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_REV + " AND due <= " + mToday
                                + " LIMIT 1") != 0;
    }


    /** true if there are any new cards due. */
    public boolean newDue() {
        return mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = " + Consts.QUEUE_TYPE_NEW + " LIMIT 1") != 0;
    }


    public boolean haveBuried() {
        return haveBuried(mCol.getDecks().active());
    }

    private boolean haveBuried(List<Long> allDecks) {
        // Refactored to allow querying an arbitrary deck
        String sdids = Utils.ids2str(allDecks);
        int cnt = mCol.getDb().queryScalar(String.format(Locale.US,
                "select 1 from cards where queue = " + Consts.QUEUE_TYPE_SIBLING_BURIED + " and did in %s limit 1", sdids));
        return cnt != 0;
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
        if (card.getQueue() == Consts.QUEUE_TYPE_NEW || card.getQueue() == Consts.QUEUE_TYPE_LRN || card.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN) {
            return _nextLrnIvl(card, ease);
        } else if (ease == Consts.BUTTON_ONE) {
            // lapsed
            JSONObject conf = _lapseConf(card);
            if (conf.getJSONArray("delays").length() > 0) {
                return (long) (conf.getJSONArray("delays").getDouble(0) * 60.0);
            }
            return _nextLapseIvl(card, conf) * 86400L;
        } else {
            // review
            return _nextRevIvl(card, ease) * 86400L;
        }
    }


    private long _nextLrnIvl(Card card, int ease) {
        // this isn't easily extracted from the learn code
        if (card.getQueue() == Consts.QUEUE_TYPE_NEW) {
            card.setLeft(_startingLeft(card));
        }
        JSONObject conf = _lrnConf(card);
        if (ease == Consts.BUTTON_ONE) {
            // fail
            return _delayForGrade(conf, conf.getJSONArray("delays").length());
        } else if (ease == Consts.BUTTON_THREE) {
            // early removal
            if (!_resched(card)) {
                return 0;
            }
            return _graduatingIvl(card, conf, true, false) * 86400L;
        } else {
            int left = card.getLeft() % 1000 - 1;
            if (left <= 0) {
                // graduate
                if (!_resched(card)) {
                    return 0;
                }
                return _graduatingIvl(card, conf, false, false) * 86400L;
            } else {
                return _delayForGrade(conf, left);
            }
        }
    }


    /**
     * Suspending *************************************************************** ********************************
     */

    /**
     * Suspend cards.
     */
    public void suspendCards(long[] ids) {
        mCol.log(ids);
        remFromDyn(ids);
        removeLrn(ids);
        mCol.getDb().execute(
                "UPDATE cards SET queue = " + Consts.QUEUE_TYPE_SUSPENDED + ", mod = " + Utils.intTime() + ", usn = " + mCol.usn() + " WHERE id IN "
                        + Utils.ids2str(ids));
    }


    /**
     * Unsuspend cards
     */
    public void unsuspendCards(long[] ids) {
        mCol.log(ids);
        mCol.getDb().execute(
                "UPDATE cards SET queue = type, mod = " + Utils.intTime() + ", usn = " + mCol.usn()
                        + " WHERE queue = " + Consts.QUEUE_TYPE_SUSPENDED + " AND id IN " + Utils.ids2str(ids));
    }


    public void buryCards(long[] cids) {
        mCol.log(cids);
        remFromDyn(cids);
        removeLrn(cids);
        mCol.getDb().execute("update cards set queue=" + Consts.QUEUE_TYPE_SIBLING_BURIED + ",mod=?,usn=? where id in " + Utils.ids2str(cids),
                new Object[]{Utils.now(), mCol.usn()});
    }


    /**
     * Bury all cards for note until next session.
     * @param nid The id of the targeted note.
     */
    public void buryNote(long nid) {
        long[] cids = Utils.arrayList2array(mCol.getDb().queryColumn(Long.class,
                "SELECT id FROM cards WHERE nid = " + nid + " AND queue >= 0", 0));
        buryCards(cids);
    }

    /**
     * Sibling spacing
     * ********************
     */

    private void _burySiblings(Card card) {
        LinkedList<Long> toBury = new LinkedList<>();
        JSONObject nconf = _newConf(card);
        boolean buryNew = nconf.optBoolean("bury", true);
        JSONObject rconf = _revConf(card);
        boolean buryRev = rconf.optBoolean("bury", true);
        // loop through and remove from queues
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().query(String.format(Locale.US,
                    "select id, queue from cards where nid=%d and id!=%d "+
                    "and (queue=" + Consts.QUEUE_TYPE_NEW + " or (queue=" + Consts.QUEUE_TYPE_REV + " and due<=%d))", card.getNid(), card.getId(), mToday), null);
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
        if (toBury.size() > 0) {
            mCol.getDb().execute("update cards set queue=" + Consts.QUEUE_TYPE_SIBLING_BURIED + ",mod=?,usn=? where id in " + Utils.ids2str(toBury),
                    new Object[] { Utils.now(), mCol.usn() });
            mCol.log(toBury);
        }
    }


    /**
     * Resetting **************************************************************** *******************************
     */

    /** Put cards at the end of the new queue. */
    public void forgetCards(long[] ids) {
        remFromDyn(ids);
        mCol.getDb().execute("update cards set type=" + Consts.CARD_TYPE_NEW + ",queue=" + Consts.QUEUE_TYPE_NEW + ",ivl=0,due=0,odue=0,factor=" + Consts.STARTING_FACTOR +
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
        long[] nonNew = Utils.arrayList2array(mCol.getDb().queryColumn(Long.class, String.format(Locale.US,
                "select id from cards where id in %s and (queue != " + Consts.QUEUE_TYPE_NEW + " or type != " + Consts.CARD_TYPE_NEW + ")", Utils.ids2str(ids)), 0));
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
        	long nid = mCol.getDb().queryLongScalar("SELECT nid FROM cards WHERE id = " + id);
        	if (!nids.contains(nid)) {
        		nids.add(nid);
        	}
        }
        if (nids.size() == 0) {
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
                    "SELECT min(due) FROM cards WHERE due >= " + start + " AND type = " + Consts.CARD_TYPE_NEW + " AND id NOT IN " + scids);
            if (low != 0) {
                int shiftby = high - low + 1;
                mCol.getDb().execute(
                        "UPDATE cards SET mod = " + now + ", usn = " + mCol.usn() + ", due = due + " + shiftby
                                + " WHERE id NOT IN " + scids + " AND due >= " + low + " AND queue = " + Consts.QUEUE_TYPE_NEW);
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
        List<Long> cids = mCol.getDb().queryColumn(Long.class, "select id from cards where did = " + did, 0);
        sortCards(Utils.toPrimitive(cids), 1, 1, true, false);
    }


    public void orderCards(long did) {
        List<Long> cids = mCol.getDb().queryColumn(Long.class, "SELECT id FROM cards WHERE did = " + did + " ORDER BY nid", 0);
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


    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    public boolean haveBuried(long did) {
        List<Long> all = new ArrayList<>(mCol.getDecks().children(did).values());
        all.add(did);
        return haveBuried(all);
    }

    public void unburyCardsForDeck(long did) {
        List<Long> all = new ArrayList<>(mCol.getDecks().children(did).values());
        all.add(did);
        unburyCardsForDeck(all);
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
                                + "avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING+ ") then case when ease > 1 then 1.0 else 0.0 end else null end) as revRate, avg(case when type in (" + Consts.CARD_TYPE_LRN + ", " + Consts.CARD_TYPE_RELEARNING + ") then time else null end) as revTime, "
                                + "avg(case when type = " + Consts.CARD_TYPE_REV + " then case when ease > 1 then 1.0 else 0.0 end else null end) as relrnRate, avg(case when type = " + Consts.CARD_TYPE_REV + " then time else null end) as relrnTime "
                                + "from revlog where id > "
                                + ((mCol.getSched().getDayCutoff() - (10 * 86400)) * 1000), null);
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
        int type = card.getQueue();
        switch (type) {
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
    private void _sortIntoLrn(long due, long id) {
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

    /** not in libAnki. Added due to #5666: inconsistent selected deck card counts on sync */
    public int[] recalculateCounts() {
        _resetLrnCount();
        _resetNewCount();
        _resetRevCount();
        return new int[] { mNewCount, mLrnCount, mRevCount };
    }

    public void setReportLimit(int reportLimit) {
        this.mReportLimit = reportLimit;
    }

    /** End #5666 */


    public void setContext(WeakReference<Activity> contextReference) {
        mContextReference = contextReference;
    }
}
