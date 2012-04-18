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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.anki.AnkiDroidApp;

/**
 * A card is a presentation of a note, and has two sides: a question and an
 * answer. Any number of fields can appear on each side. When you add a fact to
 * Anki, cards which show that fact are generated. Some models generate one
 * card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 * 
 * 
 *      Type: 0=new, 1=learning, 2=due Queue: same as above, and: -1=suspended,
 *      -2=user buried, -3=sched buried Due is used differently for different
 *      queues. - new queue: note id or random int - rev queue: integer day -
 *      lrn queue: integer timestamp
 */

public class Card implements Cloneable {

	public static final int TYPE_NEW = 0;
	public static final int TYPE_LRN = 1;
	public static final int TYPE_REV = 2;

	// BEGIN SQL table entries
	private long mId = 0;
	private long mNid;
	private long mDid;
	private int mOrd;
	private long mCrt = Utils.intNow();
	private long mMod;
	private int mType = 0;
	private int mQueue = 0;
	private long mDue = 0;
	private int mIvl = 0;
	private int mFactor = 0;
	private int mReps = 0;
	private int mLapses = 0;
	private int mLeft = 0;
	private int mUsn = 0;
	private int mFlags = 0;
	private long mODue = 0;
	private long mODid = 0;
	private String mData = "";
	// END SQL table entries

	private HashMap<String, String> mQA;
	private Note mNote;

	private double mTimerStarted;
	private double mTimerStopped;
	// private double mFuzz = 0;

	// Leech flags, not read from database, only set to true during the actual
	// suspension
	private boolean mIsLeechTagged;
	private boolean mIsLeechSuspended;

	private Collection mCol;

	public Card(Collection col) {
		this(col, 0);
	}

	public Card(Collection col, long id) {
		mCol = col;
		mTimerStarted = Double.NaN;
		mQA = null;
		mNote = null;
		if (id != 0) {
			mId = id;
			load();
		} else {
			// to flush, set nid, ord, and due
			mId = Utils.timestampID(mCol.getDb(), "cards");
			mDid = 1;
			mCrt = Utils.intNow();
			mType = 0;
			mQueue = 0;
			mIvl = 0;
			mFactor = 0;
			mReps = 0;
			mLapses = 0;
			mLeft = 0;
			mODue = 0;
			mFlags = 0;
			mData = "";
		}
	}

