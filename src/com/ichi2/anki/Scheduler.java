/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteStatement;
import android.text.format.Time;
import android.util.Log;

import com.ichi2.anki.Deck.QueueItem;
import com.ichi2.anki.Deck.SpacedCardsItem;
import com.ichi2.anki.Fact.Field;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

public class Scheduler {
    private static final int NEW_CARDS_DISTRIBUTE = 0;
    private static final int NEW_CARDS_LAST = 1;
    private static final int NEW_CARDS_FIRST = 2;

    private static final int NEW_CARDS_RANDOM = 0;
    private static final int NEW_CARDS_OLD_FIRST = 1;
    private static final int NEW_CARDS_NEW_FIRST = 2;

    private static final int REV_CARDS_OLD_FIRST = 0;
    private static final int REV_CARDS_NEW_FIRST = 1;
    private static final int REV_CARDS_DUE_FIRST = 2;
    private static final int REV_CARDS_RANDOM = 3;
    
    public static final double FACTOR_FOUR = 1.3;

    private Deck mDeck;
	private AnkiDb mDb;
	private String mName = "main";
	private int mQueueLimit;
	private int mLearnLimit = 1000;
	
    // Queues
    private LinkedList<QueueItem> mLearnQueue;
    private LinkedList<QueueItem> mRevQueue;
    private LinkedList<QueueItem> mNewQueue;
    private LinkedList<QueueItem> mFailedQueue;

    private LinkedList<SpacedCardsItem> mSpacedCards;
    private HashMap<Long, Double> mSpacedFacts;

//    private LinkedList<QueueItem> mFailedCramQueue;
    
    private int mLearnCount = 0;
    private int mRevCount;
    private int mNewCount;
    private int mNewAvail;
    private int mRepsToday;
    private int mNewSeenToday;
    private int mNewCardsPerDay = 20;

    private int mNewCardSpacing = NEW_CARDS_DISTRIBUTE;
    private int mNewCardModulus;

    private double mAverageFactor;


    // Scheduling
    // Initial intervals
    private double mHardIntervalMin;
  	private double mHardIntervalMax;
  	private double mMidIntervalMin;
  	private double mMidIntervalMax;
  	private double mEasyIntervalMin;
  	private double mEasyIntervalMax;

  	// Delays on failure
  	private long mDelay0;
  	// Days to delay mature fails
    private long mDelay1;
    private double mDelay2;
    
    
    
    private int mCollapseTime = 600;

    // New card spacing global variable
    private double mNewSpacing;
  	private double mRevSpacing;
    private boolean mNewFromCache;
    
    // Cramming
    private String[] mActiveCramTags;
    private String mCramOrder;
    private double mCramLastInterval;

    private double mDayCutoff;
    
    private String mScheduler;

    // Card order strings for building SQL statements
    private static final String[] revOrderStrings = { "interval desc", "interval",
            "due", "factId, ordinal" };
    private static final String[] newOrderStrings = { "due", "due",
            "due desc" };
    
    private int mRevCardOrder = 0;
    private int mNewCardOrder = 1;



    
	public Scheduler(Deck deck) {
		mDeck = deck;
		mDb = mDeck.getDB();
		updateCutoff();
		setupStandardScheduler();
		// restore any cards temporarily suspended by alternate schedulers
		resetSchedBuried();
	}


    /**
     * Return the next card object.
     *
     * @return The next due card or null if nothing is due.
     */
    public Card getCard() {
        mCurrentCardId = getCardId();
        if (mCurrentCardId != 0l) {
            return cardFromId(mCurrentCardId);
        } else {
            return null;
        }
    }


    public void reset() {
        // Recheck counts
        rebuildCounts();
        // Empty queues; will be refilled by getCard()
        mFailedQueue.clear();
        mRevQueue.clear();
        mNewQueue.clear();
        mSpacedFacts.clear();
        // Determine new card distribution
        if (mNewCardSpacing == NEW_CARDS_DISTRIBUTE) {
            if (mNewCount != 0) {
                mNewCardModulus = (mNewCount + mRevCount) / mNewCount;
                // If there are cards to review, ensure modulo >= 2
                if (mRevCount != 0) {
                    mNewCardModulus = Math.max(2, mNewCardModulus);
                }
            } else {
                mNewCardModulus = 0;
            }
        } else {
            mNewCardModulus = 0;
        }
        // Recache css - Removed for speed optim, we don't use this cache anyway
        // rebuildCSS();

        // Spacing for delayed cards - not to be confused with newCardSpacing above
        mNewSpacing = 0;
        mRevSpacing = 0;
    }

    
    // Return the type of the current card (what queue it's in)
    private int cardType(Card card) {
        if (card.isRev()) {
            return 1;
        } else if (!card.isNew()) {
            return 0;
        } else {
            return 2;
        }
    }

    /*
     * Tools ******************************************
     */
    
    private void resetSchedBuried() {
    	mDb.getDatabase().execSQL("UPDATE cards SET queue = type WHERE queue = -3");
    }


    @SuppressWarnings("unused")
    private String cardLimit(String active, String inactive, String sql) {
        String[] yes = Utils.parseTags(getVar(active));
        String[] no = Utils.parseTags(getVar(inactive));
        if (yes.length > 0) {
            long yids[] = Utils.toPrimitive(tagIds(yes).values());
            long nids[] = Utils.toPrimitive(tagIds(no).values());
            return sql.replace("WHERE", "WHERE +c.id IN (SELECT cardId FROM cardTags WHERE " + "tagId IN "
                    + Utils.ids2str(yids) + ") AND +c.id NOT IN (SELECT cardId FROM " + "cardTags WHERE tagId in "
                    + Utils.ids2str(nids) + ") AND");
        } else if (no.length > 0) {
            long nids[] = Utils.toPrimitive(tagIds(no).values());
            return sql.replace("WHERE", "WHERE +c.id NOT IN (SELECT cardId FROM cardTags WHERE tagId IN "
                    + Utils.ids2str(nids) + ") AND");
        } else {
            return sql;
        }
    }

    /*
     * Daily cutoff************************************
     */
    

