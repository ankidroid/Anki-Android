/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

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
import java.util.Locale;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Map.Entry;

/**
 * A deck stores all of the cards and scheduling information. It is saved in a file with a name ending in .anki
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Deck
 */
public class Deck {

    public static final int CARD_TYPE_FAILED = 0;
    public static final int CARD_TYPE_REV = 1;
    public static final int CARD_TYPE_NEW = 2;

    /**
     * Tag for logging messages
     */
    private static String TAG = "AnkiDroid";

    /**
     * Priorities Auto priorities - High = 4 - Medium = 3 - Normal = 2 - Low = 1 - None = 0 Manual priorities - Review
     * early = -1 - Buried = -2 - Suspended = -3
     **/

    // Rest
    private static final int DECK_VERSION = 50;
    
    private static final int MATURE_THRESHOLD = 21;

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

    private static final double factorFour = 1.3;
    private static final double initialFactor = 2.5;
    private static final double minimumAverage = 1.7;
    private static final double maxScheduleTime = 36500.0;

    // Used to format doubles with English's decimal separator system
    private static final Locale ENGLISH_LOCALE = new Locale("en_US");

    // Card order strings for building SQL statements
    private static final String revOrderStrings[] = { "priority desc, interval desc", "priority desc, interval",
            "priority desc, combinedDue", "priority desc, factId, ordinal" };
    private static final String newOrderStrings[] = { "priority desc, combinedDue", "priority desc, combinedDue",
            "priority desc, combinedDue desc" };

    // BEGIN: SQL table columns
    long id;

    double created;

    public double modified;

    String description;

    int version;

    long currentModelId;

    String syncName;

    double lastSync;

    // Scheduling
    // Initial intervals
    double hardIntervalMin;

    double hardIntervalMax;

    double midIntervalMin;

    double midIntervalMax;

    double easyIntervalMin;

    double easyIntervalMax;

    // Delays on failure
    long delay0;

    // Days to delay mature fails
    long delay1;

    double delay2;

    // Collapsing future cards
    double collapseTime;

    // Priorities and postponing
    String highPriority;

    String medPriority;

    String lowPriority;

    String suspended; // obsolete in libanki 1.1

    // 0 is random, 1 is by input date
    private int newCardOrder;

    // When to show new cards
    private int newCardSpacing;

    // Limit the number of failed cards in play
    int failedCardMax;

    // Number of new cards to show per day
    private int newCardsPerDay;

    // Currently unused
    private long sessionRepLimit;

    private long sessionTimeLimit;

    // Stats offset
    double utcOffset;

    // Count cache
    int cardCount;

    int factCount;

    int failedNowCount; // obsolete in libanki 1.1

    int failedSoonCount;

    int revCount;

    int newCount;

    // Review order
    private int revCardOrder;

    // END: SQL table columns

    // BEGIN JOINed variables
    // Model currentModel; // Deck.currentModelId = Model.id
    // ArrayList<Model> models; // Deck.id = Model.deckId
    // END JOINed variables

    double averageFactor;

    int newCardModulus;

    int newCountToday;

    public double lastLoaded;

    boolean newEarly;

    boolean reviewEarly;

    double dueCutoff;

    String scheduler;

    // Queues
    LinkedList<QueueItem> failedQueue;
    LinkedList<QueueItem> revQueue;
    LinkedList<QueueItem> newQueue;
    LinkedList<QueueItem> failedCramQueue;
    HashMap<Long, Double> spacedFacts;
    int queueLimit;

    // Cramming
    private String[] activeCramTags;
    private String cramOrder;

    // Not in Anki Desktop
    String deckPath;

    String deckName;

    private Stats globalStats;

    private Stats dailyStats;

    private Card currentCard;

    /**
     * Undo/Redo variables.
     */
    Stack<UndoRow> undoStack;

    Stack<UndoRow> redoStack;

    boolean undoEnabled = false;


