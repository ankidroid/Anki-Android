/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Pair;
import com.ichi2.anki.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

public class Sched {

    // whether new cards should be mixed with reviews, or shown first or last
    public static final int NEW_CARDS_DISTRIBUTE = 0;
    public static final int NEW_CARDS_LAST = 1;
    public static final int NEW_CARDS_FIRST = 2;

    // new card insertion order
    public static final int NEW_CARDS_RANDOM = 0;
    public static final int NEW_CARDS_DUE = 1;
    // review card sort order
    public static final int REV_CARDS_RANDOM = 0;
    public static final int REV_CARDS_OLD_FIRST = 1;
    public static final int REV_CARDS_NEW_FIRST = 2;

    // removal types
    public static final int REM_CARD = 0;
    public static final int REM_NOTE = 1;
    public static final int REM_DECK = 2;

    // count display
    public static final int COUNT_ANSWERED = 0;
    public static final int COUNT_REMAINING = 1;

    // media log
    public static final int MEDIA_ADD = 0;
    public static final int MEDIA_REM = 1;
    

    // dynamic deck order
    public static final int DYN_OLDEST = 0;
    public static final int DYN_RANDOM = 1;
    public static final int DYN_SMALLINT = 2;
    public static final int DYN_BIGINT = 3;
    public static final int DYN_LAPSES = 4;
    public static final int DYN_ADDED = 5;
    public static final int DYN_DUE = 6;
    public static final int DYN_REVADDED = 7;

    // model types
    public static final int MODEL_STD = 0;
    public static final int MODEL_CLOZE = 1;

    private static final String[] REV_ORDER_STRINGS = { "ivl DESC", "ivl" };
    private static final int[] FACTOR_ADDITION_VALUES = { -150, 0, 150 };

    // not in libanki
    public static final int DECK_INFORMATION_NAMES = 0;
    public static final int DECK_INFORMATION_SIMPLE_COUNTS = 1;
    public static final int DECK_INFORMATION_EXTENDED_COUNTS = 2;

    private Collection mCol;
    private String mName = "std";
    private int mQueueLimit;
    private int mReportLimit;
    public int mReps;
    private boolean mHaveQueues;
    private int mToday;
    public long mDayCutoff;
    private boolean mHaveCustomStudy;
    private boolean mSpreadRev = true;

    private int mNewCount;
    private int mLrnCount;
    private int mRevCount;

    private int mNewCardModulus;

    private double[] mEtaCache = new double[] { -1, -1, -1, -1 };

    // Queues
    private LinkedList<long[]> mNewQueue;
    private LinkedList<long[]> mLrnQueue;
    private LinkedList<long[]> mLrnDayQueue;
    private LinkedList<long[]> mRevQueue;

    private LinkedList<Long> mNewDids;
    private LinkedList<Long> mLrnDids;
    private LinkedList<Long> mRevDids;

    private TreeMap<Integer, Integer> mGroupConfs;
    private TreeMap<Integer, JSONObject> mConfCache;

    private HashMap<Long, Pair<String[], long[]>> mCachedDeckCounts;

    /**
     * queue types: 0=new/cram, 1=lrn, 2=rev, 3=day lrn, -1=suspended, -2=buried revlog types: 0=lrn, 1=rev, 2=relrn,
     * 3=cram positive intervals are in positive revlog intervals are in days (rev), negative in seconds (lrn)
     */