    public void updateCutoff() {
        Calendar cal = Calendar.getInstance();
        int newday = (int) mUtcOffset + (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 1000;
        cal.add(Calendar.MILLISECOND, -cal.get(Calendar.ZONE_OFFSET) - cal.get(Calendar.DST_OFFSET));
        cal.add(Calendar.SECOND, (int) -mUtcOffset + 86400);
        cal.set(Calendar.AM_PM, Calendar.AM);
        cal.set(Calendar.HOUR, 0); // Yes, verbose but crystal clear
        cal.set(Calendar.MINUTE, 0); // Apologies for that, here was my rant
        cal.set(Calendar.SECOND, 0); // But if you can improve this bit and
        cal.set(Calendar.MILLISECOND, 0); // collapse it to one statement please do
        cal.getTimeInMillis();

        Log.d(AnkiDroidApp.TAG, "New day happening at " + newday + " sec after 00:00 UTC");
        cal.add(Calendar.SECOND, newday);
        long cutoff = cal.getTimeInMillis() / 1000;
        // Cutoff must not be in the past
        while (cutoff < System.currentTimeMillis() / 1000) {
            cutoff += 86400.0;
        }
        // Cutoff must not be more than 24 hours in the future
        cutoff = Math.min(System.currentTimeMillis() / 1000 + 86400, cutoff);
        mFailedCutoff = cutoff;
        if (getBool("perDay")) {
            mDueCutoff = (double) cutoff;
        } else {
            mDueCutoff = (double) Utils.now();
        }
    }  
    
    // Checks if the day has rolled over.
    private void checkDay() {
        if (Utils.now() > mFailedCutoff) {
            updateCutoff();
            reset();
        }
    }

    /*
     * Learning queue**********************************
     */
    
    private void resetLearn() {
    	Cursor cur = null;
    	try {
    		String sql = "SELECT due, id FROM cards WHERE queue = 0 AND due < "
    			+ mDayCutoff + " ORDER BY due LIMIT " + mLearnLimit;
        	cur = mDb.getDatabase().rawQuery(cardLimit("revActive", "revInactive", sql), null);
        	while (cur.moveToNext()) {
        		QueueItem qi = new QueueItem(cur.getDouble(0), cur.getLong(1));
        		mLearnQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        	mLearnCount = mLearnQueue.size();
    	} finally {
    		if (cur != null && !cur.isClosed()) {
    			cur.close();
            }
        }
    }


    private long getLearnCard() {
    	if (!mLearnQueue.isEmpty() && mLearnQueue.getFirst() < Utils.now()) {
    		return ... // return heappop(self.learnQueue)
    	}
    }
    

    /*
     * Reviews*****************************************
     */
    private void resetReview() {
    	String sql = String.format(Utils.ENGLISH_LOCALE,
                    "SELECT count(*) FROM cards c WHERE queue = 1 AND due < %f", mDayCutoff);
    	mRevCount = (int) mDb.queryScalar(cardLimit("revActive", "revInactive", sql));
        mRevQueue.clear();
    }


    private long getReviewCard() {
    	if (haveRevCards()) {
    		return mRevQueue.remove().getCardID();
    	}
    }


    private boolean haveReviewCard() {
    	if (!mRevQueue.isEmpty()) {
    		if (mRevQueue.isEmpty()) {
    			fillRevQueue();
    		}
    		return mRevQueue
    	} else {
    		return false;
    	}
    }


    @SuppressWarnings("unused")
    private void fillRevQueue() {
        if ((mRevCount != 0) && mRevQueue.isEmpty()) {
            Cursor cur = null;
            try {
                String sql = "SELECT c.id, factId FROM cards c WHERE queue = 1 AND due < "
                        + mDayCutoff + " ORDER BY " + revOrder() + " LIMIT " + mQueueLimit;
                cur = mDb.getDatabase().rawQuery(cardLimit("revActive", "revInactive", sql), null);
                while (cur.moveToNext()) {
                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                    mRevQueue.add(0, qi); // Add to front, so list is reversed as it is built
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
    }

    // FIXME: current random order won't work with new spacing
    private String revOrder() {
        return revOrderStrings[mRevCardOrder];
    }


    // FIXME: rewrite
    private boolean showFailedLast() {
        return ((mCollapseTime != 0.0) || (mDelay0 == 0));
    }


    /*
     * New cards***************************************
     */
    private void resetNew() {
    	//pass
    }


    private void rebuildNewCount() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE queue = 2 AND due < %f", mDueCutoff);
        mNewAvail = (int) mDb.queryScalar(cardLimit("newActive", "newInactive", sql));
        updateNewCountToday();
        mSpacedCards.clear();
    }


    private void updateNewCountToday() {
        mNewCount = Math.max(Math.min(mNewAvail, mNewCardsPerDay - mNewSeenToday), 0);
    }


    private void fillNewQueue() {
        if ((mNewCount != 0) && mNewQueue.isEmpty() && mSpacedCards.isEmpty()) {
            Cursor cur = null;
            try {
                String sql = "SELECT c.id, factId FROM cards c WHERE queue = 2 AND due < "
                        + mDayCutoff + " ORDER BY " + newOrder() + " LIMIT " + mQueueLimit;
                cur = mDb.getDatabase().rawQuery(cardLimit("newActive", "newInactive", sql), null);
                while (cur.moveToNext()) {
                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                    mNewQueue.addFirst(qi); // Add to front, so list is reversed as it is built
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
    }


    private void updateNewCardRatio() {
    	if (mNewCardSpacing == NEW_CARDS_DISTRIBUTE) {
    		if (mNewCount != 0) {
    			
    		}
    	}
    }
    
    
    @SuppressWarnings("unused")
    private boolean timeForNewCard() {
        // True if it's time to display a new card when distributing.
        if (mNewCount == 0) {
            return false;
        }
        if (mNewCardSpacing == NEW_CARDS_LAST) {
            return false;
        }
        if (mNewCardSpacing == NEW_CARDS_FIRST) {
            return true;
        }
        if (mNewCardModulus != 0) {
            return (mRepsToday % mNewCardModulus == 0);
        } else {
            return false;
        }
    }


    private long getNewCard() {
        int src = 0;
        if ((!mSpacedCards.isEmpty()) && (mSpacedCards.get(0).getSpace() < Utils.now())) {
            // Spaced card has expired
            src = 0;
        } else if (!mNewQueue.isEmpty()) {
            // Card left in new queue
            src = 1;
        } else if (!mSpacedCards.isEmpty()) {
            // Card left in spaced queue
            src = 0;
        } else {
            // Only cards spaced to another day left
            return 0L;
        }

        if (src == 0) {
            mNewFromCache = true;
            return mSpacedCards.get(0).getCards().get(0);
        } else {
            mNewFromCache = false;
            return mNewQueue.getLast().getCardID();
        }
    }


    private String newOrder() {
        return newOrderStrings[mNewCardOrder];
    }

    
    /*
     * Getting the next card***************************
     */
    /**
     * Return the next due card Id, or 0
     *
     * @param check Check for expired, or new day rollover
     * @return The Id of the next card, or 0 in case of error
     */
    private long getCard(boolean check) {
        checkDay();
        // learning card due?
        long id = getLearnCard();
        if (id != 0L) {
        	return id;
        }
        // Distribute new cards?
        if (newNoSpaced() && timeForNewCard()) {
            return getNewCard();
        }
        // Card due for review?
        if (revNoSpaced()) {
            return mRevQueue.getLast().getCardID();
        }
        // New cards left?
        if (mNewCount != 0) {
        	id = getNewCard();
        	if (id != 0L) {
        		return id;
        	}
        }
        // Display failed cards early/last
        if ((!check) && showFailedLast() && (!mFailedQueue.isEmpty())) {
            return mFailedQueue.getLast().getCardID();
        }
        // If we're in a custom scheduler, we may need to switch back
        if (finishSchedulerMethod != null) {
            finishScheduler();
            reset();
            return getCardId();
        }
        
//        
//        fillQueues();
//        updateNewCountToday();
//        if (!mFailedQueue.isEmpty()) {
//            // Failed card due?
//            if (mDelay0 != 0l) {
//                if ((long) ((QueueItem) mFailedQueue.getLast()).getDue() + mDelay0 < System.currentTimeMillis() / 1000) {
//                    return mFailedQueue.getLast().getCardID();
//                }
//            }
//            // Failed card queue too big?
//            if ((mFailedCardMax != 0) && (mFailedSoonCount >= mFailedCardMax)) {
//                return mFailedQueue.getLast().getCardID();
//            }
//        }
//        if (check) {
//            // Check for expired cards, or new day rollover
//            updateCutoff();
//            reset();
//            return getCardId(false);
//        }
//
//
//        return 0l;
    }

    
    /*
     * Answering a card*****************************
     */

    public void answerCard(Card card, int ease) {
        Log.i(AnkiDroidApp.TAG, "answerCard");
        double now = Utils.now();
        long id = card.getId();

        String undoName = UNDO_TYPE_ANSWER_CARD;
        setUndoStart(undoName, id);

        // Old state
        String oldState = card.getState();
        int oldQueue = cardQueue(card);
        double lastDelaySecs = Utils.now() - card.getDue();
        double lastDelay = lastDelaySecs / 86400.0;
        boolean oldSuc = card.getSuccessive();
        ContentValues oldvalues = card.getAnswerValues();

        // update card details
        double last = card.getInterval();
        card.setInterval(nextInterval(card, ease));
        card.setLastInterval(last);
        if (card.getReps() != 0) {
        	// only update if card was not new
            card.setLastDue(card.getDue());
        }
        card.setDue(nextDue(card, ease, oldState));
        if (!hasFinishScheduler()) {
        	// don't update factor in custom schedulers
            updateFactor(card, ease);
        }

        // Spacing
        spaceCards(card);

        // Adjust counts for current card
        if (ease == 1) {
            if (card.getDue() < mDayCutoff) {
                mLearnCount += 1;
            }
        }
        if (oldQueue == 0) {
            mLearnCount -= 1;
        } else if (oldQueue == 1) {
            mRevCount -= 1;
        } else {
            mNewAvail -= 1;
        }

        // card stats
        updateCardStats(card, ease, oldState);
        // Update type & ensure past cutoff
        card.setType(cardType(card));
        card.setQueue(card.getType());
        if (ease != 1) {
            card.setDue(Math.max(card.getDue(), mDayCutoff + 1));
        }

        // Allow custom schedulers to munge the card
        if (answerPreSaveMethod != null) {
            answerPreSave(card, ease);
        }

        // Save
        card.setDue(card.getDue());
        mDb.update(mDeck, "cards", card.getAnswerValues(), "id = " + id, null, true, new ContentValues[] {oldvalues}, new String[] {"id = " + id});

        // review history
        RevLog.logReview(mDeck, card, ease, 0);
        mModified = now;

        // Leech handling - we need to do this after the queue, as it may cause a reset
        if (isLeech(card)) {
            Log.i(AnkiDroidApp.TAG, "card is leech!");
            handleLeech(card);
        }
        setUndoEnd(undoName);
    }


    private void updateCardStats(Card card, int ease, String state) {
    	card.setReps(card.getReps() + 1);
    	if (ease == 1) {
    		card.setSuccessive(0);
    		card.setLapses(card.getLapses() + 1);
    	} else {
    		card.setSuccessive(card.getSuccessive() + 1);
    	}
    	// if (card.getFirstAnswered == 0) {
    	//     card.setFirstAnswered(Utils.now());
    	// }
    	card.setModified();
    }


    private void _spaceCards(Card card) {
        // Update new counts
        double _new = Utils.now() + mNewSpacing;
        ContentValues values = new ContentValues();
        values.put("due", String.format(Utils.ENGLISH_LOCALE, "(CASE WHEN queue = 1 THEN " +
                		"due + 86400 * (CASE WHEN interval*%f < 1 THEN 0 ELSE interval*%f END) " +
                		"WHEN queue = 2 THEN %f END)", mRevSpacing, mRevSpacing, _new));
        values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
        mDb.update(mDeck, "cards", values, String.format(Utils.ENGLISH_LOCALE, "id != %d AND factId = %d " 
                + "AND due < %f AND queue BETWEEN 1 AND 2", card.getId(), card.getFactId(), mDayCutoff), null, false);
        // update local cache of seen facts
        mSpacedFacts.put(card.getFactId(), _new);
    }


    /*
     * Interval management*********************************************************
     */

    public double nextInterval(Card card, int ease) {
        double delay = card.adjustedDelay(ease);
        return _nextInterval(card, delay, ease);
    }
    private double _nextInterval(Card card, double delay, int ease) {
        double interval = card.getInterval();
        double factor = card.getFactor();

        // if cramming / reviewing early
        if (delay < 0) {
            interval = Math.max(card.getLastInterval(), card.getInterval() + delay);
            if (interval < mMidIntervalMin) {
                interval = 0;
            }
            delay = 0;
        }

        // if interval is less than mid interval, use presets
        if (ease == Card.EASE_FAILED) {
            interval *= mDelay2;
            if (interval < mHardIntervalMin) {
                interval = 0;
            }
        } else if (interval == 0) {
            if (ease == Card.EASE_HARD) {
                interval = mHardIntervalMin + card.getFuzz() * (mHardIntervalMax - mHardIntervalMin);
            } else if (ease == Card.EASE_MID) {
                interval = mMidIntervalMin + card.getFuzz() * (mMidIntervalMax - mMidIntervalMin);
            } else if (ease == Card.EASE_EASY) {
                interval = mEasyIntervalMin + card.getFuzz() * (mEasyIntervalMax - mEasyIntervalMin);
            }
        } else {
            // if not cramming, boost initial 2
            if ((interval < mHardIntervalMax) && (interval > 0.166)) {
                double mid = (mMidIntervalMin + mMidIntervalMax) / 2.0;
                interval = mid / factor;
            }
            // multiply last interval by factor
            if (ease == Card.EASE_HARD) {
                interval = (interval + delay / 4.0) * 1.2;
            } else if (ease == Card.EASE_MID) {
                interval = (interval + delay / 2.0) * factor;
            } else if (ease == Card.EASE_EASY) {
                interval = (interval + delay) * factor * FACTOR_FOUR;
            }
            interval *= 0.95 + card.getFuzz() * (1.05 - 0.95);
        }
        return interval;
    }


    private double nextDue(Card card, int ease, String oldState) {
        double due;
        if (ease == Card.EASE_FAILED) {
        	// 600 is a magic value which means no bonus, and is used to ease upgrades
            if (!mScheduler.equals("cram") && oldState.equals(Card.STATE_MATURE) && mDelay1 != 0 && mDelay1 != 600) {
                // user wants a bonus of 1+ days. put the failed cards at the
            	// start of the future day, so that failures that day will come
            	// after the waiting cards
            	return mDayCutoff + (mDelay1 - 1) * 86400;
            } else {
                due = 0.0;
            }
        } else {
            due = card.getInterval() * 86400.0;
        }
        return (due + Utils.now());
    }


    public void updateFactor(Card card, int ease) {
        if (card.getReps() == 0) {
        	// card is new, inherit beginning factor
            card.setFactor(mAverageFactor); 
        }
        if (card.getSuccessive() != 0 && !isBeingLearnt(card)) {
            if (ease == Card.EASE_FAILED) {
                card.setFactor(card.getFactor() - 0.20);
            } else if (ease == Card.EASE_HARD) {
            	card.setFactor(card.getFactor() - 0.15);
            }
        }
        if (ease == Card.EASE_EASY) {
        	card.setFactor(card.getFactor() + 0.10);
        }
        card.setFactor(Math.max(1.3, card.getFactor()));
    }


    public double adjustedDelay(Card card, int ease) {
        if (cardIsNew(card)) {
            return 0;
        }
        if (card.getDue() <= mDayCutoff) {
            return (mDayCutoff - card.getDue()) / 86400.0;
        } else {
            return (mDayCutoff - card.getDue()) / 86400.0;
        }
    }


    /*
     * Leeches******************************
     */
    
    private boolean isLeech(Card card) {
        int no = card.getLapses();
        int fmax = 0;
        if (hasKey("leechFails")) {
            fmax = getInt("leechFails");
        } else {
            // No leech threshold found in DeckVars
            return false;
        }
        Log.i(AnkiDroidApp.TAG, "leech handling: " + card.getSuccessive() + " successive fails and " + no + " total fails, threshold at " + fmax);
        // Return true if:
        // - The card failed AND
        // - The number of failures exceeds the leech threshold AND
        // - There were at least threshold/2 reps since last time
        if (!card.isRev() && (no >= fmax) && ((fmax - no) % Math.max(fmax / 2, 1) == 0)) {
            return true;
        } else {
            return false;
        }
    }


    private void handleLeech(Card card) {
        Card scard = cardFromId(card.getId());
        String tags = scard.getFact().getTags();
        tags = Utils.addTags("Leech", tags);
        scard.getFact().setTags(Utils.canonifyTags(tags));
        // FIXME: Inefficient, we need to save the fact so that the modified tags can be used in setModified,
        // then after setModified we need to save again! Just make setModified to use the tags from the fact,
        // not reload them from the DB.
        scard.getFact().toDb();
        scard.getFact().setModified(true, this);
        scard.getFact().toDb();
        updateFactTags(new long[] { scard.getFact().getId() });
        card.setLeechFlag(true);
        if (getBool("suspendLeeches")) {
            suspendCards(new long[] { card.getId() });
            card.setSuspendedFlag(true);
        }
        reset();
    }


    /*
     * Review early*****************************
     */

    public void setupReviewEarlyScheduler() {
        try {
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillRevEarlyQueue");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildRevEarlyCount");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
            answerPreSaveMethod = Deck.class.getDeclaredMethod("_reviewEarlyPreSave", Card.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        mScheduler = "reviewEarly";
    }


    @SuppressWarnings("unused")
    private void _reviewEarlyPreSave(Card card, int ease) {
        if (ease > 1) {
            // Prevent it from appearing in next queue fill
            card.setQueue(-3);
        }
    }
    
    
    @SuppressWarnings("unused")
    private void _rebuildRevEarlyCount() {
        // In the future it would be nice to skip the first x days of due cards
        mRevCount = (int) mDb.queryScalar(cardLimit("revActive", "revInactive", String.format(Utils.ENGLISH_LOCALE,
                        "SELECT count() FROM cards c WHERE queue = 1 AND due > %f", mDayCutoff)));
    }


    @SuppressWarnings("unused")
    private void _fillRevEarlyQueue() {
        if ((mRevCount != 0) && mRevQueue.isEmpty()) {
            Cursor cur = null;
            try {
                cur = mDb.getDatabase().rawQuery(cardLimit("revActive", "revInactive", String.format(
                                Utils.ENGLISH_LOCALE,
                                "SELECT id, factId FROM cards c WHERE queue = 1 AND due > %f " +
                                "ORDER BY due LIMIT %d", mDayCutoff, mQueueLimit)), null);
                while (cur.moveToNext()) {
                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                    mRevQueue.add(0, qi); // Add to front, so list is reversed as it is built
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
    }

    
    /*
     * Learn more*****************************
     */

    public void setupLearnMoreScheduler() {
        try {
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildLearnMoreCount");
            updateNewCountTodayMethod = Deck.class.getDeclaredMethod("_updateLearnMoreCountToday");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        mScheduler = "learnMore";
    }


    @SuppressWarnings("unused")
    private void _rebuildLearnMoreCount() {
        mNewAvail = (int) mDb.queryScalar(
                cardLimit("newActive", "newInactive", String.format(Utils.ENGLISH_LOCALE,
                        "SELECT count(*) FROM cards c WHERE queue = 2 AND due < %f", mDayCutoff)));
        mSpacedCards.clear();
    }


    @SuppressWarnings("unused")
    private void _updateLearnMoreCountToday() {
        mNewCount = mNewAvail;
    }


    /*
     * Cramming*****************************
     */

    public void setupCramScheduler(String[] active, String order) {
        try {
            getCardIdMethod = Deck.class.getDeclaredMethod("_getCramCardId", boolean.class);
            mActiveCramTags = active;
            mCramOrder = order;
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildNewCramCount");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildCramCount");
            rebuildRrnCountMethod = Deck.class.getDeclaredMethod("_rebuildLrnCramCount");
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillCramQueue");
            fillRevLrnQueueMethod = Deck.class.getDeclaredMethod("_fillLrnCramQueue");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
            mLrnCramQueue.clear();
            requeueCardMethod = Deck.class.getDeclaredMethod("_requeueCramCard", Card.class, boolean.class);
            cardQueueMethod = Deck.class.getDeclaredMethod("_cramCardQueue", Card.class);
            answerCardMethod = Deck.class.getDeclaredMethod("_answerCramCard", Card.class, int.class);
            spaceCardsMethod = Deck.class.getDeclaredMethod("_spaceCramCards", Card.class);
            // Reuse review early's code
            answerPreSaveMethod = Deck.class.getDeclaredMethod("_cramPreSave", Card.class, int.class);
            cardLimitMethod = Deck.class.getDeclaredMethod("_cramCardLimit", String[].class, String[].class,
                    String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        mScheduler = "cram";
    }

    
    @SuppressWarnings("unused")
    private void _cramPreSave(Card card, int ease) {
        // prevent it from appearing in next queue fill
    	card.setLastInterval(mCramLastInterval);
    	card.setType(-3);
    }


    @SuppressWarnings("unused")
    private void _spaceCramCards(Card card) {
        mSpacedFacts.put(card.getFactId(), Utils.now() + mNewSpacing);
    }
    
    
    @SuppressWarnings("unused")
    private long _getCramCardId(boolean check) {
        checkDay();
        fillQueues();

        if ((mLrnCardMax != 0) && (mLrnCount >= mLrnCardMax)) {
            return ((QueueItem) mLrnQueue.getLast()).getCardID();
        }
        // Card due for review?
        if (revNoSpaced()) {
            return ((QueueItem) mRevQueue.getLast()).getCardID();
        }
        if (!mLrnQueue.isEmpty()) {
            return ((QueueItem) mLrnQueue.getLast()).getCardID();
        }
        if (check) {
            // Collapse spaced cards before reverting back to old scheduler
            reset();
            return getCardId(false);
        }
        // If we're in a custom scheduler, we may need to switch back
        if (finishSchedulerMethod != null) {
            finishScheduler();
            reset();
            return getCardId();
        }
        return 0l;
    }


    @SuppressWarnings("unused")
    private int _cramCardQueue(Card card) {
        if ((!mRevQueue.isEmpty()) && (((QueueItem) mRevQueue.getLast()).getCardID() == card.getId())) {
            return 1;
        } else {
            return 0;
        }
    }

    
    @SuppressWarnings("unused")
    private void _requeueCramCard(Card card, boolean oldIsRev) {
        if (cardQueue(card) == 1) {
            mRevQueue.removeLast();
        } else {
            mLrnCramQueue.removeLast();
        }
    }


    @SuppressWarnings("unused")
    private void _rebuildCramNewCount() {
        mNewAvail = 0;
    	mNewCount = 0;
    }


    @SuppressWarnings("unused")
    private String _cramCardLimit(String active[], String inactive[], String sql) {
        // inactive is (currently) ignored
        if (active.length > 0) {
            long yids[] = Utils.toPrimitive(tagIds(active).values());
            return sql.replace("WHERE ", "WHERE +c.id IN (SELECT cardId FROM cardTags WHERE " + "tagId IN "
                    + Utils.ids2str(yids) + ") AND ");
        } else {
            return sql;
        }
    }
    
    
    @SuppressWarnings("unused")
    private void _fillCramQueue() {
        if ((mRevCount != 0) && mRevQueue.isEmpty()) {
            Cursor cur = null;
            try {
                Log.i(AnkiDroidApp.TAG, "fill cram queue: " + Arrays.toString(mActiveCramTags) + " " + mCramOrder + " " + mQueueLimit);
                String sql = "SELECT id, factId FROM cards c WHERE queue BETWEEN 0 AND 2 ORDER BY " + mCramOrder
                        + " LIMIT " + mQueueLimit;
                sql = cardLimit(mActiveCramTags, null, sql);
                Log.i(AnkiDroidApp.TAG, "SQL: " + sql);
                cur = mDb.getDatabase().rawQuery(sql, null);
                while (cur.moveToNext()) {
                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                    mRevQueue.add(0, qi); // Add to front, so list is reversed as it is built
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
    }


    @SuppressWarnings("unused")
    private void _rebuildCramCount() {
        mRevCount = (int) mDb.queryScalar(
                cardLimit(mActiveCramTags, null, "SELECT count(*) FROM cards c WHERE queue BETWEEN 0 AND 2"));
    }


    @SuppressWarnings("unused")
    private void _rebuildLrnCramCount() {
        mLrnCount = mLrnCramQueue.size();
    }


    @SuppressWarnings("unused")
    private void _fillLrnCramQueue() {
        mLrnQueue = mLrnCramQueue;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    /*
     * Standard Scheduling*****************************
     */
    public void setupStandardScheduler() {
        try {
            getCardIdMethod = Deck.class.getDeclaredMethod("_getCardId", boolean.class);
            fillFailedQueueMethod = Deck.class.getDeclaredMethod("_fillFailedQueue");
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillRevQueue");
            fillNewQueueMethod = Deck.class.getDeclaredMethod("_fillNewQueue");
            rebuildFailedCountMethod = Deck.class.getDeclaredMethod("_rebuildFailedCount");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildRevCount");
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildNewCount");
            requeueCardMethod = Deck.class.getDeclaredMethod("_requeueCard", Card.class, boolean.class);
            timeForNewCardMethod = Deck.class.getDeclaredMethod("_timeForNewCard");
            updateNewCountTodayMethod = Deck.class.getDeclaredMethod("_updateNewCountToday");
            cardQueueMethod = Deck.class.getDeclaredMethod("_cardQueue", Card.class);
            finishSchedulerMethod = null;
            answerCardMethod = Deck.class.getDeclaredMethod("_answerCard", Card.class, int.class);
            cardLimitMethod = Deck.class.getDeclaredMethod("_cardLimit", String.class, String.class, String.class);
            answerPreSaveMethod = null;
            spaceCardsMethod = Deck.class.getDeclaredMethod("_spaceCards", Card.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        mScheduler = "standard";
        // Restore any cards temporarily suspended by alternate schedulers
        if (mVersion == DECK_VERSION) {
            resetAfterReviewEarly();
        }
    }


//    private void fillQueues() {
//        fillFailedQueue();
//        fillRevQueue();
//        fillNewQueue();
//        //for (QueueItem i : mFailedQueue) {
//        //    Log.i(AnkiDroidApp.TAG, "failed queue: cid: " + i.getCardID() + " fid: " + i.getFactID() + " cd: " + i.getDue());
//        //}
//        //for (QueueItem i : mRevQueue) {
//        //    Log.i(AnkiDroidApp.TAG, "rev queue: cid: " + i.getCardID() + " fid: " + i.getFactID());
//        //}
//        //for (QueueItem i : mNewQueue) {
//        //    Log.i(AnkiDroidApp.TAG, "new queue: cid: " + i.getCardID() + " fid: " + i.getFactID());
//        //}
//    }
//
//
//    public long retrieveCardCount() {
//        return mDb.queryScalar("SELECT count(*) from cards");
//    }
//
//
//    private void rebuildCounts() {
//        // global counts
//        try {
//            mCardCount = (int) mDb.queryScalar("SELECT count(*) from cards");
//            mFactCount = (int) mDb.queryScalar("SELECT count(*) from facts");
//        } catch (SQLException e) {
//            Log.e(AnkiDroidApp.TAG, "rebuildCounts: Error while getting global counts: " + e.toString());
//            mCardCount = 0;
//            mFactCount = 0;
//        }
//        // day count
//        Cursor cursor = null;
//        ArrayList<Long> cardIds = new ArrayList<Long>();
//        try {
//            cursor = mDb.getDatabase().rawQuery(
//                    "SELECT count(), SUM(CASE WHEN rep = 1 THEN 1 ELSE 0 END) FROM revlog WHERE time > = " + mFailedCutoff + 86400, null);
//            while (cursor.moveToNext()) {
//            	mRepsToday = cursor.getInt(0);
//            	mNewSeenToday = cursor.getInt(1);
//            }
//        } catch (SQLException e) {
//            Log.e(AnkiDroidApp.TAG, "rebuildCounts: Error while getting day counts: " + e.toString());
//            mRepsToday = 0;
//            mNewSeenToday = 0;
//        } finally {
//            if (cursor != null && !cursor.isClosed()) {
//                cursor.close();
//            }
//        }
//        // due counts
//        rebuildFailedCount();
//        rebuildRevCount();
//        rebuildNewCount();
//    }
//
//
//
//
//    /**
//     * This is a count of all failed cards within the current day cutoff. The cards may not be ready for review yet, but
//     * can still be displayed if failedCardsMax is reached.
//     */
//    @SuppressWarnings("unused")
//    private void _rebuildFailedCount() {
//        String sql = String.format(Utils.ENGLISH_LOCALE,
//                "SELECT count(*) FROM cards c WHERE queue = 0 AND due < %f", mFailedCutoff);
//        mFailedSoonCount = (int) mDb.queryScalar(cardLimit("revActive", "revInactive", sql));
//    }
//
//
//    @SuppressWarnings("unused")
//    private void _rebuildRevCount() {
//        String sql = String.format(Utils.ENGLISH_LOCALE,
//                "SELECT count(*) FROM cards c WHERE queue = 1 AND due < %f", mDueCutoff);
//        mRevCount = (int) mDb.queryScalar(cardLimit("revActive", "revInactive", sql));
//    }
//
//
//
//
//
//    @SuppressWarnings("unused")
//    private void _fillFailedQueue() {
//        if ((mFailedSoonCount != 0) && mFailedQueue.isEmpty()) {
//            Cursor cur = null;
//            try {
//                String sql = "SELECT c.id, factId, due FROM cards c WHERE queue = 0 AND due < "
//                        + mFailedCutoff + " ORDER BY due LIMIT " + mQueueLimit;
//                cur = mDb.getDatabase().rawQuery(cardLimit("revActive", "revInactive", sql), null);
//                while (cur.moveToNext()) {
//                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
//                    mFailedQueue.add(0, qi); // Add to front, so list is reversed as it is built
//                }
//            } finally {
//                if (cur != null && !cur.isClosed()) {
//                    cur.close();
//                }
//            }
//        }
//    }
//
//
//
//
//
//
//
//    private boolean queueNotEmpty(LinkedList<QueueItem> queue, Method fillFunc) {
//        return queueNotEmpty(queue, fillFunc, false);
//    }
//
//
//    private boolean queueNotEmpty(LinkedList<QueueItem> queue, Method fillFunc, boolean _new) {
////        while (true) {
//            removeSpaced(queue, _new);
//            if (!queue.isEmpty()) {
//                return true;
//            }
//            try {
//                fillFunc.invoke(Deck.this);
//                // with libanki
//            } catch (Exception e) {
//                Log.e(AnkiDroidApp.TAG, "queueNotEmpty: Error while invoking overridable fill method:" + e.toString());
//                return false;
//            }
////            if (queue.isEmpty()) {
//                return false;
////            }
////        }
//    }
//
//
//    private void removeSpaced(LinkedList<QueueItem> queue) {
//        removeSpaced(queue, false);
//    }
//
//
//    private void removeSpaced(LinkedList<QueueItem> queue, boolean _new) {
//        ArrayList<Long> popped = new ArrayList<Long>();
//        double delay = 0.0;
//        while (!queue.isEmpty()) {
//            long fid = ((QueueItem) queue.getLast()).getFactID();
//            if (mSpacedFacts.containsKey(fid)) {
//                // Still spaced
//                long id = queue.removeLast().getCardID();
//                // Assuming 10 cards/minute, track id if likely to expire before queue refilled
//                if (_new && (mNewSpacing < (double) mQueueLimit * 6.0)) {
//                    popped.add(id);
//                    delay = mSpacedFacts.get(fid);
//                }
//            } else {
//                if (!popped.isEmpty()) {
//                    mSpacedCards.add(new SpacedCardsItem(delay, popped));
//                }
//                break;
//            }
//        }
//    }
//
//
//    private boolean revNoSpaced() {
//        return queueNotEmpty(mRevQueue, fillRevQueueMethod);
//    }
//
//
//    private boolean newNoSpaced() {
//        return queueNotEmpty(mNewQueue, fillNewQueueMethod, true);
//    }
//
//
//    @SuppressWarnings("unused")
//    private void _requeueCard(Card card, boolean oldIsRev) {
//        int newType = 0;
//        // try {
//        if (card.getReps() == 1) {
//            if (mNewFromCache) {
//                // Fetched from spaced cache
//                newType = 2;
//                ArrayList<Long> cards = mSpacedCards.remove().getCards();
//                // Reschedule the siblings
//                if (cards.size() > 1) {
//                    cards.remove(0);
//                    mSpacedCards.addLast(new SpacedCardsItem(Utils.now() + mNewSpacing, cards));
//                }
//            } else {
//                // Fetched from normal queue
//                newType = 1;
//                mNewQueue.removeLast();
//            }
//        } else if (!oldIsRev) {
//            mFailedQueue.removeLast();
//        } else {
//            // try {
//                mRevQueue.removeLast();
//            // }
//            // catch(NoSuchElementException e) {
//            //     Log.w(AnkiDroidApp.TAG, "mRevQueue empty");
//            // }
//        }
//        // } catch (Exception e) {
//        // throw new RuntimeException("requeueCard() failed. Counts: " +
//        // mFailedSoonCount + " " + mRevCount + " " + mNewCountToday + ", Queue: " +
//        // mFailedQueue.size() + " " + mRevQueue.size() + " " + mNewQueue.size() + ", Card info: " +
//        // card.getReps() + " " + card.isRev() + " " + oldIsRev);
//        // }
//    }
//
//
//
//
//
//    // Rebuild the type cache. Only necessary on upgrade. 
//    public void rebuildTypes() {
//        // set type first
//        mDb.getDatabase().execSQL(
//                "UPDATE cards SET type = (CASE WHEN successive THEN 1 WHEN reps THEN 0 ELSE 2 END)");
//        // then queue
//        mDb.getDatabase().execSQL(
//                "UPDATE cards SET type = queue WHEN queue != -1");
//    }
//
//
//    @SuppressWarnings("unused")
//    private int _cardQueue(Card card) {
//        return cardType(card);
//    }
//
//
//
//
//
//    
//
//
//
//
//    private void resetAfterReviewEarly() {
//        // Put temporarily suspended cards back into play. Caller must .reset()
//    	mDb.getDatabase().execSQL("UPDATE cards SET queue = type WHERE queue = -3");
//    }
//
//    @SuppressWarnings("unused")
//    private void _onReviewEarlyFinished() {
//        // Clean up buried cards
//        resetAfterReviewEarly();
//        // And go back to regular scheduler
//        setupStandardScheduler();
//    }
//
//
//
//
//
//
//    @SuppressWarnings("unused")
//    private void _answerCramCard(Card card, int ease) {
//	mCramLastInterval = card.getLastInterval();
//        _answerCard(card, ease);
//        if (ease == 1) {
//            mFailedCramQueue.addFirst(new QueueItem(card.getId(), card.getFactId()));
//        }
//    }
//
//
//
//
//
//
//
//
//
//
//
//
//    /*
//     * Getting the next card*****************************
//     */
//
//
//
//    // Refreshes the current card and returns it (used when editing cards)
//    public Card getCurrentCard() {
//        return cardFromId(mCurrentCardId);
//    }
//
//
//
//
//
//    /*
//     * Get card: helper functions*****************************
//     */
//
//
//
//
//
//
//
//    /**
//     * Given a card ID, return a card and start the card timer.
//     *
//     * @param id The ID of the card to be returned
//     */
//
//    public Card cardFromId(long id) {
//        if (id == 0) {
//            return null;
//        }
//        Card card = new Card(this);
//        boolean result = card.fromDB(id);
//
//        if (!result) {
//            return null;
//        }
//        card.mDeck = this;
////        card.genFuzz();
//        card.startTimer();
//        return card;
//    }
//
//
//    // TODO: The real methods to update cards on Anki should be implemented instead of this
//    public void updateAllCards() {
//        updateAllCardsFromPosition(0, Long.MAX_VALUE);
//    }
//
//
//    public long updateAllCardsFromPosition(long numUpdatedCards, long limitCards) {
//        // TODO: Cache this query, order by FactId, Id
//        Cursor cursor = null;
//        try {
//            cursor = mDb.getDatabase().rawQuery(
//                    "SELECT id, factId " + "FROM cards " + "ORDER BY factId, id " + "LIMIT " + limitCards + " OFFSET "
//                            + numUpdatedCards, null);
//
//            mDb.getDatabase().beginTransaction();
//            while (cursor.moveToNext()) {
//                // Get card
//                Card card = new Card(this);
//                card.fromDB(cursor.getLong(0));
//                Log.i(AnkiDroidApp.TAG, "Card id = " + card.getId() + ", numUpdatedCards = " + numUpdatedCards);
//
//                // Load tags
//                card.loadTags();
//
//                // Get the related fact
//                Fact fact = card.getFact();
//                // Log.i(AnkiDroidApp.TAG, "Fact id = " + fact.id);
//
//                // Generate the question and answer for this card and update it
//                HashMap<String, String> newQA = CardModel.formatQA(fact, card.getCardModel(), card.splitTags());
//                card.setQuestion(newQA.get("question"));
//                Log.i(AnkiDroidApp.TAG, "Question = " + card.getQuestion());
//                card.setAnswer(newQA.get("answer"));
//                Log.i(AnkiDroidApp.TAG, "Answer = " + card.getAnswer());
//
//                card.updateQAfields();
//
//                numUpdatedCards++;
//
//            }
//            mDb.getDatabase().setTransactionSuccessful();
//        } finally {
//            mDb.getDatabase().endTransaction();
//            if (cursor != null && !cursor.isClosed()) {
//                cursor.close();
//            }
//        }
//
//        return numUpdatedCards;
//    }
//
//
//
//
//    @SuppressWarnings("unused")
//


    
    /*
     * Scheduler related overridable methods******************************
     */
    private Method getCardIdMethod;
    private Method fillFailedQueueMethod;
    private Method fillRevQueueMethod;
    private Method fillNewQueueMethod;
    private Method rebuildFailedCountMethod;
    private Method rebuildRevCountMethod;
    private Method rebuildNewCountMethod;
    private Method requeueCardMethod;
    private Method timeForNewCardMethod;
    private Method updateNewCountTodayMethod;
    private Method cardQueueMethod;
    private Method finishSchedulerMethod;
    private Method answerCardMethod;
    private Method cardLimitMethod;
    private Method answerPreSaveMethod;
    private Method spaceCardsMethod;


    private long getCardId() {
        try {
            return ((Long) getCardIdMethod.invoke(Deck.this, true)).longValue();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private long getCardId(boolean check) {
        try {
            return ((Long) getCardIdMethod.invoke(Deck.this, check)).longValue();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void fillFailedQueue() {
        try {
            fillFailedQueueMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void fillRevQueue() {
        try {
            fillRevQueueMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void fillNewQueue() {
        try {
            fillNewQueueMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void rebuildFailedCount() {
        try {
            rebuildFailedCountMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void rebuildRevCount() {
        try {
            rebuildRevCountMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void rebuildNewCount() {
        try {
            rebuildNewCountMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void requeueCard(Card card, boolean oldIsRev) {
        try {
            requeueCardMethod.invoke(Deck.this, card, oldIsRev);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private boolean timeForNewCard() {
        try {
            return ((Boolean) timeForNewCardMethod.invoke(Deck.this)).booleanValue();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void updateNewCountToday() {
        try {
            updateNewCountTodayMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private int cardQueue(Card card) {
        try {
            return ((Integer) cardQueueMethod.invoke(Deck.this, card)).intValue();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    public void finishScheduler() {
        try {
            finishSchedulerMethod.invoke(Deck.this);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    public void answerCard(Card card, int ease) {
        try {
            answerCardMethod.invoke(Deck.this, card, ease);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private String cardLimit(String active, String inactive, String sql) {
        try {
            return ((String) cardLimitMethod.invoke(Deck.this, active, inactive, sql));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private String cardLimit(String[] active, String[] inactive, String sql) {
        try {
            return ((String) cardLimitMethod.invoke(Deck.this, active, inactive, sql));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void answerPreSave(Card card, int ease) {
        try {
            answerPreSaveMethod.invoke(Deck.this, card, ease);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    private void spaceCards(Card card) {
        try {
            spaceCardsMethod.invoke(Deck.this, card);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean hasFinishScheduler() {
        return !(finishSchedulerMethod == null);
    }





    /*
     * Queue Management*****************************
     */

    private class QueueItem {
        private long cardID;
        private long factID;
        private double due;


        QueueItem(long cardID, long factID) {
            this.cardID = cardID;
            this.factID = factID;
            this.due = 0.0;
        }


        QueueItem(double due, long cardID) {
            this.due = due;
            this.cardID = cardID;
        }


        long getCardID() {
            return cardID;
        }


        long getFactID() {
            return factID;
        }


        double getDue() {
            return due;
        }
    }

    private class SpacedCardsItem {
        private double space;
        private ArrayList<Long> cards;


        SpacedCardsItem(double space, ArrayList<Long> cards) {
            this.space = space;
            this.cards = cards;
        }


        double getSpace() {
            return space;
        }


        ArrayList<Long> getCards() {
            return cards;
        }
    }	
	
}
