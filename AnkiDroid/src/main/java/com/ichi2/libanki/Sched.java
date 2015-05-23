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
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.libanki.hooks.Hooks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.Map;
import java.util.Random;

import timber.log.Timber;

public class Sched {



    // Not in libanki
    private static final int[] FACTOR_ADDITION_VALUES = { -150, 0, 150 };

    private String mName = "std";
    private boolean mHaveCustomStudy = true;
    private boolean mSpreadRev = true;
    private boolean mBurySiblingsOnAnswer = true;

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

    private double[] mEtaCache = new double[] { -1, -1, -1, -1 };

    // Queues
    private final LinkedList<Long> mNewQueue = new LinkedList<Long>();
    private final LinkedList<long[]> mLrnQueue = new LinkedList<long[]>();
    private final LinkedList<Long> mLrnDayQueue = new LinkedList<Long>();
    private final LinkedList<Long> mRevQueue = new LinkedList<Long>();

    private LinkedList<Long> mNewDids;
    private LinkedList<Long> mLrnDids;
    private LinkedList<Long> mRevDids;

    // Not in libanki
    private HashMap<Long, Pair<String[], long[]>> mCachedDeckCounts;
    private WeakReference<Activity> mContextReference;

    /**
     * queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried
     * revlog types: 0=lrn, 1=rev, 2=relrn, 3=cram
     * positive revlog intervals are in days (rev), negative in seconds (lrn)
     */

    public Sched(Collection col) {
        mCol = col;
        mQueueLimit = 50;
        mReportLimit = 1000;
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
            if (!mBurySiblingsOnAnswer) {
                _burySiblings(card);
            }
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
        if (mBurySiblingsOnAnswer) {
            _burySiblings(card);
        }
        card.setReps(card.getReps() + 1);
        // former is for logging new cards, latter also covers filt. decks
        card.setWasNew((card.getType() == 0));
        boolean wasNewQ = (card.getQueue() == 0);
        if (wasNewQ) {
            // came from the new queue, move to learning
            card.setQueue(1);
            // if it was a new card, it's now a learning card
            if (card.getType() == 0) {
                card.setType(1);
            }
            // init reps to graduation
            card.setLeft(_startingLeft(card));
            // dynamic?
            if (card.getODid() != 0 && card.getType() == 2) {
                if (_resched(card)) {
                    // reviews get their ivl boosted on first sight
                    card.setIvl(_dynIvlBoost(card));
                    card.setODue(mToday + card.getIvl());
                }
            }
            _updateStats(card, "new");
        }
        if (card.getQueue() == 1 || card.getQueue() == 3) {
            _answerLrnCard(card, ease);
            if (!wasNewQ) {
                _updateStats(card, "lrn");
            }
        } else if (card.getQueue() == 2) {
            _answerRevCard(card, ease);
            _updateStats(card, "rev");
        } else {
            throw new RuntimeException("Invalid queue");
        }
        _updateStats(card, "time", card.timeTaken());
        card.setMod(Utils.intNow());
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
        if (card.getQueue() == 3) {
            return 1;
        }
        return card.getQueue();
    }


