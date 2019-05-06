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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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

import timber.log.Timber;

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
                    "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.AvoidBranchingStatementAsLastInLoop",
                    "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt"})
public class SchedV2 extends Sched {



    // Not in libanki
    private static final int[] FACTOR_ADDITION_VALUES = { -150, 0, 150 };

    private String mName = "std";
    private boolean mHaveCustomStudy = true;
    private boolean mBurySiblingsOnAnswer = true;

    private Collection mCol;
    private int mQueueLimit;
    private int mReportLimit;
    private int mDynReportLimit;
    private int mReps;
    private boolean mHaveQueues;
    private Integer mToday;
    public long mDayCutoff;
    private long mLrnCutoff;

    private int mNewCount;
    private int mLrnCount;
    private int mRevCount;

    private int mNewCardModulus;

    private double[] mEtaCache = new double[] { -1, -1, -1, -1 };

    // Queues
    private final LinkedList<Long> mNewQueue = new LinkedList<>();
    private final LinkedList<long[]> mLrnQueue = new LinkedList<>();
    private final LinkedList<Long> mLrnDayQueue = new LinkedList<>();
    private final LinkedList<Long> mRevQueue = new LinkedList<>();

    private LinkedList<Long> mNewDids;
    private LinkedList<Long> mLrnDids;

    // Not in libanki
    private WeakReference<Activity> mContextReference;


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
        mReportLimit = 1000;
        mDynReportLimit = 99999;
        mReps = 0;
        mToday = null;
        mHaveQueues = false;
        mLrnCutoff = 0;
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

        _answerCard(card, ease);

