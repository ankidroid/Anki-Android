/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General private License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General private License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General private License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeMap;

public class Scheduler {

    // whether new cards should be mixed with reviews, or shown first or last
    public static final int NEW_CARDS_DISTRIBUTE = 0;
    public static final int NEW_CARDS_LAST = 1;
    public static final int NEW_CARDS_FIRST = 2;

    // new card insertion order
    public static final int NEW_CARDS_RANDOM = 0;
    public static final int NEW_CARDS_DUE = 1;

    // sort order for day's new cards
    public static final int NEW_TODAY_ORD = 0;
    public static final int NEW_TODAY_DUE = 1;

    // review card sort order
    public static final int REV_CARDS_OLD_FIRST = 0;
    public static final int REV_CARDS_NEW_FIRST = 1;
    public static final int REV_CARDS_RANDOM = 2;

    // lech actions
    public static final int LEECH_ACTION_SUSPEND = 0;
    public static final int LEECH_ACTION_TAG_ONLY = 1;

    // counts
    public static final int COUNTS_NEW = 0;
    public static final int COUNTS_LRN = 1;
    public static final int COUNTS_REV = 2;

    private static final String[] revOrderStrings = { "ivl desk", "ivl", "due" };
    private static final int[] factorAdditionValues = { -150, 0, 150 };

    private Deck mDeck;
    private AnkiDb mDb;
    private String mName = "std";
    private int mQueueLimit;
    private int mReportLimit;
    private int mReps;
    private int mToday;
    private int mDayCutoff;

    private int mNewCount;
    private int mLrnCount;
    private int mRevCount;

    private int mNewCardModulus;

    // Queues
    private LinkedList<int[]> mNewQueue;
    private LinkedList<int[]> mLrnQueue;
    private LinkedList<int[]> mRevQueue;

    private TreeMap<Integer, Integer> mGroupConfs;
    private TreeMap<Integer, JSONObject> mConfCache;


    /**
     * revlog: types: 0=lrn, 1=rev, 2=relrn, 3=cram positive intervals are in days (rev), negative intervals in seconds
     * (lrn)
     */

    /**
     * the standard Anki scheduler
     */
    public Scheduler(Deck deck) {
        mDeck = deck;
        mDb = mDeck.getDB();
        mQueueLimit = 200;
        mReportLimit = 1000;
        mReps = 0;
        _updateCutoff();

        // Initialise queues
        mNewQueue = new LinkedList<int[]>();
        mLrnQueue = new LinkedList<int[]>();
        mRevQueue = new LinkedList<int[]>();

        // Initialise conf maps
        mGroupConfs = new TreeMap<Integer, Integer>();
        mConfCache = new TreeMap<Integer, JSONObject>();
    }


    /**
     * Pop the next card from the queue. None if finished.
     * 
     * @return The next due card or null if nothing is due.
     */
    public Card getCard() {
        _checkDay();
        int id = _getCardId();
        if (id != 0) {
            Card c = mDeck.getCard(id);
            c.startTimer();
            return c;
        } else {
            return null;
        }
    }


    public void reset() {
        _resetConf();
        _resetCounts();
        _resetLrn();
        _resetRev();
        _resetNew();
    }


    public void answerCard(Card card, int ease) {
        Log.i(AnkiDroidApp.TAG, "answerCard");
        mDeck.markReview(card);
        mReps += 1;
        card.setReps(card.getReps() + 1);
        if (card.getQueue() == 0) {
            // put it in the learn queue
            card.setQueue(1);
            card.setType(1);
        }
        if (card.getQueue() == 1) {
            _answerLrnCard(card, ease);
        } else if (card.getQueue() == 2) {
            _answerRevCard(card, ease);
        } else {
            Log.e(AnkiDroidApp.TAG, "invalid queue");
        }
        card.setMod();
        card.flushSched();
    }


    /**
     * Does not include fetched but unanswered.
     */
    public int[] counts() {
        int[] count = new int[3];
        count[0] = mNewCount;
        count[1] = mLrnCount;
        count[2] = mRevCount;
        return count;
    }


    public int countIdx(Card card) {
        return card.getQueue();
    }


    public boolean lrnButtons(Card card) {
        if (card.getQueue() == 2) {
            return false;
        } else {
            return true;
        }
    }


