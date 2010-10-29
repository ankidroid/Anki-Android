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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

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

    // BEGIN: SQL table columns
    private long mId;
    private double mCreated;
    private double mModified;
    private String mDescription;
    private int mVersion;
    private long mCurrentModelId;
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
    private double mDelay0;
    private double mDelay1;
    private double mDelay2;

    // Collapsing future cards
    private double mCollapseTime;

    // Priorities and postponing
    private String mHighPriority;
    private String mMedPriority;
    private String mLowPriority;
    private String mSuspended;

    // 0 is random, 1 is by input date
    private int mNewCardOrder;

    // When to show new cards
    private int mNewCardSpacing;

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
    private int mFailedNowCount;
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

    // Not in Anki Desktop
    private String mDeckPath;
    private String mDeckName;
    private Stats mGlobalStats;
    private Stats mDailyStats;
    private Card mCurrentCard;

    /**
     * Undo/Redo variables.
     */
    private Stack<UndoRow> mUndoStack;
    private Stack<UndoRow> mRedoStack;
    private boolean mUndoEnabled = false;


    private void initVars() {
        // tmpMediaDir = null;
        // forceMediaDir = null;
        // lastTags = "";
        mLastLoaded = Utils.now();
        // undoEnabled = false;
        // sessionStartReps = 0;
        // sessionStartTime = 0;
        // lastSessionStart = 0;
        mNewEarly = false;
        mReviewEarly = false;
    }


    public static synchronized Deck openDeck(String path) throws SQLException {
        Deck deck = null;
        Cursor cursor = null;
        Log.i(AnkiDroidApp.TAG, "openDeck - Opening database " + path);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(path);

        try {
            // Read in deck table columns
            cursor = ankiDB.getDatabase().rawQuery("SELECT *" + " FROM decks" + " LIMIT 1", null);

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
            deck.mDelay0 = cursor.getDouble(14);
            deck.mDelay1 = cursor.getDouble(15);
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
        Log.i(AnkiDroidApp.TAG,
                String.format(ENGLISH_LOCALE, "openDeck - modified: %f currentTime: %f", deck.mModified, Utils.now()));

        deck.mDeckPath = path;
        deck.mDeckName = (new File(path)).getName().replace(".anki", "");
        deck.initVars();

        // Ensure necessary indices are available
        deck.updateDynamicIndices();
        // Save counts to determine if we should save deck after check
        int oldCount = deck.mFailedSoonCount + deck.mRevCount + deck.mNewCount;
        // Update counts
        deck.rebuildQueue();

        try {
            // Unsuspend reviewed early & buried
            cursor = ankiDB.getDatabase().rawQuery("SELECT id " + "FROM cards " + "WHERE type in (0,1,2) and "
                    + "isDue = 0 and " + "priority in (-1,-2)", null);

            if (cursor.moveToFirst()) {
                int count = cursor.getCount();
                long[] ids = new long[count];
                for (int i = 0; i < count; i++) {
                    ids[i] = cursor.getLong(0);
                    cursor.moveToNext();
                }
                deck.updatePriorities(ids);
                deck.checkDue();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Save deck to database if it has been modified
        if ((oldCount != (deck.mFailedSoonCount + deck.mRevCount + deck.mNewCount)) || deck.modifiedSinceSave()) {
            deck.commitToDB();
        }

        // Create a temporary view for random new cards. Randomizing the cards by themselves
        // as is done in desktop Anki in Deck.randomizeNewCards() takes too long.
        try {
            ankiDB.getDatabase().execSQL("CREATE TEMPORARY VIEW acqCardsRandom AS " + "SELECT * FROM cards "
                    + "WHERE type = 2 AND isDue = 1 " + "ORDER BY RANDOM()");
        } catch (SQLException e) {
            /* Temporary view may still be present if the DB has not been closed */
            Log.i(AnkiDroidApp.TAG, "Failed to create temporary view: " + e.getMessage());
        }

        return deck;
    }


    public synchronized void closeDeck() {
        DeckTask.waitToFinish(); // Wait for any thread working on the deck to finish.
        if (modifiedSinceSave()) {
            commitToDB();
        }
        AnkiDatabaseManager.closeDatabase(mDeckPath);
    }


    private boolean modifiedSinceSave() {
        return mModified > mLastLoaded;
    }


    private void setModified() {
        mModified = Utils.now();
    }


    private void flushMod() {
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

        AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().update("decks", values, "id = " + mId, null);
    }


    public static double getLastModified(String deckPath) {
        double value;
        Cursor cursor = null;
        // Log.i(AnkiDroidApp.TAG, "Deck - getLastModified from deck = " + deckPath);

        boolean dbAlreadyOpened = AnkiDatabaseManager.isDatabaseOpen(deckPath);

        try {
            cursor = AnkiDatabaseManager.getDatabase(deckPath).getDatabase().rawQuery("SELECT modified" + " FROM decks"
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

    public String getDeckPath() {
        return mDeckPath;
    }


    public void setDeckPath(String path) {
        mDeckPath = path;
    }


    public String getSyncName() {
        return mSyncName;
    }


    public void setSyncName(String name) {
        mSyncName = name;
        flushMod();
    }


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


    public int getNewCardsPerDay() {
        return mNewCardsPerDay;
    }


    public void setNewCardsPerDay(int num) {
        if (num >= 0) {
            mNewCardsPerDay = num;
            flushMod();
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


    /*
     * Getting the next card*********************************************************
     */

    /**
     * Return the next card object.
     * 
     * @return The next due card or null if nothing is due.
     */
    public Card getCard() {
        checkDue();
        long id = getCardId();
        mCurrentCard = cardFromId(id);
        return mCurrentCard;
    }


    // Refreshes the current card and returns it (used when editing cards)
    // TODO find a less lame way to do this.
    public Card getCurrentCard() {
        return cardFromId(mCurrentCard.getId());
    }


    private long getCardId() {
        long id;
        // Failed card due?
        if ((mDelay0 != 0) && (mFailedNowCount != 0)) {
            return AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar("SELECT id FROM failedCards LIMIT 1");
        }
        // Failed card queue too big?
        if ((mFailedCardMax != 0) && (mFailedSoonCount >= mFailedCardMax)) {
            return AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar("SELECT id FROM failedCards LIMIT 1");
        }
        // Distribute new cards?
        if (timeForNewCard()) {
            id = maybeGetNewCard();
            if (id != 0) {
                return id;
            }
        }
        // Card due for review?
        if (mRevCount != 0) {
            return getRevCard();
        }
        // New cards left?
        id = maybeGetNewCard();
        if (id != 0) {
            return id;
        }
        // Review ahead?
        if (mReviewEarly) {
            id = getCardIdAhead();
            if (id != 0) {
                return id;
            } else {
                resetAfterReviewEarly();
                checkDue();
            }
        }
        // Display failed cards early/last
        if (showFailedLast()) {
            try {
                id = AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar("SELECT id FROM failedCards LIMIT 1");
            } catch (Exception e) {
                return 0;
            }
            return id;
        }
        return 0;
    }


    private long getCardIdAhead() {
        long id = 0;
        try {
            id = AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar(
                    "SELECT id " + "FROM cards " + "WHERE type = 1 and " + "isDue = 0 and " + "priority in (1,2,3,4) "
                            + "ORDER BY combinedDue " + "LIMIT 1");
        } catch (SQLException e) {
            return 0;
        }
        return id;
    }


    /*
     * Get card: helper functions*********************************************************
     */

    private boolean timeForNewCard() {
        if (mNewCardSpacing == NEW_CARDS_LAST) {
            return false;
        }
        if (mNewCardSpacing == NEW_CARDS_FIRST) {
            return true;
        }
        // Force old if there are very high priority cards
        try {
            AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar(
                    "SELECT 1 " + "FROM cards " + "WHERE type = 1 and " + "isDue = 1 and " + "priority = 4 "
                            + "LIMIT 1");
        } catch (Exception e) { // No result from query.
            if (mNewCardModulus == 0) {
                return false;
            } else {
                return (mDailyStats.getReps() % mNewCardModulus) == 0;
            }
        }
        return false;
    }


    private long maybeGetNewCard() {
        if ((mNewCountToday == 0) && (!mNewEarly)) {
            return 0;
        }
        return getNewCard();
    }


    private String newCardTable() {
        return (new String[] { "acqCardsRandom ", "acqCardsOld ", "acqCardsNew " })[mNewCardOrder];
    }


    private String revCardTable() {
        return (new String[] { "revCardsOld ", "revCardsNew ", "revCardsDue ", "revCardsRandom " })[mRevCardOrder];
    }


    private long getNewCard() {
        long id;
        try {
            id = AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar(
                    "SELECT id " + "FROM " + newCardTable() + "LIMIT 1");
        } catch (Exception e) {
            return 0;
        }
        return id;
    }


    private long getRevCard() {
        long id;
        try {
            id = AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar(
                    "SELECT id " + "FROM " + revCardTable() + "LIMIT 1");
        } catch (Exception e) {
            return 0;
        }
        return id;
    }


    private boolean showFailedLast() {
        return (mCollapseTime != 0) || (mDelay0 == 0);
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
        double now = Utils.now();
        ContentValues updateValues = new ContentValues();
        updateValues.put("question", card.getQuestion());
        updateValues.put("answer", card.getAnswer());
        updateValues.put("modified", now);
        AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().update(
                "cards", updateValues, "id = ?", new String[] { "" + card.getId() });
        // AnkiDb.getDatabase().execSQL(String.format(NULL_LOCALE,
        // "UPDATE cards " +
        // "SET question = %s, " +
        // "answer = %s, " +
        // "modified = %f, " +
        // "WHERE id != %d and factId = %d",
        // card.question, card.answer, now, card.id, card.factId));
        //
        Log.v(AnkiDroidApp.TAG, "Update question and answer in card id# " + card.getId());

    }


    // Optimization for updateAllCardsFromPosition and updateAllCards
    // Drops some indices and changes synchronous pragma
    public void beforeUpdateCards() {
        long now = System.currentTimeMillis();
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
        ankiDB.getDatabase().execSQL("PRAGMA synchronous=NORMAL");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_duePriority");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_priorityDue");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_factor");
        ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_sort");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_factId");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_intervalDesc");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_intervalAsc");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_randomOrder");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_dueAsc");
        // ankiDB.getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_dueDesc");
        Log.w(AnkiDroidApp.TAG, "BEFORE UPDATE = " + (System.currentTimeMillis() - now));
    }


    public void afterUpdateCards() {
        long now = System.currentTimeMillis();
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
        // ankiDB.getDatabase().execSQL("CREATE INDEX ix_cards_duePriority on cards (type, isDue, combinedDue, priority)");
        // ankiDB.getDatabase().execSQL("CREATE INDEX ix_cards_priorityDue on cards (type, isDue, priority, combinedDue)");
        // ankiDB.getDatabase().execSQL("CREATE INDEX ix_cards_factor on cards 			(type, factor)");
        ankiDB.getDatabase().execSQL("CREATE INDEX ix_cards_sort on cards (answer collate nocase)");
        // ankiDB.getDatabase().execSQL("CREATE INDEX ix_cards_factId on cards (factId, type)");
        // updateDynamicIndices();
        ankiDB.getDatabase().execSQL("PRAGMA synchronous=FULL");
        Log.w(AnkiDroidApp.TAG, "AFTER UPDATE = " + (System.currentTimeMillis() - now));
        // now = System.currentTimeMillis();
        // ankiDB.getDatabase().execSQL("ANALYZE");
        // Log.i("ANALYZE = " + System.currentTimeMillis() - now);
    }


    // TODO: The real methods to update cards on Anki should be implemented instead of this
    public void updateAllCards() {
        updateAllCardsFromPosition(0, Long.MAX_VALUE);
    }


    public long updateAllCardsFromPosition(long numUpdatedCards, long limitCards) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
        // TODO: Cache this query, order by FactId, Id
        Cursor cursor = ankiDB.getDatabase().rawQuery("SELECT id, factId " + "FROM cards " + "ORDER BY factId, id "
                + "LIMIT " + limitCards + " OFFSET " + numUpdatedCards, null);

        ankiDB.getDatabase().beginTransaction();
        try {
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
                card.setModified(Utils.now());

                card.updateQAfields();

                numUpdatedCards++;

            }
            cursor.close();
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return numUpdatedCards;
    }


    /*
     * Answering a card*********************************************************
     */

    public void answerCard(Card card, int ease) {
        Log.i(AnkiDroidApp.TAG, "answerCard");

        Cursor cursor = null;
        String undoName = "Answer Card";
        setUndoStart(undoName);
        double now = Utils.now();
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);

        // Old state
        String oldState = cardState(card);
        double lastDelaySecs = Utils.now() - card.getCombinedDue();
        double start = System.currentTimeMillis();
        double lastDelay = lastDelaySecs / 86400.0;
        int oldSuc = card.getSuccessive();

        // update card details
        double last = card.getInterval();
        card.setInterval(nextInterval(card, ease));
        if (lastDelay >= 0) {
            card.setLastInterval(last); // keep last interval if reviewing early
        }
        if (card.getReps() != 0) {
            card.setLastDue(card.getDue()); // only update if card was not new
        }
        card.setDue(nextDue(card, ease, oldState));
        card.setIsDue(0);
        card.setLastFactor(card.getFactor());
        if (lastDelay >= 0) {
            updateFactor(card, ease); // don't update factor if learning ahead
        }

        // spacing
        double space, spaceFactor, minSpacing, minOfOtherCards;
        try {
            cursor = ankiDB.getDatabase().rawQuery(
                    "SELECT models.initialSpacing, models.spacing " + "FROM facts, models "
                    + "WHERE facts.modelId = models.id and " + "facts.id = " + card.getFactId(), null);
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
            cursor = ankiDB.getDatabase().rawQuery(
                    "SELECT min(interval) " + "FROM cards " + "WHERE factId = " + card.getFactId()
                    + " and id != " + card.getId(), null);
            if (!cursor.moveToFirst()) {
                minOfOtherCards = 0;
            } else {
                minOfOtherCards = cursor.getDouble(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (minOfOtherCards != 0) {
            space = Math.min(minOfOtherCards, card.getInterval());
        } else {
            space = 0;
        }
        space = space * spaceFactor * 86400.0;
        space = Math.max(minSpacing, space);
        space += Utils.now();
        card.setCombinedDue(Math.max(card.getDue(), space));

        // check what other cards we've spaced
        String extra;
        if (mReviewEarly) {
            extra = "";
        } else {
            // if not reviewing early, make sure the current card is counted
            // even if it was not due yet (it's a failed card)
            extra = "or id = " + card.getId();
        }

        try {
            cursor = ankiDB.getDatabase().rawQuery("SELECT type, count(type) " + "FROM cards " + "WHERE factId = "
                    + card.getFactId() + " and " + "(isDue = 1 " + extra + ") " + "GROUP BY type", null);
            while (cursor.moveToNext()) {
                Log.i(AnkiDroidApp.TAG, "failedSoonCount before = " + mFailedSoonCount);
                Log.i(AnkiDroidApp.TAG, "revCount before = " + mRevCount);
                Log.i(AnkiDroidApp.TAG, "newCount before = " + mNewCount);
                if (cursor.getInt(0) == 0) {
                    mFailedSoonCount -= cursor.getInt(1);
                } else if (cursor.getInt(0) == 1) {
                    mRevCount -= cursor.getInt(1);
                } else {
                    mNewCount -= cursor.getInt(1);
                }
                Log.i(AnkiDroidApp.TAG, "failedSoonCount after = " + mFailedSoonCount);
                Log.i(AnkiDroidApp.TAG, "revCount after = " + mRevCount);
                Log.i(AnkiDroidApp.TAG, "newCount after = " + mNewCount);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // space other cards
        ankiDB.getDatabase().execSQL(String.format(ENGLISH_LOCALE, "UPDATE cards " + "SET spaceUntil = %f, "
                + "combinedDue = max(%f, due), " + "modified = %f, " + "isDue = 0 " + "WHERE id != %d and factId = %d",
                space, space, now, card.getId(), card.getFactId()));
        card.setSpaceUntil(0);

        // temp suspend if learning ahead
        if (mReviewEarly && lastDelay < 0) {
            if (oldSuc != 0 || lastDelaySecs > mDelay0 || !showFailedLast()) {
                card.setPriority(-1);
            }
        }
        // card stats
        card.updateStats(ease, oldState);

        card.toDB();

        // global/daily stats
        Stats.updateAllStats(mGlobalStats, mDailyStats, card, ease, oldState);

        // review history
        CardHistoryEntry entry = new CardHistoryEntry(this, card, ease, lastDelay);
        entry.writeSQL();
        mModified = now;

        // // TODO: Fix leech handling
        // if (isLeech(card))
        // card = handleLeech(card);
        setUndoEnd(undoName);
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
        double interval = card.getInterval();
        double factor = card.getFactor();

        // if shown early and not failed
        if ((delay < 0) && (card.getSuccessive() != 0)) {
            interval = Math.max(card.getLastInterval(), card.getInterval() + delay);
            if (interval < mMidIntervalMin) {
                interval = 0;
            }
            delay = 0;
        }

        // if interval is less than mid interval, use presets
        if (ease == 1) {
            interval *= mDelay2;
            if (interval < mHardIntervalMin) {
                interval = 0;
            }
        } else if (interval == 0) {
            if (ease == 2) {
                interval = mHardIntervalMin + ((double) Math.random()) * (mHardIntervalMax - mHardIntervalMin);
            } else if (ease == 3) {
                interval = mMidIntervalMin + ((double) Math.random()) * (mMidIntervalMax - mMidIntervalMin);
            } else if (ease == 4) {
                interval = mEasyIntervalMin + ((double) Math.random()) * (mEasyIntervalMax - mEasyIntervalMin);
            }
        } else {
            // if not cramming, boost initial 2
            if ((interval < mHardIntervalMax) && (interval > 0.166)) {
                double mid = (mMidIntervalMin + mMidIntervalMax) / 2.0;
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
                due = mDelay1;
            } else {
                due = mDelay0;
            }
        } else {
            due = card.getInterval() * 86400.0;
        }
        return (due + Utils.now());
    }


    private void updateFactor(Card card, int ease) {
        card.setLastFactor(card.getFactor());
        if (card.getReps() == 0) {
            card.setFactor(mAverageFactor); // card is new, inherit beginning factor
        }
        if (card.getSuccessive() != 0 && !cardIsBeingLearnt(card)) {
            if (ease == 1) {
                card.setFactor(card.getFactor() - 0.20);
            } else if (ease == 2) {
                card.setFactor(card.getFactor() - 0.15);
            }
        }
        if (ease == 4) {
            card.setFactor(card.getFactor() + 0.10);
        }
        card.setFactor(Math.max(1.3, card.getFactor()));
    }


    private double adjustedDelay(Card card, int ease) {
        double now = Utils.now();
        if (cardIsNew(card)) {
            return 0;
        }
        if (card.getCombinedDue() <= now) {
            return (now - card.getDue()) / 86400.0;
        } else {
            return (now - card.getCombinedDue()) / 86400.0;
        }
    }


    /*
     * Queue/cache management*********************************************************
     */

    /**
     * Retrieve the total number of cards of the deck.
     * @return the number of cards contained in the deck
     */
    public long retrieveCardCount() {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
        return ankiDB.queryScalar("SELECT count(id) FROM cards");
    }


    public void rebuildCounts(boolean full) {
        Log.i(AnkiDroidApp.TAG, "rebuildCounts - Rebuilding global and due counts...");
        Log.i(AnkiDroidApp.TAG, "Full review = " + full);

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
        // Need to check due first, so new due cards are not added later
        checkDue();
        // Global counts
        if (full) {
            mCardCount = (int) retrieveCardCount();
            mFactCount = (int) ankiDB.queryScalar("SELECT count(id) FROM facts");
        }

        // Due counts
        mFailedSoonCount = (int) ankiDB.queryScalar("SELECT count(id) FROM failedCards");
        Log.i(AnkiDroidApp.TAG, "failedSoonCount = " + mFailedSoonCount);
        mFailedNowCount = (int) ankiDB.queryScalar("SELECT count(id) " + "FROM cards " + "WHERE type = 0 and "
                + "isDue = 1 and " + "combinedDue <= "
                + String.format(ENGLISH_LOCALE, "%f", Utils.now()));
        Log.i(AnkiDroidApp.TAG, "failedNowCount = " + mFailedNowCount);
        mRevCount = (int) ankiDB.queryScalar("SELECT count(id) " + "FROM cards " + "WHERE type = 1 and "
                + "priority in (1,2,3,4) and " + "isDue = 1");
        Log.i(AnkiDroidApp.TAG, "revCount = " + mRevCount);
        mNewCount = (int) ankiDB.queryScalar("SELECT count(id) " + "FROM cards " + "WHERE type = 2 and "
                + "priority in (1,2,3,4) and " + "isDue = 1");
        Log.i(AnkiDroidApp.TAG, "newCount = " + mNewCount);
    }


    /*
     * Report real counts for miscounting debugging
     */
    // public String reportCounts() {
    // Cursor cursor = null;
    // int myfailedSoonCount = 0;
    // int myrevCount = 0;
    // int mynewCount = 0;
    // int myfailedNowCount = (int) AnkiDb.queryScalar(
    // "SELECT count(id) " +
    // "FROM cards " +
    // "WHERE type = 0 and " +
    // "isDue = 1 and " +
    // "combinedDue <= " +
    // String.format(ENGLISH_LOCALE, "%f", Utils.now()));
    // try {
    // cursor = AnkiDb.getDatabase().rawQuery(
    // "SELECT type, count(id) " +
    // "FROM cards " +
    // "WHERE priority in (1,2,3,4) and " +
    // "isDue = 1 " +
    // "GROUP BY type", null);
    // while (cursor.moveToNext()) {
    // switch (cursor.getInt(0)) {
    // case 0: myfailedSoonCount = cursor.getInt(1); break;
    // case 1: myrevCount = cursor.getInt(1); break;
    // case 2: mynewCount = cursor.getInt(1); break;
    // }
    // }
    // } finally {
    // if (cursor != null) cursor.close();
    // }
    //
    // return myfailedSoonCount + "-" + myfailedNowCount + "-" + myrevCount + "-" + mynewCount + "<br/>" +
    // failedSoonCount + "-" + failedNowCount + "-" + revCount + "-" + newCount;
    // }

    /**
     * Mark expired cards due and update counts.
     */
    public void checkDue() {
        Log.i(AnkiDroidApp.TAG, "Checking due cards...");
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);

        checkDailyStats();

        ContentValues val = new ContentValues(1);
        val.put("isDue", 1);
        ankiDB.getDatabase().update(
                "cards",
                val,
                "priority in (1,2,3,4) and "
                        + "type in (0,1,2) and "
                        + "isDue = 0 and "
                        + String.format(ENGLISH_LOCALE, "combinedDue <= %f", Utils.now() + mDelay0), null);

        mFailedNowCount = (int) ankiDB.queryScalar("SELECT count(id) " + "FROM cards " + "WHERE type = 0 and "
                + "isDue = 1 and "
                + String.format(ENGLISH_LOCALE, "combinedDue <= %f", Utils.now()));

        Cursor cursor = null;
        try {
            cursor = ankiDB.getDatabase().rawQuery("SELECT type, count(id) " + "FROM cards "
                    + "WHERE priority in (1,2,3,4) and " + "isDue = 1 " + "GROUP BY type", null);

            while (cursor.moveToNext()) {
                switch (cursor.getInt(0)) {
                    case 0:
                        mFailedSoonCount = cursor.getInt(1);
                        break;
                    case 1:
                        mRevCount = cursor.getInt(1);
                        break;
                    case 2:
                        mNewCount = cursor.getInt(1);
                        break;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        mNewCountToday = Math.max(Math.min(mNewCount, mNewCardsPerDay - newCardsToday()), 0);
        Log.i(AnkiDroidApp.TAG, "newCountToday = Math.max(Math.min(newCount, newCardsPerDay - newCardsToday()), 0) : "
                + mNewCountToday);
    }


    /**
     * Update relative delays based on current time.
     */
    private void rebuildQueue() {
        Cursor cursor = null;
        Log.i(AnkiDroidApp.TAG, "rebuildQueue - Rebuilding query...");
        // Setup global/daily stats
        mGlobalStats = Stats.globalStats(this);
        mDailyStats = Stats.dailyStats(this);

        // Mark due cards and update counts
        checkDue();

        // Invalid card count
        // Determine new card distribution
        if (mNewCardSpacing == NEW_CARDS_DISTRIBUTE) {
            if (mNewCountToday > 0) {
                mNewCardModulus = (mNewCountToday + mRevCount) / mNewCountToday;
                // If there are cards to review, ensure modulo >= 2
                if (mRevCount > 0) {
                    mNewCardModulus = Math.max(2, mNewCardModulus);
                }
            } else {
                mNewCardModulus = 0;
            }
        } else {
            mNewCardModulus = 0;
        }
        Log.i(AnkiDroidApp.TAG, "newCardModulus set to " + mNewCardModulus);

        try {
            cursor = AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().rawQuery(
                    "SELECT avg(factor) " + "FROM cards WHERE type = 1", null);
            if (!cursor.moveToFirst()) {
                mAverageFactor = Deck.initialFactor;
            } else {
                mAverageFactor = cursor.getDouble(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        mAverageFactor = Math.max(mAverageFactor, minimumAverage);

        // Recache CSS
        // rebuildCSS();
    }


    /**
     * Checks if the day has rolled over.
     */
    private void checkDailyStats() {
        if (!Stats.genToday(this).toString().equals(mDailyStats.getDay().toString())) {
            mDailyStats = Stats.dailyStats(this);
        }
    }


    private void resetAfterReviewEarly() {
        Cursor cursor = null;
        long[] ids = null;
        try {
            cursor = AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().rawQuery(
                    "SELECT id " + "FROM cards WHERE priority = -1", null);
            if (cursor.moveToFirst()) {
                int count = cursor.getCount();
                ids = new long[count];
                for (int i = 0; i < count; i++) {
                    ids[i] = cursor.getLong(0);
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (ids != null) {
            updatePriorities(ids);
            flushMod();
        }
        if (mReviewEarly || mNewEarly) {
            mReviewEarly = false;
            mNewEarly = false;
            checkDue();
        }
    }

    /*
     * Tags: adding/removing in bulk*********************************************************
     */
    public static final String TAG_MARKED = "Marked";


    public ArrayList<String> factTags(long[] factIds) {
        return AnkiDatabaseManager.getDatabase(mDeckPath).queryColumn(String.class,
                "select tags from facts WHERE id in " + Utils.ids2str(factIds), 0);
    }


    public void addTag(long factId, String tag) {
        long[] ids = new long[1];
        ids[0] = factId;
        addTag(ids, tag);
    }


    public void addTag(long[] factIds, String tag) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
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
                ankiDB.getDatabase().execSQL("update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                        + String.format(ENGLISH_LOCALE, "%f", Utils.now())
                        + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = ankiDB.queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        ContentValues values = new ContentValues();

        for (String cardId : cardIdList) {
            try {
                // Check if the tag already exists
                ankiDB.queryScalar("select id from cardTags" + " where cardId = " + cardId + " and tagId = " + tagId
                        + " and src = 0");
            } catch (SQLException e) {
                values.put("cardId", cardId);
                values.put("tagId", tagId);
                values.put("src", "0");
                ankiDB.getDatabase().insert("cardTags", null, values);
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
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
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
                ankiDB.getDatabase().execSQL("update facts set " + "tags = \"" + newTags + "\", " + "modified = "
                        + String.format(ENGLISH_LOCALE, "%f", Utils.now())
                        + " where id = " + factIds[i]);
            }
        }

        ArrayList<String> cardIdList = ankiDB.queryColumn(String.class,
                "select id from cards where factId in " + Utils.ids2str(factIds), 0);

        for (String cardId : cardIdList) {
            ankiDB.getDatabase().execSQL("delete from cardTags" + " WHERE cardId = " + cardId + " and tagId = " + tagId
                    + " and src = 0");
        }

        // delete unused tags from tags table
        try {
            ankiDB.queryScalar("select id from cardTags where tagId = " + tagId + " limit 1");
        } catch (SQLException e) {
            ankiDB.getDatabase().execSQL("delete from tags" + " where id = " + tagId);
        }

        flushMod();
    }


    /*
     * Priorities*********************************************************
     */
    public void suspendCard(long cardId) {
        long[] ids = new long[1];
        ids[0] = cardId;
        suspendCards(ids);
    }


    public void suspendCards(long[] ids) {
        AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().execSQL("UPDATE cards SET " + "isDue = 0, "
                + "priority = -3, " + "modified = "
                + String.format(ENGLISH_LOCALE, "%f", Utils.now())
                + " WHERE id IN " + Utils.ids2str(ids));
        rebuildCounts(false);
        flushMod();
    }


    public void unsuspendCard(long cardId) {
        long[] ids = new long[1];
        ids[0] = cardId;
        unsuspendCards(ids);
    }


    public void unsuspendCards(long[] ids) {
        AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().execSQL("UPDATE cards SET " + "priority = 0, "
                + "modified = " + String.format(ENGLISH_LOCALE, "%f", Utils.now())
                + " WHERE id IN " + Utils.ids2str(ids));
        updatePriorities(ids);
        rebuildCounts(false);
        flushMod();
    }


    private void updatePriorities(long[] cardIds) {
        updatePriorities(cardIds, null, true);
    }


    void updatePriorities(long[] cardIds, String[] suspend, boolean dirty) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
        Cursor cursor = null;
        Log.i(AnkiDroidApp.TAG, "updatePriorities - Updating priorities...");
        // Any tags to suspend
        if (suspend != null) {
            long[] ids = tagIds(suspend, false);
            ankiDB.getDatabase().execSQL("UPDATE tags " + "SET priority = 0 " + "WHERE id in " + Utils.ids2str(ids));
        }

        String limit = "";
        if (cardIds.length <= 1000) {
            limit = "and cardTags.cardId in " + Utils.ids2str(cardIds);
        }
        String query = "SELECT cardTags.cardId, " + "CASE " + "WHEN min(tags.priority) = 0 THEN 0 "
                + "WHEN max(tags.priority) > 2 THEN max(tags.priority) " + "WHEN min(tags.priority) = 1 THEN 1 "
                + "ELSE 2 END " + "FROM cardTags,tags " + "WHERE cardTags.tagId = tags.id " + limit + " "
                + "GROUP BY cardTags.cardId";
        try {
            cursor = ankiDB.getDatabase().rawQuery(query, null);
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
                            + String.format(ENGLISH_LOCALE, "%f", Utils.now());
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
                    ankiDB.getDatabase().execSQL("UPDATE cards " + "SET priority = " + pri + extra + " WHERE id in "
                            + Utils.ids2str(cs) + " and " + "priority != " + pri + " and " + "priority >= -2");
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        ContentValues val = new ContentValues(1);
        val.put("isDue", 0);
        int cnt = ankiDB.getDatabase()
                .update("cards", val, "type in (0,1,2) and " + "priority = 0 and " + "isDue = 1", null);
        if (cnt > 0) {
            rebuildCounts(false);
        }
    }


    /*
     * Counts related to due cards *********************************************************
     */

    private int newCardsToday() {
        return (mDailyStats.getNewEase0() + mDailyStats.getNewEase1() + mDailyStats.getNewEase2() + mDailyStats.getNewEase3() + mDailyStats.getNewEase4());
    }


    /*
     * Card Predicates*********************************************************
     */

    private String cardState(Card card) {
        if (cardIsNew(card)) {
            return "new";
        } else if (card.getInterval() > MATURE_THRESHOLD) {
            return "mature";
        }
        return "young";
    }


    /**
     * Check if a card is a new card.
     * @param card The card to check.
     * @return True if a card has never been seen before.
     */
    private boolean cardIsNew(Card card) {
        return card.getReps() == 0;
    }


    /**
     * Check if a card is a new card.
     * @param card The card to check.
     * @return True if card should use present intervals.
     */
    private boolean cardIsBeingLearnt(Card card) {
        return card.getLastInterval() < 7;
    }


    /*
     * Cards CRUD*********************************************************
     */

    public void deleteCards(List<String> ids) {
        Log.i(AnkiDroidApp.TAG, "deleteCards = " + ids.toString());

        // Bulk delete cards by ID
        int len = ids.size();
        if (len > 0) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
            commitToDB();
            double now = Utils.now();
            Log.i(AnkiDroidApp.TAG, "Now = " + now);
            String idsString = Utils.ids2str(ids);

            // Grab fact ids
            // ArrayList<String> factIds = ankiDB.queryColumn(String.class,
            // "SELECT factId FROM cards WHERE id in " + idsString,
            // 0);

            // Delete cards
            ankiDB.getDatabase().execSQL("DELETE FROM cards WHERE id in " + idsString);

            // Note deleted cards
            String sqlInsert = "INSERT INTO cardsDeleted values (?," + String.format(ENGLISH_LOCALE, "%f", now) + ")";
            SQLiteStatement statement = ankiDB.getDatabase().compileStatement(sqlInsert);
            for (String id : ids) {
                statement.bindString(1, id);
                statement.executeInsert();
            }
            statement.close();

            // Gather affected tags (before we delete the corresponding cardTags)
            ArrayList<String> tags = ankiDB.queryColumn(String.class, "SELECT tagId FROM cardTags WHERE cardId in "
                    + idsString, 0);

            // Delete cardTags
            ankiDB.getDatabase().execSQL("DELETE FROM cardTags WHERE cardId in " + idsString);

            // Find out if this tags are used by anything else
            ArrayList<String> unusedTags = new ArrayList<String>();
            for (String tagId : tags) {
                Cursor cursor = ankiDB.getDatabase().rawQuery(
                        "SELECT * FROM cardTags WHERE tagId = " + tagId + " LIMIT 1", null);
                if (!cursor.moveToFirst()) {
                    unusedTags.add(tagId);
                }
                cursor.close();
            }

            // Delete unused tags
            ankiDB.getDatabase().execSQL(
                    "DELETE FROM tags WHERE id in " + Utils.ids2str(unusedTags) + " and priority = 2");

            // Remove any dangling fact
            deleteDanglingFacts();
            rebuildCounts(true);
            flushMod();
        }
    }


    /*
     * Facts CRUD*********************************************************
     */

    public void deleteFacts(List<String> ids) {
        Log.i(AnkiDroidApp.TAG, "deleteFacts = " + ids.toString());
        int len = ids.size();
        if (len > 0) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
            commitToDB();
            double now = Utils.now();
            String idsString = Utils.ids2str(ids);
            Log.i(AnkiDroidApp.TAG, "DELETE FROM facts WHERE id in " + idsString);
            ankiDB.getDatabase().execSQL("DELETE FROM facts WHERE id in " + idsString);
            Log.i(AnkiDroidApp.TAG, "DELETE FROM fields WHERE factId in " + idsString);
            ankiDB.getDatabase().execSQL("DELETE FROM fields WHERE factId in " + idsString);
            String sqlInsert = "INSERT INTO factsDeleted VALUES(?," + String.format(ENGLISH_LOCALE, "%f", now) + ")";
            SQLiteStatement statement = ankiDB.getDatabase().compileStatement(sqlInsert);
            for (String id : ids) {
                Log.i(AnkiDroidApp.TAG, "inserting into factsDeleted");
                statement.bindString(1, id);
                statement.executeInsert();
            }
            statement.close();
            rebuildCounts(true);
            setModified();
        }
    }


    /**
     * Delete any fact without cards.
     * @return ArrayList<String> list with the id of the deleted facts
     */
    public ArrayList<String> deleteDanglingFacts() {
        Log.i(AnkiDroidApp.TAG, "deleteDanglingFacts");
        ArrayList<String> danglingFacts = AnkiDatabaseManager.getDatabase(mDeckPath).queryColumn(String.class,
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
        Log.i(AnkiDroidApp.TAG, "deleteModel = " + id);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);
        Cursor cursor = ankiDB.getDatabase().rawQuery("SELECT * FROM models WHERE id = " + id, null);
        // Does the model exist?
        if (cursor.moveToFirst()) {
            // Delete the cards that use the model id, through fact
            ArrayList<String> cardsToDelete = ankiDB
                    .queryColumn(String.class, "SELECT cards.id FROM cards, facts WHERE facts.modelId = " + id
                            + " AND facts.id = cards.factId", 0);
            deleteCards(cardsToDelete);

            // Delete model
            ankiDB.getDatabase().execSQL("DELETE FROM models WHERE id = " + id);

            // Note deleted model
            ContentValues values = new ContentValues();
            values.put("modelId", id);
            values.put("deletedTime", Utils.now());
            ankiDB.getDatabase().insert("modelsDeleted", null, values);

            flushMod();
        }
        cursor.close();
    }


    public void deleteFieldModel(String modelId, String fieldModelId) {
        Log.i(AnkiDroidApp.TAG, "deleteFieldModel, modelId = " + modelId + ", fieldModelId = " + fieldModelId);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);

        long start, stop;
        start = System.currentTimeMillis();

        // Delete field model
        ankiDB.getDatabase().execSQL("DELETE FROM fields WHERE fieldModel = " + fieldModelId);

        // Note like modified the facts that use this model
        ankiDB.getDatabase().execSQL("UPDATE facts SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", Utils.now())
                + " WHERE modelId = " + modelId);

        // TODO: remove field model from list

        // Update Question/Answer formats
        // TODO: All these should be done with the field object
        String fieldName = "";
        Cursor cursor = ankiDB.getDatabase().rawQuery("SELECT name FROM fieldModels WHERE id = " + fieldModelId, null);
        if (cursor.moveToNext()) {
            fieldName = cursor.getString(0);
        }
        cursor.close();

        cursor = ankiDB.getDatabase().rawQuery("SELECT id, qformat, aformat FROM cardModels WHERE modelId = " + modelId,
                null);
        String sql = "UPDATE cardModels SET qformat = ?, aformat = ? WHERE id = ?";
        SQLiteStatement statement = ankiDB.getDatabase().compileStatement(sql);
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
        ankiDB.getDatabase().execSQL("UPDATE models SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", Utils.now())
                + " WHERE id = " + modelId);

        flushMod();

        stop = System.currentTimeMillis();
        Log.v(AnkiDroidApp.TAG, "deleteFieldModel - deleted field models in " + (stop - start) + " ms.");
    }


    public void deleteCardModel(String modelId, String cardModelId) {
        Log.i(AnkiDroidApp.TAG, "deleteCardModel, modelId = " + modelId + ", fieldModelId = " + cardModelId);
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);

        // Delete all cards that use card model from the deck
        ArrayList<String> cardIds = ankiDB.queryColumn(String.class, "SELECT id FROM cards WHERE cardModelId = "
                + cardModelId, 0);
        deleteCards(cardIds);

        // I assume that the line "model.cardModels.remove(cardModel)" actually deletes cardModel from DB (I might be
        // wrong)
        ankiDB.getDatabase().execSQL("DELETE FROM cardModels WHERE id = " + cardModelId);

        // Note the model like modified (TODO: We should use the object model instead handling the DB directly)
        ankiDB.getDatabase().execSQL("UPDATE models SET modified = "
                + String.format(ENGLISH_LOCALE, "%f", Utils.now())
                + " WHERE id = " + modelId);

        flushMod();
    }

    /*
     * Undo/Redo*********************************************************
     */
    private class UndoRow {
        private String mName;
        private Long mStart;
        private Long mEnd;


        UndoRow(String name, Long start, Long end) {
            mName = name;
            mStart = start;
            mEnd = end;
        }
    }


    // FIXME: Where should this be called from?
    @SuppressWarnings("unused")
    private void initUndo() {
        mUndoStack = new Stack<UndoRow>();
        mRedoStack = new Stack<UndoRow>();
        mUndoEnabled = true;

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);

        ankiDB.getDatabase().execSQL("CREATE TEMPORARY TABLE undoLog (seq INTEGER PRIMARY KEY NOT NULL, sql TEXT)");

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
            ankiDB.getDatabase().execSQL(String.format(ENGLISH_LOCALE, sql, table, table, table));
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
            ankiDB.getDatabase().execSQL(sql);
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
            ankiDB.getDatabase().execSQL(sql);
        }
    }


    public void setUndoBarrier() {
        if (mUndoStack.isEmpty() || mUndoStack.peek() != null) {
            mUndoStack.push(null);
        }
    }


    public void setUndoStart(String name) {
        setUndoStart(name, false);
    }


    /**
     * @param reviewEarly set to true for early review
     */
    public void setReviewEarly(boolean reviewEarly) {
        mReviewEarly = reviewEarly;
    }


    public void setUndoStart(String name, boolean merge) {
        if (!mUndoEnabled) {
            return;
        }
        commitToDB();
        if (merge && !mUndoStack.isEmpty()) {
            if ((mUndoStack.peek() != null) && (mUndoStack.peek().mName.equals(name))) {
                return;
            }
        }
        mUndoStack.push(new UndoRow(name, latestUndoRow(), null));
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
        long result;
        try {
            result = AnkiDatabaseManager.getDatabase(mDeckPath).queryScalar("SELECT MAX(rowid) FROM undoLog");
        } catch (SQLException e) {
            result = 0;
        }
        return result;
    }


    private void undoredo(Stack<UndoRow> src, Stack<UndoRow> dst) {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);

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
        ArrayList<String> sql = ankiDB.queryColumn(String.class, String.format(ENGLISH_LOCALE,
                "SELECT sql FROM undoLog " + "WHERE seq > %d and seq <= %d " + "ORDER BY seq DESC", start, end), 0);
        Long newstart = latestUndoRow();
        Iterator<String> iter = sql.iterator();
        while (iter.hasNext()) {
            ankiDB.getDatabase().execSQL(iter.next());
        }

        Long newend = latestUndoRow();
        dst.push(new UndoRow(row.mName, newstart, newend));
    }


    public void undo() {
        undoredo(mUndoStack, mRedoStack);
        commitToDB();
        rebuildCounts(true);
    }


    public void redo() {
        undoredo(mRedoStack, mUndoStack);
        commitToDB();
        rebuildCounts(true);
    }


    /*
     * Dynamic indices*********************************************************
     */

    public void updateDynamicIndices() {
        Log.i(AnkiDroidApp.TAG, "updateDynamicIndices - Updating indices...");
        HashMap<String, String> indices = new HashMap<String, String>();
        indices.put("intervalDesc", "(type, isDue, priority desc, interval desc)");
        indices.put("intervalAsc", "(type, isDue, priority desc, interval)");
        indices.put("randomOrder", "(type, isDue, priority desc, factId, ordinal)");
        indices.put("dueAsc", "(type, isDue, priority desc, due)");
        indices.put("dueDesc", "(type, isDue, priority desc, due desc)");

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

        Set<Entry<String, String>> entries = indices.entrySet();
        Iterator<Entry<String, String>> iter = entries.iterator();
        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            if (required.contains(entry.getKey())) {
                AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().execSQL(
                        "CREATE INDEX IF NOT EXISTS " + "ix_cards_"
                        + entry.getKey() + " ON cards " + entry.getValue());
            } else {
                AnkiDatabaseManager.getDatabase(mDeckPath).getDatabase().execSQL(
                        "DROP INDEX IF EXISTS " + "ix_cards_"
                        + entry.getKey());
            }
        }
    }


    /*
     * Utility functions (might be better in a separate class) *********************************************************
     */

    /**
     * Return ID for tag, creating if necessary.
     * @param tag the tag we are looking for
     * @param create whether to create the tag if it doesn't exist in the database
     * @return ID of the specified tag, 0 if it doesn't exist, and -1 in the case of error
     */
    private long tagId(String tag, Boolean create) {
        long id = 0;
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeckPath);

        try {
            id = ankiDB.queryScalar("select id from tags where tag = \"" + tag + "\"");
        } catch (SQLException e) {
            if (create) {
                ContentValues value = new ContentValues();
                value.put("tag", tag);
                id = ankiDB.getDatabase().insert("tags", null, value);
            } else {
                id = 0;
            }
        }
        return id;
    }


    /**
     * Gets the IDs of the specified tags.
     * @param tags An array of the tags to get IDs for.
     * @return An array of IDs of the tags.
     */
    private long[] tagIds(String[] tags, Boolean create) {
        // TODO: Finish porting this method from tags.py.
        return null;
    }


    /**
     * @param failedSoonCount the failedSoonCount to set
     */
    public void setFailedSoonCount(int failedSoonCount) {
        mFailedSoonCount = failedSoonCount;
    }


    /**
     * @return the failedSoonCount
     */
    public int getFailedSoonCount() {
        return mFailedSoonCount;
    }


    /**
     * @param revCount the revCount to set
     */
    public void setRevCount(int revCount) {
        mRevCount = revCount;
    }


    /**
     * @return the revCount
     */
    public int getRevCount() {
        return mRevCount;
    }


    /**
     * @param newCountToday the newCountToday to set
     */
    public void setNewCountToday(int newCountToday) {
        mNewCountToday = newCountToday;
    }


    /**
     * @return the newCountToday
     */
    public int getNewCountToday() {
        return mNewCountToday;
    }


    /**
     * @param cardCount the cardCount to set
     */
    public void setCardCount(int cardCount) {
        mCardCount = cardCount;
    }


    /**
     * @return the cardCount
     */
    public int getCardCount() {
        return mCardCount;
    }


    /**
     * @param currentModelId the currentModelId to set
     */
    public void setCurrentModelId(long currentModelId) {
        mCurrentModelId = currentModelId;
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
     * @param utcOffset the utcOffset to set
     */
    public void setUtcOffset(double utcOffset) {
        mUtcOffset = utcOffset;
    }


    /**
     * @return the utcOffset
     */
    public double getUtcOffset() {
        return mUtcOffset;
    }


    /**
     * @param newCount the newCount to set
     */
    public void setNewCount(int newCount) {
        mNewCount = newCount;
    }


    /**
     * @return the newCount
     */
    public int getNewCount() {
        return mNewCount;
    }


    /**
     * @param modified the modified to set
     */
    public void setModified(double modified) {
        mModified = modified;
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
    }


    /**
     * @return the factCount
     */
    public int getFactCount() {
        return mFactCount;
    }


    /**
     * @param averageFactor the averageFactor to set
     */
    public void setAverageFactor(double averageFactor) {
        mAverageFactor = averageFactor;
    }


    /**
     * @return the averageFactor
     */
    public double getAverageFactor() {
        return mAverageFactor;
    }


    /**
     * @param collapseTime the collapseTime to set
     */
    public void setCollapseTime(double collapseTime) {
        mCollapseTime = collapseTime;
    }


    /**
     * @return the collapseTime
     */
    public double getCollapseTime() {
        return mCollapseTime;
    }


    /**
     * @param created the created to set
     */
    public void setCreated(double created) {
        mCreated = created;
    }


    /**
     * @return the created
     */
    public double getCreated() {
        return mCreated;
    }


    /**
     * @param delay0 the delay0 to set
     */
    public void setDelay0(double delay0) {
        mDelay0 = delay0;
    }


    /**
     * @return the delay0
     */
    public double getDelay0() {
        return mDelay0;
    }


    /**
     * @param delay1 the delay1 to set
     */
    public void setDelay1(double delay1) {
        mDelay1 = delay1;
    }


    /**
     * @return the delay1
     */
    public double getDelay1() {
        return mDelay1;
    }


    /**
     * @param delay2 the delay2 to set
     */
    public void setDelay2(double delay2) {
        mDelay2 = delay2;
    }


    /**
     * @return the delay2
     */
    public double getDelay2() {
        return mDelay2;
    }


    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        mDescription = description;
    }


    /**
     * @return the description
     */
    public String getDescription() {
        return mDescription;
    }


    /**
     * @param easyIntervalMax the easyIntervalMax to set
     */
    public void setEasyIntervalMax(double easyIntervalMax) {
        mEasyIntervalMax = easyIntervalMax;
    }


    /**
     * @return the easyIntervalMax
     */
    public double getEasyIntervalMax() {
        return mEasyIntervalMax;
    }


    /**
     * @param easyIntervalMin the easyIntervalMin to set
     */
    public void setEasyIntervalMin(double easyIntervalMin) {
        mEasyIntervalMin = easyIntervalMin;
    }


    /**
     * @return the easyIntervalMin
     */
    public double getEasyIntervalMin() {
        return mEasyIntervalMin;
    }


    /**
     * @param failedCardMax the failedCardMax to set
     */
    public void setFailedCardMax(int failedCardMax) {
        mFailedCardMax = failedCardMax;
    }


    /**
     * @return the failedCardMax
     */
    public int getFailedCardMax() {
        return mFailedCardMax;
    }


    /**
     * @param failedNowCount the failedNowCount to set
     */
    public void setFailedNowCount(int failedNowCount) {
        mFailedNowCount = failedNowCount;
    }


    /**
     * @return the failedNowCount
     */
    public int getFailedNowCount() {
        return mFailedNowCount;
    }


    /**
     * @param hardIntervalMax the hardIntervalMax to set
     */
    public void setHardIntervalMax(double hardIntervalMax) {
        mHardIntervalMax = hardIntervalMax;
    }


    /**
     * @return the hardIntervalMax
     */
    public double getHardIntervalMax() {
        return mHardIntervalMax;
    }


    /**
     * @param hardIntervalMin the hardIntervalMin to set
     */
    public void setHardIntervalMin(double hardIntervalMin) {
        mHardIntervalMin = hardIntervalMin;
    }


    /**
     * @return the hardIntervalMin
     */
    public double getHardIntervalMin() {
        return mHardIntervalMin;
    }


    /**
     * @param highPriority the highPriority to set
     */
    public void setHighPriority(String highPriority) {
        mHighPriority = highPriority;
    }


    /**
     * @return the highPriority
     */
    public String getHighPriority() {
        return mHighPriority;
    }


    /**
     * @param id the id to set
     */
    public void setId(long id) {
        mId = id;
    }


    /**
     * @return the id
     */
    public long getId() {
        return mId;
    }


    /**
     * @param lastLoaded the lastLoaded to set
     */
    public void setLastLoaded(double lastLoaded) {
        mLastLoaded = lastLoaded;
    }


    /**
     * @return the lastLoaded
     */
    public double getLastLoaded() {
        return mLastLoaded;
    }


    /**
     * @param lowPriority the lowPriority to set
     */
    public void setLowPriority(String lowPriority) {
        mLowPriority = lowPriority;
    }


    /**
     * @return the lowPriority
     */
    public String getLowPriority() {
        return mLowPriority;
    }


    /**
     * @param medPriority the medPriority to set
     */
    public void setMedPriority(String medPriority) {
        mMedPriority = medPriority;
    }


    /**
     * @return the medPriority
     */
    public String getMedPriority() {
        return mMedPriority;
    }


    /**
     * @param midIntervalMax the midIntervalMax to set
     */
    public void setMidIntervalMax(double midIntervalMax) {
        mMidIntervalMax = midIntervalMax;
    }


    /**
     * @return the midIntervalMax
     */
    public double getMidIntervalMax() {
        return mMidIntervalMax;
    }


    /**
     * @param midIntervalMin the midIntervalMin to set
     */
    public void setMidIntervalMin(double midIntervalMin) {
        mMidIntervalMin = midIntervalMin;
    }


    /**
     * @return the midIntervalMin
     */
    public double getMidIntervalMin() {
        return mMidIntervalMin;
    }


    /**
     * @param newCardModulus the newCardModulus to set
     */
    public void setNewCardModulus(int newCardModulus) {
        mNewCardModulus = newCardModulus;
    }


    /**
     * @return the newCardModulus
     */
    public int getNewCardModulus() {
        return mNewCardModulus;
    }


    /**
     * @param newEarly the newEarly to set
     */
    public void setNewEarly(boolean newEarly) {
        mNewEarly = newEarly;
    }


    /**
     * @return the newEarly
     */
    public boolean isNewEarly() {
        return mNewEarly;
    }


    /**
     * @return the reviewEarly
     */
    public boolean isReviewEarly() {
        return mReviewEarly;
    }


    /**
     * @param suspended the suspended to set
     */
    public void setSuspended(String suspended) {
        mSuspended = suspended;
    }


    /**
     * @return the suspended
     */
    public String getSuspended() {
        return mSuspended;
    }


    /**
     * @param undoEnabled the undoEnabled to set
     */
    public void setUndoEnabled(boolean undoEnabled) {
        mUndoEnabled = undoEnabled;
    }


    /**
     * @return the undoEnabled
     */
    public boolean isUndoEnabled() {
        return mUndoEnabled;
    }
}
