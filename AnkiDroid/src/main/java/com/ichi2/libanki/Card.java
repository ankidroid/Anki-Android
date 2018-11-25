/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 A Card is the ultimate entity subject to review; it encapsulates the scheduling parameters (from which to derive
 the next interval), the note it is derived from (from which field data is retrieved), its own ownership (which deck it
 currently belongs to), and the retrieval of presentation elements (filled-in templates).
 
 Card presentation has two components: the question (front) side and the answer (back) side. The presentation of the
 card is derived from the template of the card's Card Type. The Card Type is a component of the Note Type (see Models)
 that this card is derived from.
 
 This class is responsible for:
 - Storing and retrieving database entries that map to Cards in the Collection
 - Providing the HTML representation of the Card's question and answer
 - Recording the results of review (answer chosen, time taken, etc)

 It does not:
 - Generate new cards (see Collection)
 - Store the templates or the style sheet (see Models)
 
 Type: 0=new, 1=learning, 2=due
 Queue: same as above, and:
        -1=suspended, -2=user buried, -3=sched buried
 Due is used differently for different queues.
 - new queue: note id or random int
 - rev queue: integer day
 - lrn queue: integer timestamp
 */
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.ExcessiveMethodLength","PMD.FieldDeclarationsShouldBeAtStartOfClass",
                    "PMD.MethodNamingConventions"})
public class Card implements Cloneable {

    public static final int TYPE_NEW = 0;
    public static final int TYPE_LRN = 1;
    public static final int TYPE_REV = 2;
    public static final int QUEUE_SUSP = -1;
    public static final int QUEUE_USER_BRD = -2;
    public static final int QUEUE_SCHED_BRD = -3;

    private Collection mCol;
    private double mTimerStarted;

    // Not in LibAnki. Record time spent reviewing in order to restore when resuming.
    private double mElapsedTime;

    // BEGIN SQL table entries
    private long mId;
    private long mNid;
    private long mDid;
    private int mOrd;
    private long mMod;
    private int mUsn;
    private int mType;
    private int mQueue;
    private long mDue;
    private int mIvl;
    private int mFactor;
    private int mReps;
    private int mLapses;
    private int mLeft;
    private long mODue;
    private long mODid;
    private int mFlags;
    private String mData;
    // END SQL table entries

    private HashMap<String, String> mQA;
    private Note mNote;

    // Used by Sched to determine which queue to move the card to after answering.
    private boolean mWasNew;

    // Used by Sched to record the original interval in the revlog after answering.
    private int mLastIvl;


    public Card(Collection col) {
        this(col, null);
    }


    public Card(Collection col, Long id) {
        mCol = col;
        mTimerStarted = Double.NaN;
        mQA = null;
        mNote = null;
        if (id != null) {
            mId = id;
            load();
        } else {
            // to flush, set nid, ord, and due
            mId = Utils.timestampID(mCol.getDb(), "cards");
            mDid = 1;
            mType = 0;
            mQueue = 0;
            mIvl = 0;
            mFactor = 0;
            mReps = 0;
            mLapses = 0;
            mLeft = 0;
            mODue = 0;
            mODid = 0;
            mFlags = 0;
            mData = "";
        }
    }


