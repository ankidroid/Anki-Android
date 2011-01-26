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
import android.util.Log;

import com.ichi2.anki.Fact.Field;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A deck stores all of the cards and scheduling information. It is saved in a file with a name ending in .anki See
 * http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Deck
 */
public class Deck {

    public static final String TAG_MARKED = "Marked";

    public static final int DECK_VERSION = 64;

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
    public static final double INITIAL_FACTOR = 2.5;
    private static final double MINIMUM_AVERAGE = 1.7;
    private static final double MAX_SCHEDULE_TIME = 36500.0;

    // Card order strings for building SQL statements
    private static final String[] revOrderStrings = { "priority desc, interval desc", "priority desc, interval",
            "priority desc, combinedDue", "priority desc, factId, ordinal" };
    private static final String[] newOrderStrings = { "priority desc, due", "priority desc, due",
            "priority desc, due desc" };

    // BEGIN: SQL table columns
    private long mId;
    private double mCreated;
    private double mModified;
    private String mDescription;
    private int mVersion;
    private long mCurrentModelId;

    // syncName stores an md5sum of the deck path when syncing is enabled.
    // If it doesn't match the current deck path, the deck has been moved,
    // and syncing is disabled on load.
    private String mSyncName;
    private double mLastSync;

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

    // Collapsing future cards
    private double mCollapseTime;

    // Priorities and postponing
    private String mHighPriority;
    private String mMedPriority;
    private String mLowPriority;
    private String mSuspended; // obsolete in libanki 1.1

    // 0 is random, 1 is by input date
    private int mNewCardOrder;

    // When to show new cards
    private int mNewCardSpacing;

    // New card spacing global variable
    private double mNewSpacing;
    private boolean mNewFromCache;

    // Limit the number of failed cards in play
    private int mFailedCardMax;

    // Number of new cards to show per day
    private int mNewCardsPerDay;

    // Currently unused
    private long mSessionRepLimit;
    private long mSessionTimeLimit;

    // Stats offset
    private double mUtcOffset;

    // Count cache
    private int mCardCount;
    private int mFactCount;
    private int mFailedNowCount; // obsolete in libanki 1.1
    private int mFailedSoonCount;
    private int mRevCount;
    private int mNewCount;

    // Review order
    private int mRevCardOrder;

    // END: SQL table columns

    // BEGIN JOINed variables
    // Model currentModel; // Deck.currentModelId = Model.id
    // ArrayList<Model> models; // Deck.id = Model.deckId
    // END JOINed variables

    private double mAverageFactor;
    private int mNewCardModulus;
    private int mNewCountToday;
    private double mLastLoaded;
    private boolean mNewEarly;
    private boolean mReviewEarly;
    public String mMediaPrefix;

    private double mDueCutoff;
    private double mFailedCutoff;

    private String mScheduler;

    // Any comments resulting from upgrading the deck should be stored here, both in success and failure
    private ArrayList<Integer> upgradeNotes;

    // Queues
    private LinkedList<QueueItem> mFailedQueue;
    private LinkedList<QueueItem> mRevQueue;
    private LinkedList<QueueItem> mNewQueue;
    private LinkedList<QueueItem> mFailedCramQueue;
    private HashMap<Long, Double> mSpacedFacts;
    private LinkedList<SpacedCardsItem> mSpacedCards;
    private int mQueueLimit;

    // Cramming
    private String[] mActiveCramTags;
    private String mCramOrder;

    // Not in Anki Desktop
    private String mDeckPath;
    private String mDeckName;

    private Stats mGlobalStats;
    private Stats mDailyStats;

    private long mCurrentCardId;

    /**
     * Undo/Redo variables.
     */
    private Stack<UndoRow> mUndoStack;
    private Stack<UndoRow> mRedoStack;
    private boolean mUndoEnabled = false;


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
            deck.mNewCount = cursor.getInt(34);
            deck.mRevCardOrder = cursor.getInt(35);

