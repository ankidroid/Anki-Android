/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.anki.AnkiDroidApp;

/**
 * A card is a presentation of a fact, and has two sides: a question and an answer. Any number of fields can appear on
 * each side. When you add a fact to Anki, cards which show that fact are generated. Some models generate one card,
 * others generate more than one.
 *
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 * 
 * 
 * Type: 0=new, 1=learning, 2=due
 * Queue: same as above, and:
 *        -1=suspended, -2=user buried, -3=sched buried, -4=deleted
 * Due is used differently for different queues.
 * - new queue: fact id or random int
 * - rev queue: integer day
 * - lrn queue: integer timestamp
 */


public class Card {

    // BEGIN SQL table entries
    private int mId = 0;
    private int mFId;
    private int mGId;
    private int mOrd;
    private int mCrt = Utils.intNow();
    private int mMod;
    private int mType = 0;
    private int mQueue = 0;
    private int mDue = 0;
    private int mIvl = 0;
    private int mFactor = 0;
    private int mReps = 0;
    private int mLapses = 0;
    private int mGrade = 0;
    private int mCycles = 0;
    private int mEDue = 0;
    private String mData = "";
    // END SQL table entries

    private Deck mDeck;
    private HashMap<String, String> mQA;
	private Fact mFact;

    private double mTimerStarted;
    private double mTimerStopped;
//    private double mFuzz = 0;

    // Leech flags, not read from database, only set to true during the actual suspension
//    private boolean isLeechMarked;
//    private boolean isLeechSuspended;


    public Card(Deck deck) {
    	this(deck, 0);
    }
    public Card(Deck deck, Fact fact) {
    	this(deck, fact, 0);
    }
    public Card(Deck deck, int id) {
    	this(deck, null, id);
    }
    public Card(Deck deck, Fact fact, int id) {
        mDeck = deck;
        mFact = fact;
        mTimerStarted = Double.NaN;
        if (id != 0)  {
        	mId = id;
        	fromDB(id);
        } else {
        	// to flush, set fid, ord and due
        	mGId = 1;
        	mType = 0;
        	mQueue = 0;
        	mIvl = 0;
        	mFactor = 0;
        	mReps = 0;
        	mLapses = 0;
        	mCycles = 0;
        	mEDue = 0;
        	mData = "";
        }
    }