	public void load() {
		Cursor cursor = null;
		try {
			cursor = mCol.getDb().getDatabase()
					.rawQuery("SELECT * FROM cards WHERE id = " + mId, null);
			if (!cursor.moveToFirst()) {
				Log.w(AnkiDroidApp.TAG,
						"Card.java (fromDB(id)): No result from query.");
				return;
			}
			mId = cursor.getLong(0);
			mNid = cursor.getLong(1);
			mDid = cursor.getLong(2);
			mOrd = cursor.getInt(3);
			mMod = cursor.getLong(4);
			mUsn = cursor.getInt(5);
			mType = cursor.getInt(6);
			mQueue = cursor.getInt(7);
			mDue = cursor.getInt(8);
			mIvl = cursor.getInt(9);
			mFactor = cursor.getInt(10);
			mReps = cursor.getInt(11);
			mLapses = cursor.getInt(12);
			mLeft = cursor.getInt(13);
			mODue = cursor.getLong(14);
			mODid = cursor.getLong(15);
			mFlags = cursor.getInt(16);
			mData = cursor.getString(17);
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		mQA = null;
		mNote = null;
	}

	public void flush() {
		mMod = Utils.intNow();
		mUsn = mCol.usn();
		StringBuilder sb = new StringBuilder();
		sb.append("INSERT OR REPLACE INTO cards VALUES (");
		sb.append(mId).append(", ");
		sb.append(mNid).append(", ");
		sb.append(mDid).append(", ");
		sb.append(mOrd).append(", ");
		sb.append(mMod).append(", ");
		sb.append(mUsn).append(", ");
		sb.append(mType).append(", ");
		sb.append(mQueue).append(", ");
		sb.append(mDue).append(", ");
		sb.append(mIvl).append(", ");
		sb.append(mFactor).append(", ");
		sb.append(mReps).append(", ");
		sb.append(mLapses).append(", ");
		sb.append(mLeft).append(", ");
		sb.append(mODue).append(", ");
		sb.append(mODid).append(", ");
		sb.append(mFlags).append(", ");
		sb.append("\"").append(mData).append("\")");
		mCol.getDb().execute(sb.toString());
	}

	public void flushSched() {
		mMod = Utils.intNow();
		mUsn = mCol.usn();
		ContentValues values = new ContentValues();
		values.put("mod", mMod);
		values.put("usn", mUsn);
		values.put("type", mType);
		values.put("queue", mQueue);
		values.put("due", mDue);
		values.put("ivl", mIvl);
		values.put("factor", mFactor);
		values.put("reps", mReps);
		values.put("lapses", mLapses);
		values.put("left", mLeft);
		values.put("odue", mODue);
		values.put("odid", mODid);
		mCol.getDb().update("cards", values, "id = " + mId, null);
	}

	public String getQuestion(boolean simple) {
		return getQuestion(false, simple);
	}

	public String getQuestion(boolean reload, boolean simple) {
		if (simple) {
			return _getQA(reload).get("q");
		} else {
			return css() + _getQA(reload).get("q");
		}
	}

	public String getAnswer(boolean simple) {
		if (simple) {
			return _getQA(false).get("a").replaceAll("<hr[^>]*>", "<br>─────<br>");
		} else {
			return css() + _getQA(false).get("a");
		}
	}

	public String css() {
		try {
			// return (new
			// StringBuilder()).append("<style type=\"text/css\">").append(template().get("css")).append("</style>").toString();
			return (new StringBuilder()).append("<style>")
					.append(template().get("css")).append("</style>")
					.toString();
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public HashMap<String, String> _getQA(boolean reload) {
		if (mQA == null || reload) {
			mQA = new HashMap<String, String>();
			Note n = note(reload);
			JSONObject m = model();
			Object[] data;
			try {
				data = new Object[] { mId, n.getId(), m.getLong("id"), mDid,
						mOrd, n.stringTags(), n.joinedFields() };
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			mQA = mCol._renderQA(data);
		}
		return mQA;
	}

	public Note note() {
		return note(false);
	}

	public Note note(boolean reload) {
		if (mNote == null || reload) {
			mNote = mCol.getNote(mNid);
		}
		return mNote;
	}

	public JSONObject model() {
		return mCol.getModels().get(note().getMid());
	}

	public JSONObject template() {
		try {
			return model().getJSONArray("tmpls").getJSONObject(mOrd);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public void startTimer() {
		mTimerStarted = Utils.now();
	}

	public void setTimer(double time) {
		mTimerStarted = time;
	}

	// public void stopTimer() {
	// mTimerStopped = Utils.now();
	// }

	// public void resumeTimer() {
	// if (!Double.isNaN(mTimerStarted) && !Double.isNaN(mTimerStopped)) {
	// mTimerStarted += Utils.now() - mTimerStopped;
	// mTimerStopped = Double.NaN;
	// } else {
	// Log.i(AnkiDroidApp.TAG, "Card Timer: nothing to resume");
	// }
	// }

	/**
	 * Time taken to answer card, in integer MS.
	 */
	public int timeLimit() {
		JSONObject conf = mCol.getDecks().confForDid(mODid == 0 ? mDid : mODid);
		int total = (int) ((Utils.now() - mTimerStarted) * 1000);
		try {
			return conf.getInt("maxTaken") * 1000;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public int timeTaken() {
		int total = (int) ((Utils.now() - mTimerStarted) * 1000);
		return Math.min(total, timeLimit());
	}

	/**
	 * The cardModel defines a field typeAnswer. If it is empty, then no answer
	 * should be typed. Otherwise a typed answer should be compared to the value
	 * of field related to a cards fact. A field is found based on the factId in
	 * the card and the fieldModelId. The fieldModel's id is found by searching
	 * with the typeAnswer name and cardModel's modelId
	 * 
	 * @return 2 dimensional array with answer value at index=0 and fieldModel's
	 *         class at index=1 null if typeAnswer is empty (i.e. do not prompt
	 *         for answer). Otherwise a string (which can be empty) from the
	 *         actual field value. The fieldModel's id is correctly hexafied and
	 *         formatted for class attribute of span for formatting
	 */
	public String[] getComparedFieldAnswer() {
		String[] returnArray = new String[2];
		// CardModel myCardModel = this.getCardModel();
		// String typeAnswer = myCardModel.getTypeAnswer();
		// if (null == typeAnswer || 0 == typeAnswer.trim().length()) {
		// returnArray[0] = null;
		// }
		// Model myModel = Model.getModel(mDeck, myCardModel.getModelId(),
		// true);
		// TreeMap<Long, FieldModel> fieldModels = myModel.getFieldModels();
		// FieldModel myFieldModel = null;
		// long myFieldModelId = 0l;
		// for (TreeMap.Entry<Long, FieldModel> entry : fieldModels.entrySet())
		// {
		// myFieldModel = entry.getValue();
		// myFieldModelId = myFieldModel.match(myCardModel.getModelId(),
		// typeAnswer);
		// if (myFieldModelId != 0l) {
		// break;
		// }
		// }
		// returnArray[0] = com.ichi2.anki.Field.fieldValuefromDb(this.mDeck,
		// this.mFactId, myFieldModelId);
		// returnArray[1] = "fm" + Long.toHexString(myFieldModelId);
		return returnArray;
	}

	//
	// /**
	// // * Questions and answers
	// // */
	// public void rebuildQA(Deck deck) {
	// rebuildQA(deck, true);
	// }
	// public void rebuildQA(Deck deck, boolean media) {
	// // Format qa
	// if (mFact != null && mCardModel != null) {
	// HashMap<String, String> qa = CardModel.formatQA(mFact, mCardModel,
	// _splitTags());
	//
	// if (media) {
	// // Find old media references
	// HashMap<String, Integer> files = new HashMap<String, Integer>();
	// ArrayList<String> filesFromQA = Media.mediaFiles(mQuestion);
	// filesFromQA.addAll(Media.mediaFiles(mAnswer));
	// for (String f : filesFromQA) {
	// if (files.containsKey(f)) {
	// files.put(f, files.get(f) - 1);
	// } else {
	// files.put(f, -1);
	// }
	// }
	// // Update q/a
	// mQuestion = qa.get("question");
	// mAnswer = qa.get("answer");
	// // Determine media delta
	// filesFromQA = Media.mediaFiles(mQuestion);
	// filesFromQA.addAll(Media.mediaFiles(mAnswer));
	// for (String f : filesFromQA) {
	// if (files.containsKey(f)) {
	// files.put(f, files.get(f) + 1);
	// } else {
	// files.put(f, 1);
	// }
	// }
	// // Update media counts if we're attached to deck
	// for (Entry<String, Integer> entry : files.entrySet()) {
	// Media.updateMediaCount(deck, entry.getKey(), entry.getValue());
	// }
	// } else {
	// // Update q/a
	// mQuestion = qa.get("question");
	// mAnswer = qa.get("answer");
	// }
	// setModified();
	// }
	// }
	//
	//
	//
	//
	// public double getFuzz() {
	// if (mFuzz == 0) {
	// genFuzz();
	// }
	// return mFuzz;
	// }
	//
	// public void genFuzz() {
	// // Random rand = new Random();
	// // mFuzz = 0.95 + (0.1 * rand.nextDouble());
	// mFuzz = (double) Math.random();
	// }
	//
	//
	//
	// public String getCardDetails(Context context) {
	// Resources res = context.getResources();
	// StringBuilder builder = new StringBuilder();
	// builder.append("<html><body text=\"#FFFFFF\"><table><colgroup><col span=\"1\" style=\"width: 40%;\"><col span=\"1\" style=\"width: 60%;\"></colgroup><tr><td>");
	// builder.append(res.getString(R.string.card_details_question));
	// builder.append("</td><td>");
	// builder.append(Utils.stripHTML(mQuestion));
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_answer));
	// builder.append("</td><td>");
	// builder.append(Utils.stripHTML(mAnswer));
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_due));
	// builder.append("</td><td>");
	// if (mYesCount + mNoCount == 0) {
	// builder.append("-");
	// } else if (mCombinedDue < mDeck.getDueCutoff()) {
	// builder.append(res.getString(R.string.card_details_now));
	// } else {
	// builder.append(Utils.getReadableInterval(context, (mCombinedDue -
	// Utils.now()) / 86400.0, true));
	// }
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_interval));
	// builder.append("</td><td>");
	// if (mInterval == 0) {
	// builder.append("-");
	// } else {
	// builder.append(Utils.getReadableInterval(context, mInterval));
	// }
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_ease));
	// builder.append("</td><td>");
	// double ease = Math.round(mFactor * 100);
	// builder.append(ease / 100);
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_average_time));
	// builder.append("</td><td>");
	// if (mYesCount + mNoCount == 0) {
	// builder.append("-");
	// } else {
	// builder.append(Utils.doubleToTime(mAverageTime));
	// }
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_total_time));
	// builder.append("</td><td>");
	// builder.append(Utils.doubleToTime(mReviewTime));
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_yes_count));
	// builder.append("</td><td>");
	// builder.append(mYesCount);
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_no_count));
	// builder.append("</td><td>");
	// builder.append(mNoCount);
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_added));
	// builder.append("</td><td>");
	// builder.append(DateFormat.getDateFormat(context).format((long) (mCreated
	// - mDeck.getUtcOffset()) * 1000l));
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_changed));
	// builder.append("</td><td>");
	// builder.append(DateFormat.getDateFormat(context).format((long) (mModified
	// - mDeck.getUtcOffset()) * 1000l));
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_tags));
	// builder.append("</td><td>");
	// String tags = Arrays.toString(mDeck.allUserTags("WHERE id = " +
	// mFactId));
	// builder.append(tags.substring(1, tags.length() - 1));
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_model));
	// builder.append("</td><td>");
	// Model model = Model.getModel(mDeck, mCardModelId, false);
	// builder.append(model.getName());
	// builder.append("</td></tr><tr><td>");
	// builder.append(res.getString(R.string.card_details_card_model));
	// builder.append("</td><td>");
	// builder.append(model.getCardModel(mCardModelId).getName());
	// builder.append("</td></tr></html></body>");
	// return builder.toString();
	// }

	public long getId() {
		return mId;
	}

	public void setId(long id) {
		mId = id;
	}

	public long getMod() {
		return mMod;
	}

	public void setMod() {
		mMod = Utils.intNow();
	}

	public void setMod(long mod) {
		mMod = mod;
	}

	public void setUsn(int usn) {
		mUsn = usn;
	}

	public long getNid() {
		return mNid;
	}

	public int getType() {
		return mType;
	}

	public void setType(int type) {
		mType = type;
	}

	public void setLeft(int left) {
		mLeft = left;
	}

	public int getLeft() {
		return mLeft;
	}

	public int getQueue() {
		return mQueue;
	}

	public void setQueue(int queue) {
		mQueue = queue;
	}

	public long getODue() {
		return mODue;
	}

	public void setODid(long odid) {
		mODid = odid;
	}

	public long getODid() {
		return mODid;
	}

	public void setODue(long odue) {
		mODue = odue;
	}

	public long getDue() {
		return mDue;
	}

	public void setDue(long due) {
		mDue = due;
	}

	public int getLastIvl() {
		// TODO
		return 0;
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

	public void setLastIvl(int lastIvl) {
		// mLastIvl = lastIvl;
	}

	// Leech flag
	public boolean getLeechFlag() {
		return mIsLeechTagged;
	}

	public void setLeechFlag(boolean flag) {
		mIsLeechTagged = flag;
	}

	// Suspended flag
	public boolean getSuspendedFlag() {
		return mIsLeechSuspended;
	}

	public void setSuspendedFlag(boolean flag) {
		mIsLeechSuspended = flag;
	}

	public void setNid(long nid) {
		mNid = nid;
	}

	public void setOrd(int ord) {
		mOrd = ord;
	}

	public void setDid(long did) {
		mDid = did;
	}

	public long getDid() {
		return mDid;
	}

	public Card clone() {
		try {
			return (Card) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