            Log.i(AnkiDroidApp.TAG, "openDeck - Read " + cursor.getColumnCount() + " columns from decks table.");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "openDeck - modified: %f currentTime: %f",
                deck.mModified, Utils.now()));

        // Initialise queues
        deck.mFailedQueue = new LinkedList<QueueItem>();
        deck.mRevQueue = new LinkedList<QueueItem>();
        deck.mNewQueue = new LinkedList<QueueItem>();
        deck.mFailedCramQueue = new LinkedList<QueueItem>();
        deck.mSpacedFacts = new HashMap<Long, Double>();
        deck.mSpacedCards = new LinkedList<SpacedCardsItem>();

        deck.mDeckPath = path;
        deck.mDeckName = (new File(path)).getName().replace(".anki", "");

        if (deck.mVersion < DECK_VERSION) {
            deck.createMetadata();
        }

        boolean needUnpack = false;
        if (deck.getUtcOffset() == -1.0) {
            deck.setUtcOffset();
            needUnpack = true;
        }
        
        deck.initVars();

        // Upgrade to latest version
        deck.upgradeDeck();

        if (!rebuild) {
            // Minimal startup
            deck.mGlobalStats = Stats.globalStats(deck);
            deck.mDailyStats = Stats.dailyStats(deck);
            return deck;
        }
        
        if (needUnpack) {
            deck.addIndices();
            // TODO: updateCardsFromModel
            deck.mCreated = Utils.now();
            
        }
        
        double oldMod = deck.mModified;

        // Ensure necessary indices are available
        deck.updateDynamicIndices();

        // FIXME: Temporary code for upgrade - ensure cards suspended on older clients are recognized
        // Ensure cards suspended on older clients are recognized
        deck.getDB().getDatabase().execSQL(
                "UPDATE cards SET type = type - 3 WHERE type BETWEEN 0 AND 2 AND priority = -3");

        // - New delay1 handling
        if (deck.mDelay1 > 7l) {
            // We treat 600==0 to avoid breaking older clients
            deck.mDelay1 = 600l;
        }

        ArrayList<Long> ids = new ArrayList<Long>();
        // Unsuspend buried/rev early - can remove priorities in the future
        ids = deck.getDB().queryColumn(Long.class,
                "SELECT id FROM cards WHERE type > 2 OR (priority BETWEEN -2 AND -1)", 0);
        if (!ids.isEmpty()) {
            deck.updatePriorities(Utils.toPrimitive(ids));
            deck.getDB().getDatabase().execSQL("UPDATE cards SET type = relativeDelay WHERE type > 2");
            // Save deck to database
            deck.commitToDB();
        }

        // Determine starting factor for new cards
        Cursor cur = null;
        try {
            cur = deck.getDB().getDatabase().rawQuery("SELECT avg(factor) FROM cards WHERE type = 1", null);
            if (cur.moveToNext()) {
                deck.mAverageFactor = cur.getDouble(0);
            } else {
                deck.mAverageFactor = INITIAL_FACTOR;
            }
            if (deck.mAverageFactor == 0.0) {
                deck.mAverageFactor = INITIAL_FACTOR;
            }
        } catch (Exception e) {
            deck.mAverageFactor = INITIAL_FACTOR;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        deck.mAverageFactor = Math.max(deck.mAverageFactor, MINIMUM_AVERAGE);

        // Rebuild queue
        deck.reset();
        // Make sure we haven't accidentally bumped the modification time
        double dbMod = 0.0;
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
        // Create a temporary view for random new cards. Randomizing the cards by themselves
        // as is done in desktop Anki in Deck.randomizeNewCards() takes too long.
        try {
            deck.getDB().getDatabase().execSQL(
                    "CREATE TEMPORARY VIEW acqCardsRandom AS SELECT * FROM cards " + "WHERE type = " + Card.TYPE_NEW
                            + " AND isDue = 1 ORDER BY RANDOM()");
        } catch (SQLException e) {
            /* Temporary view may still be present if the DB has not been closed */
            Log.i(AnkiDroidApp.TAG, "Failed to create temporary view: " + e.getMessage());
        }
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
        DeckTask.waitToFinish(); // Wait for any thread working on the deck to finish.
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

    public TreeMap<Long, CardModel> availableCardModels(Fact fact) {
        TreeMap<Long, CardModel> cardModels = new TreeMap<Long, CardModel>();
        TreeMap<Long, CardModel> availableCardModels = new TreeMap<Long, CardModel>();
        CardModel.fromDb(this, fact.getModelId(), cardModels);
        for (Map.Entry<Long, CardModel> entry : cardModels.entrySet()) {
            CardModel cardmodel = entry.getValue();
            if (cardmodel.isActive()) {
                // TODO: check for emptiness
                availableCardModels.put(cardmodel.getId(), cardmodel);
            }
        }
        return availableCardModels;
    }

    public TreeMap<Long, CardModel> cardModels(Fact fact) {
        TreeMap<Long, CardModel> cardModels = new TreeMap<Long, CardModel>();
        CardModel.fromDb(this, fact.getModelId(), cardModels);
        return cardModels;
    }

    /**
     * deckVars methods
     */
    public boolean hasKey(String key) {
        Cursor cur = null;
        try {
            cur = getDB().getDatabase().rawQuery("SELECT 1 FROM deckVars WHERE key = '" + key + "'", null);
            return cur.moveToNext();
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    public int getInt(String key) throws SQLException {
        Cursor cur = null;
        try {
            cur = getDB().getDatabase().rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.moveToFirst()) {
                return cur.getInt(0);
            } else {
                throw new SQLException("DeckVars.getInt: could not retrieve value for " + key);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    public double getFloat(String key) throws SQLException {
        Cursor cur = null;
        try {
            cur = getDB().getDatabase().rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.moveToFirst()) {
                return cur.getFloat(0);
            } else {
                throw new SQLException("DeckVars.getFloat: could not retrieve value for " + key);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    public boolean getBool(String key) {
        Cursor cur = null;
        try {
            cur = getDB().getDatabase().rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.moveToFirst()) {
                return (cur.getInt(0) != 0);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return false;
    }


    public String getVar(String key) {
        Cursor cur = null;
        try {
            cur = getDB().getDatabase().rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.moveToFirst()) {
                return cur.getString(0);
            }
        } catch (SQLException e) {
            Log.e(AnkiDroidApp.TAG, "getVar: " + e.toString());
            throw new RuntimeException(e);
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return null;
    }


    public void setVar(String key, String value) {
        setVar(key, value, true);
    }


    public void setVar(String key, String value, boolean mod) {
        Cursor cur = null;
        try {
            cur = getDB().getDatabase().rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.moveToNext()) {
                if (cur.getString(0).equals(value)) {
                    return;
                } else {
                    getDB().getDatabase()
                            .execSQL("UPDATE deckVars SET value='" + value + "' WHERE key = '" + key + "'");
                }
            } else {
                getDB().getDatabase().execSQL(
                        "INSERT INTO deckVars (key, value) VALUES ('" + key + "', '" + value + "')");
            }
        } catch (SQLException e) {
            Log.e(AnkiDroidApp.TAG, "setVar: " + e.toString());
            throw new RuntimeException(e);
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    public void setVarDefault(String key, String value) {
        if (!hasKey(key)) {
            getDB().getDatabase().execSQL("INSERT INTO deckVars (key, value) values ('" + key + "', '" + value + "')");
        }
    }


    private void initVars() {
        // tmpMediaDir = null;
        mMediaPrefix = null;
        // lastTags = "";
        mLastLoaded = Utils.now();
        // undoEnabled = false;
        // sessionStartReps = 0;
        // sessionStartTime = 0;
        // lastSessionStart = 0;
        mQueueLimit = 200;
        // If most recent deck var not defined, make sure defaults are set
        if (!hasKey("latexPost")) {
            setVarDefault("suspendLeeches", "1");
            setVarDefault("leechFails", "16");
            setVarDefault("perDay", "1");
            setVarDefault("newActive", "");
            setVarDefault("revActive", "");
            setVarDefault("newInactive", mSuspended);
            setVarDefault("revInactive", mSuspended);
            setVarDefault("newSpacing", "60");
            setVarDefault("mediaURL", "");
            setVarDefault("latexPre", "\\documentclass[12pt]{article}\n" + "\\special{papersize=3in,5in}\n"
                    + "\\usepackage[utf8]{inputenc}\n" + "\\usepackage{amssymb,amsmath}\n" + "\\pagestyle{empty}\n"
                    + "\\begin{document}\n");
            setVarDefault("latexPost", "\\end{document}");
            // FIXME: The next really belongs to the dropbox setup module, it's not supposed to be empty if the user
            // wants to use dropbox. ankiqt/ankiqt/ui/main.py : setupMedia
            setVarDefault("mediaLocation", "");
        }
        updateCutoff();
        setupStandardScheduler();
    }


    // Media
    // *****

    /**
     * Return the media directory if exists, none if couldn't be created.
     * 
     * @param create If true it will attempt to create the folder if it doesn't exist
     * @return The path of the media directory
     */
    public String mediaDir() {
        return mediaDir(false);
    }


    public String mediaDir(boolean create) {
        String dir = null;
        if (mDeckPath != null && !mDeckPath.equals("")) {
            if (mMediaPrefix != null) {
                dir = mMediaPrefix + "/" + mDeckName + ".media";
            } else {
                dir = mDeckPath.replaceAll("\\.anki$", ".media");
            }
            if (!create) {
                // Don't create, but return dir
                return dir;
            } else {
                File mediaDir = new File(dir);
                if (!mediaDir.exists()) {
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
        }

        if (dir == null) {
            return null;
        } else {
            // TODO: Inefficient, should use prior File
            File mediaDir = new File(dir);
            if (!mediaDir.exists() || !mediaDir.isDirectory()) {
                return null;
            }
        }
        return dir;
    }


    /**
     * Upgrade deck to latest version. Any comments resulting from the upgrade, should be stored in upgradeNotes, as
     * R.string.id, successful or not. The idea is to have Deck.java generate the notes from upgrading and not the UI.
     * Still we need access to a Resources object and it's messy to pass that in openDeck. Instead we store the ids for
     * the messages and make a separate call from the UI to static upgradeNotesToMessages in order to properly translate
     * the IDs to messages for viewing. We shouldn't do this directly from the UI, as the messages contain %s variables
     * that need to be populated from deck values, and it's better to contain the libanki logic to the relevant classes.
     * 
     * @return True if the upgrade is supported, false if the upgrade needs to be performed by Anki Desktop
     */
    private boolean upgradeDeck() {
        // Oldest versions in existence are 31 as of 11/07/2010
        // We support upgrading from 39 and up.
        // Unsupported are about 135 decks, missing about 6% as of 11/07/2010
        //
        double oldmod = mModified;

        upgradeNotes = new ArrayList<Integer>();
        if (mVersion < 39) {
            // Unsupported version
            upgradeNotes.add(com.ichi2.anki.R.string.deck_upgrade_too_old_version);
            return false;
        }
        if (mVersion < 40) {
            // Now stores media url
            getDB().getDatabase().execSQL("UPDATE models SET features = ''");
            mVersion = 40;
            commitToDB();
        }
        if (mVersion < 43) {
            getDB().getDatabase().execSQL("UPDATE fieldModels SET features = ''");
            mVersion = 43;
            commitToDB();
        }
        if (mVersion < 44) {
            // Leaner indices
            getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_factId");
            mVersion = 44;
            commitToDB();
        }
        if (mVersion < 48) {
            updateFieldCache(Utils.toPrimitive(getDB().queryColumn(Long.class, "SELECT id FROM facts", 0)));
            mVersion = 48;
            commitToDB();
        }
        if (mVersion < 50) {
            // more new type handling
            rebuildTypes();
            mVersion = 50;
            commitToDB();
        }
        if (mVersion < 52) {
            // The commented code below follows libanki by setting the syncName to the MD5 hash of the path.
            // The problem with that is that it breaks syncing with already uploaded decks.
            // if ((mSyncName != null) && !mSyncName.equals("")) {
            // if (!mDeckName.equals(mSyncName)) {
            // upgradeNotes.add(com.ichi2.anki.R.string.deck_upgrade_52_note);
            // disableSyncing(false);
            // } else {
            // enableSyncing(false);
            // }
            // }
            mVersion = 52;
            commitToDB();
        }
        if (mVersion < 53) {
            if (getBool("perDay")) {
                if (Math.abs(mHardIntervalMin - 0.333) < 0.001) {
                    mHardIntervalMin = Math.max(1.0, mHardIntervalMin);
                    mHardIntervalMax = Math.max(1.1, mHardIntervalMax);
                }
            }
            mVersion = 53;
            commitToDB();
        }
        if (mVersion < 54) {
            // editFontFamily now used as a boolean, but in integer type, so set to 1 == true
            getDB().getDatabase().execSQL("UPDATE fieldModels SET editFontFamily = 1");
            mVersion = 54;
            commitToDB();
        }
        if (mVersion < 55) {
            // Set a default font for unset fonts
            getDB().getDatabase().execSQL(
                    "UPDATE fieldModels SET quizFontFamily = 'Arial' "
                            + "WHERE NOT quizFontFamily OR quizFontFamily IS null");
            mVersion = 55;
            commitToDB();
        }
        if (mVersion < 57) {
            // Add an index for priority & modified
            mVersion = 57;
            commitToDB();
        }
        if (mVersion < 61) {
            // TODO: Rebuild the QA cache

            // Rebuild the media db based on new format
            Media.rebuildMediaDir(this, false);
            mVersion = 61;
            commitToDB();
        }
        if (mVersion < 62) {
            // Updated Indices
            String[] indices = { "intervalDesc", "intervalAsc", "randomOrder", "dueAsc", "dueDesc" };
            for (String d : indices) {
                getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_" + d + "2");
            }
            getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_typeCombined");
            addIndices();
            updateDynamicIndices();
            getDB().getDatabase().execSQL("VACUUM");
            mVersion = 62;
            commitToDB();
        }
        if (mVersion < 63) {
            // Set a default font for unset font sizes
            getDB().getDatabase().execSQL(
                    "UPDATE fieldModels SET quizFontSize = 20 WHERE quizFontSize = ''" + "OR quizFontSize IS NULL");
            getDB().getDatabase().execSQL(
                    "UPDATE fieldModels SET editFontSize = 20 WHERE editFontSize = ''" + "OR editFontSize IS NULL");
            mVersion = 63;
            commitToDB();
        }
        if (mVersion < 64) {
            // Remove old static indices, as all clients should be libanki1.2+
            String[] oldStaticIndices = { "ix_cards_duePriority", "ix_cards_priorityDue" };
            for (String d : oldStaticIndices) {
                getDB().getDatabase().execSQL("DROP INDEX IF EXISTS " + d);
            }
            // Remove old dynamic indices
            String[] oldDynamicIndices = { "intervalDesc", "intervalAsc", "randomOrder", "dueAsc", "dueDesc" };
            for (String d : oldDynamicIndices) {
                getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_" + d);
            }
            getDB().getDatabase().execSQL("ANALYZE");
            mVersion = 64;
            commitToDB();
            // Note: we keep the priority index for now
        }
        if (mVersion < 65) {
            // We weren't correctly setting relativeDelay when answering cards in previous versions, so ensure
            // everything is set correctly
            rebuildTypes();
            mVersion = 65;
            commitToDB();
        }
        // Executing a pragma here is very slow on large decks, so we store our own record
        if (getInt("pageSize") != 4096) {
            commitToDB();
            getDB().getDatabase().execSQL("PRAGMA page_size = 4096");
            getDB().getDatabase().execSQL("PRAGMA legacy_file_format = 0");
            getDB().getDatabase().execSQL("VACUUM");
            setVar("pageSize", "4096", false);
            commitToDB();
        }
        assert (mModified == oldmod);
        return true;
    }


    public static String upgradeNotesToMessages(Deck deck, Resources res) {
        // FIXME: upgradeNotes should be a list of HashMaps<Integer, ArrayList<String>> containing any values
        // necessary for generating the messages. In the case of upgrade 52, name and syncName.
        String notes = "";
        for (Integer note : deck.upgradeNotes) {
            if (note == com.ichi2.anki.R.string.deck_upgrade_too_old_version) {
                // Unsupported version
                notes = notes.concat(res.getString(note.intValue()));
                // } else if (note == com.ichi2.anki.R.string.deck_upgrade_52_note) {
                // // Upgrade note for version 52 regarding syncName
                // notes = notes.concat(String.format(res.getString(note.intValue()),
                // deck.getDeckName(), deck.getSyncName()));
            }
        }
        return notes;
    }


    /**
     * Add indices to the DB.
     */
    private void addIndices() {
        // Counts, failed cards
        getDB().getDatabase().execSQL(
                "CREATE INDEX IF NOT EXISTS ix_cards_typeCombined ON cards (type, " + "combinedDue, factId)");
        // Scheduler-agnostic type
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_relativeDelay ON cards (relativeDelay)");
        // Index on modified, to speed up sync summaries
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_modified ON cards (modified)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_facts_modified ON facts (modified)");
        // Priority - temporary index to make compat code faster. This can be removed when all clients are on 1.2,
        // as can the ones below
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_priority ON cards (priority)");
        // Average factor
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_factor ON cards (type, factor)");
        // Card spacing
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_factId ON cards (factId)");
        // Stats
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_stats_typeDay ON stats (type, day)");
        // Fields
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_fields_factId ON fields (factId)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_fields_fieldModelId ON fields (fieldModelId)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_fields_value ON fields (value)");
        // Media
        getDB().getDatabase().execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ix_media_filename ON media (filename)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_media_originalPath ON media (originalPath)");
        // Deletion tracking
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cardsDeleted_cardId ON cardsDeleted (cardId)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_modelsDeleted_modelId ON modelsDeleted (modelId)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_factsDeleted_factId ON factsDeleted (factId)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_mediaDeleted_factId ON mediaDeleted (mediaId)");
        // Tags
        getDB().getDatabase().execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ix_tags_tag ON tags (tag)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cardTags_tagCard ON cardTags (tagId, cardId)");
        getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cardTags_cardId ON cardTags (cardId)");
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
            SQLiteStatement st = getDB().getDatabase().compileStatement("UPDATE facts SET spaceUntil=? WHERE id=?");
            for (Long fid : r.keySet()) {
                st.bindString(1, r.get(fid));
                st.bindLong(2, fid.longValue());
                st.execute();
            }
            getDB().getDatabase().setTransactionSuccessful();
            getDB().getDatabase().endTransaction();
        }
    }


    private boolean modifiedSinceSave() {
        return mModified > mLastLoaded;
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


        QueueItem(long cardID, long factID, double due) {
            this.cardID = cardID;
            this.factID = factID;
            this.due = due;
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


    /*
     * Tomorrow's due cards ******************************
     */
    public int getNextDueCards() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE type = 0 OR type = 1 AND combinedDue < %f", mDueCutoff + 86400);
        return (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
    }


    public int getNextNewCards() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE type = 2 AND combinedDue < %f", mDueCutoff + 86400);
        return Math.min((int) getDB().queryScalar(cardLimit("newActive", "newInactive", sql)), mNewCardsPerDay);
    }

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


    public String name() {
        return mScheduler;
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


    private void fillQueues() {
        fillFailedQueue();
        fillRevQueue();
        fillNewQueue();
        for (QueueItem i : mFailedQueue) {
            Log.i(AnkiDroidApp.TAG, "failed queue: cid: " + i.getCardID() + " fid: " + i.getFactID() + " cd: "
                    + i.getDue());
        }
        for (QueueItem i : mRevQueue) {
            Log.i(AnkiDroidApp.TAG, "rev queue: cid: " + i.getCardID() + " fid: " + i.getFactID());
        }
        for (QueueItem i : mNewQueue) {
            Log.i(AnkiDroidApp.TAG, "new queue: cid: " + i.getCardID() + " fid: " + i.getFactID());
        }
    }


    public long retrieveCardCount() {
        return getDB().queryScalar("SELECT count(*) from cards");
    }


    private void rebuildCounts() {
        // global counts
        try {
            mCardCount = (int) getDB().queryScalar("SELECT count(*) from cards");
            mFactCount = (int) getDB().queryScalar("SELECT count(*) from facts");
        } catch (SQLException e) {
            Log.e(AnkiDroidApp.TAG, "rebuildCounts: Error while getting global counts: " + e.toString());
            mCardCount = 0;
            mFactCount = 0;
        }
        // due counts
        rebuildFailedCount();
        rebuildRevCount();
        rebuildNewCount();
    }


    @SuppressWarnings("unused")
    private String _cardLimit(String active, String inactive, String sql) {
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


    /**
     * This is a count of all failed cards within the current day cutoff. The cards may not be ready for review yet, but
     * can still be displayed if failedCardsMax is reached.
     */
    @SuppressWarnings("unused")
    private void _rebuildFailedCount() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE type = 0 AND combinedDue < %f", mFailedCutoff);
        mFailedSoonCount = (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
    }


    @SuppressWarnings("unused")
    private void _rebuildRevCount() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE type = 1 AND combinedDue < %f", mDueCutoff);
        mRevCount = (int) getDB().queryScalar(cardLimit("revActive", "revInactive", sql));
    }


    @SuppressWarnings("unused")
    private void _rebuildNewCount() {
        String sql = String.format(Utils.ENGLISH_LOCALE,
                "SELECT count(*) FROM cards c WHERE type = 2 AND combinedDue < %f", mDueCutoff);
        mNewCount = (int) getDB().queryScalar(cardLimit("newActive", "newInactive", sql));
        updateNewCountToday();
        mSpacedCards.clear();
    }


    @SuppressWarnings("unused")
    private void _updateNewCountToday() {
        mNewCountToday = Math.max(Math.min(mNewCount, mNewCardsPerDay - newCardsDoneToday()), 0);
    }


    @SuppressWarnings("unused")
    private void _fillFailedQueue() {
        if ((mFailedSoonCount != 0) && mFailedQueue.isEmpty()) {
            Cursor cur = null;
            try {
                String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 0 AND combinedDue < "
                        + mFailedCutoff + " ORDER BY combinedDue LIMIT " + mQueueLimit;
                cur = getDB().getDatabase().rawQuery(cardLimit("revActive", "revInactive", sql), null);
                while (cur.moveToNext()) {
                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
                    mFailedQueue.add(0, qi); // Add to front, so list is reversed as it is built
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
    }


    @SuppressWarnings("unused")
    private void _fillRevQueue() {
        if ((mRevCount != 0) && mRevQueue.isEmpty()) {
            Cursor cur = null;
            try {
                String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 1 AND combinedDue < "
                        + mDueCutoff + " ORDER BY " + revOrder() + " LIMIT " + mQueueLimit;
                cur = getDB().getDatabase().rawQuery(cardLimit("revActive", "revInactive", sql), null);
                while (cur.moveToNext()) {
                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
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
    private void _fillNewQueue() {
        if ((mNewCountToday != 0) && mNewQueue.isEmpty() && mSpacedCards.isEmpty()) {
            Cursor cur = null;
            try {
                String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 2 AND combinedDue < "
                        + mDueCutoff + " ORDER BY " + newOrder() + " LIMIT " + mQueueLimit;
                cur = getDB().getDatabase().rawQuery(cardLimit("newActive", "newInactive", sql), null);
                while (cur.moveToNext()) {
                    QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
                    mNewQueue.addFirst(qi); // Add to front, so list is reversed as it is built
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
        }
    }


    private boolean queueNotEmpty(LinkedList<QueueItem> queue, Method fillFunc) {
        return queueNotEmpty(queue, fillFunc, false);
    }


    private boolean queueNotEmpty(LinkedList<QueueItem> queue, Method fillFunc, boolean _new) {
        while (true) {
            removeSpaced(queue, _new);
            if (!queue.isEmpty()) {
                return true;
            }
            try {
                fillFunc.invoke(Deck.this);
                mSpacedFacts.clear(); // workaround for freezing cards problem. remove this when the code is up to date
                // with libanki
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "queueNotEmpty: Error while invoking overridable fill method:" + e.toString());
                return false;
            }
            if (queue.isEmpty()) {
                return false;
            }
        }
    }


    private void removeSpaced(LinkedList<QueueItem> queue) {
        removeSpaced(queue, false);
    }


    private void removeSpaced(LinkedList<QueueItem> queue, boolean _new) {
        ArrayList<Long> popped = new ArrayList<Long>();
        double delay = 0.0;
        while (!queue.isEmpty()) {
            long fid = ((QueueItem) queue.getLast()).getFactID();
            if (mSpacedFacts.containsKey(fid)) {
                // Still spaced
                long id = queue.removeLast().getCardID();
                // Assuming 10 cards/minute, track id if likely to expire before queue refilled
                if (_new && (mNewSpacing < (double) mQueueLimit * 6.0)) {
                    popped.add(id);
                    delay = mSpacedFacts.get(fid);
                }
            } else {
                if (!popped.isEmpty()) {
                    mSpacedCards.add(new SpacedCardsItem(delay, popped));
                }
                break;
            }
        }
    }


    private boolean revNoSpaced() {
        return queueNotEmpty(mRevQueue, fillRevQueueMethod);
    }


    private boolean newNoSpaced() {
        return queueNotEmpty(mNewQueue, fillNewQueueMethod, true);
    }


    @SuppressWarnings("unused")
    private void _requeueCard(Card card, boolean oldIsRev) {
        int newType = 0;
        // try {
        if (card.getReps() == 1) {
            if (mNewFromCache) {
                // Fetched from spaced cache
                newType = 2;
                ArrayList<Long> cards = mSpacedCards.remove().getCards();
                // Reschedule the siblings
                if (cards.size() > 1) {
                    cards.remove(0);
                    mSpacedCards.addLast(new SpacedCardsItem(Utils.now() + mNewSpacing, cards));
                }
            } else {
                // Fetched from normal queue
                newType = 1;
                mNewQueue.removeLast();
            }
        } else if (!oldIsRev) {
            mFailedQueue.removeLast();
        } else {
            mRevQueue.removeLast();
        }
        // } catch (Exception e) {
        // throw new RuntimeException("requeueCard() failed. Counts: " +
        // mFailedSoonCount + " " + mRevCount + " " + mNewCountToday + ", Queue: " +
        // mFailedQueue.size() + " " + mRevQueue.size() + " " + mNewQueue.size() + ", Card info: " +
        // card.getReps() + " " + card.isRev() + " " + oldIsRev);
        // }
    }


    private String revOrder() {
        return revOrderStrings[mRevCardOrder];
    }


    private String newOrder() {
        return newOrderStrings[mNewCardOrder];
    }


    // Rebuild the type cache. Only necessary on upgrade.
    private void rebuildTypes() {
        getDB().getDatabase().execSQL(
                "UPDATE cards SET " + "type = (CASE " + "WHEN successive THEN 1 WHEN reps THEN 0 ELSE 2 END), "
                        + "relativeDelay = (CASE " + "WHEN successive THEN 1 WHEN reps THEN 0 ELSE 2 END) "
                        + "WHERE type >= 0");
        // old-style suspended cards
        getDB().getDatabase().execSQL("UPDATE cards SET type = type - 3 WHERE priority = -3 AND type >= 0");
    }


    @SuppressWarnings("unused")
    private int _cardQueue(Card card) {
        return cardType(card);
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


    public void reset() {
        // Setup global/daily stats
        mGlobalStats = Stats.globalStats(this);
        mDailyStats = Stats.dailyStats(this);
        // Recheck counts
        rebuildCounts();
        // Empty queues; will be refilled by getCard()
        mFailedQueue.clear();
        mRevQueue.clear();
        mNewQueue.clear();
        mSpacedFacts.clear();
        // Determine new card distribution
        if (mNewCardSpacing == NEW_CARDS_DISTRIBUTE) {
            if (mNewCountToday != 0) {
                mNewCardModulus = (mNewCountToday + mRevCount) / mNewCountToday;
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
        // Recache css
        rebuildCSS();

        // Spacing for delayed cards - not to be confused with newCardSpacing above
        mNewSpacing = getFloat("newSpacing");
    }


    // Checks if the day has rolled over.
    private void checkDailyStats() {
        if (!Utils.genToday(mUtcOffset).toString().equals(mDailyStats.getDay().toString())) {
            mDailyStats = Stats.dailyStats(this);
        }
    }


    /*
     * Review early*****************************
     */

    public void setupReviewEarlyScheduler() {
        try {
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillRevEarlyQueue");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildRevEarlyCount");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("_onReviewEarlyFinished");
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
            card.setType(card.getType() + 6);
        }
    }


    private void resetAfterReviewEarly() {
        // Put temporarily suspended cards back into play. Caller must .reset()
        // FIXME: Can ignore priorities in the future (following libanki)
        ArrayList<Long> ids = getDB().queryColumn(Long.class,
                "SELECT id FROM cards WHERE type BETWEEN 6 AND 8 OR priority = -1", 0);

        if (!ids.isEmpty()) {
            updatePriorities(Utils.toPrimitive(ids));
            getDB().getDatabase().execSQL("UPDATE cards SET type = type -6 WHERE type BETWEEN 6 AND 8");
            flushMod();
        }
    }


    @SuppressWarnings("unused")
    private void _onReviewEarlyFinished() {
        // Clean up buried cards
        resetAfterReviewEarly();
        // And go back to regular scheduler
        setupStandardScheduler();
    }


    @SuppressWarnings("unused")
    private void _rebuildRevEarlyCount() {
        // In the future it would be nice to skip the first x days of due cards

        mRevCount = (int) getDB().queryScalar(cardLimit("revActive", "revInactive", String.format(Utils.ENGLISH_LOCALE, 
                        "SELECT count() FROM cards c WHERE type = 1 AND combinedDue > %f", mDueCutoff)));
    }


    @SuppressWarnings("unused")
    private void _fillRevEarlyQueue() {
        if ((mRevCount != 0) && mRevQueue.isEmpty()) {
            Cursor cur = null;
            try {
                cur = getDB().getDatabase().rawQuery(cardLimit("revActive", "revInactive", String.format(
                                Utils.ENGLISH_LOCALE,
                                "SELECT id, factId, combinedDue FROM cards c WHERE type = 1 AND combinedDue > %f " +
                                "ORDER BY combinedDue LIMIT %d", mDueCutoff, mQueueLimit)), null);
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
        mNewCount = (int) getDB().queryScalar(
                cardLimit("newActive", "newInactive", String.format(Utils.ENGLISH_LOCALE,
                        "SELECT count(*) FROM cards c WHERE type = 2 AND combinedDue < %f", mDueCutoff)));
        mSpacedCards.clear();
    }


    @SuppressWarnings("unused")
    private void _updateLearnMoreCountToday() {
        mNewCountToday = mNewCount;
    }


    /*
     * Cramming*****************************
     */

    public void setupCramScheduler(String[] active, String order) {
        try {
            getCardIdMethod = Deck.class.getDeclaredMethod("_getCramCardId", boolean.class);
            mActiveCramTags = active;
            mCramOrder = order;
            rebuildFailedCountMethod = Deck.class.getDeclaredMethod("_rebuildFailedCramCount");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildCramCount");
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildNewCramCount");
            fillFailedQueueMethod = Deck.class.getDeclaredMethod("_fillFailedCramQueue");
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillCramQueue");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
            mFailedCramQueue.clear();
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
    private void _answerCramCard(Card card, int ease) {
        _answerCard(card, ease);
        if (ease == 1) {
            mFailedCramQueue.addFirst(new QueueItem(card.getId(), card.getFactId()));
        }
    }


    @SuppressWarnings("unused")
    private long _getCramCardId(boolean check) {
        checkDailyStats();
        fillQueues();

        if ((mFailedCardMax != 0) && (mFailedSoonCount >= mFailedCardMax)) {
            return ((QueueItem) mFailedQueue.getLast()).getCardID();
        }
        // Card due for review?
        if (revNoSpaced()) {
            return ((QueueItem) mRevQueue.getLast()).getCardID();
        }
        if (!mFailedQueue.isEmpty()) {
            return ((QueueItem) mFailedQueue.getLast()).getCardID();
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
            mFailedCramQueue.removeLast();
        }
    }


    @SuppressWarnings("unused")
    private void _rebuildNewCramCount() {
        mNewCount = 0;
        mNewCountToday = 0;
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
                Log.i(AnkiDroidApp.TAG, "fill cram queue: " + mActiveCramTags + " " + mCramOrder + " " + mQueueLimit);
                String sql = "SELECT id, factId FROM cards c WHERE type BETWEEN 0 AND 2 ORDER BY " + mCramOrder
                        + " LIMIT " + mQueueLimit;
                sql = cardLimit(mActiveCramTags, null, sql);
                Log.i(AnkiDroidApp.TAG, "SQL: " + sql);
                cur = getDB().getDatabase().rawQuery(sql, null);
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
        mRevCount = (int) getDB().queryScalar(
                cardLimit(mActiveCramTags, null, "SELECT count(*) FROM cards c WHERE type BETWEEN 0 AND 2"));
    }


    @SuppressWarnings("unused")
    private void _rebuildFailedCramCount() {
        mFailedSoonCount = mFailedCramQueue.size();
    }


    @SuppressWarnings("unused")
    private void _fillFailedCramQueue() {
        mFailedQueue = mFailedCramQueue;
    }


    @SuppressWarnings("unused")
    private void _spaceCramCards(Card card) {
        mSpacedFacts.put(card.getFactId(), Utils.now() + mNewSpacing);
    }


    @SuppressWarnings("unused")
    private void _cramPreSave(Card card, int ease) {
        // prevent it from appearing in next queue fill
        card.setType(card.getType() + 6);
    }


    private void setModified() {
        mModified = Utils.now();
    }


    public void setModified(double mod) {
        mModified = mod;
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
        values.put("newCount", mNewCount);
        values.put("revCardOrder", mRevCardOrder);

        getDB().getDatabase().update("decks", values, "id = " + mId, null);
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
     * @return the newCountToday
     */
    public int getNewCountToday() {
        return mNewCountToday;
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
        // 4am
        Calendar cal = Calendar.getInstance();
        mUtcOffset = 4 * 60 * 60 - (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 1000;
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


    public int getVersion() {
        return mVersion;
    }


    /*
     * Getting the next card*****************************
     */

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


    // Refreshes the current card and returns it (used when editing cards)
    public Card getCurrentCard() {
        return cardFromId(mCurrentCardId);
    }


    /**
     * Return the next due card Id, or 0
     * 
     * @param check Check for expired, or new day rollover
     * @return The Id of the next card, or 0 in case of error
     */
    @SuppressWarnings("unused")
    private long _getCardId(boolean check) {
        checkDailyStats();
        fillQueues();
        updateNewCountToday();
        if (!mFailedQueue.isEmpty()) {
            // Failed card due?
            if (mDelay0 != 0l) {
                if ((long) ((QueueItem) mFailedQueue.getLast()).getDue() + mDelay0 < System.currentTimeMillis() / 1000) {
                    return mFailedQueue.getLast().getCardID();
                }
            }
            // Failed card queue too big?
            if ((mFailedCardMax != 0) && (mFailedSoonCount >= mFailedCardMax)) {
                return mFailedQueue.getLast().getCardID();
            }
        }
        // Distribute new cards?
        if (newNoSpaced() && timeForNewCard()) {
            long id = getNewCard();
            if (id != 0L) {
                return id;
            }
        }
        // Card due for review?
        if (revNoSpaced()) {
            return mRevQueue.getLast().getCardID();
        }
        // New cards left?
        if (mNewCountToday != 0) {
            return getNewCard();
        }
        if (check) {
            // Check for expired cards, or new day rollover
            updateCutoff();
            reset();
            return getCardId(false);
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
        return 0l;
    }


    /*
     * Get card: helper functions*****************************
     */

    @SuppressWarnings("unused")
    private boolean _timeForNewCard() {
        // True if it's time to display a new card when distributing.
        if (mNewCountToday == 0) {
            return false;
        }
        if (mNewCardSpacing == NEW_CARDS_LAST) {
            return false;
        }
        if (mNewCardSpacing == NEW_CARDS_FIRST) {
            return true;
        }
        // Force review if there are very high priority cards
        try {
            if (!mRevQueue.isEmpty()) {
                if (getDB().queryScalar(
                        "SELECT 1 FROM cards WHERE id = " + mRevQueue.getLast().getCardID() + " AND priority = 4") == 1) {
                    return false;
                }
            }
        } catch (Exception e) {
            // No result from query.
        }
        if (mNewCardModulus != 0) {
            return (mDailyStats.getReps() % mNewCardModulus == 0);
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


    private boolean showFailedLast() {
        return ((mCollapseTime != 0.0) || (mDelay0 == 0));
    }


    /**
     * Given a card ID, return a card and start the card timer.
     * 
     * @param id The ID of the card to be returned
     */

    public Card cardFromId(long id) {
        if (id == 0) {
            return null;
        }
        Card card = new Card(this);
        boolean result = card.fromDB(id);

        if (!result) {
            return null;
        }
        card.mDeck = this;
        card.genFuzz();
        card.startTimer();
        return card;
    }


    // TODO: The real methods to update cards on Anki should be implemented instead of this
    public void updateAllCards() {
        updateAllCardsFromPosition(0, Long.MAX_VALUE);
    }


    public long updateAllCardsFromPosition(long numUpdatedCards, long limitCards) {
        // TODO: Cache this query, order by FactId, Id
        Cursor cursor = null;
        try {
            cursor = getDB().getDatabase().rawQuery(
                    "SELECT id, factId " + "FROM cards " + "ORDER BY factId, id " + "LIMIT " + limitCards + " OFFSET "
                            + numUpdatedCards, null);

            getDB().getDatabase().beginTransaction();
            while (cursor.moveToNext()) {
                // Get card
                Card card = new Card(this);
                card.fromDB(cursor.getLong(0));
                Log.i(AnkiDroidApp.TAG, "Card id = " + card.getId() + ", numUpdatedCards = " + numUpdatedCards);

                // Load tags
                card.loadTags();

                // Get the related fact
                Fact fact = card.getFact();
                // Log.i(AnkiDroidApp.TAG, "Fact id = " + fact.id);

                // Generate the question and answer for this card and update it
                HashMap<String, String> newQA = CardModel.formatQA(fact, card.getCardModel(), card.splitTags());
                card.setQuestion(newQA.get("question"));
                Log.i(AnkiDroidApp.TAG, "Question = " + card.getQuestion());
                card.setAnswer(newQA.get("answer"));
                Log.i(AnkiDroidApp.TAG, "Answer = " + card.getAnswer());

                card.updateQAfields();

                numUpdatedCards++;

            }
            getDB().getDatabase().setTransactionSuccessful();
        } finally {
            getDB().getDatabase().endTransaction();
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        return numUpdatedCards;
    }


    /*
     * Answering a card*****************************
     */

    public void _answerCard(Card card, int ease) {
        Log.i(AnkiDroidApp.TAG, "answerCard");
        String undoName = "Answer Card";
        setUndoStart(undoName, card.getId());
        double now = Utils.now();

        // Old state
        String oldState = card.getState();
        int oldQueue = cardQueue(card);
        double lastDelaySecs = Utils.now() - card.getCombinedDue();
        double lastDelay = lastDelaySecs / 86400.0;
        boolean oldIsRev = card.isRev();

        // update card details
        double last = card.getInterval();
        card.setInterval(nextInterval(card, ease));
        if (lastDelay >= 0) {
            card.setLastInterval(last); // keep last interval if reviewing early
        }
        if (!card.isNew()) {
            card.setLastDue(card.getDue()); // only update if card was not new
        }
        card.setDue(nextDue(card, ease, oldState));
        card.setIsDue(0);
        card.setLastFactor(card.getFactor());
        card.setSpaceUntil(0);
        if (lastDelay >= 0) {
            card.updateFactor(ease, mAverageFactor); // don't update factor if learning ahead
        }

        // Spacing
        spaceCards(card);
        // Adjust counts for current card
        if (ease == 1) {
            if (card.getDue() < mFailedCutoff) {
                mFailedSoonCount += 1;
            }
        }
        if (oldQueue == 0) {
            mFailedSoonCount -= 1;
        } else if (oldQueue == 1) {
            mRevCount -= 1;
        } else {
            mNewCount -= 1;
        }

        // card stats
        card.updateStats(ease, oldState);
        // Update type & ensure past cutoff
        card.setType(cardType(card));
        card.setRelativeDelay(card.getType());
        if (ease != 1) {
            card.setDue(Math.max(card.getDue(), mDueCutoff + 1));
        }

        // Allow custom schedulers to munge the card
        if (answerPreSaveMethod != null) {
            answerPreSave(card, ease);
        }

        // Save
        card.setCombinedDue(card.getDue());
        card.toDB();

        // global/daily stats
        Stats.updateAllStats(mGlobalStats, mDailyStats, card, ease, oldState);

        // review history
        CardHistoryEntry entry = new CardHistoryEntry(this, card, ease, lastDelay);
        entry.writeSQL();
        mModified = now;

        // Remove form queue
        requeueCard(card, oldIsRev);

        // Leech handling - we need to do this after the queue, as it may cause a reset
        if (isLeech(card)) {
            Log.i(AnkiDroidApp.TAG, "card is leech!");
            handleLeech(card);
        }
        setUndoEnd(undoName);
    }


    @SuppressWarnings("unused")
    private void _spaceCards(Card card) {
        // Update new counts
        double _new = Utils.now() + mNewSpacing;
        // Space cards
        getDB().getDatabase().execSQL(
                String.format(Utils.ENGLISH_LOCALE, "UPDATE cards SET " + "combinedDue = (CASE "
                        + "WHEN type = 1 THEN %f " + "WHEN type = 2 THEN %f " + "END), " + "modified = %f, isDue = 0 "
                        + "WHERE id != %d AND factId = %d " + "AND combinedDue < %f " + "AND type BETWEEN 1 AND 2",
                        mDueCutoff, _new, Utils.now(), card.getId(), card.getFactId(), mDueCutoff));
        // Update local cache of seen facts
        mSpacedFacts.put(card.getFactId(), _new);
    }


    private boolean isLeech(Card card) {
        int no = card.getNoCount();
        int fmax = 0;
        try {
            fmax = getInt("leechFails");
        } catch (SQLException e) {
            // No leech threshold found in DeckVars
            return false;
        }
        Log.i(AnkiDroidApp.TAG, "leech handling: " + card.getSuccessive() + " successive fails and " + no
                + " total fails, threshold at " + fmax);
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
     * Interval management*********************************************************
     */

    public double nextInterval(Card card, int ease) {
        double delay = card.adjustedDelay(ease);
        return nextInterval(card, delay, ease);
    }


    private double nextInterval(Card card, double delay, int ease) {
        double interval = card.getInterval();
        double factor = card.getFactor();

        // if shown early and not failed
        if ((delay < 0) && card.isRev()) {
            // FIXME: From libanki: This should recreate lastInterval from interval /
            // lastFactor, or we lose delay information when reviewing early
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
                interval = mHardIntervalMin + ((double) Math.random()) * (mHardIntervalMax - mHardIntervalMin);
            } else if (ease == Card.EASE_MID) {
                interval = mMidIntervalMin + ((double) Math.random()) * (mMidIntervalMax - mMidIntervalMin);
            } else if (ease == Card.EASE_EASY) {
                interval = mEasyIntervalMin + ((double) Math.random()) * (mEasyIntervalMax - mEasyIntervalMin);
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
            double fuzz = 0.95 + ((double) Math.random()) * (1.05 - 0.95);
            interval *= fuzz;
        }
        interval = Math.min(interval, MAX_SCHEDULE_TIME);
        return interval;
    }


    private double nextDue(Card card, int ease, String oldState) {
        double due;
        if (ease == Card.EASE_FAILED) {
        	// 600 is a magic value which means no bonus, and is used to ease upgrades
            if (oldState.equals(Card.STATE_MATURE) && mDelay1 != 0 && mDelay1 != 600) {
                // user wants a bonus of 1+ days. put the failed cards at the
            	// start of the future day, so that failures that day will come
            	// after the waiting cards
            	return mFailedCutoff + (mDelay1 - 1) * 86400;
            } else {
                due = 0.0;
            }
        } else {
            due = card.getInterval() * 86400.0;
        }
        return (due + Utils.now());
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
                    "SELECT cards.id, facts.tags, models.tags, cardModels.name "
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
        t.addAll(getDB().queryColumn(String.class, "SELECT tags FROM models", 0));
        t.addAll(getDB().queryColumn(String.class, "SELECT name FROM cardModels", 0));
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
            getDB().getDatabase().execSQL("DELETE FROM cardTags WHERE cardId IN " + Utils.ids2str(cardIds));
            String fids = Utils.ids2str(Utils.toPrimitive(getDB().queryColumn(Long.class,
                    "SELECT factId FROM cards WHERE id IN " + Utils.ids2str(cardIds), 0)));
            Log.i(AnkiDroidApp.TAG, "updateCardTags fids: " + fids);
            tids = tagIds(allTags_("WHERE id IN " + fids));
            Log.i(AnkiDroidApp.TAG, "updateCardTags tids keys: "
                    + Arrays.toString(tids.keySet().toArray(new String[tids.size()])));
            Log.i(AnkiDroidApp.TAG, "updateCardTags tids values: "
                    + Arrays.toString(tids.values().toArray(new Long[tids.size()])));
            rows = splitTagsList("AND facts.id IN " + fids);
            Log.i(AnkiDroidApp.TAG, "updateCardTags rows keys: "
                    + Arrays.toString(rows.keySet().toArray(new Long[rows.size()])));
            for (List<String> l : rows.values()) {
                Log.i(AnkiDroidApp.TAG, "updateCardTags rows values: ");
                for (String v : l) {
                    Log.i(AnkiDroidApp.TAG, "updateCardTags row item: " + v);
                }
            }
        }

        ArrayList<HashMap<String, Long>> d = new ArrayList<HashMap<String, Long>>();

        for (Long id : rows.keySet()) {
            for (int src = 0; src < 3; src++) { // src represents the tag type, fact: 0, model: 1, template: 2
                for (String tag : Utils.parseTags(rows.get(id).get(src))) {
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
            getDB().getDatabase().execSQL(
                    "INSERT INTO cardTags (cardId, tagId, src) VALUES " + "(" + ditem.get("cardId") + ", "
                            + ditem.get("tagId") + ", " + ditem.get("src") + ")");
        }
        getDB().getDatabase().execSQL(
                "DELETE FROM tags WHERE priority = 2 AND id NOT IN " + "(SELECT DISTINCT tagId FROM cardTags)");
    }


    /*
     * Tags: adding/removing in bulk*********************************************************
     */

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
        String undoName = "Add Tag";
        setUndoStart(undoName);

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
                getDB().getDatabase().execSQL(
                        "update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                                + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()) + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = getDB().queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        ContentValues values = new ContentValues();

        for (String cardId : cardIdList) {
            try {
                // Check if the tag already exists
                getDB().queryScalar(
                        "SELECT id FROM cardTags WHERE cardId = " + cardId + " and tagId = " + tagId + " and src = "
                                + Card.TAGS_FACT);
            } catch (SQLException e) {
                values.put("cardId", cardId);
                values.put("tagId", tagId);
                values.put("src", String.valueOf(Card.TAGS_FACT));
                getDB().getDatabase().insert("cardTags", null, values);
            }
        }

        flushMod();
        setUndoEnd(undoName);
    }


    public void deleteTag(long factId, String tag) {
        String undoName = "Delete Tag";
        setUndoStart(undoName);
        long[] ids = new long[1];
        ids[0] = factId;
        deleteTag(ids, tag);
        setUndoEnd(undoName);
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
                getDB().getDatabase().execSQL(
                        "update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                                + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()) + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = getDB().queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        for (String cardId : cardIdList) {
            getDB().getDatabase().execSQL(
                    "DELETE FROM cardTags WHERE cardId = " + cardId + " and tagId = " + tagId + " and src = "
                            + Card.TAGS_FACT);
        }

        // delete unused tags from tags table
        try {
            getDB().queryScalar("select id from cardTags where tagId = " + tagId + " limit 1");
        } catch (SQLException e) {
            getDB().getDatabase().execSQL("delete from tags" + " where id = " + tagId);
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
        String undoName = "Suspend Card";
        if (ids.length == 1) {
            setUndoStart(undoName, ids[0]);        	
        } else {
        	setUndoStart(undoName);
        }
        getDB().getDatabase().execSQL(
                "UPDATE cards SET type = relativeDelay -3, priority = -3, modified = "
                        + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now())
                        + ", isDue = 0 WHERE type >= 0 AND id IN " + Utils.ids2str(ids));
        setUndoEnd(undoName);
        flushMod();
    }


    /**
     * Unsuspend cards in bulk. Caller must .reset()
     * 
     * @param ids List of card IDs of the cards that are to be unsuspended.
     */
    public void unsuspendCards(long[] ids) {
        getDB().getDatabase().execSQL(
                "UPDATE cards SET type = relativeDelay, priority = 0, " + "modified = "
                        + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()) + " WHERE type < 0 AND id IN "
                        + Utils.ids2str(ids));
        updatePriorities(ids);
        flushMod();
    }


    /**
     * Priorities
     *******************************/

    /**
     * Update all card priorities if changed. If partial is true, only updates cards with tags defined as priority low,
     * med or high in the deck, or with tags whose priority is set to 2 and they are not found in the priority tags of
     * the deck. If false, it updates all card priorities Caller must .reset()
     * 
     * @param partial Partial update (true) or not (false)
     * @param dirty Passed to updatePriorities(), if true it updates the modified field of the cards
     */
    public void updateAllPriorities() {
        updateAllPriorities(false, true);
    }


    public void updateAllPriorities(boolean partial) {
        updateAllPriorities(partial, true);
    }


    public void updateAllPriorities(boolean partial, boolean dirty) {
        HashMap<Long, Integer> newPriorities = updateTagPriorities();
        if (!partial) {
            newPriorities.clear();
            Cursor cur = null;
            try {
                cur = getDB().getDatabase().rawQuery("SELECT id, priority AS pri FROM tags", null);
                while (cur.moveToNext()) {
                    newPriorities.put(cur.getLong(0), cur.getInt(1));
                }
            } catch (SQLException e) {
                Log.e(AnkiDroidApp.TAG, "updateAllPriorities: Error while getting all tags: " + e.toString());
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            ArrayList<Long> cids = getDB().queryColumn(
                    Long.class,
                    "SELECT DISTINCT cardId FROM cardTags WHERE tagId in "
                            + Utils.ids2str(Utils.toPrimitive(newPriorities.keySet())), 0);
            updatePriorities(Utils.toPrimitive(cids), null, dirty);
        }
    }


    /**
     * Update priority setting on tags table
     */
    private HashMap<Long, Integer> updateTagPriorities() {
        // Make sure all priority tags exist
        for (String s : new String[] { mLowPriority, mMedPriority, mHighPriority }) {
            tagIds(Utils.parseTags(s));
        }

        HashMap<Long, Integer> newPriorities = new HashMap<Long, Integer>();
        Cursor cur = null;
        ArrayList<String> tagNames = null;
        ArrayList<Long> tagIdList = null;
        ArrayList<Integer> tagPriorities = null;
        try {
            tagNames = new ArrayList<String>();
            tagIdList = new ArrayList<Long>();
            tagPriorities = new ArrayList<Integer>();
            cur = getDB().getDatabase().rawQuery("SELECT tag, id, priority FROM tags", null);
            while (cur.moveToNext()) {
                tagNames.add(cur.getString(0).toLowerCase());
                tagIdList.add(cur.getLong(1));
                tagPriorities.add(cur.getInt(2));
            }
        } catch (SQLException e) {
            Log.e(AnkiDroidApp.TAG, "updateTagPriorities: Error while tag priorities: " + e.toString());
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        HashMap<String, Integer> typeAndPriorities = new HashMap<String, Integer>();
        typeAndPriorities.put(mLowPriority, 1);
        typeAndPriorities.put(mMedPriority, 3);
        typeAndPriorities.put(mHighPriority, 4);
        HashMap<String, Integer> up = new HashMap<String, Integer>();
        for (String type : typeAndPriorities.keySet()) {
            for (String tag : Utils.parseTags(type.toLowerCase())) {
                up.put(tag, typeAndPriorities.get(type));
            }
        }
        String tag = null;
        long tagId = 0l;
        for (int i = 0; i < tagNames.size(); i++) {
            tag = tagNames.get(i);
            tagId = tagIdList.get(i).longValue();
            if (up.containsKey(tag) && (up.get(tag).compareTo(tagPriorities.get(i)) == 0)) {
                newPriorities.put(tagId, up.get(tag));
            } else if ((!up.containsKey(tag)) && (tagPriorities.get(i).intValue() != 2)) {
                newPriorities.put(tagId, 2);
            } else {
                continue;
            }
            try {
                getDB().getDatabase().execSQL(
                        "UPDATE tags SET priority = " + newPriorities.get(tagId) + " WHERE id = " + tagId);
            } catch (SQLException e) {
                Log.e(AnkiDroidApp.TAG, "updatePriorities: Error while updating tag priorities for tag " + tag + ": "
                        + e.toString());
                continue;
            }
        }
        return newPriorities;
    }


    /**
     * Update priorities for cardIds in bulk. Caller must .reset().
     * 
     * @param cardIds List of card IDs identifying whose cards' priorities to update.
     * @param suspend List of tags. The cards from the above list that have those tags will be suspended.
     * @param dirty If true will update the modified value of each card handled.
     */
    private void updatePriorities(long[] cardIds) {
        updatePriorities(cardIds, null, true);
    }


    private void updatePriorities(long[] cardIds, String[] suspend) {
        updatePriorities(cardIds, suspend, true);
    }


    void updatePriorities(long[] cardIds, String[] suspend, boolean dirty) {
        Cursor cursor = null;
        Log.i(AnkiDroidApp.TAG, "updatePriorities - Updating priorities...");
        // Any tags to suspend
        if (suspend != null && suspend.length > 0) {
            long ids[] = Utils.toPrimitive(tagIds(suspend, false).values());
            getDB().getDatabase().execSQL("UPDATE tags SET priority = 0 WHERE id in " + Utils.ids2str(ids));
        }

        String limit = "";
        if (cardIds.length <= 1000) {
            limit = "and cardTags.cardId in " + Utils.ids2str(cardIds);
        }
        String query = "SELECT cardTags.cardId, CASE WHEN max(tags.priority) > 2 THEN max(tags.priority) "
                + "WHEN min(tags.priority) = 1 THEN 1 ELSE 2 END FROM cardTags,tags "
                + "WHERE cardTags.tagId = tags.id " + limit + " GROUP BY cardTags.cardId";
        try {
            cursor = getDB().getDatabase().rawQuery(query, null);
            if (cursor.moveToFirst()) {
                int len = cursor.getCount();
                long[][] cards = new long[len][2];
                for (int i = 0; i < len; i++) {
                    cards[i][0] = cursor.getLong(0);
                    cards[i][1] = cursor.getInt(1);
                }

                String extra = "";
                if (dirty) {
                    extra = ", modified = " + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now());
                }
                for (int pri = Card.PRIORITY_NONE; pri <= Card.PRIORITY_HIGH; pri++) {
                    int count = 0;
                    for (int i = 0; i < len; i++) {
                        if (cards[i][1] == pri) {
                            count++;
                        }
                    }
                    long[] cs = new long[count];
                    int j = 0;
                    for (int i = 0; i < len; i++) {
                        if (cards[i][1] == pri) {
                            cs[j] = cards[i][0];
                            j++;
                        }
                    }
                    // Catch review early & buried but not suspended cards
                    getDB().getDatabase().execSQL(
                            "UPDATE cards " + "SET priority = " + pri + extra + " WHERE id in " + Utils.ids2str(cs)
                                    + " and " + "priority != " + pri + " and " + "priority >= -2");
                }
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }


    /*
     * Counts related to due cards *********************************************************
     */

    private int newCardsDoneToday() {
        return mDailyStats.getNewCardsCount();
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
            getDB().getDatabase().execSQL("DELETE FROM cards WHERE id in " + idsString);

            // Note deleted cards
            String sqlInsert = "INSERT INTO cardsDeleted values (?," + String.format(Utils.ENGLISH_LOCALE, "%f", now)
                    + ")";
            SQLiteStatement statement = getDB().getDatabase().compileStatement(sqlInsert);
            for (String id : ids) {
                statement.bindString(1, id);
                statement.executeInsert();
            }
            statement.close();

            // Gather affected tags (before we delete the corresponding cardTags)
            ArrayList<String> tags = getDB().queryColumn(String.class,
                    "SELECT tagId FROM cardTags WHERE cardId in " + idsString, 0);

            // Delete cardTags
            getDB().getDatabase().execSQL("DELETE FROM cardTags WHERE cardId in " + idsString);

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

            // Delete unused tags
            getDB().getDatabase().execSQL(
                    "DELETE FROM tags WHERE id in " + Utils.ids2str(unusedTags) + " and priority = "
                            + Card.PRIORITY_NORMAL);

            // Remove any dangling fact
            deleteDanglingFacts();
            flushMod();
        }
    }


    /*
     * Facts CRUD*********************************************************
     */

    /**
     * Add a fact to the deck. Return list of new cards
     */
    public Fact addFact(Fact fact, TreeMap<Long, CardModel> cardModels) {
        return addFact(fact, cardModels, true);
    }


    public Fact addFact(Fact fact, TreeMap<Long, CardModel> cardModels, boolean reset) {
        // TODO: assert fact is Valid
        // TODO: assert fact is Unique
        double now = Utils.now();
        // add fact to fact table
        ContentValues values = new ContentValues();
        values.put("id", fact.getId());
        values.put("modelId", fact.getModelId());
        values.put("created", now);
        values.put("modified", now);
        values.put("tags", "");
        values.put("spaceUntil", 0);
        getDB().getDatabase().insert("facts", null, values);

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
            AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().insert("fields", null, values);
        }

        ArrayList<Long> newCardIds = new ArrayList<Long>();
        for (Map.Entry<Long, CardModel> entry : cardModels.entrySet()) {
            CardModel cardModel = entry.getValue();
            Card newCard = new Card(this, fact, cardModel, Utils.now());
            newCard.addToDb();
            newCardIds.add(newCard.getId());
            mCardCount++;
            mNewCount++;
            Log.i(AnkiDroidApp.TAG, entry.getKey().toString());
        }
        commitToDB();
        // TODO: code related to random in newCardOrder

        // Update card q/a
        fact.setModified(true, this);
        updateFactTags(new long[] { fact.getId() });

        // This will call reset() which will update counts
        updatePriorities(Utils.toPrimitive(newCardIds));

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
            getDB().getDatabase().execSQL("DELETE FROM facts WHERE id in " + idsString);
            Log.i(AnkiDroidApp.TAG, "DELETE FROM fields WHERE factId in " + idsString);
            getDB().getDatabase().execSQL("DELETE FROM fields WHERE factId in " + idsString);
            String sqlInsert = "INSERT INTO factsDeleted VALUES(?," + String.format(Utils.ENGLISH_LOCALE, "%f", now)
                    + ")";
            SQLiteStatement statement = getDB().getDatabase().compileStatement(sqlInsert);
            for (String id : ids) {
                Log.i(AnkiDroidApp.TAG, "inserting into factsDeleted");
                statement.bindString(1, id);
                statement.executeInsert();
            }
            statement.close();
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
            getDB().getDatabase().execSQL("DELETE FROM models WHERE id = " + id);

            // Note deleted model
            ContentValues values = new ContentValues();
            values.put("modelId", id);
            values.put("deletedTime", Utils.now());
            getDB().getDatabase().insert("modelsDeleted", null, values);

            flushMod();
        }
    }


    public void deleteFieldModel(String modelId, String fieldModelId) {
        Log.i(AnkiDroidApp.TAG, "deleteFieldModel, modelId = " + modelId + ", fieldModelId = " + fieldModelId);

        // Delete field model
        getDB().getDatabase().execSQL("DELETE FROM fields WHERE fieldModel = " + fieldModelId);

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
        getDB().getDatabase().execSQL(
                "UPDATE models SET modified = " + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now())
                        + " WHERE id = " + modelId);

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
        getDB().getDatabase().execSQL("DELETE FROM cardModels WHERE id = " + cardModelId);

        // Note the model like modified (TODO: We should use the object model instead handling the DB directly)
        getDB().getDatabase().execSQL(
                "UPDATE models SET modified = " + String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now())
                        + " WHERE id = " + modelId);

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
        private Long mStart;
        private Long mEnd;
        private Long mCardId;


        UndoRow(String name, Long cardId, Long start, Long end) {
            mName = name;
            mCardId = cardId;
            mStart = start;
            mEnd = end;
        }
    }


    private void initUndo() {
        mUndoStack = new Stack<UndoRow>();
        mRedoStack = new Stack<UndoRow>();
        mUndoEnabled = true;

        try {
            getDB().getDatabase()
                    .execSQL("CREATE TEMPORARY TABLE undoLog (seq INTEGER PRIMARY KEY NOT NULL, sql TEXT)");
        } catch (SQLException e) {
            /* Temporary table may still be present if the DB has not been closed */
            Log.i(AnkiDroidApp.TAG, "Failed to create temporary table: " + e.getMessage());
        }

        ArrayList<String> tables = getDB().queryColumn(String.class,
                "SELECT name FROM sqlite_master WHERE type = 'table'", 0);
        Iterator<String> iter = tables.iterator();
        while (iter.hasNext()) {
            String table = iter.next();
            if (table.equals("undoLog") || table.equals("sqlite_stat1")) {
                continue;
            }
            ArrayList<String> columns = getDB().queryColumn(String.class, "PRAGMA TABLE_INFO(" + table + ")", 1);
            // Insert trigger
            StringBuilder sql = new StringBuilder(512);
            sql.append("CREATE TEMP TRIGGER _undo_%s_it AFTER INSERT ON %s BEGIN INSERT INTO undoLog VALUES ").append(
                    "(null, 'DELETE FROM %s WHERE rowid = ' || new.rowid); END");
            getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, sql.toString(), table, table, table));
            // Update trigger
            sql = new StringBuilder(512);
            sql.append(String.format(Utils.ENGLISH_LOCALE, "CREATE TEMP TRIGGER _undo_%s_ut AFTER UPDATE ON %s BEGIN "
                    + "INSERT INTO undoLog VALUES (null, 'UPDATE %s ", table, table, table));
            String sep = "SET ";
            for (String column : columns) {
                if (column.equals("unique")) {
                    continue;
                }
                sql.append(String.format(Utils.ENGLISH_LOCALE, "%s%s=' || quote(old.%s) || '", sep, column, column));
                sep = ",";
            }
            sql.append(" WHERE rowid = ' || old.rowid); END");
            getDB().getDatabase().execSQL(sql.toString());
            // Delete trigger
            sql = new StringBuilder(512);
            sql.append(String.format(Utils.ENGLISH_LOCALE, "CREATE TEMP TRIGGER _undo_%s_dt BEFORE DELETE ON %s BEGIN "
                    + "INSERT INTO undoLog VALUES (null, 'INSERT INTO %s (rowid", table, table, table));
            for (String column : columns) {
                sql.append(String.format(Utils.ENGLISH_LOCALE, ",\"%s\"", column));
            }
            sql.append(") VALUES (' || old.rowid ||'");
            for (String column : columns) {
                if (column.equals("unique")) {
                    sql.append(",1");
                    continue;
                }
                sql.append(String.format(Utils.ENGLISH_LOCALE, ", ' || quote(old.%s) ||'", column));
            }
            sql.append(")'); END");
            getDB().getDatabase().execSQL(sql.toString());
        }
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
        try {
            getDB().getDatabase().execSQL("delete from undoLog");
        } catch (SQLException e) {

        }
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
        commitToDB();
        if (merge && !mUndoStack.isEmpty()) {
            if ((mUndoStack.peek() != null) && (mUndoStack.peek().mName.equals(name))) {
                // libanki: merge with last entry?
                return;
            }
        }
        mUndoStack.push(new UndoRow(name, cardId, latestUndoRow(), null));
    }


    public void setUndoEnd(String name) {
        if (!mUndoEnabled) {
            return;
        }
        commitToDB();
        long end = latestUndoRow();
        while (mUndoStack.peek() == null) {
            mUndoStack.pop(); // Strip off barrier
        }
        UndoRow row = mUndoStack.peek();
        row.mEnd = end;
        if (row.mStart == row.mEnd) {
            mUndoStack.pop();
        } else {
            mRedoStack.clear();
        }
    }


    private long latestUndoRow() {
        long result = 0;
        try {
            result = getDB().queryScalar("SELECT MAX(rowid) FROM undoLog");
        } catch (SQLException e) {
            Log.i(AnkiDroidApp.TAG, e.getMessage());
        }
        return result;
    }


    private long undoredo(Stack<UndoRow> src, Stack<UndoRow> dst, long oldCardId) {

        UndoRow row;
        commitToDB();
        while (true) {
            row = src.pop();
            if (row != null) {
                break;
            }
        }
        Long start = row.mStart;
        Long end = row.mEnd;
        if (end == null) {
            end = latestUndoRow();
        }
        ArrayList<String> sql = getDB().queryColumn(
                String.class,
                String.format(Utils.ENGLISH_LOCALE, "SELECT sql FROM undoLog " + "WHERE seq > %d and seq <= %d "
                        + "ORDER BY seq DESC", start, end), 0);
        Long newstart = latestUndoRow();
        for (String s : sql) {
            getDB().getDatabase().execSQL(s);
        }

        Long newend = latestUndoRow();
        dst.push(new UndoRow(row.mName, oldCardId, newstart, newend));
        return row.mCardId;
    }


    /**
     * Undo the last action(s). Caller must .reset()
     */
    public long undo(long oldCardId) {
        long cardId = 0;
    	if (!mUndoStack.isEmpty()) {
            cardId = undoredo(mUndoStack, mRedoStack, oldCardId);
            commitToDB();
            reset();
        }
        return cardId;
    }


    /**
     * Redo the last action(s). Caller must .reset()
     */
    public long redo(long oldCardId) {
        long cardId = 0;
        if (!mRedoStack.isEmpty()) {
        	cardId = undoredo(mRedoStack, mUndoStack, oldCardId);
            commitToDB();
            reset();
        }
        return cardId;
    }


    /*
     * Dynamic indices*********************************************************
     */

    private void updateDynamicIndices() {
        Log.i(AnkiDroidApp.TAG, "updateDynamicIndices - Updating indices...");
        HashMap<String, String> indices = new HashMap<String, String>();
        indices.put("intervalDesc", "(type, priority desc, interval desc, factId, combinedDue)");
        indices.put("intervalAsc", "(type, priority desc, interval, factId, combinedDue)");
        indices.put("randomOrder", "(type, priority desc, factId, ordinal, combinedDue)");
        indices.put("dueAsc", "(type, priority desc, due, factId, combinedDue)");
        indices.put("dueDesc", "(type, priority desc, due desc, factId, combinedDue)");

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
            indexName = "ix_cards_" + entry.getKey() + "2";
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
                // Leave old indices for older clients
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
            bundledDeck.put("newCount", mNewCount);
            bundledDeck.put("newCountToday", mNewCountToday);
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
            mMediaPrefix = deckPayload.getString("mediaPrefix");
            mMidIntervalMax = deckPayload.getDouble("midIntervalMax");
            mMidIntervalMin = deckPayload.getDouble("midIntervalMin");
            mModified = deckPayload.getDouble("modified");
            // needLock
            mNewCardOrder = deckPayload.getInt("newCardOrder");
            mNewCardSpacing = deckPayload.getInt("newCardSpacing");
            mNewCardsPerDay = deckPayload.getInt("newCardsPerDay");
            mNewCount = deckPayload.getInt("newCount");
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
                id = getDB().getDatabase().insert("tags", null, value);
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

}