	public boolean fromDB(long id) {
        Cursor cursor = null;
        try {
            cursor = mDeck.getDB().getDatabase().rawQuery("SELECT * FROM cards WHERE id = " + id, null);
            if (!cursor.moveToFirst()) {
                Log.w(AnkiDroidApp.TAG, "Card.java (fromDB(id)): No result from query.");
                return false;
            }
            mId = cursor.getInt(0);
            mFId = cursor.getInt(1);
            mGId = cursor.getInt(2);
            mOrd = cursor.getInt(3);
            mCrt = cursor.getInt(4);
            mMod = cursor.getInt(5);
            mType = cursor.getInt(6);
            mQueue = cursor.getInt(7);
            mDue = cursor.getInt(8);
            mIvl = cursor.getInt(9);
            mFactor = cursor.getInt(10);
            mReps = cursor.getInt(11);
            mLapses = cursor.getInt(12);
            mGrade = cursor.getInt(13);
            mCycles = cursor.getInt(14);
            mEDue = cursor.getInt(15);
            mData = cursor.getString(16);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return true;
    }


    public void flush() {
    	mMod = Utils.intNow();
    	// facts table
    	StringBuilder sb = new StringBuilder();
    	sb.append("INSERT OR REPLACE INTO cards VALUES (");
    	sb.append(mId).append(", ");
    	sb.append(mFId).append(", ");
    	sb.append(mGId).append(", ");
    	sb.append(mOrd).append(", ");
    	sb.append(mMod).append(", ");
    	sb.append(mType).append(", ");
    	sb.append(mQueue).append(", ");
    	sb.append(mIvl).append(", ");
    	sb.append(mIvl).append(", ");
    	sb.append(mFactor).append(", ");
    	sb.append(mReps).append(", ");
    	sb.append(mLapses).append(", ");
    	sb.append(mGrade).append(", ");
    	sb.append(mCycles).append(", ");
    	sb.append(mEDue).append(", ");
    	sb.append(mData).append(")");
    	mDeck.getDB().getDatabase().execSQL(sb.toString());
    }


    public ContentValues getSchedValues() {
    	ContentValues values = new ContentValues();
        values.put("mod", mMod);
        values.put("type", mType);
        values.put("queue", mQueue);
        values.put("due", mDue);
        values.put("ivl", mIvl);
        values.put("factor", mFactor);
        values.put("reps", mReps);
        values.put("lapses", mLapses);
        values.put("grade", mGrade);
        values.put("cycles", mCycles);
        values.put("edue", mEDue);
        return values;
    }


    public void flushSched() {
    	mMod = Utils.intNow();
        mDeck.getDB().getDatabase().update("cards", getSchedValues(), "id = " + mId, null);
    }


    public String getQuestion() {
        return getQuestion("q", false);
    }
    public String getQuestion(String classes, boolean reload) {
        return _withClass(_getQA(reload).get("q"), classes);
    }


    public String getAnswer() {
        return getAnswer("a");
    }
    public String getAnswer(String classes) {
        return _withClass(_getQA(false).get("a"), classes);
    }


    public HashMap<String, String> _getQA(boolean reload) {
        if (mQA == null || reload) {
            mQA = new HashMap<String, String>();
        	Cursor cursor = null;
        	String gname = "";
            try {
                cursor = mDeck.getDB().getDatabase().rawQuery("SELECT name FROM groups WHERE id = " + mGId, null);
                while (cursor.moveToNext()) {
                	gname = cursor.getString(0);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            mQA = mDeck._renderQA(getModel(), mOrd, getFact().getFields(), gname, getFact().stringTags());
        }
        return mQA;
    }


    public String _withClass(String txt, String extra) {
        return new StringBuilder().append("<div class=\"").append(cssClass())
        	.append(" ").append(extra).append("\">").append(txt).append("</div>").toString();
    }


    public Fact getFact() {
        if (mFact == null) {
            mFact = mDeck.getFact(mFId);
        }
        return mFact;
    }


    public Model getModel() {
    	return mDeck.getModel(getFact().getMId());
    }


    public JSONObject getTemplate() {
        return getModel().getTemplate(mOrd);
    }


    public String cssClass() {
        try {
			return new StringBuilder().append("cm").append(Utils.hexifyID(getModel().getId()))
			.append("-").append(Utils.hexifyID(getTemplate().getInt("ord"))).toString();
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }


    public void startTimer() {
        mTimerStarted = Utils.now();
    }


    public void stopTimer() {
        mTimerStopped = Utils.now();
    }


    public void resumeTimer() {
        if (!Double.isNaN(mTimerStarted) && !Double.isNaN(mTimerStopped)) {
            mTimerStarted += Utils.now() - mTimerStopped;
            mTimerStopped = Double.NaN;
        } else {
            Log.i(AnkiDroidApp.TAG, "Card Timer: nothing to resume");
        }
    }


    /**
     * Time taken to answer card, in integer MS.
     */
    public int timeTaken() {
    	return (int)((Utils.now() - mTimerStarted) * 1000);
    }


//
//	/**
////	 * Questions and answers
////	 */
//	public void rebuildQA(Deck deck) {
//		rebuildQA(deck, true);
//	}
//	public void rebuildQA(Deck deck, boolean media) {
//        // Format qa
//		if (mFact != null && mCardModel != null) {
//			HashMap<String, String> qa = CardModel.formatQA(mFact, mCardModel, _splitTags());
//
//            if (media) {
//                // Find old media references
//                HashMap<String, Integer> files = new HashMap<String, Integer>();
//                ArrayList<String> filesFromQA = Media.mediaFiles(mQuestion);
//                filesFromQA.addAll(Media.mediaFiles(mAnswer));
//                for (String f : filesFromQA) {
//                    if (files.containsKey(f)) {
//                        files.put(f, files.get(f) - 1);
//                    } else {
//                        files.put(f, -1);
//                    }
//                }
//                // Update q/a
//                mQuestion = qa.get("question");
//                mAnswer = qa.get("answer");
//                // Determine media delta
//                filesFromQA = Media.mediaFiles(mQuestion);
//                filesFromQA.addAll(Media.mediaFiles(mAnswer));
//                for (String f : filesFromQA) {
//                    if (files.containsKey(f)) {
//                        files.put(f, files.get(f) + 1);
//                    } else {
//                        files.put(f, 1);
//                    }
//                }
//                // Update media counts if we're attached to deck
//                for (Entry<String, Integer> entry : files.entrySet()) {
//                    Media.updateMediaCount(deck, entry.getKey(), entry.getValue());
//                }
//            } else {
//                // Update q/a
//                mQuestion = qa.get("question");
//                mAnswer = qa.get("answer");
//            }
//            setModified();
//		}
//	}
//
//
//
//
//    public double getFuzz() {
//    	if (mFuzz == 0) {
//    		genFuzz();
//    	}
//    	return mFuzz;
//    }
//
//    public void genFuzz() {
//        // Random rand = new Random();
//        // mFuzz = 0.95 + (0.1 * rand.nextDouble());
//        mFuzz = (double) Math.random();
//    }
//
//
//
//    public String getCardDetails(Context context) {
//    	Resources res = context.getResources();
//        StringBuilder builder = new StringBuilder();
//        builder.append("<html><body text=\"#FFFFFF\"><table><colgroup><col span=\"1\" style=\"width: 40%;\"><col span=\"1\" style=\"width: 60%;\"></colgroup><tr><td>");
//        builder.append(res.getString(R.string.card_details_question));
//        builder.append("</td><td>");
//        builder.append(Utils.stripHTML(mQuestion));
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_answer));
//        builder.append("</td><td>");
//        builder.append(Utils.stripHTML(mAnswer));
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_due));
//        builder.append("</td><td>");
//        if (mYesCount + mNoCount == 0) {
//            builder.append("-");
//        } else if (mCombinedDue < mDeck.getDueCutoff()) {
//            builder.append(res.getString(R.string.card_details_now));
//        } else {
//            builder.append(Utils.getReadableInterval(context, (mCombinedDue - Utils.now()) / 86400.0, true));
//        }
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_interval));
//        builder.append("</td><td>");
//        if (mInterval == 0) {
//            builder.append("-");
//        } else {
//            builder.append(Utils.getReadableInterval(context, mInterval));
//        }
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_ease));
//        builder.append("</td><td>");
//        double ease = Math.round(mFactor * 100);
//        builder.append(ease / 100);
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_average_time));
//        builder.append("</td><td>");
//        if (mYesCount + mNoCount == 0) {
//            builder.append("-");
//        } else {
//            builder.append(Utils.doubleToTime(mAverageTime));
//        }
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_total_time));
//        builder.append("</td><td>");
//        builder.append(Utils.doubleToTime(mReviewTime));
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_yes_count));
//        builder.append("</td><td>");
//        builder.append(mYesCount);
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_no_count));
//        builder.append("</td><td>");
//        builder.append(mNoCount);
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_added));
//        builder.append("</td><td>");
//        builder.append(DateFormat.getDateFormat(context).format((long) (mCreated - mDeck.getUtcOffset()) * 1000l));
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_changed));
//        builder.append("</td><td>");
//        builder.append(DateFormat.getDateFormat(context).format((long) (mModified - mDeck.getUtcOffset()) * 1000l));
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_tags));
//        builder.append("</td><td>");
//        String tags = Arrays.toString(mDeck.allUserTags("WHERE id = " + mFactId));
//        builder.append(tags.substring(1, tags.length() - 1));
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_model));
//        builder.append("</td><td>");
//        Model model = Model.getModel(mDeck, mCardModelId, false);
//        builder.append(model.getName());
//        builder.append("</td></tr><tr><td>");
//        builder.append(res.getString(R.string.card_details_card_model));
//        builder.append("</td><td>");
//        builder.append(model.getCardModel(mCardModelId).getName());
//        builder.append("</td></tr></html></body>");
//        return builder.toString();
//    }

    public int getId() {
    	return mId;
    }


    public void setId(int id) {
    	mId = id;
    }


    public int getFId() {
    	return mFId;
    }


    public void setFId(int fid) {
    	mFId = fid;
    }


    public int getGId() {
    	return mGId;
    }


    public void setGId(int gid) {
    	mGId = gid;
    }


    public int getOrd() {
    	return mOrd;
    }


    public void setOrd(int ord) {
    	mOrd = ord;
    }


    public int getCrt() {
    	return mCrt;
    }


    public void setCrt(int crt) {
    	mCrt = crt;
    }


    public int getMod() {
    	return mMod;
    }


    public void setMod() {
    	mMod = Utils.intNow();
    }


    public void setMod(int mod) {
    	mMod = mod;
    }


    public int getType() {
    	return mType;
    }


    public void setType(int type) {
    	mType = type;
    }


    public int getQueue() {
    	return mQueue;
    }


    public void setQueue(int queue) {
    	mQueue = queue;
    }


    public int getDue() {
    	return mDue;
    }


    public void setDue(int due) {
    	mDue = due;
    }


    public int getIvl() {
    	return mIvl;
    }


    public void setIvl(int ivl) {
    	mIvl = ivl;
    }


    public int getFactor() {
    	return mFactor;
    }


    public void setFactor(int factor) {
    	mFactor = factor;
    }


    public int getReps() {
        return mReps;
    }


    public int setReps(int reps) {
        return mReps = reps;
    }


    public int getLapses() {
        return mLapses;
    }


    public void setLapses(int lapses) {
        mLapses = lapses;
    }


    public int getLastIvl() {
        return 0;//mLastIvl;
    }


    public void setLastIvl(int lastIvl) {
//        mLastIvl = lastIvl;
    }


    public int getGrade() {
        return mGrade;
    }


    public void setGrade(int grade) {
        mGrade = grade;
    }


    public int getCycles() {
        return mCycles;
    }


    public void setCycles(int cycles) {
        mCycles = cycles;
    }


    public int getEDue() {
        return mEDue;
    }


    public void setEDue(int edue) {
    	mEDue = edue;
    }


    public String getData() {
        return mData;
    }
//
//
//    public double nextInterval(Card card, int ease) {
//        return mDeck.nextInterval(card, ease);
//    }
//
//    // Leech flag
//    public boolean getLeechFlag() {
//        return isLeechMarked;
//    }
//    public void setLeechFlag(boolean flag) {
//        isLeechMarked = flag;
//    }
//    // Suspended flag
//    public boolean getSuspendedFlag() {
//        return isLeechSuspended;
//    }
//    public void setSuspendedFlag(boolean flag) {
//        isLeechSuspended = flag;
//    }
}
