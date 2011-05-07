	/****************************************************************************************
	 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.libanki;

import com.ichi2.anki.AnkiDb;

public class CramScheduler {
	
	private Deck mDeck;
	private AnkiDb mDb;
	private String mName = "cram";
	private int mOrder;
	private int mMin;
	private int mMax;
	
    private int mNewCount;
    private int mLrnCount;

	
	
	private int mQueueLimit;
	private int mReportLimit;
	private int mReps;
	private int mToday;
	private int mDayCutoff;


	public CramScheduler(Deck deck, int order, int min, int max) {
		mDeck = deck;
		mDb = mDeck.getDB();
		// should be the opposite order of what you want
		mOrder = order;
		// days to limit cram to, where tomorrow=0. Max is inclusive.
		mMin = min;
		mMax = max;
//		reset();
		
		mQueueLimit = 200;
		mReportLimit = 1000;
		mReps = 0;
//		_updateCutoff();

		
		
//		// Initialise queues
//		mNewQueue = new LinkedList<int[]>();
//		mLrnQueue = new LinkedList<int[]>();
//		mRevQueue = new LinkedList<int[]>();
//
//		// Initialise conf maps
//		mGroupConfs = new TreeMap<Integer, Integer>();
//		mConfCache = new TreeMap<Integer, JSONObject>();	
	}


    private int[] counts() {
    	int[] count = new int[3];
    	count[0] = mNewCount;
    	count[1] = mLrnCount;
    	count[2] = 0;
    	return count;
    }

//
//    private void reset() {
//    	Scheduler._resetConf();
//    	Scheduler._resetCounts();
//    	Scheduler._resetLrn();
//    	Scheduler._resetRev();
//    	Scheduler._resetNew();
//    }
//	
//	
//	  /*
//	     * Cramming*****************************
//	     */
//
//	    public void setupCramScheduler(String[] active, String order) {
//	        try {
//	            getCardIdMethod = Deck.class.getDeclaredMethod("_getCramCardId", boolean.class);
//	            mActiveCramTags = active;
//	            mCramOrder = order;
//	            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildNewCramCount");
//	            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildCramCount");
//	            rebuildRrnCountMethod = Deck.class.getDeclaredMethod("_rebuildLrnCramCount");
//	            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillCramQueue");
//	            fillRevLrnQueueMethod = Deck.class.getDeclaredMethod("_fillLrnCramQueue");
//	            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
//	            mLrnCramQueue.clear();
//	            requeueCardMethod = Deck.class.getDeclaredMethod("_requeueCramCard", Card.class, boolean.class);
//	            cardQueueMethod = Deck.class.getDeclaredMethod("_cramCardQueue", Card.class);
//	            answerCardMethod = Deck.class.getDeclaredMethod("_answerCramCard", Card.class, int.class);
//	            spaceCardsMethod = Deck.class.getDeclaredMethod("_spaceCramCards", Card.class);
//	            // Reuse review early's code
//	            answerPreSaveMethod = Deck.class.getDeclaredMethod("_cramPreSave", Card.class, int.class);
//	            cardLimitMethod = Deck.class.getDeclaredMethod("_cramCardLimit", String[].class, String[].class,
//	                    String.class);
//	        } catch (NoSuchMethodException e) {
//	            throw new RuntimeException(e);
//	        }
//	        mScheduler = "cram";
//	    }
//
//	    
//	    @SuppressWarnings("unused")
//	    private void _cramPreSave(Card card, int ease) {
//	        // prevent it from appearing in next queue fill
//	    	card.setLastInterval(mCramLastInterval);
//	    	card.setType(-3);
//	    }
//
//
//	    @SuppressWarnings("unused")
//	    private void _spaceCramCards(Card card) {
//	        mSpacedFacts.put(card.getFactId(), Utils.now() + mNewSpacing);
//	    }
//	    
//	    
//	    @SuppressWarnings("unused")
//	    private long _getCramCardId(boolean check) {
//	        checkDay();
//	        fillQueues();
//
//	        if ((mLrnCardMax != 0) && (mLrnCount >= mLrnCardMax)) {
//	            return ((QueueItem) mLrnQueue.getLast()).getCardID();
//	        }
//	        // Card due for review?
//	        if (revNoSpaced()) {
//	            return ((QueueItem) mRevQueue.getLast()).getCardID();
//	        }
//	        if (!mLrnQueue.isEmpty()) {
//	            return ((QueueItem) mLrnQueue.getLast()).getCardID();
//	        }
//	        if (check) {
//	            // Collapse spaced cards before reverting back to old scheduler
//	            reset();
//	            return getCardId(false);
//	        }
//	        // If we're in a custom scheduler, we may need to switch back
//	        if (finishSchedulerMethod != null) {
//	            finishScheduler();
//	            reset();
//	            return getCardId();
//	        }
//	        return 0l;
//	    }
//
//
//	    @SuppressWarnings("unused")
//	    private int _cramCardQueue(Card card) {
//	        if ((!mRevQueue.isEmpty()) && (((QueueItem) mRevQueue.getLast()).getCardID() == card.getId())) {
//	            return 1;
//	        } else {
//	            return 0;
//	        }
//	    }
//
//	    
//	    @SuppressWarnings("unused")
//	    private void _requeueCramCard(Card card, boolean oldIsRev) {
//	        if (cardQueue(card) == 1) {
//	            mRevQueue.removeLast();
//	        } else {
//	            mLrnCramQueue.removeLast();
//	        }
//	    }
//
//
//	    @SuppressWarnings("unused")
//	    private void _rebuildCramNewCount() {
//	        mNewAvail = 0;
//	    	mNewCount = 0;
//	    }
//
//
//	    @SuppressWarnings("unused")
//	    private String _cramCardLimit(String active[], String inactive[], String sql) {
//	        // inactive is (currently) ignored
//	        if (active.length > 0) {
//	            long yids[] = Utils.toPrimitive(tagIds(active).values());
//	            return sql.replace("WHERE ", "WHERE +c.id IN (SELECT cardId FROM cardTags WHERE " + "tagId IN "
//	                    + Utils.ids2str(yids) + ") AND ");
//	        } else {
//	            return sql;
//	        }
//	    }
//	    
//	    
//	    @SuppressWarnings("unused")
//	    private void _fillCramQueue() {
//	        if ((mRevCount != 0) && mRevQueue.isEmpty()) {
//	            Cursor cur = null;
//	            try {
//	                Log.i(AnkiDroidApp.TAG, "fill cram queue: " + Arrays.toString(mActiveCramTags) + " " + mCramOrder + " " + mQueueLimit);
//	                String sql = "SELECT id, factId FROM cards c WHERE queue BETWEEN 0 AND 2 ORDER BY " + mCramOrder
//	                        + " LIMIT " + mQueueLimit;
//	                sql = cardLimit(mActiveCramTags, null, sql);
//	                Log.i(AnkiDroidApp.TAG, "SQL: " + sql);
//	                cur = mDb.getDatabase().rawQuery(sql, null);
//	                while (cur.moveToNext()) {
//	                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
//	                    mRevQueue.add(0, qi); // Add to front, so list is reversed as it is built
//	                }
//	            } finally {
//	                if (cur != null && !cur.isClosed()) {
//	                    cur.close();
//	                }
//	            }
//	        }
//	    }
//
//
//	    @SuppressWarnings("unused")
//	    private void _rebuildCramCount() {
//	        mRevCount = (int) mDb.queryScalar(
//	                cardLimit(mActiveCramTags, null, "SELECT count(*) FROM cards c WHERE queue BETWEEN 0 AND 2"));
//	    }
//
//
//	    @SuppressWarnings("unused")
//	    private void _rebuildLrnCramCount() {
//	        mLrnCount = mLrnCramQueue.size();
//	    }
//
//
//	    @SuppressWarnings("unused")
//	    private void _fillLrnCramQueue() {
//	        mLrnQueue = mLrnCramQueue;
//	    }
//	    
//	    
//	}

}