    public Sched(Collection col) {
        mCol = col;
        mQueueLimit = 50;
        mReportLimit = 1000;
        mReps = 0;
        mHaveCustomStudy = true;
        mHaveQueues = false;
        _updateCutoff();

        // Initialise queues
        mNewQueue = new LinkedList<long[]>();
        mLrnQueue = new LinkedList<long[]>();
        mLrnDayQueue = new LinkedList<long[]>();
        mRevQueue = new LinkedList<long[]>();
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
            mReps += 1;
            card.startTimer();
        }
        return card;
    }

    /* NOT IN LIBANKI */
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


    public void reset() {
        _updateCutoff();
        _resetLrn();
        _resetRev();
        _resetNew();
        mHaveQueues = true;
    }


    public boolean answerCard(Card card, int ease) {
        Log.i(AnkiDroidApp.TAG, "answerCard - ease:" + ease);
        boolean isLeech = false;
        mCol.markUndo(Collection.UNDO_REVIEW, new Object[]{card});
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
            isLeech = _answerRevCard(card, ease);
            _updateStats(card, "rev");
        } else {
            throw new RuntimeException("Invalid queue");
        }
        _updateStats(card, "time", card.timeTaken());
        card.setMod(Utils.intNow());
        card.setUsn(mCol.usn());
        card.flushSched();
        return isLeech;
    }


    public int[] counts() {
        return counts(null);
    }


    public int[] counts(Card card) {
        int[] counts = new int[3];
        counts[0] = mNewCount;
        counts[1] = mLrnCount;
        counts[2] = mRevCount;
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
            JSONObject conf = _lapseConf(card);
            try {
                if (card.getType() == 0 || conf.getJSONArray("delays").length() > 1) {
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


    /**
     * Unbury cards when closing.
     */
    public void unburyCards() {
    	boolean mod = mCol.getDb().getMod();
        mCol.getDb().execute("UPDATE cards SET queue = type WHERE queue = -2");
        mCol.getDb().setMod(mod);
    }


    // /**
    // * A very rough estimate of time to review.
    // */
    // public int eta() {
    // Cursor cur = null;
    // int cnt = 0;
    // int sum = 0;
    // try {
    // cur = mDb.getDatabase().rawQuery(
    // "SELECT count(), sum(taken) FROM (SELECT * FROM revlog " +
    // "ORDER BY time DESC LIMIT 10)", null);
    // if (cur.moveToFirst()) {
    // cnt = cur.getInt(0);
    // sum = cur.getInt(1);
    // }
    // } finally {
    // if (cur != null && !cur.isClosed()) {
    // cur.close();
    // }
    // }
    // if (cnt == 0) {
    // return 0;
    // }
    // double avg = sum / ((float) cnt);
    // int[] c = counts();
    // return (int) ((avg * c[0] * 3 + avg * c[1] * 3 + avg * c[2]) / 1000.0);
    // }

    /**
     * Rev/lrn/time daily stats *************************************************
     * **********************************************
     */

    private void _updateStats(Card card, String type) {
        _updateStats(card, type, 1);
    }


    public void _updateStats(Card card, String type, long l) {
        String key = type + "Today";
        long did = card.getDid();
        ArrayList<JSONObject> list = mCol.getDecks().parents(did);
        list.add(mCol.getDecks().get(did));
        for (JSONObject g : list) {
            try {
                JSONArray a = g.getJSONArray(key);
                // add
                a.put(1, a.getLong(1) + l);
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


    private int _walkingCount() {
        return _walkingCount(null, null, null);
    }


    private int _walkingCount(LinkedList<Long> dids) {
        return _walkingCount(dids, null, null);
    }


    private int _walkingCount(Method limFn, Method cntFn) {
        return _walkingCount(null, limFn, cntFn);
    }


    private int _walkingCount(LinkedList<Long> dids, Method limFn, Method cntFn) {
        if (dids == null) {
            dids = mCol.getDecks().active();
        }
        int tot = 0;
        HashMap<Long, Integer> pcounts = new HashMap<Long, Integer>();
        // for each of the active decks
        try {
            for (long did : dids) {
                // get the individual deck's limit
                int lim = 0;
                // if (limFn != null) {
                lim = (Integer) limFn.invoke(Sched.this, mCol.getDecks().get(did));
                // }
                if (lim == 0) {
                    continue;
                }
                // check the parents
                ArrayList<JSONObject> parents = mCol.getDecks().parents(did);
                for (JSONObject p : parents) {
                    // add if missing
                    long id = p.getLong("id");
                    if (!pcounts.containsKey(id)) {
                        pcounts.put(id, (Integer) limFn.invoke(Sched.this, p));
                    }
                    // take minimum of child and parent
                    lim = Math.min(pcounts.get(id), lim);
                }
                // see how many cards we actually have
                int cnt = 0;
                // if (cntFn != null) {
                cnt = (Integer) cntFn.invoke(Sched.this, did, lim);
                // }
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

    /** LIBANKI: not in libanki */
    public Object[] deckCounts() {
        TreeSet<Object[]> decks = deckDueTree(0);
        int[] counts = new int[] { 0, 0, 0 };
        for (Object[] deck : decks) {
            if (((String[]) deck[0]).length == 1) {
                counts[0] += (Integer) deck[2];
                counts[1] += (Integer) deck[3];
                counts[2] += (Integer) deck[4];
            }
        }
        TreeSet<Object[]> decksNet = new TreeSet<Object[]>(new DeckNameCompare());
        for (Object[] d : decks) {
        	try {
        		boolean show = true;
        		for (JSONObject o : mCol.getDecks().parents((Long) d[1])) {
        			if (o.getBoolean("collapsed")) {
        				show = false;
        				break;
        			}
        		}
        		if (show) {
        			JSONObject deck = mCol.getDecks().get((Long) d[1]);
        			if (deck.getBoolean("collapsed")) {
        				String[] name = (String[]) d[0];
        				name[name.length - 1] = name[name.length - 1] + " (+)";
        				d[0] = name;
        			}
    				decksNet.add(new Object[]{d[0], d[1], d[2], d[3], d[4], deck.getInt("dyn") != 0});
        		}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
        }
        return new Object[] { decksNet, eta(counts), mCol.cardCount() };
    }

    public boolean getSpreadRev() {
        return mSpreadRev;
    }

    public void setSpreadRev(boolean mSpreadRev) {
        this.mSpreadRev = mSpreadRev;
    }

    public class DeckDueListComparator implements Comparator<JSONObject> {
        public int compare(JSONObject o1, JSONObject o2) {
            try {
				return o1.getString("name").compareTo(o2.getString("name"));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
        }
    }


    /**
     * Returns [deckname, did, rev, lrn, new]
     */
    public ArrayList<Object[]> deckDueList(int counts) {
        _checkDay();
        mCol.getDecks().recoverOrphans();
        unburyCards();
        ArrayList<JSONObject> decks = mCol.getDecks().all();
        Collections.sort(decks, new DeckDueListComparator());
        HashMap<String, Integer[]> lims = new HashMap<String, Integer[]>();
        ArrayList<Object[]> data = new ArrayList<Object[]>();
        try {
            for (JSONObject deck : decks) {
            	// if we've already seen the exact same deck name, remove the
            	// invalid duplicate and reload
            	if (lims.containsKey(deck.getString("name"))) {
            		mCol.getDecks().rem(deck.getLong("id"), false, true);
            		return deckDueList(counts);
            	}
            	String p;
            	String[] parts = deck.getString("name").split("::");
            	if (parts.length < 2) {
            		p = "";
            	} else {
            		StringBuilder sb = new StringBuilder();
            		for (int i = 0; i < parts.length - 1; i++) {
            			sb.append(parts[i]);
            			if (i < parts.length - 2) {
            				sb.append("::");
            			}
            		}
            		p = sb.toString();
            	}
            	// new
            	int nlim = _deckNewLimitSingle(deck);
            	if (p.length() > 0) {
            		if (!lims.containsKey(p)) {
                		// if parent was missing, this deck is invalid, and we need to reload the deck list
                		mCol.getDecks().rem(deck.getLong("id"), false, true);
            			return deckDueList(counts);
            		}
            		nlim = Math.min(nlim, lims.get(p)[0]);
            	}
            	int newC = _newForDeck(deck.getLong("id"), nlim);
            	// learning
            	int lrn = _lrnForDeck(deck.getLong("id"));
            	// reviews
            	int rlim = _deckRevLimitSingle(deck);
            	if (p.length() > 0) {
            		rlim = Math.min(rlim,  lims.get(p)[1]);
            	}
            	int rev = _revForDeck(deck.getLong("id"), rlim);
            	// save to list
            	// LIBANKI: order differs from libanki (here: new, lrn, rev)
            	data.add(new Object[]{deck.getString("name"), deck.getLong("id"), newC, lrn, rev});
            	// add deck as a parent
            	lims.put(deck.getString("name"), new Integer[]{nlim, rlim});
            }        	
        } catch (JSONException e) {
        	throw new RuntimeException(e);
        }
        return data;
    }


    public TreeSet<Object[]> deckDueTree(int counts) {
        return _groupChildren(deckDueList(counts));
    }


    private TreeSet<Object[]> _groupChildren(ArrayList<Object[]> grps) {
        TreeSet<Object[]> set = new TreeSet<Object[]>(new DeckNameCompare());
        // first, split the group names into components
        for (Object[] g : grps) {
            set.add(new Object[] { ((String) g[0]).split("::"), g[1], g[2], g[3], g[4] });
        }
        return _groupChildrenMain(set);
    }


    private TreeSet<Object[]> _groupChildrenMain(TreeSet<Object[]> grps) {
    	return _groupChildrenMain(grps, 0);
    }
    private TreeSet<Object[]> _groupChildrenMain(TreeSet<Object[]> grps, int depth) {
        TreeSet<Object[]> tree = new TreeSet<Object[]>(new DeckNameCompare());
        // group and recurse
        Iterator<Object[]> it = grps.iterator();
        Object[] tmp = null;
        while (tmp != null || it.hasNext()) {
            Object[] head;
            if (tmp != null) {
                head = tmp;
                tmp = null;
            } else {
                head = it.next();
            }
            String[] title = (String[]) head[0];
            long did = (Long) head[1];
            int newCount = (Integer) head[2];
            int lrnCount = (Integer) head[3];
            int revCount = (Integer) head[4];
            TreeSet<Object[]> children = new TreeSet<Object[]>(new DeckNameCompare());
            while (it.hasNext()) {
                Object[] o = it.next();
                if (((String[])o[0])[depth].equals(title[depth])) {
                    // add to children
                    children.add(o);
                } else {
                    // proceed with this as head
                    tmp = o;
                    break;
                }
            }
            children = _groupChildrenMain(children, depth + 1);
            // tally up children counts, but skip deeper sub-decks
            for (Object[] ch : children) {
               if (((String[])ch[0]).length == ((String[])head[0]).length+1) {
                  newCount += (Integer)ch[2];
                  lrnCount += (Integer)ch[3];
                  revCount += (Integer)ch[4];
               }
            }
            // limit the counts to the deck's limits
            JSONObject conf = mCol.getDecks().confForDid(did);
            JSONObject deck = mCol.getDecks().get(did);
            try {
                if (conf.getInt("dyn") == 0) {
                    revCount = Math.max(0, Math.min(revCount, conf.getJSONObject("rev").getInt("perDay") - deck.getJSONArray("revToday").getInt(1)));
                    newCount = Math.max(0, Math.min(newCount, conf.getJSONObject("new").getInt("perDay") - deck.getJSONArray("newToday").getInt(1)));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            tree.add(new Object[] {title, did, newCount, lrnCount, revCount,
            children});
        }
        TreeSet<Object[]> result = new TreeSet<Object[]>(new DeckNameCompare());
        for (Object[] t : tree) {
            result.add(new Object[]{t[0], t[1], t[2], t[3], t[4]});
            result.addAll((TreeSet<Object[]>) t[5]);
        }
        return result;
    }

    /**
     * Getting the next card ****************************************************
     * *******************************************
     */

    /**
     * Return the next due card, or None.
     */
    private Card _getCard() {
        // learning card due?
        Card c = _getLrnCard();
        if (c != null) {
            return c;
        }
        // new first, or time for one?
        if (_timeForNewCard()) {
            return _getNewCard();
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


    //
    // /** LIBANKI: not in libanki */
    // public boolean removeCardFromQueues(Card card) {
    // long id = card.getId();
    // Iterator<long[]> i = mNewQueue.iterator();
    // while (i.hasNext()) {
    // long cid = i.next()[0];
    // if (cid == id) {
    // i.remove();
    // mNewCount -= 1;
    // return true;
    // }
    // }
    // i = mLrnQueue.iterator();
    // while (i.hasNext()) {
    // long cid = i.next()[1];
    // if (cid == id) {
    // i.remove();
    // mLrnCount -= card.getLeft();
    // return true;
    // }
    // }
    // i = mLrnDayQueue.iterator();
    // while (i.hasNext()) {
    // long cid = i.next()[1];
    // if (cid == id) {
    // i.remove();
    // mLrnCount -= card.getLeft();
    // return true;
    // }
    // }
    // i = mRevQueue.iterator();
    // while (i.hasNext()) {
    // long cid = i.next()[0];
    // if (cid == id) {
    // i.remove();
    // mRevCount -= 1;
    // return true;
    // }
    // }
    // return false;
    // }

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
            mNewQueue.clear();
            Cursor cur = null;
            if (lim != 0) {
                try {
                    cur = mCol
                            .getDb()
                            .getDatabase()
                            .rawQuery("SELECT id, due FROM cards WHERE did = " + did + " AND queue = 0 LIMIT " + lim,
                                    null);
                    while (cur.moveToNext()) {
                        mNewQueue.add(new long[] { cur.getLong(0), cur.getLong(1) });
                    }
                } finally {
                    if (cur != null && !cur.isClosed()) {
                        cur.close();
                    }
                }
                if (!mNewQueue.isEmpty()) {
                    return true;
                }
            }
            // nothing left in the deck; move to next
            mNewDids.remove();
        }
        return false;
    }


    private Card _getNewCard() {
        if (!_fillNew()) {
            return null;
        }
        long[] item = mNewQueue.remove();
        // move any siblings to the end?
        try {
            JSONObject conf = mCol.getDecks().confForDid(mNewDids.getFirst());
            if (conf.getInt("dyn") != 0 || conf.getJSONObject("new").getBoolean("separate")) {
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
        return mCol.getCard(item[0]);
    }


    private void _updateNewCardRatio() {
        try {
            if (mCol.getConf().getInt("newSpread") == NEW_CARDS_DISTRIBUTE) {
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
        if (spread == NEW_CARDS_LAST) {
            return false;
        } else if (spread == NEW_CARDS_FIRST) {
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
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = 0 LIMIT " + lim + ")", false);
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
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE id IN (SELECT id FROM cards WHERE did IN " + Utils.ids2str(mCol.getDecks().active()) + " AND queue = 0 LIMIT " + mReportLimit + ")", false);
    }

    /**
     * Learning queues *********************************************************** ************************************
     */

    private void _resetLrnCount() {
        mLrnCount = _cntFnLrn(_deckLimit());
        // day
        mLrnCount += (int) mCol.getDb().queryScalar(
                "SELECT count() FROM cards WHERE did IN " + _deckLimit() + " AND queue = 3 AND due <= " + mToday
                        + " LIMIT " + mReportLimit, false);
    }


    private int _cntFnLrn(String dids) {
        return (int) mCol.getDb().queryScalar(
                "SELECT sum(left / 1000) FROM (SELECT left FROM cards WHERE did IN " + dids
                        + " AND queue = 1 AND due < " + mDayCutoff + " LIMIT " + mReportLimit + ")", false);
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
            Collections.sort(mLrnQueue, new DueComparator());
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
                    mLrnDayQueue.add(new long[] { cur.getLong(0) });
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
            return mCol.getCard(mLrnDayQueue.remove()[0]);
        }
        return null;
    }


    /**
     * @param ease 1=no, 2=yes, 3=remove
     */
    private void _answerLrnCard(Card card, int ease) {
        // ease 1=no, 2=yes, 3=remove
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
                        card.setIvl(Math.max(1, (int) (card.getIvl() * conf.getDouble("mult"))));
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
        // review cards in realearning
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
                            + " LIMIT " + mReportLimit + ")", false);
            return cnt + mCol.getDb().queryScalar(
                    "SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did
                            + " AND queue = 3 AND due < " + mToday
                            + " LIMIT " + mReportLimit + ")", false);
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
    	return mCol.getDb().queryScalar("SELECT count() FROM (SELECT 1 FROM cards WHERE did = " + did + " AND queue = 2 AND due <= " + mToday + " LIMIT " + lim + ")", false);
    }


    private void _resetRevCount() {
        try {
            mRevCount = _walkingCount(Sched.class.getDeclaredMethod("_deckRevLimitSingle", JSONObject.class),
                    Sched.class.getDeclaredMethod("_cntFnRev", long.class, int.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


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
            mRevQueue.clear();
            Cursor cur = null;
            if (lim != 0) {
                // fill the queue with the current did
                try {
                    cur = mCol
                            .getDb()
                            .getDatabase()
                            .rawQuery(
                                    "SELECT id FROM cards WHERE did = " + did + " AND queue = 2 AND due <= " + mToday
                                            + " LIMIT " + lim, null);
                    while (cur.moveToNext()) {
                        mRevQueue.add(new long[] { cur.getLong(0) });
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
        return false;
    }


    private Card _getRevCard() {
        if (_fillRev()) {
            mRevCount -= 1;
            return mCol.getCard(mRevQueue.remove()[0]);
        } else {
            return null;
        }
    }

    public int totalRevForCurrentDeck() {
        return mCol.getDb().queryScalar(String.format(Locale.US,
        		"SELECT count() FROM cards WHERE did IN (SELECT id FROM cards WHERE did IN %s AND queue = 2 AND due <= %d LIMIT %s)",
        		Utils.ids2str(mCol.getDecks().active()), mToday, mReportLimit), false);
    }


    /**
     * Answering a review card **************************************************
     * *********************************************
     */

    private boolean _answerRevCard(Card card, int ease) {
        int delay = 0;
        boolean leech = false;
        if (ease == 1) {
            Pair<Integer, Boolean> res = _rescheduleLapse(card);
            delay = res.first;
            leech = res.second;
        } else {
            _rescheduleRev(card, ease);
        }
        _logRev(card, ease, delay);
        return leech;
    }


    private Pair<Integer, Boolean> _rescheduleLapse(Card card) {
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
            	return new Pair<Integer, Boolean>(delay, true);
            }
            // if no relearning steps, nothing to do
            if (conf.getJSONArray("delays").length() == 0) {
            	return new Pair<Integer, Boolean>(delay, false);
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
                return new Pair<Integer, Boolean>(delay, false);
            } else {
            	// day learn queue
            	long ahead = ((card.getDue() - mDayCutoff) / 86400) + 1;
            	card.setDue(mToday + ahead);
            	card.setQueue(3);
            }
        	return new Pair<Integer, Boolean>(delay, true);
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
            fuzz = (int) (ivl * 0.25);
        } else if (ivl < 30) {
            fuzz = (int) (ivl * 0.15);
        } else {
            fuzz = (int) (ivl * 0.05);
        }
        // fuzz at least a day
        fuzz = Math.max(fuzz, 1);
        return new int[]{ivl - fuzz, ivl + fuzz};
    }

    /** Integer interval after interval factor and prev+1 constraints applied */
    private int _constrainedIvl(int ivl, JSONObject conf, double prev) {
    	double newIvl = ivl;
        try {
        	newIvl = ivl * conf.getDouble("ivlFct");
        } catch (JSONException e) {
        	// nothing;
        }
        return (int) Math.max(newIvl, prev + 1);
    }


    /**
     * Number of days later than scheduled.
     */
    private long _daysLate(Card card) {
        long due = card.getODid() != 0 ? card.getODue() : card.getDue();
        return Math.max(0, mToday - due);
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
        if (getSpreadRev()) {
            idealIvl = _fuzzedIvl(idealIvl);
        }
        int idealDue = mToday + idealIvl;
        JSONObject conf;
        try {
            conf = _revConf(card);
            // find sibling positions
            ArrayList<Integer> dues = mCol.getDb()
                    .queryColumn(
                            Integer.class,
                            "SELECT due FROM cards WHERE nid = " + card.getNid() + " AND type = 2 AND id != "
                                    + card.getId(), 0);
            if (dues.size() == 0 || !dues.contains(idealDue)) {
                return idealIvl;
            } else {
                int leeway = Math.max(conf.getInt("minSpace"), (int) (idealIvl * conf.getDouble("fuzz")));
                int fudge = 0;
                // do we have any room to adjust the interval?
                if (leeway != 0) {
                    // loop through possible due dates for an empty one
                    for (int diff = 1; diff < leeway + 1; diff++) {
                        // ensure we're due at least tomorrow
                        if ((idealIvl - diff >= 1) && !dues.contains(idealDue - diff)) {
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
                Log.e(AnkiDroidApp.TAG, "error: deck is not a dynamic deck");
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
            search += " -is:suspended -deck:filtered";
            ids = mCol.findCards(search, orderlimit);
            if (ids.isEmpty()) {
                return ids;
            }
            // move the cards over
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
        // move out of cram queue
        mCol.getDb().execute(
                "UPDATE cards SET did = odid, queue = (CASE WHEN type = 1 THEN 0 "
                        + "ELSE type END), type = (CASE WHEN type = 1 THEN 0 ELSE type END), "
                        + "due = odue, odue = 0, odid = 0, usn = ?, mod = ? where " + lim,
                new Object[] { mCol.usn(), Utils.intNow() });
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
            case DYN_OLDEST:
                t = "c.mod";
                break;
            case DYN_RANDOM:
                t = "random()";
                break;
            case DYN_SMALLINT:
                t = "ivl";
                break;
            case DYN_BIGINT:
                t = "ivl desc";
                break;
            case DYN_LAPSES:
                t = "lapses desc";
                break;
            case DYN_ADDED:
                t = "n.id";
                break;
            case DYN_REVADDED:
                t = "n.id desc";
                break;
            case DYN_DUE:
                t = "c.due";
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
            data.add(new Object[] { did, -100000 + c, t, u, ids.get((int) c) });
        }
        // due reviews stay in the review queue. careful: can't use "odid or did", as sqlite converts to boolean
        String queue = "(CASE WHEN type = 2 AND (CASE WHEN odue THEN odue <= " + mToday +
                " ELSE due <= " + mToday + " END) THEN 2 ELSE 0 END)";
        mCol.getDb().executeMany(
                "UPDATE cards SET odid = (CASE WHEN odid THEN odid ELSE did END), " +
                        "odue = (CASE WHEN odue THEN odue ELSE due END), did = ?, queue = " +
                        queue + ", due = ?, mod = ?, usn = ? WHERE id = ?", data);
    }


    private int _dynIvlBoost(Card card) {
        if (card.getODid() == 0 || card.getType() != 2 || card.getFactor() == 0) {
            Log.e(AnkiDroidApp.TAG, "error: deck is not a dynamic deck");
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
                return true;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return false;
    }


    /** LIBANKI: not in libanki */
    public boolean leechActionSuspend(Card card) {
        JSONObject conf;
        try {
            conf = _cardConf(card).getJSONObject("lapse");
            if (conf.getInt("leechAction") == 0) {
                return true;
            } else {
                return false;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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
            // overrides
            dict.put("delays", delays);
            dict.put("separate", conf.getBoolean("separate"));
            dict.put("order", NEW_CARDS_DUE);
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
        	JSONObject conf = _cardConf(card);
        	if (!mCol.getDecks().isDyn(card.getDid()) && card.getODid() != 0) {
        		// workaround, if a card's deck is a normal deck, but odid != 0
        		card.setODue(0);
        		card.setODid(0);
        		AnkiDroidApp.saveExceptionReportFile(e, "fixedODidInconsistencyInSched_lapseConf");
        		// return proper value after having fixed the problem
        		return _lapseConf(card);
        	} else {
	           throw new RuntimeException(e);
	    }
        }
    }


    private JSONObject _revConf(Card card) {
        try {
            JSONObject conf = _cardConf(card);
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
     */

    public void _updateCutoff() {
        // days since col created
        mToday = (int) ((Utils.now() - mCol.getCrt()) / 86400);
        // end of day cutoff
        mDayCutoff = mCol.getCrt() + ((mToday + 1) * 86400);

        // this differs from libanki: updates all decks
        for (JSONObject d : mCol.getDecks().all()) {
            update(d);
        }
        // update all daily counts, but don't save decks to prevent needless conflicts. we'll save on card answer
        // instead
        for (JSONObject deck : mCol.getDecks().all()) {
            update(deck);
        }
    }


    private void update(JSONObject g) {
        for (String t : new String[] { "new", "rev", "lrn", "time" }) {
            String k = t + "Today";
            try {
                if (g.getJSONArray(k).getInt(0) != mToday) {
                    JSONArray ja = new JSONArray();
                    ja.put(mToday);
                    ja.put(0);
                    g.put(k, ja);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public boolean _checkDay() {
        // check if the day has rolled over
        if (Utils.now() > mDayCutoff) {
            reset();
            return true;
        }
        return false;
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


    // public String _tomorrowDueMsg(Context context) {
    // int newCards = 12;// deck.getSched().newTomorrow();
    // int revCards = 1;// deck.getSched().revTomorrow() +
    // int eta = 0; // TODO
    // Resources res = context.getResources();
    // String newCardsText = res.getQuantityString(
    // R.plurals.studyoptions_congrats_new_cards, newCards, newCards);
    // String etaText = res.getQuantityString(
    // R.plurals.studyoptions_congrats_eta, eta, eta);
    // return res.getQuantityString(R.plurals.studyoptions_congrats_message,
    // revCards, revCards, newCardsText, etaText);
    // }

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


    // /**
    // * Number of rev/lrn cards due tomorrow.
    // */
    // public int revTomorrow() {
    // TODO: _walkingCount...
    // return mCol.getDb().queryScalar(
    // "SELECT count() FROM cards WHERE type > 0 AND queue != -1 AND due = "
    // + (mDayCutoff + 86400) + " AND did IN " + _deckLimit());
    // }

    /** true if there are any rev cards due. */
    public boolean revDue() {
        return mCol.getDb()
                .queryScalar(
                        "SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = 2 AND due <= " + mToday
                                + " LIMIT 1", false) != 0;
    }


    /** true if there are any new cards due. */
    public boolean newDue() {
        return mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE did IN " + _deckLimit() + " AND queue = 0 LIMIT 1",
                false) != 0;
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
            return "";
        }
        String s = Utils.fmtTimeSpan(ivl, _short);
//        try {
//			if (ivl < mCol.getConf().getInt("collapseTime")) {
//				s = "< " + s;
//			}
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
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
        mCol.getDb().execute(
                "UPDATE cards SET queue = type, mod = " + Utils.intNow() + ", usn = " + mCol.usn()
                        + " WHERE queue = -1 AND id IN " + Utils.ids2str(ids));
    }


    /**
     * Bury all cards for note until next session.
     */
    public void buryNote(long nid) {
        mCol.setDirty();
        long[] cids = Utils.arrayList2array(mCol.getDb().queryColumn(Long.class,
                "SELECT id FROM cards WHERE nid = " + nid + " AND queue >= 0", 0));
        mCol.getDb().execute("UPDATE cards SET queue = -2 WHERE id IN " + Utils.ids2str(cids));
    }


    /**
     * Counts ******************************************************************* ****************************
     */

    /** LIBANKI: not in libanki */
    public int cardCount() {
        return cardCount(_deckLimit());
    }


    public int cardCount(String dids) {
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE did IN " + dids, false);
    }


    /** LIBANKI: not in libanki */
    public int matureCount() {
        return matureCount(_deckLimit());
    }


    public int matureCount(String dids) {
        return mCol.getDb().queryScalar("SELECT count() FROM cards WHERE type = 2 AND ivl >= 21 AND did IN " + dids,
                false);
    }


    /** returns today's progress 
     * 
     * @param counts (if empty, cached version will be used if any)
     * @param card
     * @return [progressCurrentDeck, progressAllDecks, leftCards, eta]
     */
    public float[] progressToday(TreeSet<Object[]> counts, Card card, boolean eta) {
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
        			counts = (TreeSet<Object[]>)deckCounts()[0];
        		}
            	for (Object[] d : counts) {
            		int done = 0;
        			JSONObject deck = mCol.getDecks().get((Long) d[1]);
            		for (String s : cs) {
            			done += deck.getJSONArray(s + "Today").getInt(1);
            		}
            		mCachedDeckCounts.put((Long)d[1], new Pair<String[], long[]> ((String[])d[0], new long[]{done, (Integer)d[2], (Integer)d[3], (Integer)d[4]}));
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


    /** LIBANKI: not in libanki */
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

    //
    // /**
    // * Time spent learning today, in seconds.
    // */
    // public int timeToday(int fid) {
    // return (int)
    // mDb.queryScalar("SELECT sum(taken / 1000.0) FROM revlog WHERE time > 1000 * "
    // + (mDayCutoff - 86400));
    // // TODO: check for 0?
    // }
    //
    //
    // /**
    // * Number of cards answered today.
    // */
    // public int repsToday(int fid) {
    // return (int) mDb.queryScalar("SELECT count() FROM revlog WHERE time > " +
    // (mDayCutoff - 86400));
    // }
    //
    //
    // /**
    // * Dynamic indices
    // ***********************************************************************************************
    // */
    //
    // private void updateDynamicIndices() {
    // // Log.i(AnkiDroidApp.TAG, "updateDynamicIndices - Updating indices...");
    // // // determine required columns
    // // if (mDeck.getQconf().getInt("revOrder")) {
    // //
    // // }
    // // HashMap<String, String> indices = new HashMap<String, String>();
    // // indices.put("intervalDesc", "(queue, interval desc, factId, due)");
    // // indices.put("intervalAsc", "(queue, interval, factId, due)");
    // // indices.put("randomOrder", "(queue, factId, ordinal, due)");
    // // // new cards are sorted by due, not combinedDue, so that even if
    // // // they are spaced, they retain their original sort order
    // // indices.put("dueAsc", "(queue, due, factId, due)");
    // // indices.put("dueDesc", "(queue, due desc, factId, due)");
    // //
    // // ArrayList<String> required = new ArrayList<String>();
    // // if (mRevCardOrder == REV_CARDS_OLD_FIRST) {
    // // required.add("intervalDesc");
    // // }
    // // if (mRevCardOrder == REV_CARDS_NEW_FIRST) {
    // // required.add("intervalAsc");
    // // }
    // // if (mRevCardOrder == REV_CARDS_RANDOM) {
    // // required.add("randomOrder");
    // // }
    // // if (mRevCardOrder == REV_CARDS_DUE_FIRST || mNewCardOrder ==
    // NEW_CARDS_OLD_FIRST
    // // || mNewCardOrder == NEW_CARDS_RANDOM) {
    // // required.add("dueAsc");
    // // }
    // // if (mNewCardOrder == NEW_CARDS_NEW_FIRST) {
    // // required.add("dueDesc");
    // // }
    // //
    // // // Add/delete
    // // boolean analyze = false;
    // // Set<Entry<String, String>> entries = indices.entrySet();
    // // Iterator<Entry<String, String>> iter = entries.iterator();
    // // String indexName = null;
    // // while (iter.hasNext()) {
    // // Entry<String, String> entry = iter.next();
    // // indexName = "ix_cards_" + entry.getKey();
    // // if (required.contains(entry.getKey())) {
    // // Cursor cursor = null;
    // // try {
    // // cursor = getDB().getDatabase().rawQuery(
    // // "SELECT 1 FROM sqlite_master WHERE name = '" + indexName + "'", null);
    // // if ((!cursor.moveToNext()) || (cursor.getInt(0) != 1)) {
    // // getDB().execute("CREATE INDEX " + indexName +
    // " ON cards " + entry.getValue());
    // // analyze = true;
    // // }
    // // } finally {
    // // if (cursor != null) {
    // // cursor.close();
    // // }
    // // }
    // // } else {
    // // getDB().execute("DROP INDEX IF EXISTS " + indexName);
    // // }
    // // }
    // // if (analyze) {
    // // getDB().execute("ANALYZE");
    // // }
    // }

    /**
     * Resetting **************************************************************** *******************************
     */

    /** Put cards at the end of the new queue. */
    public void forgetCards(long[] ids) {
        mCol.getDb().execute("update cards set type=0,queue=0,ivl=0,factor=2500 where id in " + Utils.ids2str(ids));
        int pmax = mCol.getDb().queryScalar("SELECT max(due) FROM cards WHERE type=0", false);
        // takes care of mod + usn
        sortCards(ids, pmax + 1);
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
        mCol.getDb().executeMany(
                "update cards set type=2,queue=2,ivl=?,due=?, " + "usn=?, mod=?, factor=? where id=? and odid=0", d);
    }

    // resetCards

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
        // shift
        if (shift) {
            int low = mCol.getDb().queryScalar(
                    "SELECT min(due) FROM cards WHERE due >= " + start + " AND type = 0 AND id NOT IN " + scids, false);
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


    // resortconf

    /**
     * ************************************************************************* **********************
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


    public Collection getCol() {
        return mCol;
    }


    public int getNewCount() {
        return mNewCount;
    }


    // Needed for tests
    public LinkedList<long[]> getNewQueue() {
        return mNewQueue;
    }

    private class DeckNameCompare implements Comparator<Object[]> {
        @Override
        public int compare(Object[] lhs, Object[] rhs) {
            String[] o1 = (String[]) lhs[0];
            String[] o2 = (String[]) rhs[0];
            for (int i = 0; i < Math.min(o1.length, o2.length); i++) {
                int result = o1[i].compareToIgnoreCase(o2[i]);
                if (result != 0) {
                    return result;
                }
            }
            if (o1.length < o2.length) {
                return -1;
            } else if (o1.length > o2.length) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    private class DueComparator implements Comparator<long[]> {
        @Override
        public int compare(long[] lhs, long[] rhs) {
            return new Long(lhs[0]).compareTo(rhs[0]);
        }
    }

}
