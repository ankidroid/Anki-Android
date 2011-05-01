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

/**
 * A deck stores all of the cards and scheduling information. It is saved in a file with a name ending in .anki See
 * http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Deck
 */
public class Deck {

    public static final String TAG_MARKED = "Marked";



    public static final String UNDO_TYPE_ANSWER_CARD = "Answer Card";
    public static final String UNDO_TYPE_SUSPEND_CARD = "Suspend Card";
    public static final String UNDO_TYPE_EDIT_CARD = "Edit Card";
    public static final String UNDO_TYPE_MARK_CARD = "Mark Card";
    public static final String UNDO_TYPE_BURY_CARD = "Bury Card";
    public static final String UNDO_TYPE_DELETE_CARD = "Delete Card";

    public String mCurrentUndoRedoType = "";

    // Any comments resulting from upgrading the deck should be stored here, both in success and failure
    public ArrayList<Integer> upgradeNotes;


    // BEGIN: SQL table columns
    private long mId;
    private double mCreated;
    private double mModified;
    private double mSchemaMod = 0;
    private int mVersion;
    private long mCurrentModelId;
    private String mSyncName = "";
    private double mLastSync = 0;

    private boolean mNeedUnpack = false;


//
//    // Collapsing future cards
//    private double mCollapseTime;
//
//    // Priorities and postponing - all obsolete
//    private String mHighPriority;
//    private String mMedPriority;
//    private String mLowPriority;
//    private String mSuspended;
//
//    // 0 is random, 1 is by input date
    private int mUtcOffset = -2;
//    

//
//    // Limit the number of failed cards in play
//    private int mFailedCardMax;
//
//    // Number of new cards to show per day
    private int mNewCardsPerDay = 20;
    private int mCollapseTime = 600;
    // timeboxing
    private long mSessionRepLimit = 0;
    private long mSessionTimeLimit = 600;
    // leeches
    private boolean mSuspendLeeches = true;
    private int mLeechFails = 16;
    // selective study
    private String mNewActive = "";
    private String mNewInactive = "";
    private String mRevActive = "";
    private String mRevInactive = "";
//    private int mFactCount;
//    private int mFailedNowCount; // obsolete in libanki 1.1
//    private int mFailedSoonCount;
//    private int mRevCount;
//    private int mNewAvail;
    // END: SQL table columns

    // BEGIN JOINed variables
    // Model currentModel; // Deck.currentModelId = Model.id
    // ArrayList<Model> models; // Deck.id = Model.deckId
    // END JOINed variables

    private int mNewCount;
    private double mLastLoaded;
    private boolean mNewEarly;
    private boolean mReviewEarly;
    private String mMediaPrefix;

    private double mFailedCutoff;

    private String mScheduler;

    private Scheduler mSched;



    // Not in Anki Desktop
    private String mDeckPath;
    private String mDeckName;

    private long mCurrentCardId;
    
    private int markedTagId = 0;

    private HashMap<String, String> mDeckVars = new HashMap<String, String>();

    /**
     * Undo/Redo variables.
     */
    private Stack<UndoRow> mUndoStack;
    private Stack<UndoRow> mRedoStack;
    private boolean mUndoEnabled = false;
    private Stack<UndoRow> mUndoRedoStackToRecord = null;


    public static synchronized Deck openDeck(String path) throws SQLException {
        return openDeck(path, true);
    }


    public static synchronized Deck openDeck(String path, boolean rebuild) throws SQLException {
        Deck deck = null;
        Cursor cursor = null;
        Log.i(AnkiDroidApp.TAG, "openDeck - Opening database " + path);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(path);

        try {
            // Read in deck table columns
            cursor = ankiDB.getDatabase().rawQuery("SELECT * FROM decks LIMIT 1", null);

            if (!cursor.moveToFirst()) {
                return null;
            }

            deck = new Deck();

            deck.mId = cursor.getLong(0);
            deck.mCreated = cursor.getDouble(1);
            deck.mModified = cursor.getDouble(2);
            deck.mDescription = cursor.getString(3);
            deck.mVersion = cursor.getInt(4);
            deck.mCurrentModelId = cursor.getLong(5);
            deck.mSyncName = cursor.getString(6);
            deck.mLastSync = cursor.getDouble(7);
            deck.mHardIntervalMin = cursor.getDouble(8);
            deck.mHardIntervalMax = cursor.getDouble(9);
            deck.mMidIntervalMin = cursor.getDouble(10);
            deck.mMidIntervalMax = cursor.getDouble(11);
            deck.mEasyIntervalMin = cursor.getDouble(12);
            deck.mEasyIntervalMax = cursor.getDouble(13);
            deck.mDelay0 = cursor.getLong(14);
            deck.mDelay1 = cursor.getLong(15);
            deck.mDelay2 = cursor.getDouble(16);
            deck.mCollapseTime = cursor.getDouble(17);
            deck.mHighPriority = cursor.getString(18);
            deck.mMedPriority = cursor.getString(19);
            deck.mLowPriority = cursor.getString(20);
            deck.mSuspended = cursor.getString(21);
            deck.mNewCardOrder = cursor.getInt(22);
            deck.mNewCardSpacing = cursor.getInt(23);
            deck.mFailedCardMax = cursor.getInt(24);
            deck.mNewCardsPerDay = cursor.getInt(25);
            deck.mSessionRepLimit = cursor.getInt(26);
            deck.mSessionTimeLimit = cursor.getInt(27);
            deck.mUtcOffset = cursor.getDouble(28);
            deck.mCardCount = cursor.getInt(29);
            deck.mFactCount = cursor.getInt(30);
            deck.mFailedNowCount = cursor.getInt(31);
            deck.mFailedSoonCount = cursor.getInt(32);
            deck.mRevCount = cursor.getInt(33);
//            deck.mNewCount = cursor.getInt(34);
            deck.mRevCardOrder = cursor.getInt(35);

            Log.i(AnkiDroidApp.TAG, "openDeck - Read " + cursor.getColumnCount() + " columns from decks table.");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "openDeck - modified: %f currentTime: %f", deck.mModified, Utils.now()));

        // Initialise queues
        deck.mFailedQueue = new LinkedList<QueueItem>();
        deck.mRevQueue = new LinkedList<QueueItem>();
        deck.mNewQueue = new LinkedList<QueueItem>();
        deck.mFailedCramQueue = new LinkedList<QueueItem>();
        deck.mSpacedFacts = new HashMap<Long, Double>();
        deck.mSpacedCards = new LinkedList<SpacedCardsItem>();

        deck.mDeckPath = path;
        deck.initDeckvarsCache();
        deck.mDeckName = (new File(path)).getName().replace(".anki", "");

        if (deck.mVersion < Upgrade.DECK_VERSION) {
            deck.createMetadata();
        }

//        deck.mNeedUnpack = false;
//        if (Math.abs(deck.getUtcOffset() - 1.0) < 1e-9 || Math.abs(deck.getUtcOffset() - 2.0) < 1e-9) {
        if (Math.abs(deck.getUtcOffset() - 2.0) < 1e-9) {
            // do the rest later
//            deck.mNeedUnpack = (Math.abs(deck.getUtcOffset() - 1.0) < 1e-9);
            // make sure we do this before initVars
            deck.setUtcOffset();
            deck.mCreated = Utils.now();
        }

        deck.initVars();
        // deck.initTagTables();
        deck.updateDynamicIndices();
        // Upgrade to latest version
        Upgrade.upgradeDeck(deck);

        if (!rebuild) {
            // Minimal startup for deckpicker: only counts are needed
            deck.rebuildCounts();
            return deck;
        }

        ArrayList<Long> ids = new ArrayList<Long>();