        _updateStats(card, "time", card.timeTaken());
        card.setMod(Utils.intNow());
        card.setUsn(mCol.usn());
        card.flushSched();
    }


    public void _answerCard(Card card, int ease) {
        if (_previewingCard(card)) {
            _answerCardPreview(card, ease);
            return;
        }

        card.setReps(card.getReps() + 1);

        if (card.getQueue() == 0) {
            // came from the new queue, move to learning
            card.setQueue(1);
            card.setType(1);
            // init reps to graduation
            card.setLeft(_startingLeft(card));
            // update daily limit
            _updateStats(card, "new");
        }
        if (card.getQueue() == 1 || card.getQueue() == 3) {
            _answerLrnCard(card, ease);
        } else if (card.getQueue() == 2) {
            _answerRevCard(card, ease);
            // Update daily limit
            _updateStats(card, "rev");
        } else {
            throw new RuntimeException("Invalid queue");
        }
    }


    public void _answerCardPreview(Card card, int ease) {
        if (ease == 1) {
            // Repeat after delay
            card.setQueue(4);
            card.setDue(Utils.intNow() + _previewDelay(card));
            mLrnCount += 1;
        } else if (ease == 2) {
            // Restore original card state and remove from filtered deck
            _restorePreviewCard(card);
            _removeFromFiltered(card);
        } else {
            // This is in place of the assert
            throw new RuntimeException("Invalid ease");
        }
    }


    public int[] counts() {
        return counts(null);
    }


    public int[] counts(Card card) {
        int[] counts = {mNewCount, mLrnCount, mRevCount};
        if (card != null) {
            int idx = countIdx(card);
            counts[idx] += 1;
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
        if (card.getQueue() == 3 || card.getQueue() == 4) {
            return 1;
        }
        return card.getQueue();
    }


    public int answerButtons(Card card) {
        JSONObject conf = _cardConf(card);
        try {
            if (card.getODid() != 0 && !conf.getBoolean("resched")) {
                return 2;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return 4;
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
        ArrayList<JSONObject> decks = new ArrayList<>();
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
        HashMap<Long, Integer> pcounts = new HashMap<>();
        // for each of the active decks
        try {
            for (long did : mCol.getDecks().active()) {
                // get the individual deck's limit
                int lim = (Integer)limFn.invoke(SchedV2.this, mCol.getDecks().get(did));
                if (lim == 0) {
                    continue;
                }
                // check the parents
                List<JSONObject> parents = mCol.getDecks().parents(did);
                for (JSONObject p : parents) {
                    // add if missing
                    long id = p.getLong("id");
                    if (!pcounts.containsKey(id)) {
                        pcounts.put(id, (Integer)limFn.invoke(SchedV2.this, p));
                    }
                    // take minimum of child and parent
                    lim = Math.min(pcounts.get(id), lim);
                }
                // see how many cards we actually have
                int cnt = (Integer)cntFn.invoke(SchedV2.this, did, lim);
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
        } catch (JSONException | IllegalAccessException | InvocationTargetException e) {
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
        HashMap<String, Integer[]> lims = new HashMap<>();
        ArrayList<DeckDueTreeNode> data = new ArrayList<>();
        HashMap<Long, HashMap> childMap = mCol.getDecks().childMap();
        try {
            for (JSONObject deck : decks) {
                // if we've already seen the exact same deck name, remove the
                // invalid duplicate and reload
                if (lims.containsKey(deck.getString("name"))) {
                    Timber.i("deckDueList() removing duplicate deck %s", deck.getString("name"));
                    mCol.getDecks().rem(deck.getLong("id"), false, true);
                    return deckDueList();
                }
                String p;
                List<String> parts = Arrays.asList(deck.getString("name").split("::", -1));
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
                lrn +=  ch.lrnCount;
                _new += ch.newCount;
            }
            // limit the counts to the deck's limits
            JSONObject conf = mCol.getDecks().confForDid(did);
            JSONObject deck = mCol.getDecks().get(did);
            try {
                if (conf.getInt("dyn") == 0) {
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

    private void _resetNewCount() {
        try {
            mNewCount = _walkingCount(SchedV2.class.getDeclaredMethod("_deckNewLimitSingle", JSONObject.class),
                    SchedV2.class.getDeclaredMethod("_cntFnNew", long.class, int.class));
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
                            .query("SELECT id FROM cards WHERE did = " + did + " AND queue = 0 order by due LIMIT " + lim,
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
                fn = SchedV2.class.getDeclaredMethod("_deckNewLimitSingle", JSONObject.class);
            }
            List<JSONObject> decks = mCol.getDecks().parents(did);
            decks.add(mCol.getDecks().get(did));
            int lim = -1;
            // for the deck and each of its parents
            int rem = 0;
            for (JSONObject g : decks) {
                rem = (Integer) fn.invoke(SchedV2.this, g);
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
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = 0 LIMIT " + lim + ")");
    }


    /* Limit for deck without parent limits. */
    public int _deckNewLimitSingle(JSONObject g) {
        try {
            if (g.getInt("dyn") != 0) {
                return mDynReportLimit;
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

    private boolean _updateLrnCutoff(boolean force) {
        try {
            long nextCutoff = Utils.intNow() + mCol.getConf().getInt("collapseTime");
            if (nextCutoff - mLrnCutoff > 60 || force) {
                mLrnCutoff = nextCutoff;
                return true;
            }
            return false;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _maybeResetLrn(boolean force) {
        if (_updateLrnCutoff(force)) {
            _resetLrn();
        }
    }


    private void _resetLrnCount() {
        // sub-day
        mLrnCount = mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit()
                + " AND queue = 1 AND due < " + mLrnCutoff);

        // day
        mLrnCount += mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = 3 AND due <= " + mToday);

        // previews
        mLrnCount += mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = 4");
    }


    private void _resetLrn() {
        _updateLrnCutoff(true);
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
        long cutoff = 0;
        try {
            cutoff = Utils.intNow() + mCol.getConf().getLong("collapseTime");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        Cursor cur = null;
        mLrnQueue.clear();
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .query(
                            "SELECT due, id FROM cards WHERE did IN " + _deckLimit() + " AND queue IN (1, 4) AND due < "
                                    + cutoff + " LIMIT " + mReportLimit, null);
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
        _maybeResetLrn(collapse && mLrnCount == 0);
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


    private void _answerLrnCard(Card card, int ease) {
        JSONObject conf = _lrnConf(card);
        int type;
        if (card.getType() == 2 || card.getType() == 3) {
            type = 2;
        } else {
            type = 0;
        }

        // lrnCount was decremented once when card was fetched
        int lastLeft = card.getLeft();
        boolean leaving = false;

        // immediate graduate?
        if (ease == 4) {
            _rescheduleAsRev(card, conf, true);
            leaving = true;
        // next step?
        } else if (ease == 3) {
            // graduation time?
            if ((card.getLeft() % 1000) - 1 <= 0) {
                _rescheduleAsRev(card, conf, false);
                leaving = true;
            } else {
                _moveToNextStep(card, conf);
            }
        } else if (ease == 2) {
            _repeatStep(card, conf);
        } else {
            // move back to first step
            _moveToFirstStep(card, conf);
        }
        _logLrn(card, ease, conf, leaving, type, lastLeft);
    }


    private void _updateRevIvlOnFail(Card card, JSONObject conf) {
        card.setLastIvl(card.getIvl());
        card.setIvl(_lapseIvl(card, conf));
    }


    private int _moveToFirstStep(Card card, JSONObject conf) {
        card.setLeft(_startingLeft(card));

        // relearning card?
        if (card.getType() == 3) {
            _updateRevIvlOnFail(card, conf);
        }

        return _rescheduleLrnCard(card, conf);
    }


    private void _moveToNextStep(Card card, JSONObject conf) {
        // decrement real left count and recalculate left today
        int left = (card.getLeft() % 1000) - 1;
        try {
            card.setLeft(_leftToday(conf.getJSONArray("delays"), left) * 1000 + left);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

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
        card.setDue(Utils.intNow() + delay);

        // due today?
        if (card.getDue() < mDayCutoff) {
            // Add some randomness, up to 5 minutes or 25%
            int maxExtra = (int) Math.min(300, (int)(delay * 0.25));
            int fuzz = new Random().nextInt(maxExtra);
            card.setDue(Math.min(mDayCutoff - 1, card.getDue() + fuzz));
            card.setQueue(1);
            try {
                if (card.getDue() < (Utils.intNow() + mCol.getConf().getInt("collapseTime"))) {
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
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else {
            // the card is due in one or more days, so we need to use the day learn queue
            long ahead = ((card.getDue() - mDayCutoff) / 86400) + 1;
            card.setDue(mToday + ahead);
            card.setQueue(3);
        }
        return delay;
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


    private int  _delayForRepeatingGrade(JSONObject conf, int left) {
        // halfway between last and  next
        int delay1 = _delayForGrade(conf, left);
        int delay2;
        try {
            if (conf.getJSONArray("delays").length() > 1) {
                delay2 = _delayForGrade(conf, left - 1);
            } else {
                delay2 = delay1 * 2;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        int avg = (delay1 + Math.max(delay1, delay2)) / 2;
        return avg;
    }


    private JSONObject _lrnConf(Card card) {
        if (card.getType() == 2 || card.getType() == 3) {
            return _lapseConf(card);
        } else {
            return _newConf(card);
        }
    }


    private void _rescheduleAsRev(Card card, JSONObject conf, boolean early) {
        boolean lapse = (card.getType() == 2 || card.getType() == 3);
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
        card.setQueue(2);
        card.setType(2);
    }


    private int _startingLeft(Card card) {
        try {
            JSONObject conf;
        	if (card.getType() == 3) {
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


    private int _graduatingIvl(Card card, JSONObject conf, boolean early, boolean fuzz) {
        if (card.getType() == 2 || card.getType() == 3) {
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
                // early remove
                ideal = ja.getInt(1);
            }
            if (fuzz) {
                ideal = _fuzzedIvl(ideal);
            }
            return ideal;
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
        card.setType(2);
        card.setQueue(2);
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
                    "SELECT count() FROM (SELECT null FROM cards WHERE did = " + did
                            + " AND queue = 1 AND due < " + (Utils.intNow() + mCol.getConf().getInt("collapseTime"))
                            + " LIMIT " + mReportLimit + ")");
            return cnt + mCol.getDb().queryScalar(
                    "SELECT count() FROM (SELECT null FROM cards WHERE did = " + did
                            + " AND queue = 3 AND due <= " + mToday
                            + " LIMIT " + mReportLimit + ")");
        } catch (SQLException | JSONException e) {
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


    private int _deckRevLimitSingle(JSONObject d) {
        return _deckRevLimitSingle(d, null);
    }


    private int _deckRevLimitSingle(JSONObject d, Integer parentLimit) {
        // invalid deck selected?
        if (d == null) {
            return 0;
        }
        try {
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
                for (JSONObject parent : mCol.getDecks().parents(d.getInt("id"))) {
                    // pass in dummy parentLimit so we don't do parent lookup again
                    lim = Math.min(lim, _deckRevLimitSingle(parent, lim));
                }
                return lim;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public int _revForDeck(long did, int lim, HashMap<Long, HashMap> childMap) {
        List<Long> dids = mCol.getDecks().childDids(did, childMap);
        dids.add(0, did);
        lim = Math.min(lim, mReportLimit);
        return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did in " + Utils.ids2str(dids) + " AND queue = 2 AND due <= " + mToday + " LIMIT " + lim + ")");
    }


    private void _resetRevCount() {
        int lim = _currentRevLimit();
        mRevCount = mCol.getDb().queryScalar("SELECT count() FROM (SELECT id FROM cards WHERE did in " + Utils.ids2str(mCol.getDecks().active()) + " AND queue = 2 AND due <= " + mToday + " LIMIT " + lim + ")");
    }


    private void _resetRev() {
        _resetRevCount();
        mRevQueue.clear();
    }


    private boolean _fillRev() {
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
                                "SELECT id FROM cards WHERE did in " + Utils.ids2str(mCol.getDecks().active()) + " AND queue = 2 AND due <= " + mToday
                                        + " ORDER BY due LIMIT " + lim, null);
                while (cur.moveToNext()) {
                    mRevQueue.add(cur.getLong(0));
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            if (!mRevQueue.isEmpty()) {
                try {
                    if (mCol.getDecks().get(mCol.getDecks().selected(), false).getInt("dyn") != 0) {
                        // dynamic decks need due order preserved
                        // Note: libanki reverses mRevQueue and returns the last element in _getRevCard().
                        // AnkiDroid differs by leaving the queue intact and returning the *first* element
                        // in _getRevCard().
                    } else {
                        // fixme: as soon as a card is answered, this is no longer consistent
                        Random r = new Random();
                        r.setSeed(mToday);
                        Collections.shuffle(mRevQueue, r);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
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
        boolean early = card.getODid() != 0 && (card.getODue() > mToday);
        int type = early ? 3 : 1;
        if (ease == 1) {
            delay = _rescheduleLapse(card);
        } else {
            _rescheduleRev(card, ease, early);
        }
        _logRev(card, ease, delay, type);
    }


    private int _rescheduleLapse(Card card) {
        JSONObject conf;
        try {
            conf = _lapseConf(card);
            card.setLapses(card.getLapses() + 1);
            card.setFactor(Math.max(1300, card.getFactor() - 200));
            int delay;

            boolean suspended = _checkLeech(card, conf) && card.getQueue() == -1;
            if (conf.getJSONArray("delays").length() != 0 && !suspended) {
                card.setType(3);
                delay = _moveToFirstStep(card, conf);
            } else {
                // no relearning steps
                _updateRevIvlOnFail(card, conf);
                _rescheduleAsRev(card, conf, false);
                // need to reset the queue after rescheduling
                if (suspended) {
                    card.setQueue(-1);
                }
                delay = 0;
            }

            return delay;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private int _lapseIvl(Card card, JSONObject conf) {
        try {
            int ivl = Math.max(1, Math.max(conf.getInt("minInt"), (int)(card.getIvl() * conf.getDouble("mult"))));
            return ivl;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _rescheduleRev(Card card, int ease, boolean early) {
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


    private void _logRev(Card card, int ease, int delay, int type) {
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
    private int _nextRevIvl(Card card, int ease, boolean fuzz) {
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
        if (ease == 2) {
            return ivl2;
        }

        int ivl3 = _constrainedIvl((card.getIvl() + delay / 2) * fct, conf, ivl2, fuzz);
        if (ease == 3) {
            return ivl3;
        }

        try {
            int ivl4 = _constrainedIvl((
                    (card.getIvl() + delay) * fct * conf.getDouble("ease4")), conf, ivl3, fuzz);
            return ivl4;
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


    private int _constrainedIvl(double ivl, JSONObject conf, double prev, boolean fuzz) {
        int newIvl = (int) (ivl * conf.optDouble("ivlFct", 1));
        if (fuzz) {
            newIvl = _fuzzedIvl(newIvl);
        }

        newIvl = (int) Math.max(Math.max(newIvl, prev + 1), 1);
        try {
            newIvl = Math.min(newIvl, conf.getInt("maxIvl"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return newIvl;
    }


    /**
     * Number of days later than scheduled.
     */
    private long _daysLate(Card card) {
        long due = card.getODid() != 0 ? card.getODue() : card.getDue();
        return Math.max(0, mToday - due);
    }


    private void _updateRevIvl(Card card, int ease) {
        card.setIvl(_nextRevIvl(card, ease, true));
    }


    private void _updateEarlyRevIvl(Card card, int ease) {
        card.setIvl(_earlyReviewIvl(card, ease));
    }


    // next interval for card when answered early+correctly
    private int _earlyReviewIvl(Card card, int ease) {
        if (card.getODid() == 0 || card.getType() != 2 || card.getFactor() == 0) {
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
        if (ease == 2)  {
            factor = conf.optDouble("hardFactor", 1.2);
            // hard cards shouldn't have their interval decreased by more than 50%
            // of the normal factor
            minNewIvl = factor / 2;
        } else if (ease == 3) {
            factor = card.getFactor() / 1000;
        } else { // ease == 4
            factor = card.getFactor() / 1000;
            try {
                double ease4 = conf.getDouble("ease4");
                // 1.3 -> 1.15
                easyBonus = ease4 - (ease4 - 1)/2;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
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

    /* Rebuild a dynamic deck. */
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
        try {
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
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
        // update queue in preview case
        mCol.getDb().execute(
                "update cards set did = odid, " + _restoreQueueSnippet() +
                ", due = odue, odue = 0, odid = 0, usn = ? where " + lim,
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
            	break;
        }
        return t + " limit " + l;
    }


    private void _moveToDyn(long did, List<Long> ids, int start) {
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
        try {
            if (!deck.getBoolean("resched")) {
                queue = ", queue = 2";
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        mCol.getDb().executeMany(
                "UPDATE cards SET odid = did, " +
                        "odue = due, did = ?, due = ?, usn = ? " + queue + " WHERE id = ?", data);
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
        if (card.getType() == 1 || card.getType() == 3) {
            if (card.getODue() > 1000000000) {
                card.setQueue(1);
            } else {
                card.setQueue(3);
            }
        } else {
            card.setQueue(card.getType());
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


    private boolean _previewingCard(Card card) {
        JSONObject conf = _cardConf(card);

        try {
            return conf.getInt("dyn") != 0 && !conf.getBoolean("resched");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private int _previewDelay(Card card) {
        return _cardConf(card).optInt("previewDelay", 10) * 60;
    }


    /**
     * Daily cutoff ************************************************************* **********************************
     * This function uses GregorianCalendar so as to be sensitive to leap years, daylight savings, etc.
     */

    private void _updateCutoff() {
        int oldToday = mToday == null ? 0 : mToday;
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
            try {
                mCol.getConf().put("lastUnburied", mToday);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
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
                now = " " + context.getString(R.string.sched_unbury_action);
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


    public boolean haveBuriedSiblings() {
        return haveBuriedSiblings(mCol.getDecks().active());
    }


    private boolean haveBuriedSiblings(List<Long> allDecks) {
        // Refactored to allow querying an arbitrary deck
        String sdids = Utils.ids2str(allDecks);
        int cnt = mCol.getDb().queryScalar(String.format(Locale.US,
                "select 1 from cards where queue = -2 and did in %s limit 1", sdids));
        return cnt != 0;
    }


    public boolean haveManuallyBuried() {
        return haveManuallyBuried(mCol.getDecks().active());
    }


    private boolean haveManuallyBuried(List<Long> allDecks) {
        // Refactored to allow querying an arbitrary deck
        String sdids = Utils.ids2str(allDecks);
        int cnt = mCol.getDb().queryScalar(String.format(Locale.US,
                "select 1 from cards where queue = -3 and did in %s limit 1", sdids));
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
        try {
            if (ivl < mCol.getConf().getInt("collapseTime")) {
                s = context.getString(R.string.less_than_time, s);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return s;
    }


    /**
     * Return the next interval for CARD, in seconds.
     */
    public long nextIvl(Card card, int ease) {
        // preview mode?
        if (_previewingCard(card)) {
            if (ease == 1) {
                return _previewDelay(card);
            }
            return 0;
        }
        try {
            // (re)learning?
            if (card.getQueue() == 0 || card.getQueue() == 1 || card.getQueue() == 3) {
                return _nextLrnIvl(card, ease);
            } else if (ease == 1) {
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
                    return _earlyReviewIvl(card, ease) * 86400;
                } else {
                    return _nextRevIvl(card, ease, false) * 86400;
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    // this isn't easily extracted from the learn code
    private long _nextLrnIvl(Card card, int ease) {
        if (card.getQueue() == 0) {
            card.setLeft(_startingLeft(card));
        }
        JSONObject conf = _lrnConf(card);
        try {
            if (ease == 1) {
                // fail
                return _delayForGrade(conf, conf.getJSONArray("delays").length());
            } else if (ease == 2) {
                return _delayForRepeatingGrade(conf, card.getLeft());
            } else if (ease == 4) {
                return _graduatingIvl(card, conf, true, false) * 86400;
            } else { // ease == 3
                int left = card.getLeft() % 1000 - 1;
                if (left <= 0) {
                    // graduate
                    return _graduatingIvl(card, conf, false, false) * 86400L;
                } else {
                    return _delayForGrade(conf, left);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
        return "queue = (case when type in (1,3) then\n" +
                "  (case when (case when odue then odue else due end) > 1000000000 then 1 else 3 end)\n" +
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
                "UPDATE cards SET queue = -1, mod = " + Utils.intNow() + ", usn = " + mCol.usn() + " WHERE id IN "
                        + Utils.ids2str(ids));
    }


    /**
     * Unsuspend cards
     */
    public void unsuspendCards(long[] ids) {
        mCol.log(ids);
        mCol.getDb().execute(
                "UPDATE cards SET " + _restoreQueueSnippet() + ", mod = " + Utils.intNow() + ", usn = " + mCol.usn()
                        + " WHERE queue = -1 AND id IN " + Utils.ids2str(ids));
    }


    public void buryCards(long[] cids) {
        buryCards(cids, true);
    }


    public void buryCards(long[] cids, boolean manual) {
        int queue = manual ? -3 : -2;
        mCol.log(cids);
        mCol.getDb().execute("update cards set queue=?,mod=?,usn=? where id in " + Utils.ids2str(cids),
                new Object[]{queue, Utils.now(), mCol.usn()});
    }


    /**
     * Unbury all buried cards in all decks
     */
    public void unburyCards() {
        mCol.log(mCol.getDb().queryColumn( Long.class,"select id from cards where queue in (-2, -3)", 0));
        mCol.getDb().execute("update cards set " + _restoreQueueSnippet() + " where queue in (-2, -3)");
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
            queue = "queue in (-2, -3)";
        } else if ("manual".equals(type)) {
            queue = "queue = -3";
        } else if ("siblings".equals(type)) {
            queue = "queue = -2";
        } else {
            throw new RuntimeException("unknown type");
        }

        String sids = Utils.ids2str(allDecks != null ? allDecks : mCol.getDecks().active());

        mCol.log(mCol.getDb().queryColumn(Long.class,"select id from cards where " + queue + " and did in " + sids, 0));
        mCol.getDb().execute("update cards set mod=?,usn=?, " + _restoreQueueSnippet() + " where " + queue + " and did in " + sids,
                new Object[]{Utils.intNow(), mCol.usn()});
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
        ArrayList<Long> toBury = new ArrayList<>();
        JSONObject nconf = _newConf(card);
        boolean buryNew = nconf.optBoolean("bury", true);
        JSONObject rconf = _revConf(card);
        boolean buryRev = rconf.optBoolean("bury", true);
        // loop through and remove from queues
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().query(String.format(Locale.US,
                    "select id, queue from cards where nid=%d and id!=%d "+
                    "and (queue=0 or (queue=2 and due<=%d))", card.getNid(), card.getId(), mToday), null);
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
            buryCards(Utils.arrayList2array(toBury),false);
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
        ArrayList<Object[]> d = new ArrayList<>();
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
    public void resetCards(Long[] ids) {
        long[] nonNew = Utils.arrayList2array(mCol.getDb().queryColumn(Long.class, String.format(Locale.US,
                "select id from cards where id in %s and (queue != 0 or type != 0)", Utils.ids2str(ids)), 0));
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
        long now = Utils.intNow();
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
                    "SELECT min(due) FROM cards WHERE due >= " + start + " AND type = 0 AND id NOT IN " + scids);
            if (low != 0) {
                int shiftby = high - low + 1;
                mCol.getDb().execute(
                        "UPDATE cards SET mod = " + now + ", usn = " + mCol.usn() + ", due = due + " + shiftby
                                + " WHERE id NOT IN " + scids + " AND due >= " + low + " AND queue = 0");
            }
        }
        // reorder cards
        ArrayList<Object[]> d = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase()
                    .query("SELECT id, nid FROM cards WHERE type = 0 AND id IN " + scids, null);
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
        List<Long> dids = mCol.getDecks().didsForConf(conf);
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


    /**
     * Changing scheduler versions **************************************************
     * *********************************************
     */

    private void _emptyAllFiltered() {
        mCol.getDb().execute(String.format(Locale.US,"update cards set did = odid, queue = (case when type = 1 then 0 when type = 3 then 2 else type end), type = (case when type = 1 then 0 when type = 3 then 2 else type end), due = odue, odue = 0, odid = 0, usn = %d where odid != 0", mCol.usn()));
    }


    private void _removeAllFromLearning() {
        // remove review cards from relearning
        mCol.getDb().execute(String.format(Locale.US,"update cards set due = odue, queue = 2, type = 2, mod = %d, usn = %d, odue = 0 where queue in (1,3) and type in (2,3)", Utils.intNow(), mCol.usn()));
        // remove new cards from learning
        forgetCards(Utils.arrayList2array(mCol.getDb().queryColumn(Long.class, "select id from cards where queue in (1,3)", 0)));
    }


    // v1 doesn't support buried/suspended (re)learning cards
    private void _resetSuspendedLearning() {
        mCol.getDb().execute(String.format(Locale.US,"update cards set type = (case when type = 1 then 0 when type in (2, 3) then 2 else type end), due = (case when odue then odue else due end), odue = 0, mod = %d, usn = %d where queue < 0", Utils.intNow(), mCol.usn()));
    }


    // no 'manually buried' queue in v1
    private void _moveManuallyBuried() {
        mCol.getDb().execute(String.format(Locale.US, "update cards set queue=-2, mod=%d where queue=-3", Utils.intNow()));
    }

    // adding 'hard' in v2 scheduler means old ease entries need shifting
    // up or down
    private void _remapLearningAnswers(String sql) {
        mCol.getDb().execute("update revlog set " + sql + " and type in (0,2)");
    }

    public void moveToV1() {
        _emptyAllFiltered();
        _removeAllFromLearning();

        _moveManuallyBuried();
        _resetSuspendedLearning();
        _remapLearningAnswers("ease=ease-1 where ease in (3,4)");
    }


    public void moveToV2() {
        _emptyAllFiltered();
        _removeAllFromLearning();
        _remapLearningAnswers("ease=ease+1 where ease in (2,3)");
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
                        .query(
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
                        .query(
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

}