    public void load() {
        Cursor cursor = null;
        try {
            cursor = mCol.getDb().getDatabase().query("SELECT * FROM cards WHERE id = " + mId, null);
            if (!cursor.moveToFirst()) {
                throw new RuntimeException(" No card with id " + mId);
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
        flush(true);
    }

    public void flush(boolean changeModUsn) {
        if (changeModUsn) {
            mMod = Utils.intNow();
            mUsn = mCol.usn();
        }
        // bug check
        //if ((mQueue == 2 && mODue != 0) && !mCol.getDecks().isDyn(mDid)) {
            // TODO: runHook("odueInvalid");
        //}
        assert (mDue < Long.valueOf("4294967296"));
        mCol.getDb().execute(
                "insert or replace into cards values " +
                "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                mId,
                mNid,
                mDid,
                mOrd,
                mMod,
                mUsn,
                mType,
                mQueue,
                mDue,
                mIvl,
                mFactor,
                mReps,
                mLapses,
                mLeft,
                mODue,
                mODid,
                mFlags,
                mData
        });
        mCol.log(this);
    }


    public void flushSched() {
        mMod = Utils.intNow();
        mUsn = mCol.usn();
        // bug check
        //if ((mQueue == 2 && mODue != 0) && !mCol.getDecks().isDyn(mDid)) {
            // TODO: runHook("odueInvalid");
        //}
        assert (mDue < Long.valueOf("4294967296"));

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
        values.put("did", mDid);
        // TODO: The update DB call sets mod=true. Verify if this is intended.
        mCol.getDb().update("cards", values, "id = " + mId, null);
        mCol.log(this);
    }


    public String q() {
        return q(false);
    }


    public String q(boolean reload) {
        return q(reload, false);
    }


    public String q(boolean reload, boolean browser) {
        return css() + _getQA(reload, browser).get("q");
    }


    public String a() {
        return css() + _getQA().get("a");
    }


    public String css() {
        try {
            return String.format(Locale.US, "<style>%s</style>", model().get("css"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public HashMap<String, String> _getQA() {
        return _getQA(false);
    }


    public HashMap<String, String> _getQA(boolean reload) {
        return _getQA(reload, false);
    }


    public HashMap<String, String> _getQA(boolean reload, boolean browser) {
        if (mQA == null || reload) {
            Note f = note(reload);
            JSONObject m = model();
            JSONObject t = template();
            Object[] data;
            data = new Object[] { mId, f.getId(), m, mODid != 0L ? mODid : mDid, mOrd, f.stringTags(), f.joinedFields() };

            if (browser) {
                try {
                    String bqfmt = t.getString("bqfmt");
                    String bafmt = t.getString("bafmt");
                    mQA = mCol._renderQA(data, bqfmt, bafmt);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else {
                mQA = mCol._renderQA(data);
            }
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
        JSONObject m = model();
        try {
            if (m.getInt("type") == Consts.MODEL_STD) {
                return m.getJSONArray("tmpls").getJSONObject(mOrd);
            } else {
                return model().getJSONArray("tmpls").getJSONObject(0);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public void startTimer() {
        mTimerStarted = Utils.now();
    }


    /**
     * Time limit for answering in milliseconds.
     */
    public int timeLimit() {
        JSONObject conf = mCol.getDecks().confForDid(mODid == 0 ? mDid : mODid);
        try {
            return conf.getInt("maxTaken") * 1000;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean shouldShowTimer() {
        try {
            return mCol.getDecks().confForDid(mODid == 0 ? mDid : mODid).getInt("timer") != 0;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * Time taken to answer card, in integer MS.
     */
    public int timeTaken() {
        int total = (int) ((Utils.now() - mTimerStarted) * 1000);
        return Math.min(total, timeLimit());
    }


    public boolean isEmpty() {
        ArrayList<Integer> ords = mCol.getModels().availOrds(model(), Utils.joinFields(note().getFields()));
        return !ords.contains(mOrd);
    }


    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */


    public String qSimple() {
        return _getQA(false).get("q");
    }


    /*
     * Returns the answer with anything before the <hr id=answer> tag removed
     */
    public String getPureAnswer() {
        String s = _getQA(false).get("a");
        String target = "<hr id=answer>";
        int pos = s.indexOf(target);
        if (pos == -1) {
            return s;
        }
        return s.substring(pos + target.length()).trim();
    }

    /**
     * Save the currently elapsed reviewing time so it can be restored on resume.
     *
     * Use this method whenever a review session (activity) has been paused. Use the resumeTimer()
     * method when the session resumes to start counting review time again.
     */
    public void stopTimer() {
        mElapsedTime = Utils.now() - mTimerStarted;
    }


    /**
     * Resume the timer that counts the time spent reviewing this card.
     *
     * Unlike the desktop client, AnkiDroid must pause and resume the process in the middle of
     * reviewing. This method is required to keep track of the actual amount of time spent in
     * the reviewer and *must* be called on resume before any calls to timeTaken() take place
     * or the result of timeTaken() will be wrong.
     */
    public void resumeTimer() {
        mTimerStarted = Utils.now() - mElapsedTime;
    }

    public void setTimerStarted(double timeStarted){ mTimerStarted = timeStarted; }

    public long getId() {
        return mId;
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


    public void setNid(long nid) {
        mNid = nid;
    }


    public void setOrd(int ord) {
        mOrd = ord;
    }


    public int getOrd() {
        return mOrd;
    }


    public void setDid(long did) {
        mDid = did;
    }


    public long getDid() {
        return mDid;
    }


    public boolean getWasNew() {
        return mWasNew;
    }


    public void setWasNew(boolean wasNew) {
        mWasNew = wasNew;
    }


    public int getLastIvl() {
        return mLastIvl;
    }


    public void setLastIvl(int ivl) {
        mLastIvl = ivl;
    }


    // Needed for tests
    public Collection getCol() {
        return mCol;
    }


    // Needed for tests
    public void setCol(Collection col) {
        mCol = col;
    }


    public boolean showTimer() {
        return mCol.getDecks().confForDid(mODid == 0 ? mDid : mODid).optInt("timer", 1) != 0;
    }


    public Card clone() {
        try {
            return (Card)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }


    // A list of class members to skip in the toString() representation
    public static final Set<String> SKIP_PRINT = new HashSet<>(Arrays.asList("SKIP_PRINT", "$assertionsDisabled", "TYPE_LRN",
            "TYPE_NEW", "TYPE_REV", "mNote", "mQA", "mCol", "mTimerStarted", "mTimerStopped"));

    public String toString() {
        List<String> members = new ArrayList<>();
        for (Field f : this.getClass().getDeclaredFields()) {
            try {
                // skip non-useful elements
                if (SKIP_PRINT.contains(f.getName())) {
                    continue;
                }
                members.add(String.format("'%s': %s", f.getName(), f.get(this)));
            } catch (IllegalAccessException | IllegalArgumentException e) {
                members.add(String.format("'%s': %s", f.getName(), "N/A"));
            }
        }
        return TextUtils.join(",  ", members);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Card) {
            return this.getId() == ((Card)obj).getId();
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        // Map a long to an int. For API>=24 you would just do `Long.hashCode(this.getId())`
        return (int)(this.getId()^(this.getId()>>>32));
    }
}