        double oldMod = deck.getModified();
        // Unsuspend buried/rev early
        deck.getDB().getDatabase().execSQL("UPDATE cards SET queue = type WHERE queue BETWEEN -3 AND -2");
        deck.commitToDB();
        // Rebuild queue
        deck.reset();
        // Make sure we haven't accidentally bumped the modification time
        double dbMod = 0.0;
        Cursor cur = null;
        try {
            cur = deck.getDB().getDatabase().rawQuery("SELECT modified FROM decks", null);
            if (cur.moveToNext()) {
                dbMod = cur.getDouble(0);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        assert Math.abs(dbMod - oldMod) < 1.0e-9;
        assert deck.mModified == oldMod;

//        // 4.3.2011: deactivated since it's not used anywhere
//        // Create a temporary view for random new cards. Randomizing the cards by themselves
//        // as is done in desktop Anki in Deck.randomizeNewCards() takes too long.
//        try {
//            deck.getDB().getDatabase().execSQL(
//                    "CREATE TEMPORARY VIEW acqCardsRandom AS SELECT * FROM cards " + "WHERE type = " + Card.TYPE_NEW
//                            + " AND isDue = 1 ORDER BY RANDOM()");
//        } catch (SQLException e) {
//            /* Temporary view may still be present if the DB has not been closed */
//            Log.i(AnkiDroidApp.TAG, "Failed to create temporary view: " + e.getMessage());
//        }

        // Initialize Undo
        deck.initUndo();
        return deck;
    }


    public void createMetadata() {
        // Just create table deckvars for now
        getDB().getDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS deckVars (\"key\" TEXT NOT NULL, value TEXT, " + "PRIMARY KEY (\"key\"))");
    }


    public synchronized void closeDeck() {
    	closeDeck(true);
    }

    public synchronized void closeDeck(boolean wait) {
        if (wait) {
        	DeckTask.waitToFinish(); // Wait for any thread working on the deck to finish.
        }
        if (finishSchedulerMethod != null) {
            finishScheduler();
            reset();
        }
        if (modifiedSinceSave()) {
            commitToDB();
        }
        AnkiDatabaseManager.closeDatabase(mDeckPath);
    }


    public static synchronized int getDeckVersion(String path) throws SQLException {
        int version = (int) AnkiDatabaseManager.getDatabase(path).queryScalar("SELECT version FROM decks LIMIT 1");
        return version;
    }


    public Fact newFact(Long modelId) {
    	Model m = Model.getModel(this, modelId, true);
    	Fact mFact = new Fact(this, m);
        return mFact;
    }


    public Fact newFact() {
        Model m = Model.getModel(this, getCurrentModelId(), true);
        Fact mFact = new Fact(this, m);
        return mFact;
    }

    public LinkedHashMap<Long, CardModel> activeCardModels(Fact fact) {
    	LinkedHashMap<Long, CardModel> activeCM = new LinkedHashMap<Long, CardModel>();
        for (Map.Entry<Long, CardModel> entry : cardModels(fact).entrySet()) {
            CardModel cardmodel = entry.getValue();
            if (cardmodel.isActive()) {
                // TODO: check for emptiness
            	activeCM.put(cardmodel.getId(), cardmodel);
            }
        }
        return activeCM;
    }

    public LinkedHashMap<Long, CardModel> cardModels(Fact fact) {
    	LinkedHashMap<Long, CardModel> cardModels = new LinkedHashMap<Long, CardModel>();
        CardModel.fromDb(this, fact.getModelId(), cardModels);
        return cardModels;
    }

    /**
     * deckVars methods
     */

    public void initDeckvarsCache() {
        mDeckVars.clear();
        Cursor cur = null;
        try {
            cur = getDB().getDatabase().rawQuery("SELECT key, value FROM deckVars", null);
            while (cur.moveToNext()) {
                mDeckVars.put(cur.getString(0), cur.getString(1));
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }

    public boolean hasKey(String key) {
        return mDeckVars.containsKey(key);
    }

    public int getInt(String key) {
        if (mDeckVars.containsKey(key)) {
            try {
                return Integer.parseInt(mDeckVars.get(key));
            } catch (NumberFormatException e) {
                Log.w(AnkiDroidApp.TAG, "NumberFormatException: Converting deckvar to int failed, key: \"" + key +
                        "\", value: \"" + mDeckVars.get(key) + "\"");
                return 0;
            }
        } else {
            return 0;
        }
    }


    public double getFloat(String key) {
        if (mDeckVars.containsKey(key)) {
            try {
                return Double.parseDouble(mDeckVars.get(key));
            } catch (NumberFormatException e) {
                Log.w(AnkiDroidApp.TAG, "NumberFormatException: Converting deckvar to double failed, key: \"" + key +
                        "\", value: \"" + mDeckVars.get(key) + "\"");
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }


    public boolean getBool(String key) {
        if (mDeckVars.containsKey(key)) {
            return mDeckVars.get(key).equals("1");
        } else {
            return false;
        }
    }


    public String getVar(String key) {
        return mDeckVars.get(key);
    }


    public void setVar(String key, String value) {
        setVar(key, value, true);
    }


    public void setVar(String key, String value, boolean mod) {
        try {
            if (mDeckVars.containsKey(key)) {
                getDB().getDatabase().execSQL("UPDATE deckVars SET value='" + value + "' WHERE key = '" + key + "'");
            } else {
                getDB().getDatabase().execSQL("INSERT INTO deckVars (key, value) VALUES ('" + key + "', '" +
                        value + "')");
            }
            mDeckVars.put(key, value);
        } catch (SQLException e) {
            Log.e(AnkiDroidApp.TAG, "setVar: " + e.toString());
            throw new RuntimeException(e);
        }
        if (mod) {
            setModified();
        }
    }


    public void setVarDefault(String key, String value) {
        if (!mDeckVars.containsKey(key)) {
            setVar(key, value, false);
        }
    }


    private void initVars() {
    	if (mUtcOffset == -2) {
    		// shared deck; reset timezone and creation date
    		mUtcOffset = (int) Utils.utcOffset();
    		mCreated = Utils.now();
    	}
        mMediaPrefix = null;
        mLastLoaded = Utils.now();
        // undoEnabled = false;
        // sessionStartReps = 0;
        // sessionStartTime = 0;
        // lastSessionStart = 0;
        // If most recent deck var not defined, make sure defaults are set
        if (!hasKey("latexPost")) {
            setVarDefault("mediaURL", "");
            setVarDefault("latexPre", "\\documentclass[12pt]{article}\n" + "\\special{papersize=3in,5in}\n"
                    + "\\usepackage[utf8]{inputenc}\n" + "\\usepackage{amssymb,amsmath}\n" + "\\pagestyle{empty}\n"
                    + "\\begin{document}\n");
            setVarDefault("latexPost", "\\end{document}");
            // FIXME: The next really belongs to the dropbox setup module, it's not supposed to be empty if the user
            // wants to use dropbox. ankiqt/ankiqt/ui/main.py : setupMedia
            // setVarDefault("mediaLocation", "");
        }
        mSched = new Scheduler(this);
    }


    // Media
    // *****

    /**
     * Return the media directory if exists, none if couldn't be created.
     *
     * @param create If true it will attempt to create the folder if it doesn't exist
     * @param rename This is used to simulate the python with create=None that is only used when renaming the mediaDir
     * @return The path of the media directory
     */
    public String mediaDir() {
        return mediaDir(false, false);
    }
    public String mediaDir(boolean create) {
        return mediaDir(create, false);
    }
    public String mediaDir(boolean create, boolean rename) {
        String dir = null;
        File mediaDir = null;
        if (mDeckPath != null && !mDeckPath.equals("")) {
            Log.i(AnkiDroidApp.TAG, "mediaDir - mediaPrefix = " + mMediaPrefix);
            if (mMediaPrefix != null) {
                dir = mMediaPrefix + "/" + mDeckName + ".media";
            } else {
                dir = mDeckPath.replaceAll("\\.anki$", ".media");
            }
            if (rename) {
                // Don't create, but return dir
                return dir;
            }
            mediaDir = new File(dir);
            if (!mediaDir.exists() && create) {
                try {
                    if (!mediaDir.mkdir()) {
                        Log.e(AnkiDroidApp.TAG, "Couldn't create media directory " + dir);
                        return null;
                    }
                } catch (SecurityException e) {
                    Log.e(AnkiDroidApp.TAG, "Security restriction: Couldn't create media directory " + dir);
                    return null;
                }
            }
        }

        if (dir == null) {
            return null;
        } else {
            if (!mediaDir.exists() || !mediaDir.isDirectory()) {
                return null;
            }
        }
        Log.i(AnkiDroidApp.TAG, "mediaDir - mediaDir = " + dir);
        return dir;
    }

    public String getMediaPrefix() {
        return mMediaPrefix;
    }
    public void setMediaPrefix(String mediaPrefix) {
        mMediaPrefix = mediaPrefix;
    }



    private boolean hasLaTeX() {
        Cursor cursor = null;
        try {
            cursor = getDB().getDatabase().rawQuery(
                "SELECT Id FROM fields WHERE " +
                "(value like '%[latex]%[/latex]%') OR " +
                "(value like '%[$]%[/$]%') OR " +
                "(value like '%[$$]%[/$$]%') LIMIT 1 ", null);
            if (cursor.moveToFirst()) {
                return true;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }



    /*
     * Add stripped HTML cache for sorting/searching. Currently needed as part of the upgradeDeck, the cache is not
     * really used, yet.
     */
    private void updateFieldCache(long[] fids) {
        HashMap<Long, String> r = new HashMap<Long, String>();
        Cursor cur = null;

        Log.i(AnkiDroidApp.TAG, "updatefieldCache fids: " + Utils.ids2str(fids));
        try {
            cur = getDB().getDatabase().rawQuery(
                    "SELECT factId, group_concat(value, ' ') FROM fields " + "WHERE factId IN " + Utils.ids2str(fids)
                            + " GROUP BY factId", null);
            while (cur.moveToNext()) {
                String values = cur.getString(1);
                // if (values.charAt(0) == ' ') {
                // Fix for a slight difference between how Android SQLite and python sqlite work.
                // Inconsequential difference in this context, but messes up any effort for automated testing.
                values = values.replaceFirst("^ *", "");
                // }
                r.put(cur.getLong(0), Utils.stripHTMLMedia(values));
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

        if (r.size() > 0) {
            getDB().getDatabase().beginTransaction();
            SQLiteStatement st = getDB().getDatabase().compileStatement("UPDATE facts SET cache=? WHERE id=?");
            for (Entry<Long, String> entry : r.entrySet()) {
                st.bindString(1, entry.getValue());
                st.bindLong(2, entry.getKey().longValue());
                st.execute();
            }
            getDB().getDatabase().setTransactionSuccessful();
            getDB().getDatabase().endTransaction();
        }
    }


    private boolean modifiedSinceSave() {
        return mModified > mLastLoaded;
    }


    public long optimizeDeck() {
    	File file = new File(mDeckPath);
		long size = file.length();
    	commitToDB();
    	Log.i(AnkiDroidApp.TAG, "executing VACUUM statement");
        getDB().getDatabase().execSQL("VACUUM");
    	Log.i(AnkiDroidApp.TAG, "executing ANALYZE statement");
        getDB().getDatabase().execSQL("ANALYZE");
        file = new File(mDeckPath);
        size -= file.length();
        return size;
    }





    /*
     * Next day's due cards ******************************
     */
    public int getNextDueCards(int day) {
    	double dayStart = mDueCutoff + (86400 * (day - 1));
    	String sql = String.format(Utils.ENGLISH_LOCALE,
                    "SELECT count(*) FROM cards c WHERE queue = 1 AND due BETWEEN %f AND %f AND PRIORITY > -1", dayStart, dayStart + 86400);
        return (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
    }


    public int getNextDueMatureCards(int day) {
    	double dayStart = mDueCutoff + (86400 * (day - 1));
        String sql = String.format(Utils.ENGLISH_LOCALE,
                    "SELECT count(*) FROM cards c WHERE queue = 1 AND due BETWEEN %f AND %f AND interval >= %d", dayStart, dayStart + 86400, Card.MATURE_THRESHOLD);
        return (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
    }


    /*
     * Get failed cards count ******************************
     */
    public int getFailedDelayedCount() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE queue = 0 AND due >= " + mFailedCutoff + " AND PRIORITY > -1");
        return (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
    }


    public int getNextNewCards() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE queue = 2 AND due < %f", mDueCutoff + 86400);
        return Math.min((int) getDB().queryScalar(cardLimit("newActive", "newInactive", sql)), mNewCardsPerDay);
    }


    /*
     * Next cards by interval ******************************
     */
    public int getCardsByInterval(int interval) {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE queue = 1 AND interval BETWEEN %d AND %d", interval, interval + 1);
        return (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
    }


    /*
     * Review counts ******************************
     */
    public int[] getDaysReviewed(int day) {
        Date value = Utils.genToday(getUtcOffset() - (86400 * day));
    	Cursor cur = null;
    	int[] count = {0, 0, 0};
    	try {
            cur = getDB().getDatabase().rawQuery(String.format(Utils.ENGLISH_LOCALE,
            		"SELECT reps, (matureease1 + matureease2 + matureease3 + matureease4 +  youngease1 + youngease2 + youngease3 + youngease4), " +
            		"(matureease1 + matureease2 + matureease3 + matureease4) FROM stats WHERE day = \'%tF\' AND queue = %d", value, Stats.STATS_DAY), null);
            while (cur.moveToNext()) {
            	count[0] = cur.getInt(0);
            	count[1] = cur.getInt(1);
            	count[2] = cur.getInt(2);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

    	return count;
    }


    /*
     * Review time ******************************
     */
    public int getReviewTime(int day) {
        Date value = Utils.genToday(getUtcOffset() - (86400 * day));
    	Cursor cur = null;
    	int count = 0;
    	try {
            cur = getDB().getDatabase().rawQuery(String.format(Utils.ENGLISH_LOCALE,
            		"SELECT reviewTime FROM stats WHERE day = \'%tF\' AND reps > 0 AND queue = %d", value, Stats.STATS_DAY), null);
            while (cur.moveToNext()) {
            	count = cur.getInt(0);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }

    	return count;
    }

    /*
     * Stats ******************************
     */

    public double getProgress(boolean global) {
    	if (global) {
    		return mGlobalStats.getMatureYesShare();
    	} else {
    		return mDailyStats.getYesShare();
    	}
    }


    public int getETA() {
    	if (mDailyStats.getReps() >= 10 && mDailyStats.getAverageTime() > 0) {
    		return getETA(mFailedSoonCount, mRevCount, mNewCount, false);
		} else if (mGlobalStats.getAverageTime() > 0) {
			return getETA(mFailedSoonCount, mRevCount, mNewCount, true);
		} else {
			return -1;
		}
    }


    public int getETA(int failedCards, int revCards, int newCards, boolean global) {
    	double left;
    	double count;
    	double averageTime;
    	if (global) {
			averageTime = mGlobalStats.getAverageTime();		
		} else {
    		averageTime = mDailyStats.getAverageTime();
		}
 
    	double globalYoungNoShare = mGlobalStats.getYoungNoShare();

    	// rev + new cards first, account for failures
    	count = newCards + revCards;
    	count *= 1 + globalYoungNoShare;
    	left = count * averageTime;

    	//failed - higher time per card for higher amount of cards
    	double failedBaseMulti = 1.5;
    	double failedMod = 0.07;
    	double failedBaseCount = 20;
    	double factor = (failedBaseMulti + (failedMod * (failedCards - failedBaseCount)));
    	left += failedCards * averageTime * factor;
        	
    	return (int) (left / 60);
    }





    public String name() {
        return mScheduler;
    }



    public void reset() {
        mSched.reset();
        rebuildCSS();
    }


    public Card getCard() {
    	return mSched.getCard();
    	// ...
    }


    private void setModified() {
        mModified = Utils.now();
    }


    public void setModified(double mod) {
        mModified = mod;
    }


    public void setSchemaModified() {
        mSchemaMod = Utils.now();
    }


    public void flushMod() {
        setModified();
        commitToDB();
    }


    public void commitToDB() {
        Log.i(AnkiDroidApp.TAG, "commitToDB - Saving deck to DB...");
        ContentValues values = new ContentValues();
        values.put("created", mCreated);
        values.put("modified", mModified);
        values.put("description", mDescription);
        values.put("version", mVersion);
        values.put("currentModelId", mCurrentModelId);
        values.put("syncName", mSyncName);
        values.put("lastSync", mLastSync);
        values.put("hardIntervalMin", mHardIntervalMin);
        values.put("hardIntervalMax", mHardIntervalMax);
        values.put("midIntervalMin", mMidIntervalMin);
        values.put("midIntervalMax", mMidIntervalMax);
        values.put("easyIntervalMin", mEasyIntervalMin);
        values.put("easyIntervalMax", mEasyIntervalMax);
        values.put("delay0", mDelay0);
        values.put("delay1", mDelay1);
        values.put("delay2", mDelay2);
        values.put("collapseTime", mCollapseTime);
        values.put("highPriority", mHighPriority);
        values.put("medPriority", mMedPriority);
        values.put("lowPriority", mLowPriority);
        values.put("suspended", mSuspended);
        values.put("newCardOrder", mNewCardOrder);
        values.put("newCardSpacing", mNewCardSpacing);
        values.put("failedCardMax", mFailedCardMax);
        values.put("newCardsPerDay", mNewCardsPerDay);
        values.put("sessionRepLimit", mSessionRepLimit);
        values.put("sessionTimeLimit", mSessionTimeLimit);
        values.put("utcOffset", mUtcOffset);
        values.put("cardCount", mCardCount);
        values.put("factCount", mFactCount);
        values.put("failedNowCount", mFailedNowCount);
        values.put("failedSoonCount", mFailedSoonCount);
        values.put("revCount", mRevCount);
//        values.put("newCount", mNewCount);
        values.put("revCardOrder", mRevCardOrder);

        getDB().update(this, "decks", values, "id = " + mId, null);
    }


    public static double getLastModified(String deckPath) {
        double value;
        Cursor cursor = null;
        // Log.i(AnkiDroidApp.TAG, "Deck - getLastModified from deck = " + deckPath);

        boolean dbAlreadyOpened = AnkiDatabaseManager.isDatabaseOpen(deckPath);

        try {
            cursor = AnkiDatabaseManager.getDatabase(deckPath).getDatabase().rawQuery(
                    "SELECT modified" + " FROM decks" + " LIMIT 1", null);

            if (!cursor.moveToFirst()) {
                value = -1;
            } else {
                value = cursor.getDouble(0);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        if (!dbAlreadyOpened) {
            AnkiDatabaseManager.closeDatabase(deckPath);
        }

        return value;
    }


    /*
     * Getters and Setters for deck properties NOTE: The setters flushMod()
     * *********************************************************
     */

    public AnkiDb getDB() {
        // TODO: Make this a reference to a member variable
        return AnkiDatabaseManager.getDatabase(mDeckPath);
    }


    public String getDeckPath() {
        return mDeckPath;
    }


    public void setDeckPath(String path) {
        mDeckPath = path;
    }


    // public String getSyncName() {
    //     return mSyncName;
    // }


    // public void setSyncName(String name) {
    //     mSyncName = name;
    //     flushMod();
    // }


    public int getRevCardOrder() {
        return mRevCardOrder;
    }


    public void setRevCardOrder(int num) {
        if (num >= 0) {
            mRevCardOrder = num;
            flushMod();
        }
    }


    public int getNewCardSpacing() {
        return mNewCardSpacing;
    }


    public void setNewCardSpacing(int num) {
        if (num >= 0) {
            mNewCardSpacing = num;
            flushMod();
        }
    }


    public int getNewCardOrder() {
        return mNewCardOrder;
    }


    public void setNewCardOrder(int num) {
        if (num >= 0) {
            mNewCardOrder = num;
            flushMod();
        }
    }


    public boolean getPerDay() {
        return getBool("perDay");
    }


    public void setPerDay(boolean perDay) {
        if (perDay) {
            setVar("perDay", "1");
        } else {
            setVar("perDay", "0");
        }
    }


    public boolean getSuspendLeeches() {
        return getBool("suspendLeeches");
    }


    public void setSuspendLeeches(boolean suspendLeeches) {
        if (suspendLeeches) {
            setVar("suspendLeeches", "1");
        } else {
            setVar("suspendLeeches", "0");
        }
    }


    public int getNewCardsPerDay() {
        return mNewCardsPerDay;
    }


    public void setNewCardsPerDay(int num) {
        if (num >= 0) {
            mNewCardsPerDay = num;
            flushMod();
            reset();
        }
    }


    public long getSessionRepLimit() {
        return mSessionRepLimit;
    }


    public void setSessionRepLimit(long num) {
        if (num >= 0) {
            mSessionRepLimit = num;
            flushMod();
        }
    }


    public long getSessionTimeLimit() {
        return mSessionTimeLimit;
    }


    public void setSessionTimeLimit(long num) {
        if (num >= 0) {
            mSessionTimeLimit = num;
            flushMod();
        }
    }


    /**
     * @return the failedSoonCount
     */
    public int getFailedSoonCount() {
        return mFailedSoonCount;
    }


    /**
     * @return the revCount
     */
    public int getRevCount() {
        return mRevCount;
    }


    /**
     * @return the newCount
     */
    public int getNewAvail() {
        return mNewAvail;
    }


    /**
     * @return the number of due cards in the deck
     */
    public int getDueCount() {
        return mFailedSoonCount + mRevCount;
    }


    /**
     * @param cardCount the cardCount to set
     */
    public void setCardCount(int cardCount) {
        mCardCount = cardCount;
        // XXX: Need to flushmod() ?
    }


    /**
     * Get the cached total number of cards of the deck.
     *
     * @return The number of cards contained in the deck
     */
    public int getCardCount() {
        return mCardCount;
    }


    /**
     * @return True, if there are any tag limits
     */
    public boolean isLimitedByTag() {
        if (!getVar("newActive").equals("")) {
            return true;
        } else if (!getVar("newInactive").equals("")) {
            return true;
        } else if (!getVar("revActive").equals("")) {
            return true;
        } else if (!getVar("revInactive").equals("")) {
            return true;
        } else {
            return false;
        }
    }


    /**
	 * Get the number of mature cards of the deck.
	 *
	 * @return The number of cards contained in the deck
	 */
	public int getMatureCardCount(boolean restrictToActive) {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) from cards c WHERE (queue = 1 OR queue = 0) AND interval >= %d", Card.MATURE_THRESHOLD);
        if (restrictToActive) {
            return (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
        } else {
            return (int) getDB().queryScalar(sql);
        }
    }


    /**
     * @return the newCount
     */
    public int getNewCount(boolean restrictToActive) {
        if (restrictToActive) {
            return getNewCount();
        } else {
            return (int) getDB().queryScalar("SELECT count(*) from cards WHERE queue = 2");
        }
    }


    /**
     * @return the rev card count
     */
    public int getTotalRevFailedCount(boolean restrictToActive) {
        if (restrictToActive) {
            return (int) getDB().queryScalar(cardLimit("revActive", "revInactive", "SELECT count(*) from cards c WHERE (queue = 1 OR queue = 0)"));
        } else {
            return getCardCount() - getNewCount(false);
        }
    }


    /**
     * @return the currentModelId
     */
    public long getCurrentModelId() {
        return mCurrentModelId;
    }


    /**
     * @return the deckName
     */
    public String getDeckName() {
        return mDeckName;
    }


    /**
     * @return the deck UTC offset in number seconds
     */
    public double getUtcOffset() {
        return mUtcOffset;
    }
    public void setUtcOffset() {
        mUtcOffset = Utils.utcOffset();
    }


    /**
     * @return the newCount
     */
    public int getNewCount() {
        return mNewCount;
    }


    /**
     * @return the modified
     */
    public double getModified() {
        return mModified;
    }


    /**
     * @param lastSync the lastSync to set
     */
    public void setLastSync(double lastSync) {
        mLastSync = lastSync;
    }


    /**
     * @return the lastSync
     */
    public double getLastSync() {
        return mLastSync;
    }


    /**
     * @param factCount the factCount to set
     */
    public void setFactCount(int factCount) {
        mFactCount = factCount;
        // XXX: Need to flushmod() ?
    }


    /**
     * @return the factCount
     */
    public int getFactCount() {
        return mFactCount;
    }


    /**
     * @param lastLoaded the lastLoaded to set
     */
    public double getLastLoaded() {
        return mLastLoaded;
    }


    /**
     * @param lastLoaded the lastLoaded to set
     */
    public void setLastLoaded(double lastLoaded) {
        mLastLoaded = lastLoaded;
    }


    public void setVersion(int version) {
        mVersion = version;
    }

    public int getVersion() {
        return mVersion;
    }


    public boolean isUnpackNeeded() {
        return mNeedUnpack;
    }

    public double getDueCutoff() {
        return mDueCutoff;
    }


    public String getScheduler() {
        return mScheduler;
    }


    public ArrayList<Long> getCardsFromFactId(Long factId) {
        Cursor cursor = null;
        ArrayList<Long> cardIds = new ArrayList<Long>();
        try {
            cursor = getDB().getDatabase().rawQuery(
                    "SELECT id FROM cards WHERE factid = " + factId, null);
            while (cursor.moveToNext()) {
                cardIds.add(cursor.getLong(0));
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return cardIds;
    }



    /*
     * Tags: Querying*****************************
     */

    /**
     * Get a map of card IDs to their associated tags (fact, model and template)
     *
     * @param where SQL restriction on the query. If empty, then returns tags for all the cards
     * @return The map of card IDs to an array of strings with 3 elements representing the triad {card tags, model tags,
     *         template tags}
     */
    private HashMap<Long, List<String>> splitTagsList() {
        return splitTagsList("");
    }


    private HashMap<Long, List<String>> splitTagsList(String where) {
        Cursor cur = null;
        HashMap<Long, List<String>> results = new HashMap<Long, List<String>>();
        try {
            cur = getDB().getDatabase().rawQuery(
                    "SELECT cards.id, facts.tags, models.name, cardModels.name "
                            + "FROM cards, facts, models, cardModels "
                            + "WHERE cards.factId == facts.id AND facts.modelId == models.id "
                            + "AND cards.cardModelId = cardModels.id " + where, null);
            while (cur.moveToNext()) {
                ArrayList<String> tags = new ArrayList<String>();
                tags.add(cur.getString(1));
                tags.add(cur.getString(2));
                tags.add(cur.getString(3));
                results.put(cur.getLong(0), tags);
            }
        } catch (SQLException e) {
            Log.e(AnkiDroidApp.TAG, "splitTagsList: Error while retrieving tags from DB: " + e.toString());
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return results;
    }


    /**
     * Returns all model tags, all template tags and a filtered set of fact tags
     *
     * @param where Optional, SQL filter for fact tags. If skipped, returns all fact tags
     * @return All the distinct individual tags, sorted, as an array of string
     */
    public String[] allTags_() {
        return allTags_("");
    }


    private String[] allTags_(String where) {
        ArrayList<String> t = new ArrayList<String>();
        t.addAll(getDB().queryColumn(String.class, "SELECT tags FROM facts " + where, 0));
        t.addAll(getDB().queryColumn(String.class, "SELECT name FROM models", 0));
        t.addAll(getDB().queryColumn(String.class, "SELECT name FROM cardModels", 0));
        String joined = Utils.joinTags(t);
        String[] parsed = Utils.parseTags(joined);
        List<String> joinedList = Arrays.asList(parsed);
        TreeSet<String> joinedSet = new TreeSet<String>(joinedList);
        return joinedSet.toArray(new String[joinedSet.size()]);
    }


    public String[] allUserTags() {
        return allUserTags("");
    }


    public String[] allUserTags(String where) {
        ArrayList<String> t = new ArrayList<String>();
        t.addAll(getDB().queryColumn(String.class, "SELECT tags FROM facts " + where, 0));
        String joined = Utils.joinTags(t);
        String[] parsed = Utils.parseTags(joined);
        List<String> joinedList = Arrays.asList(parsed);
        TreeSet<String> joinedSet = new TreeSet<String>(joinedList);
        return joinedSet.toArray(new String[joinedSet.size()]);
    }


    /*
     * Tags: Caching*****************************
     */

    public void updateFactTags(long[] factIds) {
        updateCardTags(Utils.toPrimitive(getDB().queryColumn(Long.class,
                "SELECT id FROM cards WHERE factId IN " + Utils.ids2str(factIds), 0)));
    }


    public void updateCardTags() {
        updateCardTags(null);
    }


    public void updateCardTags(long[] cardIds) {
        HashMap<String, Long> tids = new HashMap<String, Long>();
        HashMap<Long, List<String>> rows = new HashMap<Long, List<String>>();
        if (cardIds == null) {
            getDB().getDatabase().execSQL("DELETE FROM cardTags");
            getDB().getDatabase().execSQL("DELETE FROM tags");
            tids = tagIds(allTags_());
            rows = splitTagsList();
        } else {
            Log.i(AnkiDroidApp.TAG, "updateCardTags cardIds: " + Arrays.toString(cardIds));
            getDB().delete(this, "cardTags", "cardId IN " + Utils.ids2str(cardIds), null);
            String fids = Utils.ids2str(Utils.toPrimitive(getDB().queryColumn(Long.class,
                    "SELECT factId FROM cards WHERE id IN " + Utils.ids2str(cardIds), 0)));
            Log.i(AnkiDroidApp.TAG, "updateCardTags fids: " + fids);
            tids = tagIds(allTags_("WHERE id IN " + fids));
            Log.i(AnkiDroidApp.TAG, "updateCardTags tids keys: " + Arrays.toString(tids.keySet().toArray(new String[tids.size()])));
            Log.i(AnkiDroidApp.TAG, "updateCardTags tids values: " + Arrays.toString(tids.values().toArray(new Long[tids.size()])));
            rows = splitTagsList("AND facts.id IN " + fids);
            Log.i(AnkiDroidApp.TAG, "updateCardTags rows keys: " + Arrays.toString(rows.keySet().toArray(new Long[rows.size()])));
            for (List<String> l : rows.values()) {
                Log.i(AnkiDroidApp.TAG, "updateCardTags rows values: ");
                for (String v : l) {
                    Log.i(AnkiDroidApp.TAG, "updateCardTags row item: " + v);
                }
            }
        }

        ArrayList<HashMap<String, Long>> d = new ArrayList<HashMap<String, Long>>();

        for (Entry<Long, List<String>> entry : rows.entrySet()) {
        	Long id = entry.getKey();
            for (int src = 0; src < 3; src++) { // src represents the tag type, fact: 0, model: 1, template: 2
                for (String tag : Utils.parseTags(entry.getValue().get(src))) {
                    HashMap<String, Long> ditem = new HashMap<String, Long>();
                    ditem.put("cardId", id);
                    ditem.put("tagId", tids.get(tag.toLowerCase()));
                    ditem.put("src", new Long(src));
                    Log.i(AnkiDroidApp.TAG, "populating ditem " + src + " " + tag);
                    d.add(ditem);
                }
            }
        }

        for (HashMap<String, Long> ditem : d) {
        	ContentValues values = new ContentValues();
        	values.put("cardId", ditem.get("cardId"));
        	values.put("tagId", ditem.get("tagId"));
        	values.put("src",  ditem.get("src"));
            getDB().insert(this, "cardTags", null, values);
        }
	deleteUnusedTags();
    }


    public ArrayList<String[]> getAllCards(String order) {
    	ArrayList<String[]> allCards = new ArrayList<String[]>();

        Cursor cur = null;
        try {
        	cur = getDB().getDatabase().rawQuery("SELECT cards.id, cards.question, cards.answer, " +
        			"facts.tags, models.tags, cardModels.name, cards.priority FROM cards, facts, " +
        			"models, cardModels WHERE cards.factId == facts.id AND facts.modelId == models.id " +
        			"AND cards.cardModelId = cardModels.id ORDER BY " + order, null);
            while (cur.moveToNext()) {
            	String[] data = new String[5];
            	data[0] = Long.toString(cur.getLong(0));
                String string = Utils.stripHTML(cur.getString(1));
            	if (string.length() < 55) {
                    data[1] = string;
            	} else {
                    data[1] = string.substring(0, 55) + "...";                   
            	}
            	string = Utils.stripHTML(cur.getString(2));
                if (string.length() < 55) {
                    data[2] = string;
                } else {
                    data[2] = string.substring(0, 55) + "...";                   
                }
            	String tags = cur.getString(3);
           	    if (tags.contains(TAG_MARKED)) {
           	        data[3] = "1";
           	    } else {
           	        data[3] = "0";
           	    }
           	    data[4] = tags + " " + cur.getString(4) + " " + cur.getString(5);
            	if (cur.getString(6).equals("-3")) {
                    data[3] = data[3] + "1";
                } else {
                    data[3] = data[3] + "0";
                }
            	allCards.add(data);
            }
        } catch (SQLException e) {
            Log.e(AnkiDroidApp.TAG, "getAllCards: " + e.toString());
            return null;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    	return allCards;
    }


    public int getMarketTagId() {
    	if (markedTagId == 0) {
    		markedTagId = -1;
            Cursor cur = null;
            try {
                cur = getDB().getDatabase().rawQuery("SELECT id FROM tags WHERE tag = \"" + TAG_MARKED + "\"", null);
                while (cur.moveToNext()) {
                	markedTagId = cur.getInt(0);
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
    	}
    	return markedTagId;
    }

    

    public void resetMarkedTagId() {
    	markedTagId = 0;
    }
    
    /*
     * Tags: adding/removing in bulk*********************************************************
     */

    public void deleteUnusedTags() {
	getDB().delete(this, "tags", "id NOT IN (SELECT DISTINCT tagId FROM cardTags)", null);
    }

    public ArrayList<String> factTags(long[] factIds) {
        return getDB().queryColumn(String.class, "SELECT tags FROM facts WHERE id IN " + Utils.ids2str(factIds), 0);
    }


    public void addTag(long factId, String tag) {
        long[] ids = new long[1];
        ids[0] = factId;
        addTag(ids, tag);
    }


    public void addTag(long[] factIds, String tag) {
        ArrayList<String> factTagsList = factTags(factIds);

        // Create tag if necessary
        long tagId = tagId(tag, true);

        int nbFactTags = factTagsList.size();
        for (int i = 0; i < nbFactTags; i++) {
            String newTags = factTagsList.get(i);

            if (newTags.indexOf(tag) == -1) {
                if (newTags.length() == 0) {
                    newTags += tag;
                } else {
                    newTags += "," + tag;
                }
            }
            Log.i(AnkiDroidApp.TAG, "old tags = " + factTagsList.get(i));
            Log.i(AnkiDroidApp.TAG, "new tags = " + newTags);

            if (newTags.length() > factTagsList.get(i).length()) {
            	ContentValues values = new ContentValues();
            	values.put("tags", newTags);
            	values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
                getDB().update(this, "facts", values, "id = " + factIds[i], null);
            }
        }

        ArrayList<String> cardIdList = getDB().queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        for (String cardId : cardIdList) {
            try {
                // Check if the tag already exists
                getDB().queryScalar(
                        "SELECT id FROM cardTags WHERE cardId = " + cardId + " and tagId = " + tagId + " and src = "
                                + Card.TAGS_FACT);
            } catch (SQLException e) {
            	ContentValues values = new ContentValues();
                values.put("cardId", cardId);
                values.put("tagId", tagId);
                values.put("src", String.valueOf(Card.TAGS_FACT));
                getDB().insert(this, "cardTags", null, values);
            }
        }

        flushMod();
    }


    public void deleteTag(long factId, String tag) {
        long[] ids = new long[1];
        ids[0] = factId;
        deleteTag(ids, tag);
    }


    public void deleteTag(long[] factIds, String tag) {
        ArrayList<String> factTagsList = factTags(factIds);

        long tagId = tagId(tag, false);

        int nbFactTags = factTagsList.size();
        for (int i = 0; i < nbFactTags; i++) {
            String factTags = factTagsList.get(i);
            String newTags = factTags;

            int tagIdx = factTags.indexOf(tag);
            if ((tagIdx == 0) && (factTags.length() > tag.length())) {
                // tag is the first element of many, remove "tag,"
                newTags = factTags.substring(tag.length() + 1, factTags.length());
            } else if ((tagIdx > 0) && (tagIdx + tag.length() == factTags.length())) {
                // tag is the last of many elements, remove ",tag"
                newTags = factTags.substring(0, tagIdx - 1);
            } else if (tagIdx > 0) {
                // tag is enclosed between other elements, remove ",tag"
                newTags = factTags.substring(0, tagIdx - 1) + factTags.substring(tag.length(), factTags.length());
            } else if (tagIdx == 0) {
                // tag is the only element
                newTags = "";
            }
            Log.i(AnkiDroidApp.TAG, "old tags = " + factTags);
            Log.i(AnkiDroidApp.TAG, "new tags = " + newTags);

            if (newTags.length() < factTags.length()) {
            	ContentValues values = new ContentValues();
                values.put("tags", newTags);
                values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
                getDB().update(this, "facts", values, "id = " + factIds[i], null);
            }
        }

        ArrayList<String> cardIdList = getDB().queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        for (String cardId : cardIdList) {
        	getDB().delete(this, "cardTags", "cardId = " + cardId + " and tagId = " + tagId + " and src = " + Card.TAGS_FACT, null);
        }

        // delete unused tags from tags table
        try {
            getDB().queryScalar("select id from cardTags where tagId = " + tagId + " limit 1");
        } catch (SQLException e) {
        	getDB().delete(this, "tags", "id = " + tagId, null);
        }

        flushMod();
    }


    /*
     * Suspending*****************************
     */

    /**
     * Suspend cards in bulk. Caller must .reset()
     *
     * @param ids List of card IDs of the cards that are to be suspended.
     */
    public void suspendCards(long[] ids) {
    	ContentValues values = new ContentValues();
        values.put("queue", "-1");
        values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
        getDB().update(this, "cards", values, "id IN " + Utils.ids2str(ids), null, false);
        Log.i(AnkiDroidApp.TAG, "Cards suspended");
        flushMod();
    }


    /**
     * Unsuspend cards in bulk. Caller must .reset()
     *
     * @param ids List of card IDs of the cards that are to be unsuspended.
     */
    public void unsuspendCards(long[] ids) {
    	ContentValues values = new ContentValues();
        values.put("queue", "type");
        values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
        getDB().update(this, "cards", values, "queue = -1 AND id IN " + Utils.ids2str(ids), null, false);
        Log.i(AnkiDroidApp.TAG, "Cards unsuspended");
        flushMod();
    }


    public boolean getSuspendedState(long id) {
        return (getDB().queryScalar("SELECT count(*) from cards WHERE id = " + id + " AND priority = -3") == 1);
    }


    /**
     * Bury all cards for fact until next session. Caller must .reset()
     *
     * @param Fact
     */
    public void buryFact(long factId, long cardId) {
        // TODO: Unbury fact after return to StudyOptions
        String undoName = UNDO_TYPE_BURY_CARD;
        setUndoStart(undoName, cardId);
        // libanki code:
//        for (long cid : getCardsFromFactId(factId)) {
//            Card card = cardFromId(cid);
//            int type = card.getType();
//            if (type == 0 || type == 1 || type == 2) {
//                card.setPriority(card.getPriority() - 2);
//                card.setType(type + 3);
//                card.setDue(0);
//            }
//        }
        // This differs from libanki:
    	ContentValues values = new ContentValues();
        values.put("queue", "-2");
        values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
        getDB().update(this, "cards", values, "queue >= 0 AND queue <= 2 AND factId = " + factId, null, false);
        setUndoEnd(undoName);
        flushMod();
    }


    /*
     * Cards CRUD*********************************************************
     */

    /**
     * Bulk delete cards by ID. Caller must .reset()
     *
     * @param ids List of card IDs of the cards to be deleted.
     */
    public void deleteCards(List<String> ids) {
        Log.i(AnkiDroidApp.TAG, "deleteCards = " + ids.toString());
        String undoName = UNDO_TYPE_DELETE_CARD;
        if (ids.size() == 1) {
            setUndoStart(undoName, Long.parseLong(ids.get(0)));
        } else {
            setUndoStart(undoName);
        }
        // Bulk delete cards by ID
        if (ids != null && ids.size() > 0) {
            commitToDB();
            double now = Utils.now();
            Log.i(AnkiDroidApp.TAG, "Now = " + now);
            String idsString = Utils.ids2str(ids);

            // Grab fact ids
            // ArrayList<String> factIds = ankiDB.queryColumn(String.class,
            // "SELECT factId FROM cards WHERE id in " + idsString,
            // 0);

            // Delete cards
            getDB().delete(this, "cards", "id IN " + idsString, null);

            // Note deleted cards
            for (String id : ids) {
                ContentValues values = new ContentValues();
                values.put("cardId", id);
                values.put("deletedTime", String.format(Utils.ENGLISH_LOCALE, "%f", now));
                getDB().insert(this, "cardsDeleted", null, values);
            }

            // Gather affected tags (before we delete the corresponding cardTags)
            ArrayList<String> tags = getDB().queryColumn(String.class,
                    "SELECT tagId FROM cardTags WHERE cardId in " + idsString, 0);

            // Delete cardTags
            getDB().delete(this, "cardTags", "cardId IN " + idsString, null);

            // Find out if this tags are used by anything else
            ArrayList<String> unusedTags = new ArrayList<String>();
            for (String tagId : tags) {
                Cursor cursor = null;
                try {
                    cursor = getDB().getDatabase().rawQuery(
                            "SELECT * FROM cardTags WHERE tagId = " + tagId + " LIMIT 1", null);
                    if (!cursor.moveToFirst()) {
                        unusedTags.add(tagId);
                    }
                } finally {
                    if (cursor != null && !cursor.isClosed()) {
                        cursor.close();
                    }
                }
            }

	    deleteUnusedTags();

            // Remove any dangling fact
            deleteDanglingFacts();
            setUndoEnd(undoName);
            flushMod();
        }
    }


    /*
     * Facts CRUD*********************************************************
     */

    /**
     * Add a fact to the deck. Return list of new cards
     */
    public Fact addFact(Fact fact, HashMap<Long, CardModel> cardModels) {
        return addFact(fact, cardModels, true);
    }


    public Fact addFact(Fact fact, HashMap<Long, CardModel> cardModels, boolean reset) {
        // TODO: assert fact is Valid
        // TODO: assert fact is Unique
        double now = Utils.now();
        // add fact to fact table
        ContentValues values = new ContentValues();
        values.put("id", fact.getId());
        values.put("modelId", fact.getModelId());
        values.put("created", now);
        values.put("modified", now);
        values.put("tags", fact.getTags());
        values.put("cache", 0);
        getDB().insert(this, "facts", null, values);

        // get cardmodels for the new fact
        // TreeMap<Long, CardModel> availableCardModels = availableCardModels(fact);
        if (cardModels.isEmpty()) {
            Log.e(AnkiDroidApp.TAG, "Error while adding fact: No cardmodels for the new fact");
            return null;
        }
        // update counts
        mFactCount++;

        // add fields to fields table
        for (Field f : fact.getFields()) {
            // Re-use the content value
            values.clear();
            values.put("value", f.getValue());
            values.put("id", f.getId());
            values.put("factId", f.getFactId());
            values.put("fieldModelId", f.getFieldModelId());
            values.put("ordinal", f.getOrdinal());
            getDB().insert(this, "fields", null, values);
        }

        ArrayList<Long> newCardIds = new ArrayList<Long>();
        for (Map.Entry<Long, CardModel> entry : cardModels.entrySet()) {
            CardModel cardModel = entry.getValue();
            Card newCard = new Card(this, fact, cardModel, Utils.now());
            newCard.addToDb();
            newCardIds.add(newCard.getId());
            mCardCount++;
            mNewAvail++;
            Log.i(AnkiDroidApp.TAG, entry.getKey().toString());
        }
        commitToDB();
        // TODO: code related to random in newCardOrder

        // Update card q/a
        fact.setModified(true, this);
        updateFactTags(new long[] { fact.getId() });

        flushMod();
        if (reset) {
            reset();
        }

        return fact;
    }


    /**
     * Bulk delete facts by ID. Don't touch cards, assume any cards have already been removed. Caller must .reset().
     *
     * @param ids List of fact IDs of the facts to be removed.
     */
    public void deleteFacts(List<String> ids) {
        Log.i(AnkiDroidApp.TAG, "deleteFacts = " + ids.toString());
        int len = ids.size();
        if (len > 0) {
            commitToDB();
            double now = Utils.now();
            String idsString = Utils.ids2str(ids);
            Log.i(AnkiDroidApp.TAG, "DELETE FROM facts WHERE id in " + idsString);
            getDB().delete(this, "facts", "id in " + idsString, null);
            Log.i(AnkiDroidApp.TAG, "DELETE FROM fields WHERE factId in " + idsString);
            getDB().delete(this, "fields", "factId in " + idsString, null);
            for (String id : ids) {
                ContentValues values = new ContentValues();
                values.put("factId", id);
                values.put("deletedTime", String.format(Utils.ENGLISH_LOCALE, "%f", now));
            	Log.i(AnkiDroidApp.TAG, "inserting into factsDeleted");
                getDB().insert(this, "factsDeleted", null, values);
            }
            setModified();
        }
    }


    /**
     * Delete any fact without cards.
     *
     * @return ArrayList<String> list with the id of the deleted facts
     */
    private ArrayList<String> deleteDanglingFacts() {
        Log.i(AnkiDroidApp.TAG, "deleteDanglingFacts");
        ArrayList<String> danglingFacts = getDB().queryColumn(String.class,
                "SELECT facts.id FROM facts WHERE facts.id NOT IN (SELECT DISTINCT factId from cards)", 0);

        if (danglingFacts.size() > 0) {
            deleteFacts(danglingFacts);
        }

        return danglingFacts;
    }


    /*
     * Models CRUD*********************************************************
     */

    /**
     * Delete MODEL, and all its cards/facts. Caller must .reset() TODO: Handling of the list of models and currentModel
     *
     * @param id The ID of the model to be deleted.
     */
    public void deleteModel(String id) {
        Log.i(AnkiDroidApp.TAG, "deleteModel = " + id);
        Cursor cursor = null;
        boolean modelExists = false;

        try {
            cursor = getDB().getDatabase().rawQuery("SELECT * FROM models WHERE id = " + id, null);
            // Does the model exist?
            if (cursor.moveToFirst()) {
                modelExists = true;
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        if (modelExists) {
            // Delete the cards that use the model id, through fact
            ArrayList<String> cardsToDelete = getDB()
                    .queryColumn(
                            String.class,
                            "SELECT cards.id FROM cards, facts WHERE facts.modelId = " + id
                                    + " AND facts.id = cards.factId", 0);
            deleteCards(cardsToDelete);

            // Delete model
            getDB().delete(this, "models", "id = " + id, null);

            // Note deleted model
            ContentValues values = new ContentValues();
            values.put("modelId", id);
            values.put("deletedTime", Utils.now());
            getDB().insert(this, "modelsDeleted", null, values);

            flushMod();
        }
    }


    public void deleteFieldModel(String modelId, String fieldModelId) {
        Log.i(AnkiDroidApp.TAG, "deleteFieldModel, modelId = " + modelId + ", fieldModelId = " + fieldModelId);

        // Delete field model
        getDB().delete(this, "fields", "fieldModel = " + fieldModelId, null);

        // Note like modified the facts that use this model
        getDB().getDatabase().execSQL(
                "UPDATE facts SET modified = " + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now())
                        + " WHERE modelId = " + modelId);

        // TODO: remove field model from list

        // Update Question/Answer formats
        // TODO: All these should be done with the field object
        String fieldName = "";
        Cursor cursor = null;
        try {
            cursor = getDB().getDatabase().rawQuery("SELECT name FROM fieldModels WHERE id = " + fieldModelId, null);
            if (cursor.moveToNext()) {
                fieldName = cursor.getString(0);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        SQLiteStatement statement = null;
        try {
            cursor = getDB().getDatabase().rawQuery(
                    "SELECT id, qformat, aformat FROM cardModels WHERE modelId = " + modelId, null);
            String sql = "UPDATE cardModels SET qformat = ?, aformat = ? WHERE id = ?";
            statement = getDB().getDatabase().compileStatement(sql);
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String newQFormat = cursor.getString(1);
                String newAFormat = cursor.getString(2);

                newQFormat = newQFormat.replace("%%(" + fieldName + ")s", "");
                newQFormat = newQFormat.replace("%%(text:" + fieldName + ")s", "");
                newAFormat = newAFormat.replace("%%(" + fieldName + ")s", "");
                newAFormat = newAFormat.replace("%%(text:" + fieldName + ")s", "");

                statement.bindString(1, newQFormat);
                statement.bindString(2, newAFormat);
                statement.bindString(3, id);

                statement.execute();
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        statement.close();

        // TODO: updateCardsFromModel();

        // Note the model like modified (TODO: We should use the object model instead handling the DB directly)
    	ContentValues values = new ContentValues();
        values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
        getDB().update(this, "models", values, "id = " + modelId, null);
        flushMod();
    }


    public void deleteCardModel(String modelId, String cardModelId) {
        Log.i(AnkiDroidApp.TAG, "deleteCardModel, modelId = " + modelId + ", fieldModelId = " + cardModelId);

        // Delete all cards that use card model from the deck
        ArrayList<String> cardIds = getDB().queryColumn(String.class,
                "SELECT id FROM cards WHERE cardModelId = " + cardModelId, 0);
        deleteCards(cardIds);

        // I assume that the line "model.cardModels.remove(cardModel)" actually deletes cardModel from DB (I might be
        // wrong)
        getDB().delete(this, "cardModels", "id = " + cardModelId, null);

        // Note the model like modified (TODO: We should use the object model instead handling the DB directly)
    	ContentValues values = new ContentValues();
        values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
        getDB().update(this, "models", values, "id = " + modelId, null);
        flushMod();
    }


    // CSS for all the fields
    private String rebuildCSS() {
        StringBuilder css = new StringBuilder(512);
        Cursor cur = null;

        try {
            cur = getDB().getDatabase().rawQuery(
                    "SELECT id, quizFontFamily, quizFontSize, quizFontColour, -1, "
                            + "features, editFontFamily FROM fieldModels", null);
            while (cur.moveToNext()) {
                css.append(_genCSS(".fm", cur));
            }
            cur.close();
            cur = getDB().getDatabase().rawQuery("SELECT id, null, null, null, questionAlign, 0, 0 FROM cardModels",
                    null);
            StringBuilder cssAnswer = new StringBuilder(512);
            while (cur.moveToNext()) {
                css.append(_genCSS("#cmq", cur));
                cssAnswer.append(_genCSS("#cma", cur));
            }
            css.append(cssAnswer.toString());
            cur.close();
            cur = getDB().getDatabase().rawQuery("SELECT id, lastFontColour FROM cardModels", null);
            while (cur.moveToNext()) {
                css.append(".cmb").append(Utils.hexifyID(cur.getLong(0))).append(" {background:").append(
                        cur.getString(1)).append(";}\n");
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        setVar("cssCache", css.toString(), false);
        addHexCache();

        return css.toString();
    }


    private String _genCSS(String prefix, Cursor row) {
        StringBuilder t = new StringBuilder(256);
        long id = row.getLong(0);
        String fam = row.getString(1);
        int siz = row.getInt(2);
        String col = row.getString(3);
        int align = row.getInt(4);
        String rtl = row.getString(5);
        int pre = row.getInt(6);
        if (fam != null) {
            t.append("font-family:\"").append(fam).append("\";");
        }
        if (siz != 0) {
            t.append("font-size:").append(siz).append("px;");
        }
        if (col != null) {
            t.append("color:").append(col).append(";");
        }
        if (rtl != null && rtl.compareTo("rtl") == 0) {
            t.append("direction:rtl;unicode-bidi:embed;");
        }
        if (pre != 0) {
            t.append("white-space:pre-wrap;");
        }
        if (align != -1) {
            if (align == 0) {
                t.append("text-align:center;");
            } else if (align == 1) {
                t.append("text-align:left;");
            } else {
                t.append("text-align:right;");
            }
        }
        if (t.length() > 0) {
            t.insert(0, prefix + Utils.hexifyID(id) + " {").append("}\n");
        }
        return t.toString();
    }


    private void addHexCache() {
        ArrayList<Long> ids = getDB().queryColumn(Long.class,
                "SELECT id FROM fieldModels UNION SELECT id FROM cardModels UNION SELECT id FROM models", 0);
        JSONObject jsonObject = new JSONObject();
        for (Long id : ids) {
            try {
                jsonObject.put(id.toString(), Utils.hexifyID(id.longValue()));
            } catch (JSONException e) {
                Log.e(AnkiDroidApp.TAG, "addHexCache: Error while generating JSONObject: " + e.toString());
                throw new RuntimeException(e);
            }
        }
        setVar("hexCache", jsonObject.toString(), false);
    }

    //
    // Syncing
    // *************************
    // Toggling does not bump deck mod time, since it may happen on upgrade and the variable is not synced

    // public void enableSyncing() {
    // enableSyncing(true);
    // }

    // public void enableSyncing(boolean ls) {
    // mSyncName = Utils.checksum(mDeckPath);
    // if (ls) {
    // mLastSync = 0;
    // }
    // commitToDB();
    // }

    // private void disableSyncing() {
    // disableSyncing(true);
    // }
    // private void disableSyncing(boolean ls) {
    // mSyncName = "";
    // if (ls) {
    // mLastSync = 0;
    // }
    // commitToDB();
    // }

    // public boolean syncingEnabled() {
    // return (mSyncName != null) && !(mSyncName.equals(""));
    // }

    // private void checkSyncHash() {
    // if ((mSyncName != null) && !mSyncName.equals(Utils.checksum(mDeckPath))) {
    // disableSyncing();
    // }
    // }

    /*
     * Undo/Redo*********************************************************
     */

    private class UndoRow {
        private String mName;
        private Long mCardId;
        private ArrayList<UndoCommand> mUndoCommands;

        UndoRow(String name, Long cardId) {
            mName = name;
            mCardId = cardId;
            mUndoCommands = new ArrayList<UndoCommand>();
        }
    }


    private class UndoCommand {
        private String mCommand;
        private String mTable;
        private ContentValues mValues;
        private String mWhereClause;

        UndoCommand(String command, String table, ContentValues values, String whereClause) {
        	mCommand = command;
        	mTable = table;
        	mValues = values;
        	mWhereClause = whereClause;
        }
    }


    private void initUndo() {
        mUndoStack = new Stack<UndoRow>();
        mRedoStack = new Stack<UndoRow>();
        mUndoEnabled = true;
    }


    public String undoName() {
        return mUndoStack.peek().mName;
    }


    public String redoName() {
        return mRedoStack.peek().mName;
    }


    public boolean undoAvailable() {
        return (mUndoEnabled && !mUndoStack.isEmpty());
    }


    public boolean redoAvailable() {
        return (mUndoEnabled && !mRedoStack.isEmpty());
    }


    public void resetUndo() {
        mUndoStack.clear();
        mRedoStack.clear();
    }


    private void setUndoBarrier() {
        if (mUndoStack.isEmpty() || mUndoStack.peek() != null) {
            mUndoStack.push(null);
        }
    }


    public void setUndoStart(String name) {
        setUndoStart(name, 0, false);
    }

    public void setUndoStart(String name, long cardId) {
        setUndoStart(name, cardId, false);
    }


    /**
     * @param reviewEarly set to true for early review
     */
    public void setReviewEarly(boolean reviewEarly) {
        mReviewEarly = reviewEarly;
    }


    private void setUndoStart(String name, long cardId, boolean merge) {
        if (!mUndoEnabled) {
            return;
        }
        if (merge && !mUndoStack.isEmpty()) {
            if ((mUndoStack.peek() != null) && (mUndoStack.peek().mName.equals(name))) {
                // libanki: merge with last entry?
                return;
            }
        }
        mUndoStack.push(new UndoRow(name, cardId));
        if (mUndoStack.size() > 20) {
        	mUndoStack.removeElementAt(0);
        }
        mUndoRedoStackToRecord = mUndoStack;
    }


    public void setUndoEnd(String name) {
        if (!mUndoEnabled) {
            return;
        }
        while (mUndoStack.peek() == null) {
            mUndoStack.pop(); // Strip off barrier
        }
        UndoRow row = mUndoStack.peek();
        if (row.mUndoCommands.size() == 0) {
            mUndoStack.pop();
        } else {
            mRedoStack.clear();
        }
        mUndoRedoStackToRecord = null;
    }


    public boolean recordUndoInformation() {
    	return mUndoEnabled && (mUndoRedoStackToRecord != null);
    }


    public void addUndoCommand(String command, String table, ContentValues values, String whereClause) {
    	mUndoRedoStackToRecord.peek().mUndoCommands.add(new UndoCommand(command, table, values, whereClause));
    }


    private long undoredo(Stack<UndoRow> src, Stack<UndoRow> dst, long oldCardId, boolean inReview) {
        UndoRow row;
        while (true) {
            row = src.pop();
            if (row != null) {
                break;
            }
        }
        if (inReview) {
           dst.push(new UndoRow(row.mName, row.mCardId));
        } else {
           dst.push(new UndoRow(row.mName, oldCardId));
        }
        mUndoRedoStackToRecord = dst;
        getDB().getDatabase().beginTransaction();
        try {
            for (UndoCommand u : row.mUndoCommands) {
                getDB().execSQL(this, u.mCommand, u.mTable, u.mValues, u.mWhereClause);
            }
            getDB().getDatabase().setTransactionSuccessful();
        } finally {
        	mUndoRedoStackToRecord = null;
        	getDB().getDatabase().endTransaction();
        }
        if (row.mUndoCommands.size() == 0) {
        	dst.pop();
        }
        mCurrentUndoRedoType = row.mName;
        return row.mCardId;
    }


    /**
     * Undo the last action(s). Caller must .reset()
     */
    public long undo(long oldCardId, boolean inReview) {
        long cardId = 0;
    	if (!mUndoStack.isEmpty()) {
            cardId = undoredo(mUndoStack, mRedoStack, oldCardId, inReview);
            commitToDB();
            reset();
        }
        return cardId;
    }


    /**
     * Redo the last action(s). Caller must .reset()
     */
    public long redo(long oldCardId, boolean inReview) {
        long cardId = 0;
        if (!mRedoStack.isEmpty()) {
        	cardId = undoredo(mRedoStack, mUndoStack, oldCardId, inReview);
            commitToDB();
            reset();
        }
        return cardId;
    }


    public String getUndoType() {
    	return mCurrentUndoRedoType;
    }

    /*
     * Dynamic indices*********************************************************
     */

    public void updateDynamicIndices() {
        Log.i(AnkiDroidApp.TAG, "updateDynamicIndices - Updating indices...");
        HashMap<String, String> indices = new HashMap<String, String>();
        indices.put("intervalDesc", "(queue, interval desc, factId, due)");
        indices.put("intervalAsc", "(queue, interval, factId, due)");
        indices.put("randomOrder", "(queue, factId, ordinal, due)");
        // new cards are sorted by due, not combinedDue, so that even if
        // they are spaced, they retain their original sort order
        indices.put("dueAsc", "(queue, due, factId, due)");
        indices.put("dueDesc", "(queue, due desc, factId, due)");

        ArrayList<String> required = new ArrayList<String>();
        if (mRevCardOrder == REV_CARDS_OLD_FIRST) {
            required.add("intervalDesc");
        }
        if (mRevCardOrder == REV_CARDS_NEW_FIRST) {
            required.add("intervalAsc");
        }
        if (mRevCardOrder == REV_CARDS_RANDOM) {
            required.add("randomOrder");
        }
        if (mRevCardOrder == REV_CARDS_DUE_FIRST || mNewCardOrder == NEW_CARDS_OLD_FIRST
                || mNewCardOrder == NEW_CARDS_RANDOM) {
            required.add("dueAsc");
        }
        if (mNewCardOrder == NEW_CARDS_NEW_FIRST) {
            required.add("dueDesc");
        }

        // Add/delete
        boolean analyze = false;
        Set<Entry<String, String>> entries = indices.entrySet();
        Iterator<Entry<String, String>> iter = entries.iterator();
        String indexName = null;
        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            indexName = "ix_cards_" + entry.getKey();
            if (required.contains(entry.getKey())) {
                Cursor cursor = null;
                try {
                    cursor = getDB().getDatabase().rawQuery(
                            "SELECT 1 FROM sqlite_master WHERE name = '" + indexName + "'", null);
                    if ((!cursor.moveToNext()) || (cursor.getInt(0) != 1)) {
                        getDB().getDatabase().execSQL("CREATE INDEX " + indexName + " ON cards " + entry.getValue());
                        analyze = true;
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } else {
                getDB().getDatabase().execSQL("DROP INDEX IF EXISTS " + indexName);
            }
        }
        if (analyze) {
            getDB().getDatabase().execSQL("ANALYZE");
        }
    }


    /*
     * JSON
     */

    public JSONObject bundleJson(JSONObject bundledDeck) {
        try {
            bundledDeck.put("averageFactor", mAverageFactor);
            bundledDeck.put("cardCount", mCardCount);
            bundledDeck.put("collapseTime", mCollapseTime);
            bundledDeck.put("created", mCreated);
            // bundledDeck.put("currentModelId", currentModelId);
            bundledDeck.put("delay0", mDelay0);
            bundledDeck.put("delay1", mDelay1);
            bundledDeck.put("delay2", mDelay2);
            bundledDeck.put("description", mDescription);
            bundledDeck.put("easyIntervalMax", mEasyIntervalMax);
            bundledDeck.put("easyIntervalMin", mEasyIntervalMin);
            bundledDeck.put("factCount", mFactCount);
            bundledDeck.put("failedCardMax", mFailedCardMax);
            bundledDeck.put("failedNowCount", mFailedNowCount);
            bundledDeck.put("failedSoonCount", mFailedSoonCount);
            bundledDeck.put("hardIntervalMax", mHardIntervalMax);
            bundledDeck.put("hardIntervalMin", mHardIntervalMin);
            bundledDeck.put("highPriority", mHighPriority);
            bundledDeck.put("id", mId);
            bundledDeck.put("lastLoaded", mLastLoaded);
            bundledDeck.put("lastSync", mLastSync);
            bundledDeck.put("lowPriority", mLowPriority);
            bundledDeck.put("medPriority", mMedPriority);
            bundledDeck.put("midIntervalMax", mMidIntervalMax);
            bundledDeck.put("midIntervalMin", mMidIntervalMin);
            bundledDeck.put("modified", mModified);
            bundledDeck.put("newCardModulus", mNewCardModulus);
//            bundledDeck.put("newCount", mNewCount);
//            bundledDeck.put("newCountToday", mNewCountToday);
            bundledDeck.put("newEarly", mNewEarly);
            bundledDeck.put("revCount", mRevCount);
            bundledDeck.put("reviewEarly", mReviewEarly);
            bundledDeck.put("suspended", mSuspended);
            bundledDeck.put("undoEnabled", mUndoEnabled);
            bundledDeck.put("utcOffset", mUtcOffset);
        } catch (JSONException e) {
            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
        }

        return bundledDeck;
    }


    public void updateFromJson(JSONObject deckPayload) {
        try {
            // Update deck
            mCardCount = deckPayload.getInt("cardCount");
            mCollapseTime = deckPayload.getDouble("collapseTime");
            mCreated = deckPayload.getDouble("created");
            // css
            mCurrentModelId = deckPayload.getLong("currentModelId");
            mDelay0 = deckPayload.getLong("delay0");
            mDelay1 = deckPayload.getLong("delay1");
            mDelay2 = deckPayload.getDouble("delay2");
            mDescription = deckPayload.getString("description");
            mDueCutoff = deckPayload.getDouble("dueCutoff");
            mEasyIntervalMax = deckPayload.getDouble("easyIntervalMax");
            mEasyIntervalMin = deckPayload.getDouble("easyIntervalMin");
            mFactCount = deckPayload.getInt("factCount");
            mFailedCardMax = deckPayload.getInt("failedCardMax");
            mFailedNowCount = deckPayload.getInt("failedNowCount");
            mFailedSoonCount = deckPayload.getInt("failedSoonCount");
            // forceMediaDir
            mHardIntervalMax = deckPayload.getDouble("hardIntervalMax");
            mHardIntervalMin = deckPayload.getDouble("hardIntervalMin");
            mHighPriority = deckPayload.getString("highPriority");
            mId = deckPayload.getLong("id");
            // key
            mLastLoaded = deckPayload.getDouble("lastLoaded");
            // lastSessionStart
            mLastSync = deckPayload.getDouble("lastSync");
            // lastTags
            mLowPriority = deckPayload.getString("lowPriority");
            mMedPriority = deckPayload.getString("medPriority");
            mMidIntervalMax = deckPayload.getDouble("midIntervalMax");
            mMidIntervalMin = deckPayload.getDouble("midIntervalMin");
            mModified = deckPayload.getDouble("modified");
            // needLock
            mNewCardOrder = deckPayload.getInt("newCardOrder");
            mNewCardSpacing = deckPayload.getInt("newCardSpacing");
            mNewCardsPerDay = deckPayload.getInt("newCardsPerDay");
//            mNewCount = deckPayload.getInt("newCount");
            // progressHandlerCalled
            // progressHandlerEnabled
            mQueueLimit = deckPayload.getInt("queueLimit");
            mRevCardOrder = deckPayload.getInt("revCardOrder");
            mRevCount = deckPayload.getInt("revCount");
            mScheduler = deckPayload.getString("scheduler");
            mSessionRepLimit = deckPayload.getInt("sessionRepLimit");
            // sessionStartReps
            // sessionStartTime
            mSessionTimeLimit = deckPayload.getInt("sessionTimeLimit");
            mSuspended = deckPayload.getString("suspended");
            // tmpMediaDir
            mUndoEnabled = deckPayload.getBoolean("undoEnabled");
            mUtcOffset = deckPayload.getDouble("utcOffset");

            commitToDB();
        } catch (JSONException e) {
            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
        }
    }


    /*
     * Utility functions (might be better in a separate class) *********************************************************
     */

    /**
     * Return ID for tag, creating if necessary.
     *
     * @param tag the tag we are looking for
     * @param create whether to create the tag if it doesn't exist in the database
     * @return ID of the specified tag, 0 if it doesn't exist, and -1 in the case of error
     */
    private long tagId(String tag, Boolean create) {
        long id = 0;

        try {
            id = getDB().queryScalar("select id from tags where tag = \"" + tag + "\"");
        } catch (SQLException e) {
            if (create) {
                ContentValues value = new ContentValues();
                value.put("tag", tag);
                id = getDB().insert(this, "tags", null, value);
            } else {
                id = 0;
            }
        }
        return id;
    }


    /**
     * Gets the IDs of the specified tags.
     *
     * @param tags An array of the tags to get IDs for.
     * @param create Whether to create the tag if it doesn't exist in the database. Default = true
     * @return An array of IDs of the tags.
     */
    private HashMap<String, Long> tagIds(String[] tags) {
        return tagIds(tags, true);
    }


    private HashMap<String, Long> tagIds(String[] tags, boolean create) {
        HashMap<String, Long> results = new HashMap<String, Long>();

        if (create) {
            for (String tag : tags) {
                getDB().getDatabase().execSQL("INSERT OR IGNORE INTO tags (tag) VALUES ('" + tag + "')");
            }
        }
        if (tags.length != 0) {
            StringBuilder tagList = new StringBuilder(128);
            for (int i = 0; i < tags.length; i++) {
                tagList.append("'").append(tags[i]).append("'");
                if (i < tags.length - 1) {
                    tagList.append(", ");
                }
            }
            Cursor cur = null;
            try {
                cur = getDB().getDatabase().rawQuery(
                        "SELECT tag, id FROM tags WHERE tag in (" + tagList.toString() + ")", null);
                while (cur.moveToNext()) {
                    results.put(cur.getString(0).toLowerCase(), cur.getLong(1));
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
        return results;
    }

    /**
     * Initialize an empty deck that has just been creating by copying the existing "empty.anki" file.
     *
     * From Damien:
     * Just copying a file is not sufficient - you need to give each model, cardModel and fieldModel new ids as well, and make sure they are all still linked up. If you don't do that, and people modify one model and then import/export one deck into another, the models will be treated as identical even though they have different layouts, and half the cards will end up corrupted.
     *  It's only the IDs that you have to worry about, and the utcOffset IIRC.
     */
    public static synchronized void initializeEmptyDeck(String deckPath) {
        AnkiDb db = AnkiDatabaseManager.getDatabase(deckPath);

        // Regenerate IDs.
        long modelId = Utils.genID();
        db.getDatabase().execSQL("UPDATE models SET id=" + modelId);
        db.getDatabase().execSQL("UPDATE cardModels SET id=" + Utils.genID() + " where ordinal=0;");
        db.getDatabase().execSQL("UPDATE cardModels SET id=" + Utils.genID() + " where ordinal=1;");
        db.getDatabase().execSQL("UPDATE fieldModels SET id=" + Utils.genID() + " where ordinal=0;");
        db.getDatabase().execSQL("UPDATE fieldModels SET id=" + Utils.genID() + " where ordinal=1;");

        // Update columns that refer to modelId.
        db.getDatabase().execSQL("UPDATE fieldModels SET modelId=" + modelId);
        db.getDatabase().execSQL("UPDATE cardModels SET modelId=" + modelId);
        db.getDatabase().execSQL("UPDATE decks SET currentModelId=" + modelId);

        // Set the UTC offset.
        db.getDatabase().execSQL("UPDATE decks SET utcOffset=" + Utils.utcOffset());
    }
}