    public int answerButtons(Card card) {
        if (card.getODue() != 0) {
            // normal review in dyn deck?
            if (card.getODid() != 0 && card.getQueue() == 2) {
                return 4;
            }
            JSONObject conf = _lrnConf(card);
            try {
                if (card.getType() == 0 || card.getType() == 1 || conf.getJSONArray("delays").length() > 1) {
                    return 3;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return 2;
        } else if (card.getQueue() == 2) {
            return 4;
        } else {
            return 3;
        }
    }


    /*
     * Unbury cards.
     */
    public void unburyCards() {
        try {
            mCol.getConf().put("lastUnburied", mToday);
            mCol.log(mCol.getDb().queryColumn(Long.class, "select id from cards where queue = -2", 0));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        mCol.getDb().execute("update cards set queue=type where queue = -2");
    }


    public void unburyCardsForDeck() {
        String sids = Utils.ids2str(mCol.getDecks().active());
        mCol.log(mCol.getDb().queryColumn(Long.class, "select id from cards where queue = -2 and did in " + sids, 0));
        mCol.getDb().execute("update cards set mod=?,usn=?,queue=type where queue = -2 and did in " + sids,
                new Object[] { Utils.intNow(), mCol.usn() });
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
        ArrayList<JSONObject> list = mCol.getDecks().parents(did);
        list.add(mCol.getDecks().get(did));
        for (JSONObject g : list) {
            try {
                JSONArray a = g.getJSONArray(key);
                // add
                a.put(1, a.getLong(1) + cnt);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            mCol.getDecks().save(g);
        }
    }


    public void extendLimits(int newc, int rev) {
        JSONObject cur = mCol.getDecks().current();
        ArrayList<JSONObject> decks = new ArrayList<JSONObject>();
        decks.add(cur);
        try {
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private int _walkingCount(Method limFn, Method cntFn) {
        int tot = 0;
        HashMap<Long, Integer> pcounts = new HashMap<Long, Integer>();
        // for each of the active decks
        try {
            for (long did : mCol.getDecks().active()) {
                // get the individual deck's limit
                int lim = (Integer)limFn.invoke(Sched.this, mCol.getDecks().get(did));
                if (lim == 0) {
                    continue;
                }
                // check the parents
                ArrayList<JSONObject> parents = mCol.getDecks().parents(did);
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
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
        mCol.getDecks().recoverOrphans();
        ArrayList<JSONObject> decks = mCol.getDecks().allSorted();
        HashMap<String, Integer[]> lims = new HashMap<String, Integer[]>();
        ArrayList<DeckDueTreeNode> data = new ArrayList<DeckDueTreeNode>();
        try {
            for (JSONObject deck : decks) {
                // if we've already seen the exact same deck name, remove the
                // invalid duplicate and reload
                if (lims.containsKey(deck.getString("name"))) {
                    mCol.getDecks().rem(deck.getLong("id"), false, true);
                    return deckDueList();
                }
                String p;
                List<String> parts = Arrays.asList(deck.getString("name").split("::"));
                if (parts.size() < 2) {
                    p = null;
                } else {
                    parts = parts.subList(0, parts.size() - 1);
                    p = TextUtils.join("::", parts);
                }
                // new
                int nlim = _deckNewLimitSingle(deck);
                if (!TextUtils.isEmpty(p)) {
                    if (!lims.containsKey(p)) {
                        // if parent was missing, this deck is invalid, and we need to reload the deck list
                        mCol.getDecks().rem(deck.getLong("id"), false, true);
                        return deckDueList();
                    }
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return data;
    }


    public List<DeckDueTreeNode> deckDueTree() {
        return _groupChildren(deckDueList());
    }


    private List<DeckDueTreeNode> _groupChildren(List<DeckDueTreeNode> grps) {
        // first, split the group names into components
        for (DeckDueTreeNode g : grps) {
            g.names = g.names[0].split("::");
        }
        // and sort based on those components
        Collections.sort(grps);
        // then run main function
        return _groupChildrenMain(grps);
    }


    private List<DeckDueTreeNode> _groupChildrenMain(List<DeckDueTreeNode> grps) {
        List<DeckDueTreeNode> tree = new ArrayList<DeckDueTreeNode>();
        // group and recurse
        ListIterator<DeckDueTreeNode> it = grps.listIterator();
        while (it.hasNext()) {
            DeckDueTreeNode node = it.next();
            String head = node.names[0];
            // Compose the "tail" node list. The tail is a list of all the nodes that proceed
            // the current one that contain the same name[0]. I.e., they are subdecks that stem
            // from this node. This is our version of python's itertools.groupby.
            List<DeckDueTreeNode> tail  = new ArrayList<DeckDueTreeNode>();
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
            List<DeckDueTreeNode> children = new ArrayList<DeckDueTreeNode>();
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
            try {
                if (conf.getInt("dyn") == 0) {
                    rev = Math.max(0, Math.min(rev, conf.getJSONObject("rev").getInt("perDay") - deck.getJSONArray("revToday").getInt(1)));
                    _new = Math.max(0, Math.min(_new, conf.getJSONObject("new").getInt("perDay") - deck.getJSONArray("newToday").getInt(1)));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
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
                "SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = 0 LIMIT " + lim + ")");
    }


    private void _resetNew() {
        _resetNewCount();
        mNewDids = new LinkedList<Long>(mCol.getDecks().active());
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
                            .rawQuery("SELECT id FROM cards WHERE did = " + did + " AND queue = 0 order by due LIMIT " + lim,
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
        try {
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * @return True if it's time to display a new card when distributing.
     */
    private boolean _timeForNewCard() {
        if (mNewCount == 0) {
            return false;
        }
        int spread;
        try {
            spread = mCol.getConf().getInt("newSpread");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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
            ArrayList<JSONObject> decks = mCol.getDecks().parents(did);
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
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    /* New count for a single deck. */
    public int _newForDeck(long did, int lim) {
    	if (lim == 0) {
    		return 0;
    	}
    	lim = Math.min(lim, mReportLimit);
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = 0 LIMIT " + lim + ")");
    }


    /* Limit for deck without parent limits. */
    public int _deckNewLimitSingle(JSONObject g) {
        try {
            if (g.getInt("dyn") != 0) {
                return mReportLimit;
            }
            JSONObject c = mCol.getDecks().confForDid(g.getLong("id"));
            return Math.max(0, c.getJSONObject("new").getInt("perDay") - g.getJSONArray("newToday").getInt(1));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public int totalNewForCurrentDeck() {
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + Utils.ids2str(mCol.getDecks().active()) + " AND queue = 0 LIMIT " + mReportLimit + ")");
    }

    /**
     * Learning queues *********************************************************** ************************************
     */

    private void _resetLrnCount() {
        // sub-day
        mLrnCount = mCol.getDb().queryScalar(
                "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did IN " + _deckLimit()
                + " AND queue = 1 AND due < " + mDayCutoff + " LIMIT " + mReportLimit + ")");

        // day
        mLrnCount += mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = 3 AND due <= " + mToday
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
                    .rawQuery(
                            "SELECT due, id FROM cards WHERE did IN " + _deckLimit() + " AND queue = 1 AND due < "
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
                try {
                    cutoff += mCol.getConf().getInt("collapseTime");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
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
                        .rawQuery(
                                "SELECT id FROM cards WHERE did = " + did + " AND queue = 3 AND due <= " + mToday
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
            type = 3;
        } else if (card.getType() == 2) {
            type = 2;
        } else {
            type = 0;
        }
        boolean leaving = false;
        // lrnCount was decremented once when card was fetched
        int lastLeft = card.getLeft();
        // immediate graduate?
        if (ease == 3) {
            _rescheduleAsRev(card, conf, true);
            leaving = true;
            // graduation time?
        } else if (ease == 2 && (card.getLeft() % 1000) - 1 <= 0) {
            _rescheduleAsRev(card, conf, false);
            leaving = true;
        } else {
            // one step towards graduation
            if (ease == 2) {
                // decrement real left count and recalculate left today
                int left = (card.getLeft() % 1000) - 1;
                try {
                    card.setLeft(_leftToday(conf.getJSONArray("delays"), left) * 1000 + left);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                // failed
            } else {
                card.setLeft(_startingLeft(card));
                boolean resched = _resched(card);
                if (conf.has("mult") && resched) {
                    // review that's lapsed
                    try {
                        card.setIvl(Math.max(Math.max(1, (int) (card.getIvl() * conf.getDouble("mult"))), conf.getInt("minInt")));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
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
                delay *= (1 + (new Random().nextInt(25) / 100));
            }
            // TODO: check, if type for second due is correct
            card.setDue((int) (Utils.now() + delay));

            // due today?
            if (card.getDue() < mDayCutoff) {
                mLrnCount += card.getLeft() / 1000;
                // if the queue is not empty and there's nothing else to do, make
                // sure we don't put it at the head of the queue and end up showing
                // it twice in a row
                card.setQueue(1);
                if (!mLrnQueue.isEmpty() && mRevCount == 0 && mNewCount == 0) {
                    long smallestDue = mLrnQueue.getFirst()[0];
                    card.setDue(Math.max(card.getDue(), smallestDue + 1));
                }
                _sortIntoLrn(card.getDue(), card.getId());
            } else {
                // the card is due in one or more days, so we need to use the day learn queue
                long ahead = ((card.getDue() - mDayCutoff) / 86400) + 1;
                card.setDue(mToday + ahead);
                card.setQueue(3);
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
        if (card.getType() == 2) {
            return _lapseConf(card);
        } else {
            return _newConf(card);
        }
    }


    private void _rescheduleAsRev(Card card, JSONObject conf, boolean early) {
        boolean lapse = (card.getType() == 2);
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
        card.setQueue(2);
        card.setType(2);
        // if we were dynamic, graduating means moving back to the old deck
        boolean resched = _resched(card);
        if (card.getODid() != 0) {
            card.setDid(card.getODid());
            card.setODue(0);
            card.setODid(0);
            // if rescheduling is off, it needs to be set back to a new card
            if (!resched && !lapse) {
                card.setType(0);
                card.setQueue(card.getType());
                card.setDue(mCol.nextID("pos"));
            }
        }
    }


    private int _startingLeft(Card card) {
        try {
            JSONObject conf;
        	if (card.getType() == 2) {
        		conf = _lapseConf(card);
        	} else {
        		conf = _lrnConf(card);
        	}
            int tot = conf.getJSONArray("delays").length();
            int tod = _leftToday(conf.getJSONArray("delays"), tot);
            return tot + tod * 1000;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /* the number of steps that can be completed by the day cutoff */
    private int _leftToday(JSONArray delays, int left) {
        return _leftToday(delays, left, 0);
    }


    private int _leftToday(JSONArray delays, int left, long now) {
        if (now == 0) {
            now = Utils.intNow();
        }
        int ok = 0;
        int offset = Math.min(left, delays.length());
        for (int i = 0; i < offset; i++) {
            try {
                now += (int) (delays.getDouble(delays.length() - offset + i) * 60.0);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
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
        if (card.getType() == 2) {
            // lapsed card being relearnt
            if (card.getODid() != 0) {
                try {
                    if (conf.getBoolean("resched")) {
                        return _dynIvlBoost(card);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            return card.getIvl();
        }
        int ideal;
        JSONArray ja;
        try {
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /* Reschedule a new card that's graduated for the first time. */
    private void _rescheduleNew(Card card, JSONObject conf, boolean early) {
        card.setIvl(_graduatingIvl(card, conf, early));
        card.setDue(mToday + card.getIvl());
        try {
            card.setFactor(conf.getInt("initialFactor"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _logLrn(Card card, int ease, JSONObject conf, boolean leaving, int type, int lastLeft) {
        int lastIvl = -(_delayForGrade(conf, lastLeft));
        int ivl = leaving ? card.getIvl() : -(_delayForGrade(conf, card.getLeft()));
        log(card.getId(), mCol.usn(), ease, ivl, lastIvl, card.getFactor(), card.timeTaken(), type);
    }


    private void log(long id, int usn, int ease, int ivl, int lastIvl, int factor, long timeTaken, int type) {
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


    public void removeLrn() {
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
                "update cards set due = odue, queue = 2, mod = " + Utils.intNow() +
                ", usn = " + mCol.usn() + ", odue = 0 where queue IN (1,3) and type = 2 " + extra);
        // new cards in learning
        forgetCards(Utils.arrayList2array(mCol.getDb().queryColumn(Long.class, "SELECT id FROM cards WHERE queue IN (1,3) " + extra, 0)));
    }


    private int _lrnForDeck(long did) {
        try {
            int cnt = mCol.getDb().queryScalar(
                    "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did = " + did
                            + " AND queue = 1 AND due < " + (Utils.intNow() + mCol.getConf().getInt("collapseTime"))
                            + " LIMIT " + mReportLimit + ")");
            return cnt + mCol.getDb().queryScalar(
                    "SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did
                            + " AND queue = 3 AND due <= " + mToday
                            + " LIMIT " + mReportLimit + ")");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
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
        try {
            if (d.getInt("dyn") != 0) {
                return mReportLimit;
            }
            JSONObject c = mCol.getDecks().confForDid(d.getLong("id"));
            return Math.max(0, c.getJSONObject("rev").getInt("perDay") - d.getJSONArray("revToday").getInt(1));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public int _revForDeck(long did, int lim) {
    	lim = Math.min(lim, mReportLimit);
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = 2 AND due <= " + mToday + " LIMIT " + lim + ")");
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
                "SELECT count() FROM (SELECT id FROM cards WHERE did = " + did + " AND queue = 2 and due <= " + mToday
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
                            .rawQuery(
                                    "SELECT id FROM cards WHERE did = " + did + " AND queue = 2 AND due <= " + mToday
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
                    try {
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
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
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
        		"SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN %s AND queue = 2 AND due <= %d LIMIT %s)",
        		Utils.ids2str(mCol.getDecks().active()), mToday, mReportLimit));
    }


    /**
     * Answering a review card **************************************************
     * *********************************************
     */

    private void _answerRevCard(Card card, int ease) {
        int delay = 0;
        if (ease == 1) {
            delay = _rescheduleLapse(card);
        } else {
            _rescheduleRev(card, ease);
        }
        _logRev(card, ease, delay);
    }


    private int _rescheduleLapse(Card card) {
        JSONObject conf;
        try {
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
            if (_checkLeech(card, conf) && card.getQueue() == -1) {
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
                card.setQueue(1);
                _sortIntoLrn(card.getDue(), card.getId());
            } else {
                // day learn queue
                long ahead = ((card.getDue() - mDayCutoff) / 86400) + 1;
                card.setDue(mToday + ahead);
                card.setQueue(3);
            }
            return delay;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private int _nextLapseIvl(Card card, JSONObject conf) {
        try {
            return Math.max(conf.getInt("minInt"), (int)(card.getIvl() * conf.getDouble("mult")));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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
        try {
            long delay = _daysLate(card);
            int interval = 0;
            JSONObject conf = _revConf(card);
            double fct = card.getFactor() / 1000.0;
            int ivl2 = _constrainedIvl((int)((card.getIvl() + delay/4) * 1.2), conf, card.getIvl());
            int ivl3 = _constrainedIvl((int)((card.getIvl() + delay/2) * fct), conf, ivl2);
            int ivl4 = _constrainedIvl((int)((card.getIvl() + delay) * fct * conf.getDouble("ease4")), conf, ivl3);
            if (ease == 2) {
                interval = ivl2;
            } else if (ease == 3) {
                interval = ivl3;
            } else if (ease == 4) {
            	interval = ivl4;
            }
            // interval capped?
            return Math.min(interval, conf.getInt("maxIvl"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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
        int idealIvl = _nextRevIvl(card, ease);
        card.setIvl(_adjRevIvl(card, idealIvl));
    }


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
        try {
            if (deck.getInt("dyn") == 0) {
                Timber.e("error: deck is not a filtered deck");
                return null;
            }
        } catch (JSONException e1) {
            throw new RuntimeException(e1);
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
        try {
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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
                "update cards set did = odid, queue = (case when type = 1 then 0 " +
                "else type end), type = (case when type = 1 then 0 else type end), " +
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
                        "(case when queue=2 and due <= %d then (ivl / cast(%d-due+0.001 as real)) else 100000+due end)",
                        mToday, mToday);
                break;
            default:
            	// if we don't understand the term, default to due order
            	t = "c.due";
        }
        return t + " limit " + l;
    }


    private void _moveToDyn(long did, List<Long> ids) {
        ArrayList<Object[]> data = new ArrayList<Object[]>();
        long t = Utils.intNow();
        int u = mCol.usn();
        for (long c = 0; c < ids.size(); c++) {
            // start at -100000 so that reviews are all due
            data.add(new Object[] { did, -100000 + c, u, ids.get((int) c) });
        }
        // due reviews stay in the review queue. careful: can't use "odid or did", as sqlite converts to boolean
        String queue = "(CASE WHEN type = 2 AND (CASE WHEN odue THEN odue <= " + mToday +
                " ELSE due <= " + mToday + " END) THEN 2 ELSE 0 END)";
        mCol.getDb().executeMany(
                "UPDATE cards SET odid = (CASE WHEN odid THEN odid ELSE did END), " +
                        "odue = (CASE WHEN odue THEN odue ELSE due END), did = ?, queue = " +
                        queue + ", due = ?, usn = ? WHERE id = ?", data);
    }


    private int _dynIvlBoost(Card card) {
        if (card.getODid() == 0 || card.getType() != 2 || card.getFactor() == 0) {
            Timber.e("error: deck is not a filtered deck");
            return 0;
        }
        long elapsed = card.getIvl() - (card.getODue() - mToday);
        double factor = ((card.getFactor() / 1000.0) + 1.2) / 2.0;
        int ivl = Math.max(1, Math.max(card.getIvl(), (int) (elapsed * factor)));
        JSONObject conf = _revConf(card);
        try {
            return Math.min(conf.getInt("maxIvl"), ivl);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Leeches ****************************************************************** *****************************
     */

    /** Leech handler. True if card was a leech. */
    private boolean _checkLeech(Card card, JSONObject conf) {
        int lf;
        try {
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
                if (conf.getInt("leechAction") == 0) {
                    // if it has an old due, remove it from cram/relearning
                    if (card.getODue() != 0) {
                        card.setDue(card.getODue());
                    }
                    if (card.getODid() != 0) {
                        card.setDid(card.getODid());
                    }
                    card.setODue(0);
                    card.setODid(0);
                    card.setQueue(-1);
                }
                // notify UI
                if (mContextReference != null) {
                    Context context = mContextReference.get();
                    Hooks.getInstance(context).runHook("leech", card, context);
                }
                return true;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
        try {
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private JSONObject _lapseConf(Card card) {
        try {
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private JSONObject _revConf(Card card) {
        try {
            JSONObject conf = _cardConf(card);
            // normal deck
            if (card.getODid() == 0) {
                return conf.getJSONObject("rev");
            }
            // dynamic deck
            return mCol.getDecks().confForDid(card.getODid()).getJSONObject("rev");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public String _deckLimit() {
        return Utils.ids2str(mCol.getDecks().active());
    }


    private boolean _resched(Card card) {
        JSONObject conf = _cardConf(card);
        try {
            if (conf.getInt("dyn") == 0) {
                return true;
            }
            return conf.getBoolean("resched");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Daily cutoff ************************************************************* **********************************
     * This function uses GregorianCalendar so as to be sensitive to leap years, daylight savings, etc.
     */

    public void _updateCutoff() {
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
            try {
                if (g.getJSONArray(key).getInt(0) != mToday) {
                    JSONArray ja = new JSONArray();
                    ja.put(mToday);
                    ja.put(0);
                    g.put(key, ja);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
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
                now = " " + context.getString(R.string.sched_unbury_button);
            } else {
                now = "";
            }
            sb.append("\n\n");
            sb.append("" + context.getString(R.string.sched_has_buried) + now);
        }
        try {
            if (mHaveCustomStudy && mCol.getDecks().current().getInt("dyn") == 0) {
                sb.append("\n\n");
                sb.append(context.getString(R.string.studyoptions_congrats_custom));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }


    /** true if there are any rev cards due. */
    public boolean revDue() {
        return mCol.getDb()
                .queryScalar(
                        "SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = 2 AND due <= " + mToday
                                + " LIMIT 1") != 0;
    }


    /** true if there are any new cards due. */
    public boolean newDue() {
        return mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = 0 LIMIT 1") != 0;
    }


    public boolean haveBuried() {
        String sdids = Utils.ids2str(mCol.getDecks().active());
        int cnt = mCol.getDb().queryScalar(String.format(Locale.US,
                "select 1 from cards where queue = -2 and did in %s limit 1", sdids));
        return cnt != 0;
    }


    /**
     * Next time reports ********************************************************
     * ***************************************
     */

    /**
     * Return the next interval for CARD as a string.
     */
    public String nextIvlStr(Card card, int ease) {
        return nextIvlStr(card, ease, false);
    }


    public String nextIvlStr(Card card, int ease, boolean _short) {
        int ivl = nextIvl(card, ease);
        if (ivl == 0) {
            return AnkiDroidApp.getAppResources().getString(R.string.sched_end);
        }
        String s = Utils.fmtTimeSpan(ivl, _short);
        try {
            if (ivl < mCol.getConf().getInt("collapseTime")) {
                s = "<" + s;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return s;
    }


    /**
     * Return the next interval for CARD, in seconds.
     */
    public int nextIvl(Card card, int ease) {
        try {
            if (card.getQueue() == 0 || card.getQueue() == 1 || card.getQueue() == 3) {
                return _nextLrnIvl(card, ease);
            } else if (ease == 1) {
                // lapsed
                JSONObject conf = _lapseConf(card);
                if (conf.getJSONArray("delays").length() > 0) {
                    return (int) (conf.getJSONArray("delays").getDouble(0) * 60.0);
                }
                return _nextLapseIvl(card, conf) * 86400;
            } else {
                // review
                return _nextRevIvl(card, ease) * 86400;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private int _nextLrnIvl(Card card, int ease) {
        // this isn't easily extracted from the learn code
        if (card.getQueue() == 0) {
            card.setLeft(_startingLeft(card));
        }
        JSONObject conf = _lrnConf(card);
        try {
            if (ease == 1) {
                // fail
                return _delayForGrade(conf, conf.getJSONArray("delays").length());
            } else if (ease == 3) {
                // early removal
                if (!_resched(card)) {
                    return 0;
                }
                return _graduatingIvl(card, conf, true, false) * 86400;
            } else {
                int left = card.getLeft() % 1000 - 1;
                if (left <= 0) {
                    // graduate
                    if (!_resched(card)) {
                        return 0;
                    }
                    return _graduatingIvl(card, conf, false, false) * 86400;
                } else {
                    return _delayForGrade(conf, left);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
                "UPDATE cards SET queue = -1, mod = " + Utils.intNow() + ", usn = " + mCol.usn() + " WHERE id IN "
                        + Utils.ids2str(ids));
    }


    /**
     * Unsuspend cards
     */
    public void unsuspendCards(long[] ids) {
        mCol.log(ids);
        mCol.getDb().execute(
                "UPDATE cards SET queue = type, mod = " + Utils.intNow() + ", usn = " + mCol.usn()
                        + " WHERE queue = -1 AND id IN " + Utils.ids2str(ids));
    }


    public void buryCards(long[] cids) {
        mCol.log(cids);
        remFromDyn(cids);
        removeLrn(cids);
        mCol.getDb().execute("update cards set queue=-2,mod=?,usn=? where id in " + Utils.ids2str(cids),
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
        LinkedList<Long> toBury = new LinkedList<Long>();
        JSONObject nconf = _newConf(card);
        boolean buryNew = nconf.optBoolean("bury", true);
        JSONObject rconf = _revConf(card);
        boolean buryRev = rconf.optBoolean("bury", true);
        // loop through and remove from queues
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(String.format(Locale.US,
                    "select id, queue from cards where nid=%d and id!=%d "+
                    "and (queue=0 or (queue=2 and due<=%d))", new Object[]{card.getNid(), card.getId(), mToday}), null);
            while (cur.moveToNext()) {
                long cid = cur.getLong(0);
                int queue = cur.getInt(1);
                if (queue == 2) {
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
            mCol.getDb().execute("update cards set queue=-2,mod=?,usn=? where id in " + Utils.ids2str(toBury),
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
        mCol.getDb().execute("update cards set type=0,queue=0,ivl=0,due=0,odue=0,factor=2500" +
                " where id in " + Utils.ids2str(ids));
        int pmax = mCol.getDb().queryScalar("SELECT max(due) FROM cards WHERE type=0");
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
        ArrayList<Object[]> d = new ArrayList<Object[]>();
        int t = mToday;
        long mod = Utils.intNow();
        Random rnd = new Random();
        for (long id : ids) {
            int r = rnd.nextInt(imax - imin + 1) + imin;
            d.add(new Object[] { Math.max(1, r), r + t, mCol.usn(), mod, 2500, id });
        }
        remFromDyn(ids);
        mCol.getDb().executeMany(
                "update cards set type=2,queue=2,ivl=?,due=?,odue=0, " +
                        "usn=?,mod=?,factor=? where id=?", d);
        mCol.log(ids);
    }


    /**
     * Completely reset cards for export.
     */
    public void resetCards(long[] ids) {
        long[] nonNew = Utils.arrayList2array(mCol.getDb().queryColumn(Long.class, String.format(Locale.US,
                        "select id from cards where id in %s and (queue != 0 or type != 0)", Utils.ids2str(ids)), 0));
        mCol.getDb().execute("update cards set reps=0, lapses=0 where id in " + Utils.ids2str(nonNew));
        forgetCards(nonNew);
        mCol.log(ids);
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
        long now = Utils.intNow();
        ArrayList<Long> nids = new ArrayList<Long>();
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
        HashMap<Long, Long> due = new HashMap<Long, Long>();
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
                    "SELECT min(due) FROM cards WHERE due >= " + start + " AND type = 0 AND id NOT IN " + scids);
            if (low != 0) {
                int shiftby = high - low + 1;
                mCol.getDb().execute(
                        "UPDATE cards SET mod = " + now + ", usn = " + mCol.usn() + ", due = due + " + shiftby
                                + " WHERE id NOT IN " + scids + " AND due >= " + low + " AND queue = 0");
            }
        }
        // reorder cards
        ArrayList<Object[]> d = new ArrayList<Object[]>();
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase()
                    .rawQuery("SELECT id, nid FROM cards WHERE type = 0 AND id IN " + scids, null);
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
        List<Long> cids = mCol.getDb().queryColumn(Long.class, "SELECT id FROM cards WHERE did = " + did + " ORDER BY id", 0);
        sortCards(Utils.toPrimitive(cids), 1, 1, false, false);
    }


    public void resortConf(JSONObject conf) {
        ArrayList<Long> dids = mCol.getDecks().didsForConf(conf);
        try {
            for (long did : dids) {
                if (conf.getJSONObject("new").getLong("order") == 0) {
                    randomizeCards(did);
                } else {
                    orderCards(did);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
        try {
            if (conf.getJSONObject("new").getInt("order") == Consts.NEW_CARDS_RANDOM) {
                randomizeCards(did);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */


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


    public int matureCount() {
        String dids = _deckLimit();
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE type = 2 AND ivl >= 21 AND did IN " + dids);
    }


    /** returns today's progress
     *
     * @param counts (if empty, cached version will be used if any)
     * @param card
     * @return [progressCurrentDeck, progressAllDecks, leftCards, eta]
     */
    public float[] progressToday(List<DeckDueTreeNode> counts, Card card, boolean eta) {
        try {
            int doneCurrent = 0;
            int[] leftCurrent = new int[]{0, 0, 0};
            String[] cs = new String[]{"new", "lrn", "rev"};
            long currentDid = 0;

            // current selected deck
            if (counts == null) {
                JSONObject deck = mCol.getDecks().current();
                currentDid = deck.getLong("id");
                for (String s : cs) {
                    doneCurrent += deck.getJSONArray(s + "Today").getInt(1);
                }
                if (card != null) {
                    int idx = countIdx(card);
                    leftCurrent[idx] += idx == 1 ? card.getLeft() / 1000 : 1;
                } else {
                    reset();
                }
                leftCurrent[0] += mNewCount;
                leftCurrent[1] += mLrnCount;
                leftCurrent[2] += mRevCount;
            }

            // refresh deck progresses with fresh counts if necessary
            if (counts != null || mCachedDeckCounts == null) {
                if (mCachedDeckCounts == null) {
                    mCachedDeckCounts = new HashMap<Long, Pair<String[], long[]>>();
                }
                mCachedDeckCounts.clear();
                if (counts == null) {
                    // reload counts
                    counts = deckDueList();
                }
                for (DeckDueTreeNode d : counts) {
                    int done = 0;
                    JSONObject deck = mCol.getDecks().get(d.did);
                    for (String s : cs) {
                        done += deck.getJSONArray(s + "Today").getInt(1);
                    }
                    mCachedDeckCounts.put(d.did, new Pair<String[], long[]> (d.names, new long[]{done, d.newCount, d.lrnCount, d.newCount}));
                }
            }

            int doneAll = 0;
            int[] leftAll = new int[]{0, 0, 0};
            for (Map.Entry<Long, Pair<String[], long[]>> d : mCachedDeckCounts.entrySet()) {
                boolean exclude = d.getKey() == currentDid; // || mCol.getDecks().isDyn(d.getKey());
                if (d.getValue().first.length == 1) {
                    if (exclude) {
                        // don't count cached version of current deck
                        continue;
                    }
                    long[] c = d.getValue().second;
                    doneAll += c[0];
                    leftAll[0] += c[1];
                    leftAll[1] += c[2];
                    leftAll[2] += c[3];
                } else if (exclude) {
                    // exclude cached values for current deck in order to avoid double count
                    long[] c = d.getValue().second;
                    doneAll -= c[0];
                    leftAll[0] -= c[1];
                    leftAll[1] -= c[2];
                    leftAll[2] -= c[3];
                }
            }
            doneAll += doneCurrent;
            leftAll[0] += leftCurrent[0];
            leftAll[1] += leftCurrent[1];
            leftAll[2] += leftCurrent[2];
            int totalAll = doneAll + leftAll[0] + leftAll[1] + leftAll[2];
            int totalCurrent = doneCurrent + leftCurrent[0] + leftCurrent[1] + leftCurrent[2];

            float progressCurrent = -1;
            if (totalCurrent != 0) {
                progressCurrent = (float) doneCurrent / (float) totalCurrent;
            }
            float progressTotal = -1;
            if (totalAll != 0) {
                progressTotal = (float) doneAll / (float) totalAll;
            }
            return new float[]{ progressCurrent, progressTotal, totalAll - doneAll, eta ? eta(leftAll, false) : -1};
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public int eta(int[] counts) {
        return eta(counts, true);
    }


    /** estimates remaining time for learning (based on last seven days) */
    public int eta(int[] counts, boolean reload) {
        double revYesRate;
        double revTime;
        double lrnYesRate;
        double lrnTime;
        if (reload || mEtaCache[0] == -1) {
            Cursor cur = null;
            try {
                cur = mCol
                        .getDb()
                        .getDatabase()
                        .rawQuery(
                                "SELECT avg(CASE WHEN ease > 1 THEN 1.0 ELSE 0.0 END), avg(time) FROM revlog WHERE type = 1 AND id > "
                                        + ((mCol.getSched().getDayCutoff() - (7 * 86400)) * 1000), null);
                if (!cur.moveToFirst()) {
                    return -1;
                }
                revYesRate = cur.getDouble(0);
                revTime = cur.getDouble(1);
                
                if (!cur.isClosed()) {
                    cur.close();
                }
                
                cur = mCol
                        .getDb()
                        .getDatabase()
                        .rawQuery(
                                "SELECT avg(CASE WHEN ease = 3 THEN 1.0 ELSE 0.0 END), avg(time) FROM revlog WHERE type != 1 AND id > "
                                        + ((mCol.getSched().getDayCutoff() - (7 * 86400)) * 1000), null);
                if (!cur.moveToFirst()) {
                    return -1;
                }
                lrnYesRate = cur.getDouble(0);
                lrnTime = cur.getDouble(1);
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            mEtaCache[0] = revYesRate;
            mEtaCache[1] = revTime;
            mEtaCache[2] = lrnYesRate;
            mEtaCache[3] = lrnTime;
        } else {
            revYesRate = mEtaCache[0];
            revTime = mEtaCache[1];
            lrnYesRate = mEtaCache[2];
            lrnTime = mEtaCache[3];
        }
        // rev cards
        double eta = revTime * counts[2];
        // lrn cards
        double factor = Math.min(1 / (1 - lrnYesRate), 10);
        double lrnAnswers = (counts[0] + counts[1] + counts[2] * (1 - revYesRate)) * factor;
        eta += lrnAnswers * lrnTime;
        return (int) (eta / 60000);
    }


    public void decrementCounts(Card card) {
        int type = card.getQueue();
        switch (type) {
        case 0:
            mNewCount--;
            break;
        case 1:
            mLrnCount -= card.getLeft() / 1000;
            break;
        case 2:
            mRevCount--;
            break;
        case 3:
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
        try {
            conf = _cardConf(card).getJSONObject("lapse");
            return conf.getInt("leechAction") == 0;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public void setContext(WeakReference<Activity> contextReference) {
        mContextReference = contextReference;
    }


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
        public List<DeckDueTreeNode> children = new ArrayList<DeckDueTreeNode>();

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
            return this.names[0].compareTo(((DeckDueTreeNode)other).names[0]);
        }

        @Override
        public String toString() {
            return String.format("%s, %d, %d, %d, %d, %d, %s",
                    Arrays.toString(names), did, depth, revCount, lrnCount, newCount, children);
        }
    }
}