    public static synchronized Deck openDeck(String path) throws SQLException {
        Deck deck = null;
        Cursor cursor = null;
        Log.i(TAG, "openDeck - Opening database " + path);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(path);

        try {
            // Read in deck table columns
            cursor = ankiDB.database.rawQuery("SELECT *" + " FROM decks" + " LIMIT 1", null);

            if (!cursor.moveToFirst()) {
                return null;
            }

            deck = new Deck();

            deck.id = cursor.getLong(0);
            deck.created = cursor.getDouble(1);
            deck.modified = cursor.getDouble(2);
            deck.description = cursor.getString(3);
            deck.version = cursor.getInt(4);
            deck.currentModelId = cursor.getLong(5);
            deck.syncName = cursor.getString(6);
            deck.lastSync = cursor.getDouble(7);
            deck.hardIntervalMin = cursor.getDouble(8);
            deck.hardIntervalMax = cursor.getDouble(9);
            deck.midIntervalMin = cursor.getDouble(10);
            deck.midIntervalMax = cursor.getDouble(11);
            deck.easyIntervalMin = cursor.getDouble(12);
            deck.easyIntervalMax = cursor.getDouble(13);
            deck.delay0 = cursor.getLong(14);
            deck.delay1 = cursor.getLong(15);
            deck.delay2 = cursor.getDouble(16);
            deck.collapseTime = cursor.getDouble(17);
            deck.highPriority = cursor.getString(18);
            deck.medPriority = cursor.getString(19);
            deck.lowPriority = cursor.getString(20);
            deck.suspended = cursor.getString(21);
            deck.newCardOrder = cursor.getInt(22);
            deck.newCardSpacing = cursor.getInt(23);
            deck.failedCardMax = cursor.getInt(24);
            deck.newCardsPerDay = cursor.getInt(25);
            deck.sessionRepLimit = cursor.getInt(26);
            deck.sessionTimeLimit = cursor.getInt(27);
            deck.utcOffset = cursor.getDouble(28);
            deck.cardCount = cursor.getInt(29);
            deck.factCount = cursor.getInt(30);
            deck.failedNowCount = cursor.getInt(31);
            deck.failedSoonCount = cursor.getInt(32);
            deck.revCount = cursor.getInt(33);
            deck.newCount = cursor.getInt(34);
            deck.revCardOrder = cursor.getInt(35);

            Log.i(TAG, "openDeck - Read " + cursor.getColumnCount() + " columns from decks table.");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Log.i(TAG, String.format(ENGLISH_LOCALE, "openDeck - modified: %f currentTime: %f", deck.modified, System
                .currentTimeMillis() / 1000.0));

        // Initialise queues
        deck.failedQueue = new LinkedList<QueueItem>();
        deck.revQueue = new LinkedList<QueueItem>();
        deck.newQueue = new LinkedList<QueueItem>();
        deck.failedCramQueue = new LinkedList<QueueItem>();
        deck.spacedFacts = new HashMap<Long, Double>();

        deck.deckPath = path;
        deck.deckName = (new File(path)).getName().replace(".anki", "");
        
        deck.initVars();
        
        // Upgrade to latest version
        deck.upgradeDeck();

        double oldMod = deck.modified;

        // Ensure necessary indices are available
        deck.updateDynamicIndices();

        // FIXME: Temporary code for upgrade - ensure cards suspended on older clients are recognized
        // Ensure cards suspended on older clients are recognized
        deck.getDB().database.execSQL("UPDATE cards SET type = type - 3 WHERE type BETWEEN 0 AND 2 AND priority = -3");

        // Ensure hard scheduling over a day if per day
        if (deck.getBool("perDay")) {
            deck.hardIntervalMin = Math.max(1.0, deck.hardIntervalMin);
            deck.hardIntervalMax = Math.max(1.1, deck.hardIntervalMax);
        }
        
        // - New delay1 handling
        if (deck.delay0 == deck.delay1) {
            deck.delay1 = 0l;
        } else if (deck.delay1 >= 28800l) {
            deck.delay1 = 1l;
        } else {
            deck.delay1 = 0l;
        }

        ArrayList<Long> ids = new ArrayList<Long>();
        // Unsuspend buried/rev early - can remove priorities in the future
        ids = deck.getDB().queryColumn(Long.class,
                "SELECT id FROM cards WHERE type > 2 OR (priority BETWEEN -2 AND -1)", 0);
        if (!ids.isEmpty()) {
            deck.updatePriorities(Utils.toPrimitive(ids));
            deck.getDB().database.execSQL("UPDATE cards SET type = relativeDelay WHERE type > 2");
            // Save deck to database
            deck.commitToDB();
        }

        // Determine starting factor for new cards
        Cursor cur = null;
        try {
            cur = deck.getDB().database.rawQuery("SELECT avg(factor) FROM cards WHERE type = 1", null);
            if (cur.moveToNext()) {
                deck.averageFactor = cur.getDouble(0);
            } else {
                deck.averageFactor = Deck.initialFactor;
            }
            if (deck.averageFactor == 0.0) {
                deck.averageFactor = Deck.initialFactor;
            }
        } catch (Exception e) {
            deck.averageFactor = Deck.initialFactor;
        } finally {
            cur.close();
        }
        deck.averageFactor = Math.max(deck.averageFactor, Deck.minimumAverage);

        // Rebuild queue is not rebuild already
        if (ids.isEmpty()) {
            deck.reset();
        }
        // Make sure we haven't accidentally bumped the modification time
        assert deck.modified == oldMod;
        // Create a temporary view for random new cards. Randomizing the cards by themselves
        // as is done in desktop Anki in Deck.randomizeNewCards() takes too long.
        deck.getDB().database.execSQL("CREATE TEMPORARY VIEW acqCardsRandom AS SELECT * FROM cards "
                + "WHERE type = 2 AND isDue = 1 ORDER BY RANDOM()");

        return deck;
    }


    public synchronized void closeDeck() {
        DeckTask.waitToFinish(); // Wait for any thread working on the deck to finish.
        if (modifiedSinceSave()) {
            commitToDB();
        }
        AnkiDatabaseManager.closeDatabase(deckPath);
    }

    /**
     * deckVars methods
     */
    public boolean hasKey(String key) {
        return getDB().database.rawQuery("SELECT 1 FROM deckVars WHERE key = '" + key + "'", null).moveToNext();
    }


    public int getInt(String key) throws SQLException {
        Cursor cur = getDB().database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
        if (cur.moveToFirst()) {
            return cur.getInt(0);
        } else {
            throw new SQLException("DeckVars.getInt: could not retrieve value for " + key);
        }
    }


    public boolean getBool(String key) {
        Cursor cur = getDB().database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
        if (cur.moveToFirst()) {
            return (cur.getInt(0) != 0);
        }
        return false;
    }


    public String getVar(String key) {
        Cursor cur = null;
        try {
            cur = getDB().database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.moveToFirst()) {
                return cur.getString(0);
            }
        } catch (SQLException e) {
            Log.e(TAG, "getVar: " + e.toString());
            throw new RuntimeException(e);
        } finally {
            if (cur != null) {
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
            cur = getDB().database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.moveToNext()) {
                if (cur.getString(0).equals(value)) {
                    return;
                } else {
                    getDB().database.execSQL("UPDATE deckVars SET value='" + value + "' WHERE key = '" + key + "'");
                }
            } else {
                getDB().database.execSQL("INSERT INTO deckVars (key, value) VALUES ('" + key + "', '" + value + "')");
            }
        } catch (SQLException e) {
            Log.e(TAG, "setVar: " + e.toString());
            throw new RuntimeException(e);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
    }


    public void setVarDefault(String key, String value) {
        if (!hasKey(key)) {
            getDB().database.execSQL("INSERT INTO deckVars (key, value) values ('" + key + "', '" + value + "')");
        }
    }


    private void initVars() {
        // tmpMediaDir = null;
        // forceMediaDir = null;
        // lastTags = "";
        lastLoaded = (double) System.currentTimeMillis() / 1000.0;
        // undoEnabled = false;
        // sessionStartReps = 0;
        // sessionStartTime = 0;
        // lastSessionStart = 0;
        queueLimit = 200;
        // If most recent deck var not defined, make sure defaults are set
        if (!hasKey("revInactive")) {
            setVarDefault("suspendLeeches", "1");
            setVarDefault("leechFails", "16");
            setVarDefault("perDay", "1");
            setVarDefault("newActive", "");
            setVarDefault("revActive", "");
            setVarDefault("newInactive", suspended);
            setVarDefault("revInactive", suspended);
        }
        updateCutoff();
        setupStandardScheduler();
    }
    
    /**
     * Upgrade deck to latest version
     * 
     * @return True if the upgrade is supported, false if the upgrade needs to be performed by Anki Desktop
     */
    private boolean upgradeDeck() {
        // Oldest versions in existence are 31 as of 11/07/2010
        // We support upgrading from 39 and up.
        // Unsupported are about 135 decks, missing about 6% as of 11/07/2010
        
        if (version < 39) {
            // Unsupported version
            return false;
        }
        if (version < 40) {
            // Now stores media url
            getDB().database.execSQL("UPDATE models SET features = ''");
            version = 40;
            commitToDB();
        }
        // skip 41
        if (version < 42) {
            version = 42;
            commitToDB();
        }
        if (version < 43) {
            getDB().database.execSQL("UPDATE fieldModels SET features = ''");
            version = 43;
            commitToDB();
        }
        if (version < 44) {
            // Leaner indices
            getDB().database.execSQL("DROP INDEX IF EXISTS ix_cards_factId");
            addIndices();
            // Per-day scheduling necessitates an increase here
            hardIntervalMin = 1.0;
            hardIntervalMax = 1.1;
            version = 44;
            commitToDB();
        }
        if (version < 47) {
            // Add an index for (type, combinedDue)
            addIndices();
            // Add new indices that exclude isDue - we'll clean up the old ones later
            updateDynamicIndices();
            getDB().database.execSQL("ANALYZE");
            version = 47;
            commitToDB();
        }
        if (version < 48) {
            updateFieldCache(Utils.toPrimitive(getDB().queryColumn(Long.class, "SELECT id FROM facts", 0)));
            version = 48;
            commitToDB();
        }
        if (version < 49) {
            rebuildTypes();
            version = 49;
            commitToDB();
        }
        if (version < 50) {
            // more new type handling
            rebuildTypes();
            // Add an index for relativeDelay (type cache)
            addIndices();
            version = 50;
            commitToDB();
        }
        // Executing a pragma here is very slow on large decks, so we store our own record
        if (getInt("pageSize") != 4096) {
            commitToDB();
            getDB().database.execSQL("PRAGMA page_size = 4096");
            getDB().database.execSQL("PRAGMA legacy_file_format = 0");
            getDB().database.execSQL("VACUUM");
            setVar("pageSize", "4096", false);
            commitToDB();
        }
        return true;
    }
    
    /**
     * Add indices to the DB.
     */
    private void addIndices() {
        // Counts, failed cards
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cards_typeCombined ON cards (type, combinedDue)");
        // Scheduler-agnostic type
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cards_relativeDelay ON cards (relativeDelay)");
        // Failed cards, review early - obsolete
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cards_duePriority " +
        "ON cards (type, isDue, combinedDue, priority)");
        // Check due - obsolete
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cards_priorityDue " +
                "ON cards (type, isDue, priority, combinedDue)");
        // Average factor
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cards_factor ON cards (type, factor)");
        // Card spacing
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cards_factId ON cards (factId)");
        // Stats
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_stats_typeDay ON stats (type, day)");
        // Fields
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_fields_factId ON fields (factId)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_fields_fieldModelId ON fields (fieldModelId)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_fields_value ON fields (value)");
        // Media
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_media_filename ON media (filename)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_media_originalPath ON media (originalPath)");
        // Deletion tracking
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cardsDeleted_cardId ON cardsDeleted (cardId)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_modelsDeleted_modelId ON modelsDeleted (modelId)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_factsDeleted_factId ON factsDeleted (factId)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_mediaDeleted_factId ON mediaDeleted (mediaId)");
        // Tags
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_tags_tag ON tags (tag)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cardTags_tagCard ON cardTags (tagId, cardId)");
        getDB().database.execSQL("CREATE INDEX IF NOT EXISTS ix_cardTags_cardId ON cardTags (cardId)");
    }
    
    /*
     * Add stripped HTML cache for sorting/searching.
     * Currently needed as part of the upgradeDeck, the cache is not really used, yet.
     */
    private void updateFieldCache(long[] fids) {
        HashMap<Long, String> r = new HashMap<Long, String>();
        Cursor cur = null;
        
        try {
            cur = getDB().database.rawQuery("SELECT factId, group_concat(value, ' ') FROM fields " +
                "WHERE factId IN " + Utils.ids2str(fids) + " GROUP BY factId" , null);
            while (cur.moveToNext()) {
                String values = cur.getString(1);
                //if (values.charAt(0) == ' ') {
                    // Fix for a slight difference between how Android SQLite and python sqlite work.
                    // Inconsequential difference in this context, but messes up any effort for automated testing.
                    values = values.replaceFirst("^ *", "");
                //}
                r.put(cur.getLong(0), Utils.stripHTMLMedia(values));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        
        if (r.size() > 0) {
            getDB().database.beginTransaction();
            SQLiteStatement st = getDB().database.compileStatement("UPDATE facts SET spaceUntil=? WHERE id=?");
            for (Long fid : r.keySet()) {
                st.bindString(1, r.get(fid));
                st.bindLong(2, fid.longValue());
                st.execute();
            }
            getDB().database.setTransactionSuccessful();
            getDB().database.endTransaction();
        }
    }

    private boolean modifiedSinceSave() {
        return modified > lastLoaded;
    }

    /*
     * Queue Management*****************************
     */

    private class QueueItem {
        private long cardID;
        private long factID;
        private double due;


        QueueItem(long _cardID, long _factID) {
            cardID = _cardID;
            factID = _factID;
            due = 0.0;
        }


        QueueItem(long _cardID, long _factID, double _due) {
            cardID = _cardID;
            factID = _factID;
            due = _due;
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
            Log.e(TAG, "made it!");
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
            Log.e(TAG, "aaaaa");
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


    private void requeueCard(Card card, int oldSuc) {
        try {
            requeueCardMethod.invoke(Deck.this, card, oldSuc);
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


    private void finishScheduler() {
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

    private void spaceCards(Card card, double space) {
        try {
            spaceCardsMethod.invoke(Deck.this, card, space);
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
        return scheduler;
    }

    /*
     * Standard Scheduling*****************************
     */
    private void setupStandardScheduler() {
        try {
            getCardIdMethod = Deck.class.getDeclaredMethod("_getCardId", boolean.class);
            fillFailedQueueMethod = Deck.class.getDeclaredMethod("_fillFailedQueue");
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillRevQueue");
            fillNewQueueMethod = Deck.class.getDeclaredMethod("_fillNewQueue");
            rebuildFailedCountMethod = Deck.class.getDeclaredMethod("_rebuildFailedCount");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildRevCount");
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildNewCount");
            requeueCardMethod = Deck.class.getDeclaredMethod("_requeueCard", Card.class, int.class);
            timeForNewCardMethod = Deck.class.getDeclaredMethod("_timeForNewCard");
            updateNewCountTodayMethod = Deck.class.getDeclaredMethod("_updateNewCountToday");
            cardQueueMethod = Deck.class.getDeclaredMethod("_cardQueue", Card.class);
            finishSchedulerMethod = null;
            answerCardMethod = Deck.class.getDeclaredMethod("_answerCard", Card.class, int.class);
            cardLimitMethod = Deck.class.getDeclaredMethod("_cardLimit", String.class, String.class, String.class);
            answerPreSaveMethod = null;
            spaceCardsMethod = Deck.class.getDeclaredMethod("_spaceCards", Card.class, double.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        scheduler = "standard";
        // Restore any cards temporarily suspended by alternate schedulers
        if (version == DECK_VERSION) {
            resetAfterReviewEarly();
        }
    }


    private void fillQueues() {
        fillFailedQueue();
        fillRevQueue();
        fillNewQueue();
    }

    public long getCardCount() {
        return getDB().queryScalar("SELECT count(*) from cards");
    }


    private void rebuildCounts() {
        // global counts
        try {
            cardCount = (int) getDB().queryScalar("SELECT count(*) from cards");
            factCount = (int) getDB().queryScalar("SELECT count(*) from facts");
        } catch (SQLException e) {
            Log.e(TAG, "rebuildCounts: Error while getting global counts: " + e.toString());
            cardCount = 0;
            factCount = 0;
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
            return sql.replace("WHERE", "WHERE +c.id IN (SELECT cardId FROM cardTags WHERE tagId IN " +
                    Utils.ids2str(yids) + " AND tagId NOT IN " + Utils.ids2str(nids) + ") AND");
        } else if (no.length > 0) {
            long nids[] = Utils.toPrimitive(tagIds(no).values());
            return sql.replace("WHERE", "WHERE +c.id NOT IN (SELECT cardId FROM cardTags WHERE tagId IN "
                    + Utils.ids2str(nids) + ") AND");
        } else {
            return sql;
        }
    }


    @SuppressWarnings("unused")
    private void _rebuildFailedCount() {
        failedSoonCount = (int) getDB().queryScalar(cardLimit("revActive", "revInactive",
                "SELECT count(*) FROM cards c WHERE type = 0 AND combinedDue < " + failedCutoff));
    }


    @SuppressWarnings("unused")
    private void _rebuildRevCount() {
        revCount = (int) getDB().queryScalar(cardLimit("revActive", "revInactive",
                "SELECT count(*) FROM cards c WHERE type = 1 AND combinedDue < " + dueCutoff));
    }


    @SuppressWarnings("unused")
    private void _rebuildNewCount() {
        newCount = (int) getDB().queryScalar(cardLimit("newActive", "newInactive",
                "SELECT count(*) FROM cards c WHERE type = 2 AND combinedDue < " + dueCutoff));
        updateNewCountToday();
    }


    @SuppressWarnings("unused")
    private void _updateNewCountToday() {
        newCountToday = Math.max(Math.min(newCount, newCardsPerDay - newCardsDoneToday()), 0);
    }


    @SuppressWarnings("unused")
    private void _fillFailedQueue() {
        if ((failedSoonCount != 0) && failedQueue.isEmpty()) {
            String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 0 AND combinedDue < " +
            failedCutoff + " ORDER BY combinedDue LIMIT " + queueLimit;
            Cursor cur = getDB().database.rawQuery(cardLimit("revActive", "revInactive", sql), null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
                failedQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }


    @SuppressWarnings("unused")
    private void _fillRevQueue() {
        if ((revCount != 0) && revQueue.isEmpty()) {
            String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 1 AND combinedDue < " + dueCutoff
                    + " ORDER BY " + revOrder() + " LIMIT " + queueLimit;
            Cursor cur = getDB().database.rawQuery(cardLimit("revActive", "revInactive", sql), null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
                revQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }


    @SuppressWarnings("unused")
    private void _fillNewQueue() {
        if ((newCount != 0) && newQueue.isEmpty()) {
            String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 2 AND combinedDue < " + dueCutoff
                    + " ORDER BY " + newOrder() + " LIMIT " + queueLimit;
            Cursor cur = getDB().database.rawQuery(cardLimit("newActive", "newInactive", sql), null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
                newQueue.addFirst(qi); // Add to front, so list is reversed as it is built
            }
        }
    }


    private boolean queueNotEmpty(LinkedList<QueueItem> queue, Method fillFunc) {
        while (true) {
            removeSpaced(queue);
            if (!queue.isEmpty()) {
                return true;
            }
            try {
                fillFunc.invoke(Deck.this);
            } catch (Exception e) {
                Log.e(TAG, "queueNotEmpty: Error while invoking overridable fill method:" + e.toString());
                return false;
            }
            if (queue.isEmpty()) {
                return false;
            }
        }
    }


    private void removeSpaced(LinkedList<QueueItem> queue) {
        while (!queue.isEmpty()) {
            long fid = ((QueueItem) queue.getLast()).getFactID();
            if (spacedFacts.containsKey(fid)) {
                if ((double) System.currentTimeMillis() / 1000.0 > spacedFacts.get(fid)) {
                    spacedFacts.remove(fid);
                } else {
                    queue.removeLast();
                }
            } else {
                return;
            }
        }
    }


    private boolean revNoSpaced() {
        return queueNotEmpty(revQueue, fillRevQueueMethod);
    }


    private boolean newNoSpaced() {
        return queueNotEmpty(newQueue, fillNewQueueMethod);
    }


    @SuppressWarnings("unused")
    private void _requeueCard(Card card, int oldSuc) {
        try{
            if (card.reps == 1) {
                newQueue.removeLast();
            } else if (oldSuc == 0) {
                failedQueue.removeLast();
            } else {
                revQueue.removeLast();
            }
        } catch (Exception e) {
            throw new RuntimeException("requeueCard() failed. Counts: " + 
                    failedSoonCount + " " + revCount + " " + newCountToday + ", Queue: " +
                    failedQueue.size() + " " + revQueue.size() + " " + newQueue.size() + ", Card info: " +
                    card.reps + " " + card.successive + " " + oldSuc);
        }
    }


    private String revOrder() {
        return revOrderStrings[revCardOrder];
    }


    private String newOrder() {
        return newOrderStrings[newCardOrder];
    }


    // Rebuild the type cache. Only necessary on upgrade.
    private void rebuildTypes() {
        getDB().database.execSQL("UPDATE cards SET "
                + "type = (CASE " 
                + "WHEN successive THEN 1 WHEN reps THEN 0 ELSE 2 END), "
                + "relativeDelay = (CASE "
                + "WHEN successive THEN 1 WHEN reps THEN 0 ELSE 2 END) "
                + "WHERE type >= 0");
        // old-style suspended cards
        getDB().database.execSQL("UPDATE cards SET type = type - 3 WHERE priority = -3 AND type >= 0");
    }


    @SuppressWarnings("unused")
    private int _cardQueue(Card card) {
        return cardType(card);
    }


    // Return the type of the current card (what queue it's in)
    private int cardType(Card card) {
        if (card.successive != 0) {
            return 1;
        } else if (card.reps != 0) {
            return 0;
        } else {
            return 2;
        }
    }


    private void updateCutoff() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, (int) -utcOffset + 86400);
        cal.set(Calendar.HOUR, 0); // Yes, verbose but crystal clear
        cal.set(Calendar.MINUTE, 0); // Apologies for that, here was my rant
        cal.set(Calendar.SECOND, 0); // But if you can improve this bit and
        cal.set(Calendar.MILLISECOND, 0); // collapse it to one statement please do

        int newday = (int) utcOffset - (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 1000;
        Log.d(TAG, "New day happening at " + newday + " sec after 00:00 UTC");
        cal.add(Calendar.SECOND, newday);
        long cutoff = cal.getTimeInMillis() / 1000;
        // Cutoff must not be in the past
        while (cutoff < System.currentTimeMillis() / 1000) {
            cutoff += 86400.0;
        }
        // Cutoff must not be more than 24 hours in the future
        cutoff = Math.min(System.currentTimeMillis() / 1000 + 86400, cutoff);
        if (getBool("perDay")) {
            dueCutoff = (double) cutoff;
        } else {
            dueCutoff = (double) System.currentTimeMillis() / 1000.0;
        }
    }


    public void reset() {
        // Setup global/daily stats
        globalStats = Stats.globalStats(this);
        dailyStats = Stats.dailyStats(this);
        // Recheck counts
        rebuildCounts();
        // Empty queues; will be refilled by getCard()
        failedQueue.clear();
        revQueue.clear();
        newQueue.clear();
        spacedFacts.clear();
        // Determine new card distribution
        if (newCardSpacing == NEW_CARDS_DISTRIBUTE) {
            if (newCountToday != 0) {
                newCardModulus = (newCountToday + revCount) / newCountToday;
                // If there are cards to review, ensure modulo >= 2
                if (revCount != 0) {
                    newCardModulus = Math.max(2, newCardModulus);
                }
            } else {
                newCardModulus = 0;
            }
        } else {
            newCardModulus = 0;
        }
        rebuildCSS();
    }


    // Checks if the day has rolled over.
    private void checkDailyStats() {
        if (!Stats.genToday(this).toString().equals(dailyStats.day.toString())) {
            dailyStats = Stats.dailyStats(this);
        }
    }


    /*
     * Review early*****************************
     */

    private void setupReviewEarlyScheduler() {
        try {
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillRevEarlyQueue");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildRevEarlyCount");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("_onReviewEarlyFinished");
            answerPreSaveMethod = Deck.class.getDeclaredMethod("_reviewEarlyPreSave", Card.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        scheduler = "reviewEarly";
    }


    @SuppressWarnings("unused")
    private void _reviewEarlyPreSave(Card card, int ease) {
        if (ease > 1) {
            // Prevent it from appearing in next queue fill
            card.type += 6;
        }
    }


    private void resetAfterReviewEarly() {
        // FIXME: Can ignore priorities in the future (following libanki)
        ArrayList<Long> ids = getDB().queryColumn(Long.class,
                "SELECT id FROM cards WHERE type BETWEEN 6 AND 8 OR priority = -1", 0);

        updatePriorities(Utils.toPrimitive(ids));
        getDB().database.execSQL("UPDATE cards SET type = type -6 WHERE type BETWEEN 6 AND 8");
        flushMod();
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
        String extraLim = ""; // In the future it would be nice to skip the first x days of due cards

        String sql = "SELECT count() FROM cards WHERE type = 1 AND combinedDue > " + dueCutoff + extraLim;
        revCount = (int) getDB().queryScalar(sql);
    }


    @SuppressWarnings("unused")
    private void _fillRevEarlyQueue() {
        if ((revCount != 0) && revQueue.isEmpty()) {
            String sql = "SELECT id, factId FROM cards WHERE type = 1 AND combinedDue > " + dueCutoff
                    + " ORDER BY combinedDue LIMIT " + queueLimit;
            Cursor cur = getDB().database.rawQuery(sql, null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                revQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }


    /*
     * Learn more*****************************
     */

    private void setupLearnMoreScheduler() {
        try {
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildLearnMoreCount");
            updateNewCountTodayMethod = Deck.class.getDeclaredMethod("_updateLearnMoreCountToday");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        scheduler = "learnMore";
    }


    @SuppressWarnings("unused")
    private void _rebuildLearnMoreCount() {
        newCount = (int) getDB().queryScalar("SELECT count() FROM cards WHERE type = 2 AND combinedDue < " + dueCutoff);
    }


    @SuppressWarnings("unused")
    private void _updateLearnMoreCountToday() {
        newCountToday = newCount;
    }


    /*
     * Cramming*****************************
     */

    private void setupCramScheduler(String[] active, String order) {
        try {
            getCardIdMethod = Deck.class.getDeclaredMethod("_getCramCardId", boolean.class);
            activeCramTags = active;
            cramOrder = order;
            rebuildFailedCountMethod = Deck.class.getDeclaredMethod("_rebuildFailedCramCount");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildCramCount");
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildNewCramCount");
            fillFailedQueueMethod = Deck.class.getDeclaredMethod("_fillFailedCramQueue");
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillCramQueue");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
            failedCramQueue.clear();
            requeueCardMethod = Deck.class.getDeclaredMethod("_requeueCramCard", Card.class, int.class);
            cardQueueMethod = Deck.class.getDeclaredMethod("_cramCardQueue", Card.class);
            answerCardMethod = Deck.class.getDeclaredMethod("_answerCramCard", Card.class, int.class);
            spaceCardsMethod = Deck.class.getDeclaredMethod("_spaceCramCards", Card.class, double.class);
            // Reuse review early's code
            answerPreSaveMethod = Deck.class.getDeclaredMethod("_reviewEarlyPreSave", Card.class, int.class);
            cardLimitMethod = Deck.class.
                getDeclaredMethod("_cramCardLimit", String[].class, String[].class, String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        scheduler = "cram";
    }


    @SuppressWarnings("unused")
    private void _answerCramCard(Card card, int ease) {
        if (ease == 1) {
            if (cardQueue(card) != 0) {
                failedSoonCount += 1;
                revCount -= 1;
            }
            requeueCard(card, 0);
            failedCramQueue.addFirst(new QueueItem(card.id, card.factId));
        } else {
            _answerCard(card, ease);
        }
    }


    @SuppressWarnings("unused")
    private long _getCramCardId(boolean check) {
        checkDailyStats();
        fillQueues();

        if (failedSoonCount >= failedCardMax) {
            return ((QueueItem) failedQueue.getLast()).getCardID();
        }
        // Card due for review?
        if (revNoSpaced()) {
            return ((QueueItem) revQueue.getLast()).getCardID();
        }
        if (!failedQueue.isEmpty()) {
            return ((QueueItem) failedQueue.getLast()).getCardID();
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
        if ((!revQueue.isEmpty()) && (((QueueItem) revQueue.getLast()).getCardID() == card.id)) {
            return 1;
        } else {
            return 0;
        }
    }


    @SuppressWarnings("unused")
    private void _requeueCramCard(Card card, int oldSuc) {
        if (cardQueue(card) == 1) {
            revQueue.removeLast();
        } else {
            failedCramQueue.removeLast();
        }
    }


    @SuppressWarnings("unused")
    private void _rebuildNewCramCount() {
        newCount = 0;
        newCountToday = 0;
    }


    @SuppressWarnings("unused")
    private String _cramCardLimit(String active[], String inactive[], String sql) {
        // inactive is (currently) ignored
        if (active.length > 1) {
            return sql.replace("WHERE ", "WHERE +c.id IN "
                    + Utils.ids2str(new ArrayList<String>(Arrays.asList(active))));
        } else if (active.length == 1) {
            String[] yes = Utils.parseTags(active[0]);
            if (yes.length > 0) {
                long yids[] = Utils.toPrimitive(tagIds(yes).values());
                return sql.replace("WHERE ", "WHERE +c.id IN (SELECT cardId FROM cardTags WHERE " + "tagId IN "
                        + Utils.ids2str(yids) + ") AND ");
            } else {
                return sql;
            }
        } else {
            return sql;
        }
    }


    @SuppressWarnings("unused")
    private void _fillCramQueue() {
        if ((revCount != 0) && revQueue.isEmpty()) {
            String sql = "SELECT id, factId FROM cards WHERE type BETWEEN 0 AND 2 ORDER BY " + cramOrder + " LIMIT "
                    + queueLimit;
            Cursor cur = getDB().database.rawQuery(cardLimit(activeCramTags, null, sql), null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                revQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }


    @SuppressWarnings("unused")
    private void _rebuildCramCount() {
        revCount = (int) getDB().queryScalar(cardLimit(activeCramTags, null,
                "SELECT count(*) FROM cards WHERE type BETWEEN 0 AND 2"));
    }


    @SuppressWarnings("unused")
    private void _rebuildFailedCramCount() {
        failedSoonCount = failedCramQueue.size();
    }


    @SuppressWarnings("unused")
    private void _fillFailedCramQueue() {
        failedQueue = failedCramQueue;
    }

    @SuppressWarnings("unused")
    private void _spaceCramCards(Card card, double space) {
        // If non-zero spacing, limit to 10 minutes or queue refill
        if (space > System.currentTimeMillis()) {
            spacedFacts.put(card.factId, System.currentTimeMillis() + 600.0);
        }
    }

    private void setModified() {
        modified = System.currentTimeMillis() / 1000.0;
    }


    private void flushMod() {
        setModified();
        commitToDB();
    }


    public void commitToDB() {
        Log.i(TAG, "commitToDB - Saving deck to DB...");
        ContentValues values = new ContentValues();
        values.put("created", created);
        values.put("modified", modified);
        values.put("description", description);
        values.put("version", version);
        values.put("currentModelId", currentModelId);
        values.put("syncName", syncName);
        values.put("lastSync", lastSync);
        values.put("hardIntervalMin", hardIntervalMin);
        values.put("hardIntervalMax", hardIntervalMax);
        values.put("midIntervalMin", midIntervalMin);
        values.put("midIntervalMax", midIntervalMax);
        values.put("easyIntervalMin", easyIntervalMin);
        values.put("easyIntervalMax", easyIntervalMax);
        values.put("delay0", delay0);
        values.put("delay1", delay1);
        values.put("delay2", delay2);
        values.put("collapseTime", collapseTime);
        values.put("highPriority", highPriority);
        values.put("medPriority", medPriority);
        values.put("lowPriority", lowPriority);
        values.put("suspended", suspended);
        values.put("newCardOrder", newCardOrder);
        values.put("newCardSpacing", newCardSpacing);
        values.put("failedCardMax", failedCardMax);
        values.put("newCardsPerDay", newCardsPerDay);
        values.put("sessionRepLimit", sessionRepLimit);
        values.put("sessionTimeLimit", sessionTimeLimit);
        values.put("utcOffset", utcOffset);
        values.put("cardCount", cardCount);
        values.put("factCount", factCount);
        values.put("failedNowCount", failedNowCount);
        values.put("failedSoonCount", failedSoonCount);
        values.put("revCount", revCount);
        values.put("newCount", newCount);
        values.put("revCardOrder", revCardOrder);

        getDB().database.update("decks", values, "id = " + id, null);
    }


    public static double getLastModified(String deckPath) {
        double value;
        Cursor cursor = null;
        // Log.i(TAG, "Deck - getLastModified from deck = " + deckPath);

        boolean dbAlreadyOpened = AnkiDatabaseManager.isDatabaseOpen(deckPath);

        try {
            cursor = AnkiDatabaseManager.getDatabase(deckPath).database.rawQuery("SELECT modified" + " FROM decks"
                    + " LIMIT 1", null);

            if (!cursor.moveToFirst()) {
                value = -1;
            } else {
                value = cursor.getDouble(0);
            }
        } finally {
            if (cursor != null) {
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
        return AnkiDatabaseManager.getDatabase(deckPath);
    }


    public String getDeckPath() {
        return deckPath;
    }


    public void setDeckPath(String path) {
        deckPath = path;
    }


    public String getSyncName() {
        return syncName;
    }


    public void setSyncName(String name) {
        syncName = name;
        flushMod();
    }


    public int getRevCardOrder() {
        return revCardOrder;
    }


    public void setRevCardOrder(int num) {
        if (num >= 0) {
            revCardOrder = num;
            flushMod();
        }
    }


    public int getNewCardSpacing() {
        return newCardSpacing;
    }


    public void setNewCardSpacing(int num) {
        if (num >= 0) {
            newCardSpacing = num;
            flushMod();
        }
    }


    public int getNewCardOrder() {
        return newCardOrder;
    }


    public void setNewCardOrder(int num) {
        if (num >= 0) {
            newCardOrder = num;
            flushMod();
        }
    }

    public boolean getPerDay() {
        return getBool("perDay");
    }

    public void setPerDay(boolean perDay) {
        setVar("perDay", perDay;
    }

    public int getNewCardsPerDay() {
        return newCardsPerDay;
    }


    public void setNewCardsPerDay(int num) {
        if (num >= 0) {
            newCardsPerDay = num;
            flushMod();
            reset();
        }
    }


    public long getSessionRepLimit() {
        return sessionRepLimit;
    }


    public void setSessionRepLimit(long num) {
        if (num >= 0) {
            sessionRepLimit = num;
            flushMod();
        }
    }


    public long getSessionTimeLimit() {
        return sessionTimeLimit;
    }


    public void setSessionTimeLimit(long num) {
        if (num >= 0) {
            sessionTimeLimit = num;
            flushMod();
        }
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
        long id = getCardId();
        if (id != 0l) {
            return cardFromId(id);
        } else {
            return null;
        }
    }


    // Refreshes the current card and returns it (used when editing cards)
    // TODO find a less lame way to do this.
    public Card getCurrentCard() {
        return cardFromId(currentCard.id);
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
        if (!failedQueue.isEmpty()) {
            // Failed card due?
            if (delay0 != 0l) {
                if ((long) ((QueueItem) failedQueue.getLast()).getDue() + delay0 < System.currentTimeMillis() / 1000) {
                    return failedQueue.getLast().getCardID();
                }
            }
            // Failed card queue too big?
            if ((failedCardMax != 0) && (failedSoonCount >= failedCardMax)) {
                return failedQueue.getLast().getCardID();
            }
        }
        // Distribute new cards?
        if (newNoSpaced() && timeForNewCard()) {
            return newQueue.getLast().getCardID();
        }
        // Card due for review?
        if (revNoSpaced()) {
            return revQueue.getLast().getCardID();
        }
        // New cards left?
        if (newCountToday != 0) {
            return newQueue.getLast().getCardID();
        }
        // Display failed cards early/last
        if (showFailedLast() && (!failedQueue.isEmpty())) {
            return failedQueue.getLast().getCardID();
        }
        if (check) {
            // Check for expired cards, or new day rollover
            updateCutoff();
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


    /*
     * Get card: helper functions*****************************
     */

    @SuppressWarnings("unused")
    private boolean _timeForNewCard() {
        // True if it's time to display a new card when distributing.
        if (newCountToday == 0) {
            return false;
        }
        if (newCardSpacing == NEW_CARDS_LAST) {
            return false;
        }
        if (newCardSpacing == NEW_CARDS_FIRST) {
            return true;
        }
        // Force review if there are very high priority cards
        try {
            if (!revQueue.isEmpty()) {
                if (getDB().queryScalar(
                        "SELECT 1 FROM cards WHERE id = " + revQueue.getLast().getCardID() + " AND priority = 4") == 1) {
                    return false;
                }
            }
        } catch (Exception e) {
            // No result from query.
        }
        if (newCardModulus != 0) {
            return (dailyStats.reps % newCardModulus == 0);
        } else {
            return false;
        }
    }


    private boolean showFailedLast() {
        return ((collapseTime != 0.0) || (delay0 == 0));
    }

    /**
     * Given a card ID, return a card and start the card timer.
     * 
     * @param id The ID of the card to be returned
     */

    private Card cardFromId(long id) {
        if (id == 0) {
            return null;
        }
        Card card = new Card(this);
        boolean result = card.fromDB(id);

        if (!result) {
            return null;
        }
        card.genFuzz();
        card.startTimer();
        return card;
    }


    /**
     * Saves an updated card to the database.
     * 
     * @param card The modified version of a card from this deck to be saved.
     */
    public void updateCard(Card card) {
        double now = System.currentTimeMillis() / 1000.0;
        ContentValues updateValues = new ContentValues();
        updateValues.put("question", card.question);
        updateValues.put("answer", card.answer);
        updateValues.put("modified", now);
        getDB().database.update("cards", updateValues, "id = ?", new String[] { ""
                + card.id });
        // AnkiDb.database.execSQL(String.format(NULL_LOCALE,
        // "UPDATE cards " +
        // "SET question = %s, " +
        // "answer = %s, " +
        // "modified = %f, " +
        // "WHERE id != %d and factId = %d",
        // card.question, card.answer, now, card.id, card.factId));
        //
        Log.v(TAG, "Update question and answer in card id# " + card.id);

    }


    // Optimization for updateAllCardsFromPosition and updateAllCards
    // Drops some indices and changes synchronous pragma
    public void beforeUpdateCards() {
        long now = System.currentTimeMillis();
        // getDB().database.execSQL("PRAGMA synchronous=NORMAL");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_duePriority");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_priorityDue");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_factor");
        // getDB().database.execSQL("DROP INDEX IF EXISTS ix_cards_sort");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_factId");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_intervalDesc");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_intervalAsc");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_randomOrder");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_dueAsc");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_dueDesc");
        Log.i(TAG, "BEFORE UPDATE = " + (System.currentTimeMillis() - now));
    }


    public void afterUpdateCards() {
        long now = System.currentTimeMillis();
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_duePriority on cards (type, isDue, combinedDue, priority)");
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_priorityDue on cards (type, isDue, priority, combinedDue)");
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_factor on cards 			(type, factor)");
        // getDB().database.execSQL("CREATE INDEX ix_cards_sort on cards (answer collate nocase)");
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_factId on cards (factId, type)");
        // updateDynamicIndices();
        // getDB().database.execSQL("PRAGMA synchronous=FULL");
        Log.i(TAG, "AFTER UPDATE = " + (System.currentTimeMillis() - now));
        // now = System.currentTimeMillis();
        // ankiDB.database.execSQL("ANALYZE");
        // Log.i("ANALYZE = " + System.currentTimeMillis() - now);
    }


    // TODO: The real methods to update cards on Anki should be implemented instead of this
    public void updateAllCards() {
        updateAllCardsFromPosition(0, Long.MAX_VALUE);
    }


    public long updateAllCardsFromPosition(long numUpdatedCards, long limitCards) {
        // TODO: Cache this query, order by FactId, Id
        Cursor cursor = getDB().database.rawQuery("SELECT id, factId " + "FROM cards " + "ORDER BY factId, id "
                + "LIMIT " + limitCards + " OFFSET " + numUpdatedCards, null);

        getDB().database.beginTransaction();
        try {
            while (cursor.moveToNext()) {
                // Get card
                Card card = new Card(this);
                card.fromDB(cursor.getLong(0));
                Log.i(TAG, "Card id = " + card.id + ", numUpdatedCards = " + numUpdatedCards);

                // Load tags
                card.loadTags();

                // Get the related fact
                Fact fact = card.getFact();
                // Log.i(TAG, "Fact id = " + fact.id);

                // Generate the question and answer for this card and update it
                HashMap<String, String> newQA = CardModel.formatQA(fact, card.getCardModel(), card.splitTags());
                card.question = newQA.get("question");
                Log.i(TAG, "Question = " + card.question);
                card.answer = newQA.get("answer");
                Log.i(TAG, "Answer = " + card.answer);
                card.modified = System.currentTimeMillis() / 1000.0;

                card.updateQAfields();

                numUpdatedCards++;

            }
            cursor.close();
            getDB().database.setTransactionSuccessful();
        } finally {
            getDB().database.endTransaction();
        }

        return numUpdatedCards;
    }


    /*
     * Answering a card
     *******************************/

    public void _answerCard(Card card, int ease) {
        Log.i(TAG, "answerCard");
        String undoName = "Answer Card";
        setUndoStart(undoName);
        double now = System.currentTimeMillis() / 1000.0;

        // Old state
        String oldState = cardState(card);
        int oldQueue = cardQueue(card);
        double lastDelaySecs = System.currentTimeMillis() / 1000.0 - card.combinedDue;
        double lastDelay = lastDelaySecs / 86400.0;
        int oldSuc = card.successive;

        // update card details
        double last = card.interval;
        card.interval = nextInterval(card, ease);
        if (lastDelay >= 0) {
            card.lastInterval = last; // keep last interval if reviewing early
        }
        if (card.reps != 0) {
            card.lastDue = card.due; // only update if card was not new
        }
        card.due = nextDue(card, ease, oldState);
        card.isDue = 0;
        card.lastFactor = card.factor;
        card.spaceUntil = 0;
        if (lastDelay >= 0) {
            updateFactor(card, ease); // don't update factor if learning ahead
        }

        // Spacing
        double space = spaceUntilTime(card);
        spaceCards(card, space);
        // Adjust counts for current card
        if (ease == 1) {
            if (!(oldState.compareTo("mature") == 0 && delay1 != 0)) {
                failedSoonCount += 1;
            }
        }
        if (oldQueue == 0) {
            failedSoonCount -= 1;
        } else if (oldQueue == 1) {
            revCount -= 1;
        } else {
            newCount -= 1;
        }

        // card stats
        card.updateStats(ease, oldState);
        // Update type & ensure past cutoff
        card.type = cardType(card);
        card.relativeDelay = card.type;
        if (ease != 1) {
            card.due = Math.max(card.due, dueCutoff+1);
        }

        // Allow custom schedulers to munge the card
        if (answerPreSaveMethod != null) {
            answerPreSave(card, ease);
        }

        // Save
        card.toDB();

        // global/daily stats
        Stats.updateAllStats(globalStats, dailyStats, card, ease, oldState);

        // review history
        CardHistoryEntry entry = new CardHistoryEntry(this, card, ease, lastDelay);
        entry.writeSQL();
        modified = now;

        // Remove form queue
        requeueCard(card, oldSuc);

        // Leech handling - we need to do this after the queue, as it may cause a reset
        if (isLeech(card)) {
            handleLeech(card);
        }
        setUndoEnd(undoName);
    }


    private double spaceUntilTime(Card card) {
        Cursor cursor = null;
        double space, spaceFactor, minSpacing, minOfOtherCards;

        try {
            cursor = getDB().database.rawQuery("SELECT models.initialSpacing, models.spacing "
                    + "FROM facts, models WHERE facts.modelId = models.id and facts.id = " + card.factId, null);
            if (!cursor.moveToFirst()) {
                minSpacing = 0;
                spaceFactor = 0;
            } else {
                minSpacing = cursor.getDouble(0);
                spaceFactor = cursor.getDouble(1);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        try {
            minOfOtherCards = getDB().queryScalar(
                    "SELECT min(interval) FROM cards WHERE factId = " + card.factId + " AND id != " + card.id);
        } finally {
            minOfOtherCards = 0;
            if (cursor != null) {
                cursor.close();
            }
        }

        if (minOfOtherCards != 0) {
            space = Math.min(minOfOtherCards, card.interval);
        } else {
            space = 0;
        }
        space = space * spaceFactor * 86400.0;
        space = Math.max(minSpacing, space);
        space += System.currentTimeMillis() / 1000.0;
        return space;
    }


    @SuppressWarnings("unused")
    private void _spaceCards(Card card, double space) {
        Cursor cursor = null;
        // Adjust counts
        try {
            cursor = getDB().database.rawQuery("SELECT type, count(type) FROM cards WHERE factId = " + card.factId
                    + " AND combinedDue < " + dueCutoff + " id != " + card.id + ") GROUP BY type", null);
            while (cursor.moveToNext()) {
                Log.i(TAG, "failedSoonCount before = " + failedSoonCount);
                Log.i(TAG, "revCount before = " + revCount);
                Log.i(TAG, "newCount before = " + newCount);
                int type = cursor.getInt(0);
                int count = cursor.getInt(1);
                if (type == 0) {
                    failedSoonCount -= count;
                } else if (type == 1) {
                    revCount -= count;
                } else if (type == 2) {
                    newCount -= count;
                }
                Log.i(TAG, "failedSoonCount after = " + failedSoonCount);
                Log.i(TAG, "revCount after = " + revCount);
                Log.i(TAG, "newCount after = " + newCount);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // space other cards
        getDB().database.execSQL(String.format(ENGLISH_LOCALE, "UPDATE cards SET spaceUntil = %f, "
                + "combinedDue = max(%f, due), modified = %f, isDue = 0 WHERE id != %ld and factId = %ld", space,
                space, ((double) System.currentTimeMillis() / 1000.0), card.id, card.factId));
        // Update local cache of seen facts
        spacedFacts.put(card.factId, space);
    }


    private boolean isLeech(Card card) {
        int no = card.noCount;
        int fmax = 0;
        try {
            fmax = getInt("leechFails");
        } catch (SQLException e) {
            // No leech threshold found in DeckVars
            return false;
        }
        // Return true if:
        // - The card failed AND
        // - The number of failures exceeds the leech threshold AND
        // - There were at least threshold/2 reps since last time
        if ((card.successive != 0) && (no >= fmax) &&     
                ((fmax - no) % Math.max(fmax/2, 1) == 0)) {
            return true;
        } else {
            return false;
        }
    }

    private void handleLeech(Card card) {
        Card scard = cardFromId(card.id);
        String tags = scard.fact.tags;
        tags = Utils.addTags("Leech", tags);
        scard.getFact().tags = Utils.canonifyTags(tags);
        scard.getFact().setModified(true);
        updateFactTags(new long[] {scard.fact.id});
        scard.toDB();
        if (getBool("suspendLeeches")) {
            suspendCards(new long[]{card.id});
        }
    }

    /*
     * Interval management*********************************************************
     */

    private double nextInterval(Card card, int ease) {
        double delay = adjustedDelay(card, ease);
        return nextInterval(card, delay, ease);
    }


    private double nextInterval(Card card, double delay, int ease) {
        double interval = card.interval;
        double factor = card.factor;

        // if shown early and not failed
        if ((delay < 0) && (card.successive != 0)) {
            // FIXME: From libanki: This should recreate lastInterval from interval /
            // lastFactor, or we lose delay information when reviewing early
            interval = Math.max(card.lastInterval, card.interval + delay);
            if (interval < midIntervalMin) {
                interval = 0;
            }
            delay = 0;
        }

        // if interval is less than mid interval, use presets
        if (ease == 1) {
            interval *= delay2;
            if (interval < hardIntervalMin) {
                interval = 0;
            }
        } else if (interval == 0) {
            if (ease == 2) {
                interval = hardIntervalMin + ((double) Math.random()) * (hardIntervalMax - hardIntervalMin);
            } else if (ease == 3) {
                interval = midIntervalMin + ((double) Math.random()) * (midIntervalMax - midIntervalMin);
            } else if (ease == 4) {
                interval = easyIntervalMin + ((double) Math.random()) * (easyIntervalMax - easyIntervalMin);
            }
        } else {
            // if not cramming, boost initial 2
            if ((interval < hardIntervalMax) && (interval > 0.166)) {
                double mid = (midIntervalMin + midIntervalMax) / 2.0;
                interval = mid / factor;
            }
            // multiply last interval by factor
            if (ease == 2) {
                interval = (interval + delay / 4.0) * 1.2;
            } else if (ease == 3) {
                interval = (interval + delay / 2.0) * factor;
            } else if (ease == 4) {
                interval = (interval + delay) * factor * factorFour;
            }
            double fuzz = 0.95 + ((double) Math.random()) * (1.05 - 0.95);
            interval *= fuzz;
        }
        if (maxScheduleTime != 0) {
            interval = Math.min(interval, maxScheduleTime);
        }
        return interval;
    }


    private double nextDue(Card card, int ease, String oldState) {
        double due;
        if (ease == 1) {
            if (oldState.equals("mature")) {
                due = delay1 * 86400.0;
            } else {
                due = 0.0;
            }
        } else {
            due = card.interval * 86400.0;
        }
        return due + (System.currentTimeMillis() / 1000.0);
    }


    private void updateFactor(Card card, int ease) {
        card.lastFactor = card.factor;
        if (card.reps == 0) {
            card.factor = averageFactor; // card is new, inherit beginning factor
        }
        if (card.successive != 0 && !cardIsBeingLearnt(card)) {
            if (ease == 1) {
                card.factor -= 0.20;
            } else if (ease == 2) {
                card.factor -= 0.15;
            }
        }
        if (ease == 4) {
            card.factor += 0.10;
        }
        card.factor = Math.max(1.3, card.factor);
    }


    private double adjustedDelay(Card card, int ease) {
        double now = System.currentTimeMillis() / 1000.0;
        if (cardIsNew(card)) {
            return 0;
        }
        if (card.combinedDue <= now) {
            return (now - card.due) / 86400.0;
        } else {
            return (now - card.combinedDue) / 86400.0;
        }
    }

    /*
     * Tags: Querying
     *******************************/

    /**
     * Get a map of card IDs to their associated tags (fact, model and template)
     *
     * @param where SQL restriction on the query. If empty, then returns tags for all the cards
     * @return The map of card IDs to an array of strings with 3 elements representing the triad
     * {card tags, model tags, template tags}
     */
    private HashMap<Long, List<String>> splitTagsList() {
        return splitTagsList("");
    }
    private HashMap<Long, List<String>> splitTagsList(String where) {
        Cursor cur = null;
        HashMap<Long, List<String>> results = new HashMap<Long, List<String>>();
        try {
            cur = getDB().database.rawQuery("SELECT cards.id, facts.tags, models.tags, cardModels.name " +
                    "FROM cards, facts, models, cardModels " +
                    "WHERE cards.factId == facts.id AND facts.modelId == models.id " +
                    "AND cards.cardModelId = cardModels.id " + where, null);
            while (cur.moveToNext()) {
                ArrayList<String> tags = new ArrayList<String>();
                tags.add(cur.getString(1));
                tags.add(cur.getString(2));
                tags.add(cur.getString(3));
                results.put(cur.getLong(0), tags);
            }
        } catch (SQLException e) {
            Log.e(TAG, "splitTagsList: Error while retrieving tags from DB: " + e.toString());
        } finally {
            if (cur != null) {
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
        t.addAll(getDB().queryColumn(String.class, "SELECT tags FROM cardModels", 0));

        return (String[]) (new TreeSet<String>(Arrays.asList(Utils.parseTags(Utils.joinTags(t)))).toArray());
    }


    /*
     * Tags: Caching 
     *******************************/
    
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
            getDB().database.execSQL("DELETE FROM cardTags");
            getDB().database.execSQL("DELETE FROM tags");
            tids = tagIds(allTags_());
            rows = splitTagsList();
        } else {
            getDB().database.execSQL("DELETE FROM cardTags WHERE cardId IN " + Utils.ids2str(cardIds));
            String fids = Utils.ids2str(Utils.toPrimitive(getDB().queryColumn(Long.class,
                            "SELECT factId FROM cards WHERE id IN " + Utils.ids2str(cardIds), 0)));
            tids = tagIds(allTags_("WHERE id IN " + fids));
            rows = splitTagsList("AND facts.id IN " + fids);
        }

        ArrayList<HashMap<String, Long>> d = new ArrayList<HashMap<String, Long>>();

        for (Long id : rows.keySet()) {
            for (int src = 0; src < 3; src++) { // src represents the tag type, fact: 0, model: 1, template: 2
                HashMap<String, Long> ditem = new HashMap<String, Long>();
                for (String tag : Utils.parseTags(rows.get(id).get(src))) {
                    ditem.put("cardId", id);
                    ditem.put("tagId", tids.get(tag.toLowerCase()));
                    ditem.put("src", new Long(src));
                }
                d.add(ditem);
            }
        }

        for (HashMap<String, Long> ditem : d) {
            getDB().database.execSQL("INSERT INTO cardTags (cardId, tagId, src) VALUES " +
                    "(" + ditem.get("cardId") + ", " +
                    ditem.get("tagId") + ", " +
                    ditem.get("src") + ")");
        }
        getDB().database.execSQL("DELETE FROM tags WHERE priority = 2 AND id NOT IN " +
                "(SELECT DISTINCT tagId FROM cardTags)");
    }
    
    
    /*
     * Tags: adding/removing in bulk*********************************************************
     */
    public static final String TAG_MARKED = "Marked";


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

        for (int i = 0; i < factTagsList.size(); i++) {
            String newTags = factTagsList.get(i);

            if (newTags.indexOf(tag) == -1) {
                if (newTags.length() == 0) {
                    newTags += tag;
                } else {
                    newTags += "," + tag;
                }
            }
            Log.i(TAG, "old tags = " + factTagsList.get(i));
            Log.i(TAG, "new tags = " + newTags);

            if (newTags.length() > factTagsList.get(i).length()) {
                getDB().database.execSQL("update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                        + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0))
                        + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = getDB().queryColumn(String.class, "select id from cards where factId in "
                + Utils.ids2str(factIds), 0);

        ContentValues values = new ContentValues();

        for (int i = 0; i < cardIdList.size(); i++) {
            String cardId = cardIdList.get(i);
            try {
                // Check if the tag already exists
                getDB().queryScalar("select id from cardTags" + " where cardId = " + cardId + " and tagId = " + tagId
                        + " and src = 0");
            } catch (SQLException e) {
                values.put("cardId", cardId);
                values.put("tagId", tagId);
                values.put("src", "0");
                getDB().database.insert("cardTags", null, values);
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

        for (int i = 0; i < factTagsList.size(); i++) {
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
            Log.i(TAG, "old tags = " + factTags);
            Log.i(TAG, "new tags = " + newTags);

            if (newTags.length() < factTags.length()) {
                getDB().database.execSQL("update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                        + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0))
                        + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = getDB().queryColumn(String.class, "select id from cards where factId in "
                + Utils.ids2str(factIds), 0);

        for (int i = 0; i < cardIdList.size(); i++) {
            String cardId = cardIdList.get(i);
            getDB().database.execSQL("delete from cardTags" + " WHERE cardId = " + cardId + " and tagId = " + tagId
                    + " and src = 0");
        }

        // delete unused tags from tags table
        try {
            getDB().queryScalar("select id from cardTags where tagId = " + tagId + " limit 1");
        } catch (SQLException e) {
            getDB().database.execSQL("delete from tags" + " where id = " + tagId);
        }

        flushMod();
    }


    /*
     * Suspending*****************************
     */

    // public void suspendCard(long cardId) {
    // long[] ids = new long[1];
    // ids[0] = cardId;
    // suspendCards(ids);
    // }

    public void suspendCards(long[] ids) {
        getDB().database.execSQL("UPDATE cards SET type = relativeDelay -3, priority = -3, modified = "
                + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0))
                + ", isDue = 0 WHERE type >= 0 AND id IN " + Utils.ids2str(ids));
        flushMod();
        reset();
    }


    // public void unsuspendCard(long cardId) {
    // long[] ids = new long[1];
    // ids[0] = cardId;
    // unsuspendCards(ids);
    // }

    public void unsuspendCards(long[] ids) {
        getDB().database.execSQL("UPDATE cards SET type = relativeDelay, priority = 0, " + "modified = "
                + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0))
                + " WHERE type < 0 AND id IN " + Utils.ids2str(ids));
        updatePriorities(ids);
        flushMod();
        reset();
    }


    /**
     * Priorities
     *******************************/

    /**
     * Update all card priorities if changed.
     * If partial is true, only updates cards with tags defined as priority low, med or high in the deck,
     * or with tags whose priority is set to 2 and they are not found in the priority tags of the deck.
     * If false, it updates all card priorities
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
                cur = getDB().database.rawQuery("SELECT id, priority AS pri FROM tags", null);
                while (cur.moveToNext()) {
                    newPriorities.put(cur.getLong(0), cur.getInt(1));
                }
            } catch (SQLException e) {
                Log.e(TAG, "updateAllPriorities: Error while getting all tags: " + e.toString());
            } finally {
                if (cur != null) {
                    cur.close();
                }
            }
            ArrayList<Long> cids = getDB().queryColumn(Long.class,
                    "SELECT DISTINCT cardId FROM cardTags WHERE tagId in " +
                    Utils.ids2str(Utils.toPrimitive(newPriorities.keySet())), 0);
            updatePriorities(Utils.toPrimitive(cids), null, dirty);
        }
    }

    /**
     * Update priority setting on tags table
     */
    private HashMap<Long, Integer> updateTagPriorities() {
        // Make sure all priority tags exist
        for (String s : new String[] {lowPriority, medPriority, highPriority, suspended}) {
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
            cur = getDB().database.rawQuery("SELECT tag, id, priority FROM tags", null);
            while (cur.moveToNext()) {
                tagNames.add(cur.getString(0).toLowerCase());
                tagIdList.add(cur.getLong(1));
                tagPriorities.add(cur.getInt(2));
            }
        } catch (SQLException e) {
            Log.e(TAG, "updateTagPriorities: Error while tag priorities: " + e.toString());
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        HashMap<String, Integer> typeAndPriorities = new HashMap<String, Integer>();
        typeAndPriorities.put(lowPriority, 1);
        typeAndPriorities.put(medPriority, 3);
        typeAndPriorities.put(highPriority, 4);
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
                getDB().database.execSQL("UPDATE tags SET priority = " + newPriorities.get(tagId)
                        + " WHERE id = " + tagId);
            } catch (SQLException e) {
                Log.e(TAG, "updatePriorities: Error while updating tag priorities for tag " +
                        tag + ": " + e.toString());
                continue;
            }
        }
        return newPriorities;
    }


    private void updatePriorities(long[] cardIds) {
        updatePriorities(cardIds, null, true);
    }
    private void updatePriorities(long[] cardIds, String[] suspend) {
        updatePriorities(cardIds, suspend, true);
    }
    void updatePriorities(long[] cardIds, String[] suspend, boolean dirty) {
        Cursor cursor = null;
        Log.i(TAG, "updatePriorities - Updating priorities...");
        // Any tags to suspend
        if (suspend != null && suspend.length > 0) {
            long ids[] = Utils.toPrimitive(tagIds(suspend, false).values());
            getDB().database.execSQL("UPDATE tags SET priority = 0 WHERE id in " + Utils.ids2str(ids));
        }

        String limit = "";
        if (cardIds.length <= 1000) {
            limit = "and cardTags.cardId in " + Utils.ids2str(cardIds);
        }
        String query = "SELECT cardTags.cardId, CASE WHEN max(tags.priority) > 2 THEN max(tags.priority) "
                + "WHEN min(tags.priority) = 1 THEN 1 ELSE 2 END FROM cardTags,tags "
                + "WHERE cardTags.tagId = tags.id " + limit + " GROUP BY cardTags.cardId";
        try {
            cursor = getDB().database.rawQuery(query, null);
            if (cursor.moveToFirst()) {
                int len = cursor.getCount();
                long[][] cards = new long[len][2];
                for (int i = 0; i < len; i++) {
                    cards[i][0] = cursor.getLong(0);
                    cards[i][1] = cursor.getInt(1);
                }

                String extra = "";
                if (dirty) {
                    extra = ", modified = "
                            + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0));
                }
                for (int pri = 0; pri < 5; pri++) {
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
                    getDB().database.execSQL("UPDATE cards " + "SET priority = " + pri + extra + " WHERE id in "
                            + Utils.ids2str(cs) + " and " + "priority != " + pri + " and " + "priority >= -2");
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        reset();
    }


    /*
     * Counts related to due cards *********************************************************
     */

    private int newCardsDoneToday() {
        return (dailyStats.newEase0 + dailyStats.newEase1 + dailyStats.newEase2 + dailyStats.newEase3 + dailyStats.newEase4);
    }


    /*
     * Card Predicates*********************************************************
     */

    private String cardState(Card card) {
        if (cardIsNew(card)) {
            return "new";
        } else if (card.interval > MATURE_THRESHOLD) {
            return "mature";
        }
        return "young";
    }


    /**
     * Check if a card is a new card.
     * 
     * @param card The card to check.
     * @return True if a card has never been seen before.
     */
    private boolean cardIsNew(Card card) {
        return card.reps == 0;
    }


    /**
     * Check if a card is a new card.
     * 
     * @param card The card to check.
     * @return True if card should use present intervals.
     */
    private boolean cardIsBeingLearnt(Card card) {
        return card.lastInterval < 7;
    }


    /*
     * Cards CRUD*********************************************************
     */

    public void deleteCards(List<String> ids) {
        Log.i(TAG, "deleteCards = " + ids.toString());

        // Bulk delete cards by ID
        if (ids != null && ids.size() > 0) {
            commitToDB();
            double now = System.currentTimeMillis() / 1000.0;
            Log.i(TAG, "Now = " + now);
            String idsString = Utils.ids2str(ids);

            // Grab fact ids
            // ArrayList<String> factIds = ankiDB.queryColumn(String.class,
            // "SELECT factId FROM cards WHERE id in " + idsString,
            // 0);

            // Delete cards
            getDB().database.execSQL("DELETE FROM cards WHERE id in " + idsString);

            // Note deleted cards
            String sqlInsert = "INSERT INTO cardsDeleted values (?," + String.format(ENGLISH_LOCALE, "%f", now) + ")";
            SQLiteStatement statement = getDB().database.compileStatement(sqlInsert);
            for (int i = 0; i < ids.size(); i++) {
                statement.bindString(1, ids.get(i));
                statement.executeInsert();
            }
            statement.close();

            // Gather affected tags (before we delete the corresponding cardTags)
            ArrayList<String> tags = getDB().queryColumn(String.class, "SELECT tagId FROM cardTags WHERE cardId in "
                    + idsString, 0);

            // Delete cardTags
            getDB().database.execSQL("DELETE FROM cardTags WHERE cardId in " + idsString);

            // Find out if this tags are used by anything else
            ArrayList<String> unusedTags = new ArrayList<String>();
            for (int i = 0; i < tags.size(); i++) {
                String tagId = tags.get(i);
                Cursor cursor = getDB().database.rawQuery("SELECT * FROM cardTags WHERE tagId = " + tagId + " LIMIT 1",
                        null);
                if (!cursor.moveToFirst()) {
                    unusedTags.add(tagId);
                }
                cursor.close();
            }

            // Delete unused tags
            getDB().database.execSQL("DELETE FROM tags WHERE id in " + Utils.ids2str(unusedTags) + " and priority = 2");

            // Remove any dangling fact
            deleteDanglingFacts();
            flushMod();
            reset();
        }
    }


    /*
     * Facts CRUD*********************************************************
     */

    public void deleteFacts(List<String> ids) {
        Log.i(TAG, "deleteFacts = " + ids.toString());
        int len = ids.size();
        if (len > 0) {
            commitToDB();
            double now = System.currentTimeMillis() / 1000.0;
            String idsString = Utils.ids2str(ids);
            Log.i(TAG, "DELETE FROM facts WHERE id in " + idsString);
            getDB().database.execSQL("DELETE FROM facts WHERE id in " + idsString);
            Log.i(TAG, "DELETE FROM fields WHERE factId in " + idsString);
            getDB().database.execSQL("DELETE FROM fields WHERE factId in " + idsString);
            String sqlInsert = "INSERT INTO factsDeleted VALUES(?," + String.format(ENGLISH_LOCALE, "%f", now) + ")";
            SQLiteStatement statement = getDB().database.compileStatement(sqlInsert);
            for (int i = 0; i < len; i++) {
                Log.i(TAG, "inserting into factsDeleted");
                statement.bindString(1, ids.get(i));
                statement.executeInsert();
            }
            statement.close();
            setModified();
            reset();
        }
    }


    /**
     * Delete any fact without cards
     * 
     * @return ArrayList<String> list with the id of the deleted facts
     */
    public ArrayList<String> deleteDanglingFacts() {
        Log.i(TAG, "deleteDanglingFacts");
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

    // TODO: Handling of the list of models and currentModel
    public void deleteModel(String id) {
        Log.i(TAG, "deleteModel = " + id);
        Cursor cursor = null;
        boolean modelExists = false;

        try {
            cursor = getDB().database.rawQuery("SELECT * FROM models WHERE id = " + id, null);
            // Does the model exist?
            if (cursor.moveToFirst()) {
                modelExists = true;
            }
        } finally {
            cursor.close();
        }

        if (modelExists) {
            // Delete the cards that use the model id, through fact
            ArrayList<String> cardsToDelete = getDB().queryColumn(String.class,
                            "SELECT cards.id FROM cards, facts WHERE facts.modelId = " + id
                                    + " AND facts.id = cards.factId", 0);
            deleteCards(cardsToDelete);

            // Delete model
            getDB().database.execSQL("DELETE FROM models WHERE id = " + id);

            // Note deleted model
            ContentValues values = new ContentValues();
            values.put("modelId", id);
            values.put("deletedTime", System.currentTimeMillis() / 1000.0);
            getDB().database.insert("modelsDeleted", null, values);

            flushMod();
        }
    }


    public void deleteFieldModel(String modelId, String fieldModelId) {
        Log.i(TAG, "deleteFieldModel, modelId = " + modelId + ", fieldModelId = " + fieldModelId);

        long start, stop;
        start = System.currentTimeMillis();

        // Delete field model
        getDB().database.execSQL("DELETE FROM fields WHERE fieldModel = " + fieldModelId);

        // Note like modified the facts that use this model
        getDB().database.execSQL("UPDATE facts SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", (System.currentTimeMillis() / 1000.0)) + " WHERE modelId = "
                + modelId);

        // TODO: remove field model from list

        // Update Question/Answer formats
        // TODO: All these should be done with the field object
        String fieldName = "";
        Cursor cursor = getDB().database.rawQuery("SELECT name FROM fieldModels WHERE id = " + fieldModelId, null);
        if (cursor.moveToNext()) {
            fieldName = cursor.getString(0);
        }
        cursor.close();

        cursor = getDB().database.rawQuery("SELECT id, qformat, aformat FROM cardModels WHERE modelId = " + modelId,
                null);
        String sql = "UPDATE cardModels SET qformat = ?, aformat = ? WHERE id = ?";
        SQLiteStatement statement = getDB().database.compileStatement(sql);
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
        cursor.close();
        statement.close();

        // TODO: updateCardsFromModel();

        // Note the model like modified (TODO: We should use the object model instead handling the DB directly)
        getDB().database.execSQL("UPDATE models SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", System.currentTimeMillis() / 1000.0) + " WHERE id = " + modelId);

        flushMod();

        stop = System.currentTimeMillis();
        Log.v(TAG, "deleteFieldModel - deleted field models in " + (stop - start) + " ms.");
    }


    public void deleteCardModel(String modelId, String cardModelId) {
        Log.i(TAG, "deleteCardModel, modelId = " + modelId + ", fieldModelId = " + cardModelId);
        
        // Delete all cards that use card model from the deck
        ArrayList<String> cardIds = getDB().queryColumn(String.class, "SELECT id FROM cards WHERE cardModelId = "
                + cardModelId, 0);
        deleteCards(cardIds);

        // I assume that the line "model.cardModels.remove(cardModel)" actually deletes cardModel from DB (I might be
        // wrong)
        getDB().database.execSQL("DELETE FROM cardModels WHERE id = " + cardModelId);

        // Note the model like modified (TODO: We should use the object model instead handling the DB directly)
        getDB().database.execSQL("UPDATE models SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", System.currentTimeMillis() / 1000.0) + " WHERE id = " + modelId);

        flushMod();
    }


    // CSS for all the fields
    private String rebuildCSS() {
        String css = "";
        Cursor cur = null;
        
        cur = getDB().database.rawQuery(
                "SELECT id, quizFontFamily, quizFontSize, quizFontColour, -1, features FROM fieldModels", null);
        while (cur.moveToNext()) {
            css += _genCSS(".fm", cur);
        }
        cur.close();
        cur = getDB().database.rawQuery(
                "SELECT id, questionFontFamily, questionFontSize, questionFontColour, " +
                "questionAlign, 0 FROM cardModels", null);
        while (cur.moveToNext()) {
            css += _genCSS("#cmq", cur);
        }
        cur.close();
        cur = getDB().database.rawQuery(
                "SELECT id, answerFontFamily, answerFontSize, answerFontColour, " +
                "answerAlign, 0 FROM cardModels", null);
        while (cur.moveToNext()) {
            css += _genCSS("#cma", cur);
        }
        cur.close();
        cur = getDB().database.rawQuery("SELECT id, lastFontColour FROM cardModels", null);
        while (cur.moveToNext()) {
            css += ".cmb" + Utils.hexifyID(cur.getLong(0)) + " {background:" + cur.getString(1) + ";}\n";
        }
        cur.close();
        setVar("cssCache", css, false);
        addHexCache();
        
        return css;
    }
    private String _genCSS(String prefix, Cursor row) {
        String t = "";
        long id = row.getLong(0); 
        String fam = row.getString(1);
        int siz = row.getInt(2);
        String col = row.getString(3);
        int align = row.getInt(4);
        String rtl = row.getString(5);
        if (fam != null) {
            t += "font-family:\"" + fam + "\";";
        }
        if (siz != 0) {
            t += "font-size:" + siz + "px;";
        }
        if (col != null) {
            t += "color:" + col + ";";
        }
        if (rtl != null && rtl.compareTo("rtl") == 0) {
            t += "direction:rtl;unicode-bidi:embed;";
        }
        if (align != -1) {
            if (align == 0) {
                t += "text-align:center;";
            } else if (align == 1) {
                t += "text-align:left;";
            } else {
                t += "text-align:right;";
            }
        }
        if (t.length() > 0) {
            t = prefix + Utils.hexifyID(id) + " {" + t + "}\n";
        }
        return t;
    }
    private void addHexCache() {
        ArrayList<Long> ids = getDB().queryColumn(Long.class,
                "SELECT id FROM fieldModels UNION SELECT id FROM cardModels UNION SELECT id FROM models", 0);
        JSONObject jsonObject = new JSONObject();
        for (Long id : ids) {
            try {
                jsonObject.put(id.toString(), Utils.hexifyID(id.longValue()));
            } catch (JSONException e) {
                Log.e(TAG, "addHexCache: Error while generating JSONObject: " + e.toString());
                throw new RuntimeException(e);
            }
        }
        setVar("hexCache", jsonObject.toString(), false);
    }





    /*
     * Undo/Redo*********************************************************
     */
    private class UndoRow {
        String name;
        Long start;
        Long end;


        UndoRow(String name, Long start, Long end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }


    // FIXME: Where should this be called from?
    @SuppressWarnings("unused")
    private void initUndo() {
        undoStack = new Stack<UndoRow>();
        redoStack = new Stack<UndoRow>();
        undoEnabled = true;

        getDB().database.execSQL("CREATE TEMPORARY TABLE undoLog (seq INTEGER PRIMARY KEY NOT NULL, sql TEXT)");

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
            String sql = "CREATE TEMP TRIGGER _undo_%s_it " + "AFTER INSERT ON %s BEGIN "
                    + "INSERT INTO undoLog VALUES " + "(null, 'DELETE FROM %s WHERE rowid = ' || new.rowid); END";
            getDB().database.execSQL(String.format(ENGLISH_LOCALE, sql, table, table, table));
            // Update trigger
            sql = String.format(ENGLISH_LOCALE, "CREATE TEMP TRIGGER _undo_%s_ut " + "AFTER UPDATE ON %s BEGIN "
                    + "INSERT INTO undoLog VALUES " + "(null, 'UPDATE %s ", table, table, table);
            String sep = "SET ";
            for (String column : columns) {
                if (column.equals("unique")) {
                    continue;
                }
                sql += String.format(ENGLISH_LOCALE, "%s%s=' || quote(old.%s) || '", sep, column, column);
                sep = ",";
            }
            sql += "WHERE rowid = ' || old.rowid); END";
            getDB().database.execSQL(sql);
            // Delete trigger
            sql = String.format(ENGLISH_LOCALE, "CREATE TEMP TRIGGER _undo_%s_dt " + "BEFORE DELETE ON %s BEGIN "
                    + "INSERT INTO undoLog VALUES " + "(null, 'INSERT INTO %s (rowid", table, table, table);
            for (String column : columns) {
                sql += String.format(ENGLISH_LOCALE, ",\"%s\"", column);
            }
            sql += ") VALUES (' || old.rowid ||'";
            for (String column : columns) {
                if (column.equals("unique")) {
                    sql += ",1";
                    continue;
                }
                sql += String.format(ENGLISH_LOCALE, ", ' || quote(old.%s) ||'", column);
            }
            sql += ")'); END";
            getDB().database.execSQL(sql);
        }
    }


    public void setUndoBarrier() {
        if (undoStack.isEmpty() || undoStack.peek() != null) {
            undoStack.push(null);
        }
    }


    public void setUndoStart(String name) {
        setUndoStart(name, false);
    }


    /**
     * @param reviewEarly set to true for early review
     */
    public void setReviewEarly(boolean reviewEarly) {
        this.reviewEarly = reviewEarly;
    }


    public void setUndoStart(String name, boolean merge) {
        if (!undoEnabled) {
            return;
        }
        commitToDB();
        if (merge && !undoStack.isEmpty()) {
            if ((undoStack.peek() != null) && (undoStack.peek().name.equals(name))) {
                return;
            }
        }
        undoStack.push(new UndoRow(name, latestUndoRow(), null));
    }


    public void setUndoEnd(String name) {
        if (!undoEnabled) {
            return;
        }
        commitToDB();
        long end = latestUndoRow();
        while (undoStack.peek() == null) {
            undoStack.pop(); // Strip off barrier
        }
        UndoRow row = undoStack.peek();
        row.end = end;
        if (row.start == row.end) {
            undoStack.pop();
        } else {
            redoStack.clear();
        }
    }


    private long latestUndoRow() {
        long result;
        try {
            result = getDB().queryScalar("SELECT MAX(rowid) FROM undoLog");
        } catch (SQLException e) {
            result = 0;
        }
        return result;
    }


    private void undoredo(Stack<UndoRow> src, Stack<UndoRow> dst) {

        UndoRow row;
        commitToDB();
        while (true) {
            row = src.pop();
            if (row != null) {
                break;
            }
        }
        Long start = row.start;
        Long end = row.end;
        if (end == null) {
            end = latestUndoRow();
        }
        ArrayList<String> sql = getDB().queryColumn(String.class, String.format(ENGLISH_LOCALE,
                "SELECT sql FROM undoLog " + "WHERE seq > %d and seq <= %d " + "ORDER BY seq DESC", start, end), 0);
        Long newstart = latestUndoRow();
        for (String s : sql) {
            getDB().database.execSQL(s);
        }

        Long newend = latestUndoRow();
        dst.push(new UndoRow(row.name, newstart, newend));
    }


    public void undo() {
        undoredo(undoStack, redoStack);
        commitToDB();
        reset();
    }


    public void redo() {
        undoredo(redoStack, undoStack);
        commitToDB();
        reset();
    }


    /*
     * Dynamic indices*********************************************************
     */

    public void updateDynamicIndices() {
        Log.i(TAG, "updateDynamicIndices - Updating indices...");
        HashMap<String, String> indices = new HashMap<String, String>();
        indices.put("intervalDesc", "(type, priority desc, interval desc)");
        indices.put("intervalAsc", "(type, priority desc, interval)");
        indices.put("randomOrder", "(type, priority desc, factId, ordinal)");
        indices.put("dueAsc", "(type, priority desc, combinedDue)");
        indices.put("dueDesc", "(type, priority desc, combinedDue desc)");

        ArrayList<String> required = new ArrayList<String>();
        if (revCardOrder == REV_CARDS_OLD_FIRST) {
            required.add("intervalDesc");
        }
        if (revCardOrder == REV_CARDS_NEW_FIRST) {
            required.add("intervalAsc");
        }
        if (revCardOrder == REV_CARDS_RANDOM) {
            required.add("randomOrder");
        }
        if (revCardOrder == REV_CARDS_DUE_FIRST || newCardOrder == NEW_CARDS_OLD_FIRST
                || newCardOrder == NEW_CARDS_RANDOM) {
            required.add("dueAsc");
        }
        if (newCardOrder == NEW_CARDS_NEW_FIRST) {
            required.add("dueDesc");
        }

        Set<Entry<String, String>> entries = indices.entrySet();
        Iterator<Entry<String, String>> iter = entries.iterator();
        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            if (required.contains(entry.getKey())) {
                getDB().database.execSQL("CREATE INDEX IF NOT EXISTS " + "ix_cards_" + entry.getKey() + "2 ON cards "
                        + entry.getValue());
            } else {
                // Leave old indices for older clients
                getDB().database.execSQL("DROP INDEX IF EXISTS " + "ix_cards_" + entry.getKey() + "2");
            }
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
                id = getDB().database.insert("tags", null, value);
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
                getDB().database.execSQL("INSERT OR IGNORE INTO tags (tag) VALUES ('" + tag + "')");
            }
        }
        if (tags.length != 0) {
            String tagList = "";
            for (int i = 0; i < tags.length; i++) {
                tagList += "'" + tags[i] + "'";
                if (i < tags.length - 1) {
                    tagList += ", ";
                }
            }
            Cursor cur = getDB().database.rawQuery("SELECT tag, id FROM tags WHERE tag in (" +
                    tagList + ")", null);
            while (cur.moveToNext()) {
                results.put(cur.getString(0).toLowerCase(), cur.getLong(1));
            }
            cur.close();
        }
        return results;
    }

}