    public int recButton(Card card) {
        if (card.getQueue() == 2) {
            return 2;
        } else {
            return 3;
        }
    }


    /**
     * Unbury and remove temporary suspends on close.
     */
    public void onClose() {
        mDb.getDatabase().execSQL("UPDATE cards SET queue = type WHERE queue BETWEEN -3 AND -2");
    }


    /**
     * A very rough estimate of time to review.
     */
    public int eta() {
        Cursor cur = null;
        int cnt = 0;
        int sum = 0;
        try {
            cur = mDb.getDatabase().rawQuery(
                    "SELECT count(), sum(taken) FROM (SELECT * FROM revlog " + "ORDER BY time DESC LIMIT 10)", null);
            if (cur.moveToFirst()) {
                cnt = cur.getInt(0);
                sum = cur.getInt(1);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        if (cnt == 0) {
            return 0;
        }
        double avg = sum / ((float) cnt);
        int[] c = counts();
        return (int) ((avg * c[0] * 3 + avg * c[1] * 3 + avg * c[2]) / 1000.0);
    }


    /**
     * Counts ***********************************************************************************************
     */

    /**
     * Return counts for selected groups, without building queue.
     */
    private int[] selCounts() {
        _resetCounts();
        return counts();
    }


    /**
     * Return counts for all groups, without building queue.
     */
    public int[] allCounts() {
        JSONArray conf;
        try {
            conf = mDeck.getQconf().getJSONArray("groups");
            if (conf.length() != 0) {
                mDeck.getQconf().put("groups", new JSONArray());
                _resetCounts();
                mDeck.getQconf().put("groups", conf);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return counts();
    }


    public void _resetCounts() {
        _updateCutoff();
        _resetLrnCount();
        _resetRevCount();
        _resetNewCount();
    }


    /**
     * Group counts ***********************************************************************************************
     */

    /**
     * Returns [groupname, cards, due, new]
     */
    private TreeMap<String, int[]> groupCounts() {
        Cursor cur = null;
        TreeMap<String, int[]> counts = new TreeMap<String, int[]>();
        TreeMap<Integer, String> gids = new TreeMap<Integer, String>();
        try {
            cur = mDb.getDatabase().rawQuery("SELECT id, name FROM groups ORDER BY name", null);
            while (cur.moveToNext()) {
                gids.put(cur.getInt(0), cur.getString(1));
            }
            cur = mDb.getDatabase().rawQuery(
                    "SELECT gid, count(), " + "sum(CASE WHEN queue = 2 AND due <= " + mToday + " THEN 1 ELSE 0 END), "
                            + "sum(CASE WHEN queue = 0 THEN 1 ELSE 0 END) FROM cards GROUP BY gid", null);
            while (cur.moveToNext()) {
                counts.put(gids.get(cur.getInt(0)), new int[] { cur.getInt(1), cur.getInt(2), cur.getInt(3) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return counts;
    }


    // private TreeMap<String, int[]> groupCountTree() {
    // //TODO return _groupChildren(groupCounts());
    // }
    //
    //
    // /**
    // * Like the count tree without the counts. Faster.
    // */
    // private TreeMap<String, int[]> groupTree() {
    // // TODO
    // // Cursor cur = null;
    // // TreeMap<String, int[]> counts = new TreeMap<String, int[]>();
    // // try {
    // // cur = mDb.getDatabase().rawQuery("SELECT id, name FROM groups ORDER BY name", null);
    // // while (cur.moveToNext()) {
    // // counts.put(cur.getString(1), new int[] {0, 0, 0});
    // // }
    // // } finally {
    // // if (cur != null && !cur.isClosed()) {
    // // cur.close();
    // // }
    // // }
    // }

    /**
     * Return counts for selected groups, without building queue.
     */
    // private TreeMap<String, int[]> _groupChildren() {
    // // TODO
    // }

    /**
     * Getting the next card
     * ***********************************************************************************************
     */

    /**
     * Return the next due card id, or None.
     */
    private int _getCardId() {
        // learning card due?
        int id = _getLrnCard();
        if (id != 0) {
            return id;
        }
        // new first, or time for one?
        if (_timeForNewCard()) {
            return _getNewCard();
        }
        // Card due for review?
        id = _getRevCard();
        if (id != 0) {
            return id;
        }
        // New cards left?
        id = _getNewCard();
        if (id != 0) {
            return id;
        }
        // collapse or finish
        return _getLrnCard(true);
    }


    /**
     * New cards ***********************************************************************************************
     */

    // FIXME: need to keep track of reps for timebox and new card introduction

    private void _resetNewCount() {
        JSONObject l = mDeck.getQconf();
        try {
            if (l.getJSONArray("newToday").getInt(0) != mToday) {
                // it's a new day; reset counts
                JSONArray ja = new JSONArray();
                ja.put(mToday);
                ja.put(0);
                l.put("newToday", ja);
            }
            int lim = Math.min(mReportLimit, l.getInt("newPerDay") - l.getJSONArray("newToday").getInt(1));
            if (lim <= 0) {
                mNewCount = 0;
            } else {
                mNewCount = (int) mDb.queryScalar("SELECT count() FROM (SELECT id FROM cards " + "WHERE queue = 0 "
                        + _groupLimit() + " LIMIT " + lim + ")");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _resetNew() {
        int lim = Math.min(mQueueLimit, mNewCount);
        Cursor cur = null;
        mNewQueue.clear();
        try {
            cur = mDb.getDatabase().rawQuery(
                    "SELECT id, due FROM cards WHERE queue = 0 " + _groupLimit() + " ORDER BY due LIMIT " + lim, null);
            while (cur.moveToNext()) {
                mNewQueue.add(new int[] { cur.getInt(0), cur.getInt(1) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        _updateNewCardRatio();
    }


    private int _getNewCard() {
        // We rely on sqlite to return the cards in id order. This may not
        // correspond to the 'ord' order. The alternative would be to do
        // something like due = fid*100+ord, but then we have no efficient way
        // of spacing siblings as we'd need to fetch the fid as well.
        if (!mNewQueue.isEmpty()) {
            int[] item = mNewQueue.remove();
            // move any siblings to the end?
            try {
                if (mDeck.getQconf().getInt("newTodayOrder") == NEW_TODAY_ORD) {
                    int n = mNewQueue.size();
                    while (!mNewQueue.isEmpty() && mNewQueue.getFirst()[1] == item[1]) {
                        mNewQueue.add(mNewQueue.remove());
                        n -= 1;
                        if (n == 0) {
                            // we only have one fact in the queue; stop rotating
                            break;
                        }
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            mNewCount -= 1;
            return item[0];
        }
        return 0;
    }


    private void _updateNewCardRatio() {
        try {
            if (mDeck.getQconf().getInt("newSpread") == NEW_CARDS_DISTRIBUTE) {
                if (mNewCount != 0) {
                    mNewCardModulus = (mNewCount + mRevCount) / mNewCount;
                }
                // if there are cards to review, ensure modulo >= 2
                if (mRevCount != 0) {
                    mNewCardModulus = Math.max(2, mNewCardModulus);
                }
            } else {
                mNewCardModulus = 0;
            }
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
            spread = mDeck.getQconf().getInt("newSpread");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (spread == NEW_CARDS_LAST) {
            return false;
        } else if (spread == NEW_CARDS_FIRST) {
            return true;
        } else if (mNewCardModulus != 0) {
            return (mReps != 0 && mReps % mNewCardModulus == 0);
        } else {
            return false;
        }
    }


    /**
     * Learning queue ***********************************************************************************************
     */

    private void _resetLrnCount() {
        try {
            mLrnCount = (int) mDb.queryScalar("SELECT count() FROM (SELECT id FROM cards WHERE queue = 1 "
                    + _groupLimit() + " AND due < " + (Utils.intNow() + mDeck.getQconf().getInt("collapseTime"))
                    + " LIMIT " + mReportLimit + ")");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _resetLrn() {
        Cursor cur = null;
        mLrnQueue.clear();
        try {
            cur = mDb.getDatabase().rawQuery(
                    "SELECT due, id FROM cards WHERE queue = 1 " + _groupLimit() + " AND due < " + mDayCutoff
                            + " ORDER BY due LIMIT " + mReportLimit, null);
            while (cur.moveToNext()) {
                mLrnQueue.add(new int[] { cur.getInt(0), cur.getInt(1) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    private int _getLrnCard() {
        return _getLrnCard(false);
    }


    private int _getLrnCard(boolean collapse) {
        if (!mLrnQueue.isEmpty()) {
            double cutoff = Utils.now();
            if (collapse) {
                try {
                    cutoff += mDeck.getQconf().getInt("collapseTime");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            if (mLrnQueue.getFirst()[0] < cutoff) {
                int id = mLrnQueue.remove()[1];
                mLrnCount -= 1;
                return id;
            }
        }
        return 0;
    }


    /**
     * @param ease 1=no, 2=yes, 3=remove
     */
    private void _answerLrnCard(Card card, int ease) {
        JSONObject conf = _lrnConf(card);
        int type;
        if (card.getType() == 2) {
            type = 2;
        } else {
            type = 0;
        }
        boolean leaving = false;
        try {
            if (ease == 3) {
                _rescheduleAsRev(card, conf, true);
                leaving = true;
            } else if (ease == 2 && card.getGrade() + 1 >= conf.getJSONArray("delays").length()) {
                _rescheduleAsRev(card, conf, false);
                leaving = true;
            } else {
                card.setCycles(card.getCycles() + 1);
                if (ease == 2) {
                    card.setGrade(card.getGrade() + 1);
                } else {
                    card.setGrade(0);
                }
                int delay = _delayForGrade(conf, card.getGrade());
                if (card.getDue() < Utils.now()) {
                    // not collapsed; add some randomness
                    delay *= (1 + (new Random().nextInt(25) / 100));
                }
                card.setDue((int) (Utils.now() + delay));
                _sortIntoLrn(card.getDue(), card.getId());
                // if it's due within the cutoff, increment count
                if (delay <= mDeck.getQconf().getInt("collapseTime")) {
                    mLrnCount += 1;
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        _logLrn(card, ease, conf, leaving, type);
    }


    /**
     * Sorts a card into the lrn queue
     */
    private void _sortIntoLrn(int due, int id) {
        Iterator i = mLrnQueue.listIterator();
        int idx = 0;
        while (i.hasNext()) {
            if (((int[]) i.next())[0] > due) {
                break;
            } else {
                idx++;
            }
        }
        mLrnQueue.add(idx, new int[] { due, id });
    }


    private int _delayForGrade(JSONObject conf, int grade) {
        int delay;
        JSONArray ja;
        try {
            ja = conf.getJSONArray("delays");
            int len = ja.length();
            if (grade < len) {
                delay = ja.getInt(grade);
            } else {
                delay = ja.getInt(len);
            }
            return delay * 60;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private JSONObject _lrnConf(Card card) {
        JSONObject conf = _cardConf(card);
        try {
            if (card.getType() == 2) {
                return conf.getJSONObject("lapse");
            } else {
                return conf.getJSONObject("new");
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _rescheduleAsRev(Card card, JSONObject conf, boolean early) {
        if (card.getType() == 2) {
            // failed; put back entry due
            card.setDue(card.getEDue());
        } else {
            _rescheduleNew(card, conf, early);
        }
        card.setQueue(2);
        card.setType(2);
    }


    private int _graduatingIvl(Card card, JSONObject conf, boolean early) {
        if (card.getType() == 2) {
            // lapsed card being relearnt
            return card.getIvl();
        }
        int ideal;
        JSONArray ja;
        try {
            ja = conf.getJSONArray("ints");
            if (!early) {
                // graduate
                ideal = ja.getInt(0);
            } else if (card.getCycles() != 0) {
                // remove
                ideal = ja.getInt(2);
            } else {
                // first time bonus
                ideal = ja.getInt(1);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return _adjRevIvl(card, ideal);
    }


    private void _rescheduleNew(Card card, JSONObject conf, boolean early) {
        card.setIvl(_graduatingIvl(card, conf, early));
        card.setDue(mToday + card.getIvl());
        try {
            card.setFactor(conf.getInt("initialFactor"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _logLrn(Card card, int ease, JSONObject conf, boolean leaving, int type) {
        // limit time taken to global setting
        int taken;
        try {
            taken = Math.min(card.timeTaken(), _cardConf(card).getInt("maxTaken") * 1000);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        int lastIvl = -(_delayForGrade(conf, Math.max(0, card.getGrade() - 1)));
        int ivl;
        if (leaving) {
            ivl = card.getIvl();
        } else {
            ivl = -_delayForGrade(conf, card.getGrade());
        }
        int time = (int) (Utils.now() * 1000);
        try {
            log(time, card.getId(), ease, ivl, lastIvl, card.getFactor(), taken, type);
        } catch (SQLiteConstraintException e) {
            log(time + 10, card.getId(), ease, ivl, lastIvl, card.getFactor(), taken, type);
        }
    }


    private void log(int time, int id, int ease, int ivl, int lastIvl, int factor, int taken, int type) {
        // mDb.getDatabase().execSQL("INSERT INTO revlog VALUES (?,?,?,?,?,?,?,?)",
        // new Object[] {time, id, ease,
        // ivl, lastIvl, factor, taken, type});
    }


    private void removeFailed() {
        removeFailed(null);
    }


    /**
     * Remove failed cards from the learning queue.
     */
    private void removeFailed(int[] ids) {
        String extra = "";
        if (ids != null) {
            extra = " AND id IN " + Utils.ids2str(ids);
        }
        mDb.getDatabase().execSQL(
                "UPDATE cards SET " + "due = edue, queue = 2, mod = " + Utils.intNow()
                        + "WHERE queue = 1 AND type = 2 " + extra);
    }


    /**
     * Reviews ***********************************************************************************************
     */

    private void _resetRevCount() {
        mRevCount = (int) mDb.queryScalar("SELECT count() FROM (SELECT id FROM cards " + "WHERE queue = 2 "
                + _groupLimit() + " AND due <= " + mToday + " LIMIT " + mReportLimit + ")");
    }


    private void _resetRev() {
        Cursor cur = null;
        mRevQueue.clear();
        try {
            cur = mDb.getDatabase().rawQuery(
                    "SELECT id FROM cards WHERE queue = 2 " + _groupLimit() + " AND due <= " + mToday + " ORDER BY "
                            + _revOrder() + " LIMIT " + mQueueLimit, null);
            while (cur.moveToNext()) {
                mRevQueue.add(new int[] { cur.getInt(0) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        try {
            if (mDeck.getQconf().getInt("revOrder") == REV_CARDS_RANDOM) {
                Collections.shuffle(mRevQueue, new Random());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private int _getRevCard() {
        if (_haveRevCards()) {
            mRevCount -= 1;
            return mRevQueue.remove()[0];
        } else {
            return 0;
        }
    }


    private boolean _haveRevCards() {
        if (mRevCount != 0) {
            if (mRevQueue.isEmpty()) {
                _resetRev();
            }
            return !mRevQueue.isEmpty();
        } else {
            return false;
        }
    }


    private String _revOrder() {
        try {
            return revOrderStrings[mDeck.getQconf().getInt("revOrder")];
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Answering a review card
     * ***********************************************************************************************
     */

    private void _answerRevCard(Card card, int ease) {
        if (ease == 1) {
            _rescheduleLapse(card);
        } else {
            _rescheduleRev(card, ease);
        }
        _logRev(card, ease);
    }


    private void _rescheduleLapse(Card card) {
        JSONObject conf;
        try {
            conf = _cardConf(card).getJSONObject("lapse");
            card.setLapses(card.getLapses() + 1);
            card.setLastIvl(card.getIvl());
            card.setIvl(_nextLapseIvl(card, conf));
            card.setFactor(Math.max(1300, card.getFactor() - 200));
            card.setDue(mToday + card.getIvl());
            // put back in learn queue?
            if (conf.getString("relearn").toLowerCase().equals("true")) {
                card.setEDue(card.getDue());
                card.setDue((int) (_delayForGrade(conf, 0) + Utils.now()));
                card.setQueue(1);
                mLrnCount += 1;
                _sortIntoLrn(card.getDue(), card.getId());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // leech?
        _checkLeech(card, conf);
    }


    private int _nextLapseIvl(Card card, JSONObject conf) {
        try {
            return (int) (card.getIvl() * conf.getInt("mult")) + 1;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _rescheduleRev(Card card, int ease) {
        // update interval
        card.setLastIvl(card.getIvl());
        _updateRevIvl(card, ease);
        // then the rest
        card.setFactor(Math.max(1300, card.getFactor() + factorAdditionValues[ease - 2]));
        card.setDue(mToday + card.getIvl());
    }


    private void _logRev(Card card, int ease) {
        int taken;
        try {
            taken = Math.min(card.timeTaken(), _cardConf(card).getInt("maxTaken") * 1000);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        int time = (int) (Utils.now() * 1000);
        try {
            log(time, card.getId(), ease, card.getId(), card.getLastIvl(), card.getFactor(), taken, 1);
        } catch (SQLiteConstraintException e) {
            log(time + 10, card.getId(), ease, card.getId(), card.getLastIvl(), card.getFactor(), taken, 1);
        }
    }


    /**
     * Interval management
     * ***********************************************************************************************
     */

    /**
     * Ideal next interval for CARD, given EASE.
     */
    private int _nextRevIvl(Card card, int ease) {
        int delay = _daysLate(card);
        double interval = 0;
        JSONObject conf = _cardConf(card);
        double fct = card.getFactor() / 1000.0;
        if (ease == 2) {
            interval = (card.getIvl() + delay / 4) * 1.2;
        } else if (ease == 3) {
            interval = (card.getIvl() + delay / 2) * fct;
        } else if (ease == 4) {
            try {
                interval = (card.getIvl() + delay) * fct * conf.getJSONObject("rev").getDouble("ease4");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        // must be at least one day greater than previous interval; two if easy
        return Math.max(card.getIvl() + (ease == 4 ? 2 : 1), (int) interval);
    }


    /**
     * Number of days later than scheduled.
     */
    private int _daysLate(Card card) {
        return Math.max(0, mToday - card.getDue());
    }


    /**
     * Update CARD's interval, trying to avoid siblings.
     */
    private void _updateRevIvl(Card card, int ease) {
        int idealIvl = _nextRevIvl(card, ease);
        card.setIvl(_adjRevIvl(card, idealIvl));
    }


    /**
     * Given IDEALIVL, return an IVL away from siblings.
     */
    private int _adjRevIvl(Card card, int idealIvl) {
        int idealDue = mToday + idealIvl;
        JSONObject conf;
        try {
            conf = _cardConf(card).getJSONObject("rev");
            // find sibling positions
            ArrayList<Integer> dues = mDb.queryColumn(Integer.class, "SELECT due FROM cards WHERE fid = "
                    + card.getFId() + " AND queue = 2 AND id != " + card.getId(), 0);
            if (dues.size() == 0 || !dues.contains(idealDue)) {
                return idealIvl;
            } else {
                int leeway = Math.max(conf.getInt("minSpace"), (int) (idealIvl * conf.getInt("fuzz")));
                int fudge = 0;
                // do we have any room to adjust the interval?
                if (leeway != 0) {
                    // loop through possible due dates for an empty one
                    for (int diff = 1; diff <= leeway + 1; diff++) {
                        // ensure we're due at least tomorrow
                        if (idealDue - diff >= 1 && !dues.contains(idealDue - diff)) {
                            fudge = -diff;
                            break;
                        } else if (!dues.contains(idealDue + diff)) {
                            fudge = diff;
                            break;
                        }
                    }
                }
                return idealIvl + fudge;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Leeches ***********************************************************************************************
     */

    /**
     * Leech handler.
     * 
     * @return True if card was a leech.
     */
    private void _checkLeech(Card card, JSONObject conf) {
        int lf;
        try {
            lf = conf.getInt("leechFails");
            if (lf == 0) {
                return;
            }
            // if over threshold or every half threshold reps after that
            if (lf >= card.getLapses() && (card.getLapses() - lf) % Math.max(lf / 2, 1) == 0) {
                // add a leech tag
                Fact f = card.getFact();
                f.addTag("leech");
                f.flush();
                card.setLeechFlag(true);
                // handle
                if (conf.getInt("leechAction") == LEECH_ACTION_SUSPEND) {
                    suspendCards(new int[] { card.getId() });
                    card.setSuspendedFlag(true);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Tools ***********************************************************************************************
     */

    /**
     * Update group conf cache.
     */
    private void _resetConf() {
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().rawQuery("SELECT id, gcid FROM groups", null);
            while (cur.moveToNext()) {
                mGroupConfs.put(cur.getInt(0), cur.getInt(1));
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        mConfCache.clear();
    }


    private JSONObject _cardConf(Card card) {
        int id = mGroupConfs.get(card.getGId());
        if (!mConfCache.containsKey(id)) {
            mConfCache.put(id, mDeck.groupConf(id));
        }
        return mConfCache.get(id);
    }


    public String _groupLimit() {
        JSONArray l;
        try {
            l = mDeck.getQconf().getJSONArray("groups");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (l.length() == 0) {
            // everything
            return "";
        }
        return " AND gid IN " + Utils.ids2str(l);
    }


    /**
     * Daily cutoff ***********************************************************************************************
     */

    /**
     * 
     */
    private void _updateCutoff() {
        // days since deck created
        mToday = (int) ((Utils.now() - mDeck.getCrt()) / 86400);
        // end of day cutoff
        mDayCutoff = mDeck.getCrt() + (mToday + 1) * 86400;
    }


    /**
     * check if the day has rolled over
     */
    private void _checkDay() {
        if (Utils.now() > mDayCutoff) {
            _updateCutoff();
            reset();
        }
    }


    /**
     * Deck finished state
     * ***********************************************************************************************
     */

    /**
     * Number of cards in the learning queue due tomorrow.
     */
    public int lrnTomorrow() {
        return (int) mDb.queryScalar("SELECT count() FROM cards WHERE queue = 1 AND due < " + mDayCutoff + 86400);
    }


    /**
     * Number of reviews due tomorrow.
     */
    public int revTomorrow() {
        return (int) mDb.queryScalar("SELECT count() FROM cards WHERE queue = 2 AND due = " + (mToday + 1)
                + _groupLimit());
    }


    /**
     * Number of new cards tomorrow.
     */
    public int newTomorrow() {
        int lim;
        try {
            lim = mDeck.getQconf().getInt("newPerDay");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return (int) mDb.queryScalar("SELECT count() FROM (SELECT id FROM cards WHERE queue = 0 " + _groupLimit()
                + " LIMIT " + lim + ")");
    }


    /**
     * Next time reports ***********************************************************************************************
     */

    /**
     * Return the next interval for CARD as a string.
     */
    public String nextIvlStr(Card card, int ease) {
        return Utils.fmtTimeSpan(nextIvl(card, ease), false);
    }


    /**
     * Return the next interval for CARD, in seconds.
     */
    public int nextIvl(Card card, int ease) {
        try {
            if (card.getQueue() == 0 || card.getQueue() == 1) {
                return _nextLrnIvl(card, ease);
            } else if (ease == 1) {
                // lapsed
                JSONObject conf = _cardConf(card).getJSONObject("lapse");
                if (conf.getString("relearn").toLowerCase().equals("true")) {
                    return conf.getJSONArray("delays").getInt(0) * 60;
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
        JSONObject conf = _lrnConf(card);
        if (ease == 1) {
            // grade 0
            return _delayForGrade(conf, 0);
        } else if (ease == 3) {
            // early removal
            return _graduatingIvl(card, conf, true) * 86400;
        } else {
            int grade = card.getGrade() + 1;
            try {
                if (grade >= conf.getJSONArray("delays").length()) {
                    // graduate
                    return _graduatingIvl(card, conf, false) * 86400;
                } else {
                    // next level
                    return _delayForGrade(conf, grade);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * Suspending ***********************************************************************************************
     */

    /**
     * Suspend cards.
     */
    private void suspendCards(int[] ids) {
        removeFailed(ids);
        mDb.getDatabase().execSQL(
                "UPDATE cards SET queue = -1, mod = " + Utils.intNow() + " WHERE id IN " + Utils.ids2str(ids));
    }


    /**
     * Unsuspend cards
     */
    private void unsuspend(int[] ids) {
        mDb.getDatabase().execSQL(
                "UPDATE cards SET queue = type, mod = " + Utils.intNow() + " WHERE queue = -1 AND id IN "
                        + Utils.ids2str(ids));
    }


    /**
     * Bury all cards for fact until next session.
     */
    private void buryFact(int fid) {
        mDeck.setDirty();
        int[] cids;
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().rawQuery("SELECT id FROM cards WHERE fid = " + fid, null);
            cids = new int[cur.getCount()];
            while (cur.moveToNext()) {
                cids[cur.getPosition()] = cur.getInt(0);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        removeFailed(cids);
        mDb.getDatabase().execSQL("UPDATE cards SET queue = -2 WHERE fidid = " + fid);
    }


    /**
     * Counts ***********************************************************************************************
     */

    /**
     * Time spent learning today, in seconds.
     */
    public int timeToday(int fid) {
        return (int) mDb.queryScalar("SELECT sum(taken / 1000.0) FROM revlog WHERE time > 1000 * "
                + (mDayCutoff - 86400));
        // TODO: check for 0?
    }


    /**
     * Number of cards answered today.
     */
    public int repsToday(int fid) {
        return (int) mDb.queryScalar("SELECT count() FROM revlog WHERE time > " + (mDayCutoff - 86400));
    }


    /**
     * Number of mature cards.
     */
    public int matureCardCount() {
        return (int) mDb.queryScalar("SELECT count() FROM cards WHERE ivl >= " + 21);
    }


    /**
     * Number of mature cards.
     */
    public int totalNewCardCount() {
        return (int) mDb.queryScalar("SELECT count() FROM cards WHERE queue = 0");
    }


    /**
     * Dynamic indices ***********************************************************************************************
     */

    private void updateDynamicIndices() {
        // Log.i(AnkiDroidApp.TAG, "updateDynamicIndices - Updating indices...");
        // // determine required columns
        // if (mDeck.getQconf().getInt("revOrder")) {
        //    		
        // }
        // HashMap<String, String> indices = new HashMap<String, String>();
        // indices.put("intervalDesc", "(queue, interval desc, factId, due)");
        // indices.put("intervalAsc", "(queue, interval, factId, due)");
        // indices.put("randomOrder", "(queue, factId, ordinal, due)");
        // // new cards are sorted by due, not combinedDue, so that even if
        // // they are spaced, they retain their original sort order
        // indices.put("dueAsc", "(queue, due, factId, due)");
        // indices.put("dueDesc", "(queue, due desc, factId, due)");
        //
        // ArrayList<String> required = new ArrayList<String>();
        // if (mRevCardOrder == REV_CARDS_OLD_FIRST) {
        // required.add("intervalDesc");
        // }
        // if (mRevCardOrder == REV_CARDS_NEW_FIRST) {
        // required.add("intervalAsc");
        // }
        // if (mRevCardOrder == REV_CARDS_RANDOM) {
        // required.add("randomOrder");
        // }
        // if (mRevCardOrder == REV_CARDS_DUE_FIRST || mNewCardOrder == NEW_CARDS_OLD_FIRST
        // || mNewCardOrder == NEW_CARDS_RANDOM) {
        // required.add("dueAsc");
        // }
        // if (mNewCardOrder == NEW_CARDS_NEW_FIRST) {
        // required.add("dueDesc");
        // }
        //
        // // Add/delete
        // boolean analyze = false;
        // Set<Entry<String, String>> entries = indices.entrySet();
        // Iterator<Entry<String, String>> iter = entries.iterator();
        // String indexName = null;
        // while (iter.hasNext()) {
        // Entry<String, String> entry = iter.next();
        // indexName = "ix_cards_" + entry.getKey();
        // if (required.contains(entry.getKey())) {
        // Cursor cursor = null;
        // try {
        // cursor = getDB().getDatabase().rawQuery(
        // "SELECT 1 FROM sqlite_master WHERE name = '" + indexName + "'", null);
        // if ((!cursor.moveToNext()) || (cursor.getInt(0) != 1)) {
        // getDB().getDatabase().execSQL("CREATE INDEX " + indexName + " ON cards " + entry.getValue());
        // analyze = true;
        // }
        // } finally {
        // if (cursor != null) {
        // cursor.close();
        // }
        // }
        // } else {
        // getDB().getDatabase().execSQL("DROP INDEX IF EXISTS " + indexName);
        // }
        // }
        // if (analyze) {
        // getDB().getDatabase().execSQL("ANALYZE");
        // }
    }


    /**
     * Resetting ***********************************************************************************************
     */

    /**
     * Repositioning new cards
     * ***********************************************************************************************
     */

    /**
     * ***********************************************************************************************
     */

    public String getName() {
        return mName;
    }


    public int getToday() {
        return mToday;
    }


    public int getDayCutoff() {
        return mDayCutoff;
    }

}
