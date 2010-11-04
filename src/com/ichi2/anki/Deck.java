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
import java.util.Map.Entry;

/**
 * A deck stores all of the cards and scheduling information. It is saved in a file with a name ending in .anki
 *
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Deck
 */
public class Deck {
    // Various constants
    private static final int SECS_IN_ONE_DAY = 86400;

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
    private static final String revOrderStrings[] = {"priority desc, interval desc",
        "priority desc, interval",
        "priority desc, combinedDue",
    "priority desc, factId, ordinal"};
    private static final String newOrderStrings[] = {"priority desc, combinedDue",
        "priority desc, combinedDue",
    "priority desc, combinedDue desc"};

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
    double delay0;

    double delay1;

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
            deck.delay0 = cursor.getDouble(14);
            deck.delay1 = cursor.getDouble(15);
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
        Log.i(TAG,
                String.format(ENGLISH_LOCALE, "openDeck - modified: %f currentTime: %f", deck.modified,
                        System.currentTimeMillis() / 1000.0));

        deck.deckPath = path;
        deck.deckName = (new File(path)).getName().replace(".anki", "");
        deck.initVars();

        double oldMod = deck.modified;

        // Ensure necessary indices are available
        deck.updateDynamicIndices();

        // FIXME: Temporary code for upgrade - ensure cards suspended on older clients are recognized
        // Ensure cards suspended on older clients are recognized
        try {
            deck.getDB().database.execSQL(
                    "UPDATE cards SET type = type - 3 WHERE type IN (0,1,2) AND priority = -3", null);
        } catch (Exception e) {
            // TODO: Report error and abort
        }

        // Ensure hard scheduling over a day if per day
        if (deck.deckVars.getBool("perDay")) {
            deck.hardIntervalMin = Math.max(1.0, deck.hardIntervalMin);
            deck.hardIntervalMax = Math.max(1.1, deck.hardIntervalMax);
        }

