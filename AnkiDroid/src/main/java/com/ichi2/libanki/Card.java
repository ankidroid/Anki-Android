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

import com.ichi2.anki.CollectionHelper;
import com.ichi2.async.CancelListener;
import com.ichi2.libanki.template.TemplateError;
import com.ichi2.utils.Assert;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.utils.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;

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

    public static final int TYPE_REV = 2;

    private Collection mCol;
    // When timer was started, in MS
    private long mTimerStarted;

    // Not in LibAnki. Record time spent reviewing in MS in order to restore when resuming.
    private long mElapsedTime;

    // BEGIN SQL table entries
    private long mId;
    private long mNid;
    private long mDid;
    private int mOrd;
    private long mMod;
    private int mUsn;
    @Consts.CARD_TYPE
    private int mType;
    @Consts.CARD_QUEUE
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


    public Card(@NonNull Collection col) {
        mCol = col;
        mTimerStarted = 0L;
        mQA = null;
        mNote = null;
        // to flush, set nid, ord, and due
        mId = mCol.getTime().timestampID(mCol.getDb(), "cards");
        mDid = 1;
        mType = Consts.CARD_TYPE_NEW;
        mQueue = Consts.QUEUE_TYPE_NEW;
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


    public Card(@NonNull Collection col, @NonNull Long id) {
        mCol = col;
        mTimerStarted = 0L;
        mQA = null;
        mNote = null;
        mId = id;
        load();
    }


    public void load() {
        try (Cursor cursor = mCol.getDb().query("SELECT * FROM cards WHERE id = ?", mId)) {
            if (!cursor.moveToFirst()) {
                throw new WrongId(mId, "card");
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
        }
        mQA = null;
        mNote = null;
    }



    public void flush() {
        flush(true);
    }

    public void flush(boolean changeModUsn) {
        if (changeModUsn) {
            mMod = getCol().getTime().intTime();
            mUsn = mCol.usn();
        }
        // bug check
        //if ((mQueue == Consts.QUEUE_TYPE_REV && mODue != 0) && !mCol.getDecks().isDyn(mDid)) {
            // TODO: runHook("odueInvalid");
        //}
        assert (mDue < Long.parseLong("4294967296"));
        mCol.getDb().execute(
                "insert or replace into cards values " +
                "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
        );
        mCol.log(this);
    }


    public void flushSched() {
        mMod = getCol().getTime().intTime();
        mUsn = mCol.usn();
        // bug check
        //if ((mQueue == Consts.QUEUE_TYPE_REV && mODue != 0) && !mCol.getDecks().isDyn(mDid)) {
            // TODO: runHook("odueInvalid");
        //}
        assert (mDue < Long.parseLong("4294967296"));

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
        mCol.getDb().update("cards", values, "id = ?", new String[] {Long.toString(mId)});
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
        return String.format(Locale.US, "<style>%s</style>", model().getString("css"));
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
            Model m = model();
            JSONObject t = template();
            long did = isInDynamicDeck() ? mODid : mDid;
            if (browser) {
                String bqfmt = t.getString("bqfmt");
                String bafmt = t.getString("bafmt");
                mQA = mCol._renderQA(mId, m, did, mOrd, f.stringTags(), f.getFields(), mFlags, browser, bqfmt, bafmt);
            } else {
                mQA = mCol._renderQA(mId, m, did, mOrd, f.stringTags(), f.getFields(), mFlags);
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


    // not in upstream
    public Model model() {
        return note().model();
    }


    public JSONObject template() {
        Model m = model();
        if (m.isStd()) {
            return m.getJSONArray("tmpls").getJSONObject(mOrd);
        } else {
            return model().getJSONArray("tmpls").getJSONObject(0);
        }
    }


    public void startTimer() {
        mTimerStarted = getCol().getTime().intTimeMS();
    }


    /**
     * Time limit for answering in milliseconds.
     */
    public int timeLimit() {
        DeckConfig conf = mCol.getDecks().confForDid(!isInDynamicDeck() ? mDid : mODid);
        return conf.getInt("maxTaken") * 1000;
    }


    /*
     * Time taken to answer card, in integer MS.
     */
    public int timeTaken() {
        // Indeed an int. Difference between two big numbers is still small.
        int total = (int) (getCol().getTime().intTimeMS() - mTimerStarted);
        return Math.min(total, timeLimit());
    }


    public boolean isEmpty() {
        try {
            return Models.emptyCard(model(), mOrd, note().getFields());
        } catch (TemplateError er) {
            Timber.w("Card is empty because the card's template has an error: %s.", er.message(getCol().getContext()));
            return true;
        }
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
        mElapsedTime = getCol().getTime().intTimeMS() - mTimerStarted;
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
        mTimerStarted = getCol().getTime().intTimeMS() - mElapsedTime;
    }


    /**
     * @param timeStarted Time in MS when timer was started
     */
    public void setTimerStarted(long timeStarted){ mTimerStarted = timeStarted; }

    public long getId() {
        return mId;
    }

    @VisibleForTesting
    public void setId(long id) {
        mId = id;
    }


    public void setMod(long mod) {
        mMod = mod;
    }

    public long getMod() {
        return mMod ;
    }


    public void setUsn(int usn) {
        mUsn = usn;
    }


    public long getNid() {
        return mNid;
    }


    @Consts.CARD_TYPE
    public int getType() {
        return mType;
    }


    public void setType(@Consts.CARD_TYPE int type) {
        mType = type;
    }


    public void setLeft(int left) {
        mLeft = left;
    }


    public int getLeft() {
        return mLeft;
    }

    @Consts.CARD_QUEUE
    public int getQueue() {
        return mQueue;
    }


    public void setQueue(@Consts.CARD_QUEUE int queue) {
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


    @VisibleForTesting
    public int setReps(int reps) {
        return mReps = reps;
    }


    public int incrReps() {
        return ++mReps;
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
        DeckConfig options = mCol.getDecks().confForDid(!isInDynamicDeck() ? mDid : mODid);
        return DeckConfig.parseTimerOpt(options, true);
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

    public @NonNull String toString() {
        Field[] declaredFields = this.getClass().getDeclaredFields();
        List<String> members = new ArrayList<>(declaredFields.length);
        for (Field f : declaredFields) {
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

    public static int intToFlag(int flags) {
        // setting all bits to 0, except the three first one.
        // equivalent to `mFlags % 8`. Used this way to copy Anki.
        return flags & 0b111;
    }

    public int userFlag() {
        return Card.intToFlag(mFlags);
    }

    public static int setFlagInInt(int mFlags, int flag) {
        Assert.that(0 <= flag, "flag to set is negative");
        Assert.that(flag <= 7, "flag to set is greater than 7.");
        // Setting the 3 firsts bits to 0, keeping the remaining.
        int extraData = (mFlags & ~0b111);
        // flag in 3 fist bits, same data as in mFlags everywhere else
        return extraData | flag;
    }

    @VisibleForTesting
    public void setFlag(int flag) {
        mFlags = flag;
    }

    public void setUserFlag(int flag) {
        mFlags = setFlagInInt(mFlags, flag);
    }

    // not in Anki.
    public String getDueString() {
        String t = nextDue();
        if (getQueue() < 0) {
            t = "(" + t + ")";
        }
        return t;
    }

    // as in Anki aqt/browser.py
    @VisibleForTesting
    public String nextDue() {
        long date;
        long due = getDue();
        if (isInDynamicDeck()) {
            return AnkiDroidApp.getAppResources().getString(R.string.card_browser_due_filtered_card);
        } else if (getQueue() == Consts.QUEUE_TYPE_LRN) {
            date = due;
        } else if (getQueue() == Consts.QUEUE_TYPE_NEW || getType() == Consts.CARD_TYPE_NEW) {
            return (Long.valueOf(due)).toString();
        } else if (getQueue() == Consts.QUEUE_TYPE_REV || getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN || (getType() == Consts.CARD_TYPE_REV && getQueue() < 0)) {
            long time = mCol.getTime().intTime();
            long nbDaySinceCreation = (due - getCol().getSched().getToday());
            date = time + (nbDaySinceCreation * SECONDS_PER_DAY);
        } else {
            return "";
        }
        return LanguageUtil.getShortDateFormatFromS(date);
    }

    /** Non libAnki */
    public boolean isInDynamicDeck() {
        // In Anki Desktop, a card with oDue <> 0 && oDid == 0 is not marked as dynamic.
        return this.getODid() != 0;
    }

    public boolean isReview() {
        return this.getType() == Consts.CARD_TYPE_REV && this.getQueue() == Consts.QUEUE_TYPE_REV;
    }

    public boolean isNew() {
        return this.getType() == Consts.CARD_TYPE_NEW;
    }

    /** A cache represents an intermediary step between a card id and a card object. Creating a Card has some fixed cost
     * in term of database access. Using an id has an unknown cost: none if the card is never accessed, heavy if the
     * card is accessed a lot of time. CardCache ensure that the cost is paid at most once, by waiting for first access
     * to load the data, and then saving them. Since CPU and RAM is usually less of a bottleneck than database access,
     * it may often be worth using this cache.
     *
     * Beware that the card is loaded only once. Change in the database are not reflected, so use it only if you can
     * safely assume that the card has not changed. That is
     * long id;
     * Card card = col.getCard(id);
     * ....
     * Card card2 = col.getCard(id);
     * is not equivalent to
     * long id;
     * Card.Cache cache = new Cache(col, id);
     * Card card = cache.getCard();
     * ....
     * Card card2 = cache.getCard();
     *
     * It is equivalent to:
     * long id;
     * Card.Cache cache = new Cache(col, id);
     * Card card = cache.getCard();
     * ....
     * cache.reload();
     * Card card2 = cache.getCard();
     */
    public static class Cache implements Cloneable {
        @NonNull
        private final Collection mCol;
        private final long mId;
        @Nullable
        private Card mCard;

        public Cache(@NonNull Collection col, long id) {
            mCol = col;
            mId = id;
        }

        /** Copy of cache. Useful to create a copy of a subclass without loosing card if it is loaded. */
        protected Cache(Cache cache) {
            mCol = cache.mCol;
            mId = cache.mId;
            mCard = cache.mCard;
        }

        /** Copy of cache. Useful to create a copy of a subclass without loosing card if it is loaded. */
        public Cache(Card card) {
            mCol = card.mCol;
            mId = card.getId();
            mCard = card;
        }

        /**
         * The card with id given at creation. Note that it has content of the time at which the card was loaded, which
         * may have changed in database. So it is not equivalent to getCol().getCard(getId()). If you need fresh data, reload
         * first.*/
        @NonNull
        public synchronized Card getCard() {
            if (mCard == null) {
                mCard = mCol.getCard(mId);
            }
            return mCard;
        }

        /** Next access to card will reload the card from the database. */
        public synchronized void reload() {
            mCard = null;
        }

        public long getId() {
            return mId;
        }

        @NonNull
        public Collection getCol() {
            return mCol;
        }

        @Override
        public int hashCode() {
            return Long.valueOf(mId).hashCode();
        }

        /** The cloned version represents the same card but data are not loaded. */
        @NonNull
        public Cache clone() {
            return new Cache(mCol, mId);
        }

        public boolean equals(Object cache) {
            if (!(cache instanceof Cache)) {
                return false;
            }
            return mId == ((Cache) cache).mId;
        }

        public void loadQA(boolean reload, boolean browser) {
            getCard()._getQA(reload, browser);
        }
    }

    public static @NonNull Card[] deepCopyCardArray(@NonNull Card[] originals, @NonNull CancelListener cancelListener) throws CancellationException {
        Collection col = CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance());
        Card[] copies = new Card[originals.length];
        for (int i = 0; i < originals.length; i++) {
            if (cancelListener.isCancelled()) {
                Timber.i("Cancelled during deep copy, probably memory pressure?");
                throw new CancellationException("Cancelled during deep copy");
            }

            // TODO: the performance-naive implementation loads from database instead of working in memory
            // the high performance version would implement .clone() on Card and test it well
            copies[i] = new Card(col, originals[i].getId());
        }
        return copies;
    }
}