        ArrayList<Long> ids = new ArrayList(0);
        try {
            // Unsuspend buried - can remove priorities in the future
            ids = deck.getDB().queryColumn(long.class,
                    "SELECT id FROM cards WHERE type in (3,4,5) OR priority IN (-1,-2)", 0);
            if (!ids.isEmpty()) {
                deck.updatePriorities(Utils.toPrimitive((Long[]) ids.toArray()));
                deck.getDB().database.execSQL("UPDATE cards SET type = type -3 WHERE type IN (3,4,5)", null);
                // Save deck to database
                deck.commitToDB();
            }
        } catch (Exception e) {
            // TODO: Error in query, do something about it?
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

        // Rebuild queue is not rebuild already
        if (ids.isEmpty()) {
            deck.reset();
        }
        // Make sure we haven't accidentally bumped the modification time
        assert deck.modified == oldMod;
        // Create a temporary view for random new cards. Randomizing the cards by themselves
        // as is done in desktop Anki in Deck.randomizeNewCards() takes too long.
        deck.getDB().database.execSQL("CREATE TEMPORARY VIEW acqCardsRandom AS " + "SELECT * FROM cards "
                + "WHERE type = 2 AND isDue = 1 " + "ORDER BY RANDOM()");

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
     * deckVars class
     */
    private class DeckVars {
        AnkiDb mDB;
        public DeckVars(String path) {
            mDB = AnkiDatabaseManager.getDatabase(path);
        }
        public boolean hasKey(String key) {
            return mDB.database.rawQuery("SELECT 1 FROM deckVars WHERE key = '" + key + "'", null).moveToNext();
        }
        public boolean getInt(String key, long value) {
            Cursor cur = mDB.database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.isFirst()) {
                value = cur.getLong(0);
                return true;
            }
            return false;
        }
        public boolean getBool(String key) {
            Cursor cur = mDB.database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.isFirst()) {
                return (cur.getInt(0) != 0);
            }
            return false;
        }
        public String getVar(String key) {
            Cursor cur = mDB.database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.isFirst()) {
                return cur.getString(0);
            }
            return null;
        }
        public void setVar(String key, String value) {
            setVar(key, value, true);
        }
        public void setVar(String key, String value, boolean mod) {
            Cursor cur = mDB.database.rawQuery("SELECT value FROM deckVars WHERE key = '" + key + "'", null);
            if (cur.isFirst()) {
                if (cur.getString(0).equals(value)) {
                    return;
                } else {
                    mDB.database.execSQL("update deckVars set value='" + value + "' where key = '" + key +"'", null);
                }
            } else {
                mDB.database.execSQL("insert into deckVars (key, value) values ('" + value + "', '" + key +"')", null);
            }
        }
        public void setVarDefault(String key, String value) {
            if (!hasKey(key)) {
                mDB.database.execSQL("INSERT INTO deckVars (key, value) values ('" + key + "', '" + value + "')");
            }
        }
    }
    private DeckVars deckVars;

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
        DeckVars deckVars = new DeckVars(deckPath);
        if (!deckVars.hasKey("revInactive")) {
            deckVars.setVarDefault("suspendLeeches", "1");
            deckVars.setVarDefault("leechFails", "16");
            deckVars.setVarDefault("perDay", "1");
            deckVars.setVarDefault("newActive", "");
            deckVars.setVarDefault("revActive", "");
            deckVars.setVarDefault("newInactive", suspended);
            deckVars.setVarDefault("revInactive", suspended);
        }
        updateCutoff();
        setupStandardScheduler();
    }

    private boolean modifiedSinceSave() {
        return modified > lastLoaded;
    }

    /*
     * Queue Management
     *******************************/

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
        } catch (Exception e) {
            Log.e(TAG, "Standard scheduler: Error in overridable methods: " + e.toString());
            return;
        }
        scheduler = "standard";
        // Restore any cards temporarily suspended by alternate schedulers
        try {
            resetAfterReviewEarly();
        } catch (Exception e) {
            // Will fail if deck hasn't been upgraded yet
            return;
        }
    }

    /*
     * Scheduler related overridable methods
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

    private long getCardId() {
        try {
            return ((Long)getCardIdMethod.invoke(true)).longValue();
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method getCardId(boolean): " + e.toString());
            return 0l;
        }
    }
    private long getCardId(boolean check) {
        try {
            return ((Long)getCardIdMethod.invoke(check)).longValue();
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method getCardId(boolean): " + e.toString());
            return 0l;
        }
    }
    private void fillFailedQueue() {
        try {
            fillFailedQueueMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method fillFailedQueue(): " + e.toString());
        }
    }
    private void fillRevQueue() {
        try {
            fillRevQueueMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method fillRevQueue(): " + e.toString());
        }
    }
    private void fillNewQueue() {
        try {
            fillNewQueueMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method fillNewQueue(): " + e.toString());
        }
    }
    private void rebuildFailedCount() {
        try {
            rebuildFailedCountMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method rebuildFailedCount(): " + e.toString());
        }
    }
    private void rebuildRevCount() {
        try {
            rebuildRevCountMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method rebuildRevCount(): " + e.toString());
        }
    }
    private void rebuildNewCount() {
        try {
            rebuildNewCountMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method rebuildNewCount(): " + e.toString());
        }
    }
    private void requeueCard(Card card, int oldSuc) {
        try {
            requeueCardMethod.invoke(card, oldSuc);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method requeueCard(Card, int): " + e.toString());
        }
    }
    private boolean timeForNewCard() {
        try {
            return ((Boolean)timeForNewCardMethod.invoke(null)).booleanValue();
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method timeForNewCard(): " + e.toString());
            return false;
        }
    }
    private void updateNewCountToday() {
        try {
            updateNewCountTodayMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method updateNewCountToday(): " + e.toString());
        }
    }
    private int cardQueue(Card card) {
        try {
            return ((Integer)cardQueueMethod.invoke(card)).intValue();
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method cardQueue(Card): " + e.toString());
            return -1;
        }
    }
    private void finishScheduler() {
        try {
            finishSchedulerMethod.invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method finishScheduler(): " + e.toString());
        }
    }
    private void answerCard(Card card, int ease) {
        try {
            answerCardMethod.invoke(card, ease);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method answerCard(Card, int): " + e.toString());
        }
    }
    private String cardLimit(String active, String inactive, String sql) {
        try {
            return ((String)cardLimitMethod.invoke(active, inactive, sql));
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method cardLimit(String, String, String): " + e.toString());
            return sql;
        }
    }
    private String cardLimit(String[] active, String sql) {
        try {
            return ((String)cardLimitMethod.invoke(active, sql));
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method cardLimit(String[], String): " + e.toString());
            return sql;
        }
    }
    private void answerPreSave(Card card, int ease) {
        try {
            answerPreSaveMethod.invoke(card, ease);
        } catch (Exception e) {
            Log.e(TAG, "Error in overridable method answerPreSave(Card, int): " + e.toString());
        }
    }

    private void fillQueues() {
        fillFailedQueue();
        fillRevQueue();
        fillNewQueue();
    }

    private void rebuildCounts() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        // global counts
        cardCount = ankiDB.database.rawQuery("SELECT count(*) from cards", null).getInt(0);
        factCount = ankiDB.database.rawQuery("SELECT count(*) from facts", null).getInt(0);
        // due counts
        rebuildFailedCount();
        rebuildRevCount();
        rebuildNewCount();
    }

    private String _cardLimit(String active, String inactive, String sql) {
        String[] yes = Utils.parseTags(deckVars.getVar(active));
        String[] no = Utils.parseTags(deckVars.getVar(inactive));
        if (yes.length > 0) {
            long yids[] = tagIds(yes);
            long nids[] = tagIds(no);
            return sql.replace("WHERE ",
                    "WHERE +c.id IN (SELECT cardId FROM cardTags WHERE " +
                    "tagId IN " + Utils.ids2str(yids) + " AND tagId NOT IN " + Utils.ids2str(nids) + ") AND ");
        } else if (no.length > 0) {
            long nids[] = tagIds(no);
            return sql.replace("WHERE ",
                    "WHERE +c.id NOT IN (SELECT cardId FROM cardTags WHERE " +
                    "tagId IN " + Utils.ids2str(nids) + ") AND ");
        } else {
            return sql;
        }
    }


    private void _rebuildFailedCount() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        failedSoonCount = (int) ankiDB.queryScalar(cardLimit("revActive", "revInactive",
                "SELECT count(*) FROM cards c WHERE type = 0 AND combinedDue < " + dueCutoff));
    }

    private void _rebuildRevCount() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        revCount = (int) ankiDB.queryScalar(cardLimit("revActive", "revInactive",
                "SELECT count(*) FROM cards c WHERE type = 1 AND combinedDue < " + dueCutoff));
    }

    private void _rebuildNewCount() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        newCount = (int) ankiDB.queryScalar(cardLimit("newActive", "newInactive",
                "SELECT count(*) FROM cards c WHERE type = 2 AND combinedDue < " + dueCutoff));
    }

    private void _updateNewCountToday() {
        newCountToday = Math.max(Math.min(newCount, newCardsPerDay - newCardsDoneToday()), 0);
    }

    private void _fillFailedQueue() {
        if ((failedSoonCount != 0) && failedQueue.isEmpty()) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
            String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 0 AND combinedDue < " +
            dueCutoff + " ORDER BY combinedDue LIMIT " + queueLimit;
            Cursor cur = ankiDB.database.rawQuery(cardLimit("revActive", "revInactive", sql), null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
                failedQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }

    private void _fillRevQueue() {
        if ((revCount != 0) && revQueue.isEmpty()) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
            String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 1 AND combinedDue < " +
            dueCutoff + " ORDER BY " + revOrder() + " LIMIT " + queueLimit;
            Cursor cur = ankiDB.database.rawQuery(cardLimit("revActive", "revInactive", sql), null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1), cur.getDouble(2));
                revQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }

    private void _fillNewQueue() {
        if ((newCount != 0) && newQueue.isEmpty()) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
            String sql = "SELECT c.id, factId, combinedDue FROM cards c WHERE type = 2 AND combinedDue < " +
            dueCutoff + " ORDER BY " + newOrder() + " LIMIT " + queueLimit;
            Cursor cur = ankiDB.database.rawQuery(cardLimit("newActive", "newInactive", sql), null);
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
                fillFunc.invoke(fillFunc, null);
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
            long fid = ((QueueItem)queue.getLast()).getFactID();
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

    private void _requeueCard(Card card, int oldSuc) {
        if (card.reps == 1) {
            newQueue.removeLast();
        } else if (oldSuc == 0) {
            failedQueue.removeLast();
        } else {
            revQueue.removeLast();
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
        rebuildTypes("");
    }
    private void rebuildTypes(String where) {
        String lim = "type >= 0"; // Don't touch suspended cards
        if (where != "") {
            where += " AND " + lim;
        } else {
            where = " WHERE " + lim;
        }
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        ankiDB.database.execSQL("UPDATE cards " +
                "SET type = (CASE " +
                "WHEN successive = 0 AND reps != 0 THEN 0 -- failed " +
                "WHEN successive != 0 AND reps != 0 THEN 1 -- review " +
                "ELSE 2 -- new " +
                "END)" + where);
        // old-style suspended cards
        ankiDB.database.execSQL("UPDATE cards SET type = type - 3 WHERE priority = 0 AND type >= 0");
    }

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
        if (deckVars.getBool("perDay")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, (int) -utcOffset + SECS_IN_ONE_DAY);
            cal.set(Calendar.HOUR, 0);        // Yes, verbose but crystal clear
            cal.set(Calendar.MINUTE, 0);      // Apologies for that, here was my rant
            cal.set(Calendar.SECOND, 0);      // But if you can improve this bit and
            cal.set(Calendar.MILLISECOND, 0); // collapse it to one statement please do

            int newday = (int) utcOffset - (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 1000;
            Log.d(TAG, "New day happening at " + newday + " sec after 00:00 UTC");
            cal.add(Calendar.SECOND, newday);
            dueCutoff = cal.getTimeInMillis() / 1000.0;
            assert dueCutoff > System.currentTimeMillis();
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
        // Recache css
        // rebuildCSS();
    }

    // Checks if the day has rolled over.
    private void checkDailyStats() {
        if (!Stats.genToday(this).toString().equals(dailyStats.day.toString())) {
            dailyStats = Stats.dailyStats(this);
        }
    }


    /*
     * Review early
     *******************************/

    private void setupReviewEarlyScheduler() {
        try {
            fillRevQueueMethod = Deck.class.getDeclaredMethod("_fillRevEarlyQueue");
            rebuildRevCountMethod = Deck.class.getDeclaredMethod("_rebuildRevEarlyCount");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("_onReviewEarlyFinished");
            answerPreSaveMethod = Deck.class.getDeclaredMethod("_reviewEarlyPreSave", Card.class, int.class);
        } catch (Exception e) {
            Log.e(TAG, "Review Early scheduler: Error in overridable methods: " + e.toString());
            return;
        }
        scheduler = "reviewEarly";
    }

    private void _reviewEarlyPreSave(Card card, int ease) {
        if (ease > 1) {
            // Prevent it from appearing in next queue fill
            card.type += 6;
        }
    }

    private void resetAfterReviewEarly() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);

        ArrayList<Long> ids = ankiDB.queryColumn(long.class,
                "SELECT id FROM cards WHERE type IN (6,7,8) OR priority = -1", 0);

        if (ids != null) {
            updatePriorities(Utils.toPrimitive((Long[]) ids.toArray()));
            ankiDB.database.execSQL("UPDATE cards SET type = type -6 WHERE type IN (6,7,8)", null);
            flushMod();
        }
    }

    private void _onReviewEarlyFinished() {
        // Clean up buried cards
        resetAfterReviewEarly();
        // And go back to regular scheduler
        setupStandardScheduler();
    }

    private void _rebuildRevEarlyCount() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        String extraLim = ""; // In the future it would be nice to skip the first x days of due cards

        String sql = "SELECT count() FROM cards WHERE type = 1 AND combinedDue > " + dueCutoff + extraLim;
        revCount = (int) ankiDB.queryScalar(sql);
    }

    private void _fillRevEarlyQueue() {
        if ((revCount != 0) && revQueue.isEmpty()) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
            String sql = "SELECT id, factId FROM cards WHERE type = 1 AND combinedDue > " +
            dueCutoff + " ORDER BY combinedDue LIMIT " + queueLimit;
            Cursor cur = ankiDB.database.rawQuery(sql, null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                revQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }


    /*
     * Learn more
     *******************************/

    private void setupLearnMoreScheduler() {
        try {
            rebuildNewCountMethod = Deck.class.getDeclaredMethod("_rebuildLearnMoreCount");
            updateNewCountTodayMethod = Deck.class.getDeclaredMethod("_updateLearnMoreCountToday");
            finishSchedulerMethod = Deck.class.getDeclaredMethod("setupStandardScheduler");
        } catch (Exception e) {
            Log.e(TAG, "Learn More scheduler: Error in overridable methods: " + e.toString());
            return;
        }
        scheduler = "learnMore";
    }

    private void _rebuildLearnMoreCount() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        newCount = (int) ankiDB.queryScalar("SELECT count() FROM cards WHERE type = 2 AND combinedDue < " + dueCutoff);
    }

    private void _updateLearnMoreCountToday() {
        newCountToday = newCount;
    }


    /*
     * Cramming
     ***************************/

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
            // Reuse review early's code
            answerPreSaveMethod = Deck.class.getDeclaredMethod("_reviewEarlyPreSave", Card.class, int.class);
            cardLimitMethod = Deck.class.getDeclaredMethod("_cramCardLimit", String[].class, String.class, String.class);
        } catch (Exception e) {
            Log.e(TAG, "Cram scheduler: Error in overridable methods: " + e.toString());
            return;
        }
        scheduler = "cram";
    }

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

    private long _getCramCardId(boolean check) {
        checkDailyStats();
        fillQueues();

        if (failedSoonCount >= failedCardMax) {
            return ((QueueItem)failedQueue.getLast()).getCardID();
        }
        // Card due for review?
        if (revNoSpaced()) {
            return ((QueueItem)revQueue.getLast()).getCardID();
        }
        if (!failedQueue.isEmpty()) {
            return ((QueueItem)failedQueue.getLast()).getCardID();
        }
        // If we're in a custom scheduler, we may need to switch back
        if (finishSchedulerMethod != null) {
            finishScheduler();
            reset();
            return getCardId();
        }
        return 0l;
    }

    private int _cramCardQueue(Card card) {
        if ((!revQueue.isEmpty()) && (((QueueItem)revQueue.getLast()).getCardID() == card.id)) {
            return 1;
        } else {
            return 0;
        }
    }

    private void _requeueCramCard(Card card, int oldSuc) {
        if (cardQueue(card) == 1) {
            revQueue.removeLast();
        } else {
            failedCramQueue.removeLast();
        }
    }

    private void _rebuildNewCramCount() {
        newCount = 0;
        newCountToday = 0;
    }

    private String _cramCardLimit(String active[], String sql) {
        if (active.length > 1) {
            return sql.replace("WHERE ", "WHERE +c.id IN " +
                    Utils.ids2str(new ArrayList<String>(Arrays.asList(active))));
        } else if (active.length == 1){
            String[] yes = Utils.parseTags(active[0]);
            if (yes.length > 0) {
                long yids[] = tagIds(yes);
                return sql.replace("WHERE ",
                        "WHERE +c.id IN (SELECT cardId FROM cardTags WHERE " +
                        "tagId IN " + Utils.ids2str(yids) + ") AND ");
            } else {
                return sql;
            }
        } else {
            return sql;
        }
    }

    private void _fillCramQueue() {
        if ((revCount != 0) && revQueue.isEmpty()) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
            String sql = "SELECT id, factId FROM cards WHERE type IN (0,1,2) ORDER BY "
                + cramOrder + " LIMIT " + queueLimit;
            Cursor cur = ankiDB.database.rawQuery(cardLimit(activeCramTags, sql), null);
            while (cur.moveToNext()) {
                QueueItem qi = new QueueItem(cur.getLong(0), cur.getLong(1));
                revQueue.add(0, qi); // Add to front, so list is reversed as it is built
            }
        }
    }

    private void _rebuildCramCount() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        revCount = (int) ankiDB.queryScalar("SELECT count(*) FROM cards WHERE type IN (0,1,2)"); 
    }

    private void _rebuildFailedCramCount() {
        failedSoonCount = failedCramQueue.size();
    }

    private void _fillFailedCramQueue() {
        failedQueue = failedCramQueue;
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

        AnkiDatabaseManager.getDatabase(deckPath).database.update("decks", values, "id = " + id, null);
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
     * Getting the next card
     *******************************/

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
     * @param check Check for expired, or new day rollover
     * @return The Id of the next card, or 0 in case of error
     */
    private long _getCardId(boolean check) {
        checkDailyStats();
        fillQueues();
        updateNewCountToday();
        if (!failedQueue.isEmpty()) {
            // Failed card due?
            if ((delay0 != 0) && (((QueueItem)failedQueue.getLast()).getDue() < System.currentTimeMillis()/1000)) {
                return failedQueue.getLast().getCardID();
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
     * Get card: helper functions
     *******************************/

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
                if (getDB().queryScalar("SELECT 1 FROM cards WHERE id = " + revQueue.getLast().getCardID() +
                " AND priority = 4") == 1) {
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
        // FIXME: Isn't the check collapseTime != 0.0 always going to return true?
        return ((collapseTime != 0.0) || (delay0 == 0));
    }

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
        AnkiDatabaseManager.getDatabase(deckPath).database.update("cards", updateValues, "id = ?", new String[] { ""
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
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        ankiDB.database.execSQL("PRAGMA synchronous=NORMAL");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_duePriority");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_priorityDue");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_factor");
        ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_sort");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_factId");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_intervalDesc");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_intervalAsc");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_randomOrder");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_dueAsc");
        // ankiDB.database.execSQL("DROP INDEX IF EXISTS ix_cards_dueDesc");
        Log.w(TAG, "BEFORE UPDATE = " + (System.currentTimeMillis() - now));
    }


    public void afterUpdateCards() {
        long now = System.currentTimeMillis();
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_duePriority on cards (type, isDue, combinedDue, priority)");
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_priorityDue on cards (type, isDue, priority, combinedDue)");
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_factor on cards 			(type, factor)");
        ankiDB.database.execSQL("CREATE INDEX ix_cards_sort on cards (answer collate nocase)");
        // ankiDB.database.execSQL("CREATE INDEX ix_cards_factId on cards (factId, type)");
        // updateDynamicIndices();
        ankiDB.database.execSQL("PRAGMA synchronous=FULL");
        Log.w(TAG, "AFTER UPDATE = " + (System.currentTimeMillis() - now));
        // now = System.currentTimeMillis();
        // ankiDB.database.execSQL("ANALYZE");
        // Log.i("ANALYZE = " + System.currentTimeMillis() - now);
    }


    // TODO: The real methods to update cards on Anki should be implemented instead of this
    public void updateAllCards() {
        updateAllCardsFromPosition(0, Long.MAX_VALUE);
    }


    public long updateAllCardsFromPosition(long numUpdatedCards, long limitCards) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        // TODO: Cache this query, order by FactId, Id
        Cursor cursor = ankiDB.database.rawQuery("SELECT id, factId " + "FROM cards " + "ORDER BY factId, id "
                + "LIMIT " + limitCards + " OFFSET " + numUpdatedCards, null);

        ankiDB.database.beginTransaction();
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
            ankiDB.database.setTransactionSuccessful();
        } finally {
            ankiDB.database.endTransaction();
        }

        return numUpdatedCards;
    }


    /*
     * Answering a card*********************************************************
     */

    public void _answerCard(Card card, int ease) {
        Log.i(TAG, "answerCard");
        Cursor cursor = null;
        String undoName = "Answer Card";
        setUndoStart(undoName);
        double now = System.currentTimeMillis() / 1000.0;

        // Old state
        String oldState = cardState(card);
        int oldQueue = cardQueue(card);
        double lastDelaySecs = System.currentTimeMillis() / 1000.0 - card.combinedDue;
        double start = System.currentTimeMillis();
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
        if (lastDelay >= 0) {
            updateFactor(card, ease); // don't update factor if learning ahead
        }

        // Spacing
        double space = spaceUntilTime(card);
        spaceCards(card, ease);
        // Adjust counts for current card
        if (oldQueue == 0) {
            if (ease != 1) {
                failedSoonCount -= 1;
            }
        } else if (oldQueue == 1) {
            revCount -= 1;
        } else {
            newCount -= 1;
        }

        // card stats
        card.updateStats(ease, oldState);
        // Update type
        card.type = cardType(card);

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

        // // TODO: Fix leech handling
        // if (isLeech(card))
        // card = handleLeech(card);
        setUndoEnd(undoName);
    }

    private double spaceUntilTime(Card card) {
        Cursor cursor = null;
        double space, spaceFactor, minSpacing, minOfOtherCards;

        try {
            cursor = getDB().database.rawQuery("SELECT models.initialSpacing, models.spacing " +
                    "FROM facts, models WHERE facts.modelId = models.id and facts.id = " + card.factId, null);
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
            minOfOtherCards = getDB().queryScalar("SELECT min(interval) FROM cards WHERE factId = " + card.factId +
                    " AND id != " + card.id);
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

    private void spaceCards(Card card, double space) {
        Cursor cursor = null;
        // Adjust counts
        try {
            cursor = getDB().database.rawQuery("SELECT type, count(type) FROM cards WHERE factId = " +
                    card.factId + " AND combinedDue < " + dueCutoff + " id != " + card.id + ") GROUP BY type", null);
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
                + "combinedDue = max(%f, due), modified = %f, isDue = 0 WHERE id != %ld and factId = %ld",
                space, space, ((double) System.currentTimeMillis() / 1000.0), card.id, card.factId));
        // Update local cache of seen facts
        spacedFacts.put(card.factId, space);
    }

    // private boolean isLeech(Card card)
    // {
    // int no = card.noCount;
    // int fmax = getInt("leechFails");
    // if (fmax == 0)
    // return false;
    // return (
    // // failed
    // (card.successive == 0) &&
    // // greater than fail threshold
    // (no >= fmax) &&
    // // at least threshold/2 reps since last time
    // (fmax - no) % (Math.max(fmax/2, 1)) == 0);
    // }
    //
    // // TODO: not sure how this is supposed to affect the DB.
    // private Card handleLeech(Card card)
    // {
    // this.refresh();
    // Card scard = cardFromId(card.id, true);
    // tags = scard.fact.tags;
    // tags = addTags("Leech", tags);
    // scard.fact.tags = canonifyTags(tags);
    // scard.fact.setModified(textChanged=True);
    // self.updateFactTags(scard.fact.id);
    // self.s.flush();
    // self.s.expunge(scard);
    // if (getBool("suspendLeeches"))
    // suspendCards(card.id);
    // this.refresh();
    // }

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
                due = delay1;
            } else {
                due = delay0;
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
     * Tags: adding/removing in bulk*********************************************************
     */
    public static final String TAG_MARKED = "Marked";


    public ArrayList<String> factTags(long[] factIds) {
        return getDB().queryColumn(String.class,
                "SELECT tags FROM facts WHERE id IN " + Utils.ids2str(factIds), 0);
    }


    public void addTag(long factId, String tag) {
        long[] ids = new long[1];
        ids[0] = factId;
        addTag(ids, tag);
    }


    public void addTag(long[] factIds, String tag) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
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
                ankiDB.database.execSQL("update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                        + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0))
                        + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = ankiDB.queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        ContentValues values = new ContentValues();

        for (int i = 0; i < cardIdList.size(); i++) {
            String cardId = cardIdList.get(i);
            try {
                // Check if the tag already exists
                ankiDB.queryScalar("select id from cardTags" + " where cardId = " + cardId + " and tagId = " + tagId
                        + " and src = 0");
            } catch (SQLException e) {
                values.put("cardId", cardId);
                values.put("tagId", tagId);
                values.put("src", "0");
                ankiDB.database.insert("cardTags", null, values);
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
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
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
                ankiDB.database.execSQL("update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                        + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0))
                        + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = ankiDB.queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        for (int i = 0; i < cardIdList.size(); i++) {
            String cardId = cardIdList.get(i);
            ankiDB.database.execSQL("delete from cardTags" + " WHERE cardId = " + cardId + " and tagId = " + tagId
                    + " and src = 0");
        }

        // delete unused tags from tags table
        try {
            ankiDB.queryScalar("select id from cardTags where tagId = " + tagId + " limit 1");
        } catch (SQLException e) {
            ankiDB.database.execSQL("delete from tags" + " where id = " + tagId);
        }

        flushMod();
    }


    /*
     * Suspending
     *******************************/

    //public void suspendCard(long cardId) {
    //    long[] ids = new long[1];
    //    ids[0] = cardId;
    //    suspendCards(ids);
    //}


    public void suspendCards(long[] ids) {
        getDB().database.execSQL("UPDATE cards SET type = (CASE " +
                "WHEN successive = 0 THEN -2 WHEN reps THEN -3 ELSE -1 END), " +
                "priority = -3, modified = " +
                String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0)) +
                ", isDue = 0 WHERE type >= 0 AND id IN " + Utils.ids2str(ids));
        flushMod();
        reset();
    }


    //public void unsuspendCard(long cardId) {
    //    long[] ids = new long[1];
    //    ids[0] = cardId;
    //    unsuspendCards(ids);
    //}


    public void unsuspendCards(long[] ids) {
        getDB().database.execSQL("UPDATE cards SET type = type + 3, priority = 0, " +
                "modified = " + String.format(ENGLISH_LOCALE, "%f", (double) (System.currentTimeMillis() / 1000.0)) +
                " WHERE type < 0 AND id IN " + Utils.ids2str(ids));
        updatePriorities(ids);
        flushMod();
        reset();
    }


    private void updatePriorities(long[] cardIds) {
        updatePriorities(cardIds, null, true);
    }


    void updatePriorities(long[] cardIds, String[] suspend, boolean dirty) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        Cursor cursor = null;
        Log.i(TAG, "updatePriorities - Updating priorities...");
        // Any tags to suspend
        if (suspend != null) {
            long[] ids = tagIds(suspend, false);
            ankiDB.database.execSQL("UPDATE tags " + "SET priority = 0 " + "WHERE id in " + Utils.ids2str(ids));
        }

        String limit = "";
        if (cardIds.length <= 1000) {
            limit = "and cardTags.cardId in " + Utils.ids2str(cardIds);
        }
        String query = "SELECT cardTags.cardId, " + "CASE "
        + "WHEN max(tags.priority) > 2 THEN max(tags.priority) " + "WHEN min(tags.priority) = 1 THEN 1 "
        + "ELSE 2 END " + "FROM cardTags,tags " + "WHERE cardTags.tagId = tags.id " + limit + " "
        + "GROUP BY cardTags.cardId";
        try {
            cursor = ankiDB.database.rawQuery(query, null);
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
                    ankiDB.database.execSQL("UPDATE cards " + "SET priority = " + pri + extra + " WHERE id in "
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
        return (dailyStats.newEase0 +
                dailyStats.newEase1 +
                dailyStats.newEase2 +
                dailyStats.newEase3 +
                dailyStats.newEase4);
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
        int len = ids.size();
        if (len > 0) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
            commitToDB();
            double now = System.currentTimeMillis() / 1000.0;
            Log.i(TAG, "Now = " + now);
            String idsString = Utils.ids2str(ids);

            // Grab fact ids
            // ArrayList<String> factIds = ankiDB.queryColumn(String.class,
            // "SELECT factId FROM cards WHERE id in " + idsString,
            // 0);

            // Delete cards
            ankiDB.database.execSQL("DELETE FROM cards WHERE id in " + idsString);

            // Note deleted cards
            String sqlInsert = "INSERT INTO cardsDeleted values (?," + String.format(ENGLISH_LOCALE, "%f", now) + ")";
            SQLiteStatement statement = ankiDB.database.compileStatement(sqlInsert);
            for (int i = 0; i < len; i++) {
                statement.bindString(1, ids.get(i));
                statement.executeInsert();
            }
            statement.close();

            // Gather affected tags (before we delete the corresponding cardTags)
            ArrayList<String> tags = ankiDB.queryColumn(String.class, "SELECT tagId FROM cardTags WHERE cardId in "
                    + idsString, 0);

            // Delete cardTags
            ankiDB.database.execSQL("DELETE FROM cardTags WHERE cardId in " + idsString);

            // Find out if this tags are used by anything else
            ArrayList<String> unusedTags = new ArrayList<String>();
            for (int i = 0; i < tags.size(); i++) {
                String tagId = tags.get(i);
                Cursor cursor = ankiDB.database.rawQuery("SELECT * FROM cardTags WHERE tagId = " + tagId + " LIMIT 1",
                        null);
                if (!cursor.moveToFirst()) {
                    unusedTags.add(tagId);
                }
                cursor.close();
            }

            // Delete unused tags
            ankiDB.database.execSQL("DELETE FROM tags WHERE id in " + Utils.ids2str(unusedTags) + " and priority = 2");

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
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
            commitToDB();
            double now = System.currentTimeMillis() / 1000.0;
            String idsString = Utils.ids2str(ids);
            Log.i(TAG, "DELETE FROM facts WHERE id in " + idsString);
            ankiDB.database.execSQL("DELETE FROM facts WHERE id in " + idsString);
            Log.i(TAG, "DELETE FROM fields WHERE factId in " + idsString);
            ankiDB.database.execSQL("DELETE FROM fields WHERE factId in " + idsString);
            String sqlInsert = "INSERT INTO factsDeleted VALUES(?," + String.format(ENGLISH_LOCALE, "%f", now) + ")";
            SQLiteStatement statement = ankiDB.database.compileStatement(sqlInsert);
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
        ArrayList<String> danglingFacts = AnkiDatabaseManager.getDatabase(deckPath).queryColumn(String.class,
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
                    "SELECT cards.id FROM cards, facts WHERE facts.modelId = " + id +
                    " AND facts.id = cards.factId", 0);
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
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);

        long start, stop;
        start = System.currentTimeMillis();

        // Delete field model
        ankiDB.database.execSQL("DELETE FROM fields WHERE fieldModel = " + fieldModelId);

        // Note like modified the facts that use this model
        ankiDB.database.execSQL("UPDATE facts SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", (System.currentTimeMillis() / 1000.0)) + " WHERE modelId = "
                + modelId);

        // TODO: remove field model from list

        // Update Question/Answer formats
        // TODO: All these should be done with the field object
        String fieldName = "";
        Cursor cursor = ankiDB.database.rawQuery("SELECT name FROM fieldModels WHERE id = " + fieldModelId, null);
        if (cursor.moveToNext()) {
            fieldName = cursor.getString(0);
        }
        cursor.close();

        cursor = ankiDB.database.rawQuery("SELECT id, qformat, aformat FROM cardModels WHERE modelId = " + modelId,
                null);
        String sql = "UPDATE cardModels SET qformat = ?, aformat = ? WHERE id = ?";
        SQLiteStatement statement = ankiDB.database.compileStatement(sql);
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
        ankiDB.database.execSQL("UPDATE models SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", System.currentTimeMillis() / 1000.0) + " WHERE id = " + modelId);

        flushMod();

        stop = System.currentTimeMillis();
        Log.v(TAG, "deleteFieldModel - deleted field models in " + (stop - start) + " ms.");
    }


    public void deleteCardModel(String modelId, String cardModelId) {
        Log.i(TAG, "deleteCardModel, modelId = " + modelId + ", fieldModelId = " + cardModelId);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);

        // Delete all cards that use card model from the deck
        ArrayList<String> cardIds = ankiDB.queryColumn(String.class, "SELECT id FROM cards WHERE cardModelId = "
                + cardModelId, 0);
        deleteCards(cardIds);

        // I assume that the line "model.cardModels.remove(cardModel)" actually deletes cardModel from DB (I might be
        // wrong)
        ankiDB.database.execSQL("DELETE FROM cardModels WHERE id = " + cardModelId);

        // Note the model like modified (TODO: We should use the object model instead handling the DB directly)
        ankiDB.database.execSQL("UPDATE models SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", System.currentTimeMillis() / 1000.0) + " WHERE id = " + modelId);

        flushMod();
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

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);

        ankiDB.database.execSQL("CREATE TEMPORARY TABLE undoLog (seq INTEGER PRIMARY KEY NOT NULL, sql TEXT)");

        ArrayList<String> tables = ankiDB.queryColumn(String.class,
                "SELECT name FROM sqlite_master WHERE type = 'table'", 0);
        Iterator<String> iter = tables.iterator();
        while (iter.hasNext()) {
            String table = iter.next();
            if (table.equals("undoLog") || table.equals("sqlite_stat1")) {
                continue;
            }
            ArrayList<String> columns = ankiDB.queryColumn(String.class, "PRAGMA TABLE_INFO(" + table + ")", 1);
            // Insert trigger
            String sql = "CREATE TEMP TRIGGER _undo_%s_it " + "AFTER INSERT ON %s BEGIN "
            + "INSERT INTO undoLog VALUES " + "(null, 'DELETE FROM %s WHERE rowid = ' || new.rowid); END";
            ankiDB.database.execSQL(String.format(ENGLISH_LOCALE, sql, table, table, table));
            // Update trigger
            sql = String.format(ENGLISH_LOCALE, "CREATE TEMP TRIGGER _undo_%s_ut " + "AFTER UPDATE ON %s BEGIN "
                    + "INSERT INTO undoLog VALUES " + "(null, 'UPDATE %s ", table, table, table);
            String sep = "SET ";
            Iterator<String> columnIter = columns.iterator();
            while (columnIter.hasNext()) {
                String column = columnIter.next();
                if (column.equals("unique")) {
                    continue;
                }
                sql += String.format(ENGLISH_LOCALE, "%s%s=' || quote(old.%s) || '", sep, column, column);
                sep = ",";
            }
            sql += "WHERE rowid = ' || old.rowid); END";
            ankiDB.database.execSQL(sql);
            // Delete trigger
            sql = String.format(ENGLISH_LOCALE, "CREATE TEMP TRIGGER _undo_%s_dt " + "BEFORE DELETE ON %s BEGIN "
                    + "INSERT INTO undoLog VALUES " + "(null, 'INSERT INTO %s (rowid", table, table, table);
            columnIter = columns.iterator();
            while (columnIter.hasNext()) {
                String column = columnIter.next();
                sql += String.format(ENGLISH_LOCALE, ",\"%s\"", column);
            }
            sql += ") VALUES (' || old.rowid ||'";
            columnIter = columns.iterator();
            while (columnIter.hasNext()) {
                String column = columnIter.next();
                if (column.equals("unique")) {
                    sql += ",1";
                    continue;
                }
                sql += String.format(ENGLISH_LOCALE, ", ' || quote(old.%s) ||'", column);
            }
            sql += ")'); END";
            ankiDB.database.execSQL(sql);
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
            result = AnkiDatabaseManager.getDatabase(deckPath).queryScalar("SELECT MAX(rowid) FROM undoLog");
        } catch (SQLException e) {
            result = 0;
        }
        return result;
    }


    private void undoredo(Stack<UndoRow> src, Stack<UndoRow> dst) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);

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
        ArrayList<String> sql = ankiDB.queryColumn(String.class, String.format(ENGLISH_LOCALE,
                "SELECT sql FROM undoLog " + "WHERE seq > %d and seq <= %d " + "ORDER BY seq DESC", start, end), 0);
        Long newstart = latestUndoRow();
        Iterator<String> iter = sql.iterator();
        while (iter.hasNext()) {
            ankiDB.database.execSQL(iter.next());
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
                getDB().database.execSQL("CREATE INDEX IF NOT EXISTS " + "ix_cards_"
                        + entry.getKey() + "2 ON cards " + entry.getValue());
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
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);

        try {
            id = ankiDB.queryScalar("select id from tags where tag = \"" + tag + "\"");
        } catch (SQLException e) {
            if (create) {
                ContentValues value = new ContentValues();
                value.put("tag", tag);
                id = ankiDB.database.insert("tags", null, value);
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
    private long[] tagIds(String[] tags) {
        return tagIds(tags, true);
    }
    private long[] tagIds(String[] tags, boolean create) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);

        if (create) {
            for (String tag : tags) {
                ankiDB.database.execSQL("INSERT OR IGNORE INTO tags (tag) VALUES ('" + tag + "')");
            }
        }
        if (tags.length != 0) {
            String tagList = "";
            for (int i = 0; i < tags.length; i++) {
                tagList += "'" + tags[i] + "'";
                if (i < tags.length-1) {
                    tagList += ", ";
                }
            }
            ArrayList<Long> results = ankiDB.queryColumn(long.class, "SELECT id FROM tags WHERE tag in (" + tagList +")", 0);
            return Utils.toPrimitive((Long[]) results.toArray());
        }
        return null;
    }


}
