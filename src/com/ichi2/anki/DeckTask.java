/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class DeckTask extends AsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData> {

    public static final int TASK_TYPE_LOAD_DECK = 0;
    public static final int TASK_TYPE_LOAD_DECK_AND_UPDATE_CARDS = 1;
    public static final int TASK_TYPE_ANSWER_CARD = 2;
    public static final int TASK_TYPE_SUSPEND_CARD = 3;
    public static final int TASK_TYPE_MARK_CARD = 4;
    public static final int TASK_TYPE_UPDATE_FACT = 5;
    public static final int TASK_TYPE_UNDO = 6;
    public static final int TASK_TYPE_REDO = 7;

    /**
     * Possible outputs trying to load a deck.
     */
    public static final int DECK_LOADED = 0;
    public static final int DECK_NOT_LOADED = 1;
    public static final int DECK_EMPTY = 2;

    private static DeckTask sInstance;
    private static DeckTask sOldInstance;

    private int mType;
    private TaskListener mListener;


    
    public static DeckTask launchDeckTask(int type, TaskListener listener, TaskData... params) {
        sOldInstance = sInstance;

        sInstance = new DeckTask();
        sInstance.mListener = listener;
        sInstance.mType = type;

        return (DeckTask) sInstance.execute(params);
    }


    /**
     * Block the current thread until the currently running DeckTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        try {
            if ((sInstance != null) && (sInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                sInstance.get();
            }
        } catch (Exception e) {
            return;
        }
    }


    @Override
    protected TaskData doInBackground(TaskData... params) {
        // Wait for previous thread (if any) to finish before continuing
        try {
            if ((sOldInstance != null) && (sOldInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                sOldInstance.get();
            }
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG,
                    "doInBackground - Got exception while waiting for thread to finish: " + e.getMessage());
        }

        switch (mType) {
            case TASK_TYPE_LOAD_DECK:
                return doInBackgroundLoadDeck(params);

            case TASK_TYPE_LOAD_DECK_AND_UPDATE_CARDS:
                TaskData taskData = doInBackgroundLoadDeck(params);
                if (taskData.mInteger == DECK_LOADED) {
                    taskData.mDeck.updateAllCards();
                    taskData.mCard = taskData.mDeck.getCurrentCard();
                }
                return taskData;

            case TASK_TYPE_ANSWER_CARD:
                return doInBackgroundAnswerCard(params);

            case TASK_TYPE_SUSPEND_CARD:
                return doInBackgroundSuspendCard(params);

            case TASK_TYPE_MARK_CARD:
                return doInBackgroundMarkCard(params);

		case TASK_TYPE_UPDATE_FACT:
                return doInBackgroundUpdateFact(params);
                
            case TASK_TYPE_UNDO:
                return doInBackgroundUndo(params);                

            case TASK_TYPE_REDO:
                return doInBackgroundRedo(params);                

            default:
                return null;
        }
    }


    @Override
    protected void onPreExecute() {
        mListener.onPreExecute();
    }


    @Override
    protected void onProgressUpdate(TaskData... values) {
        mListener.onProgressUpdate(values);
    }


    @Override
    protected void onPostExecute(TaskData result) {
        mListener.onPostExecute(result);
    }


    private TaskData doInBackgroundUpdateFact(TaskData[] params) {
        // Save the fact
        Deck deck = params[0].getDeck();
        Card editCard = params[0].getCard();
        Fact editFact = editCard.getFact();
        
        // Start undo routine
        String undoName = "Update Fact";
        deck.setUndoStart(undoName);

        // Set modified also updates the text of cards and their modified flags
        editFact.setModified(true, deck, false);
        editFact.toDb();
        
        deck.flushMod();

        // Find all cards based on this fact and update them with the updateCard method.
        // for (Card modifyCard : editFact.getUpdatedRelatedCards()) {
        //     modifyCard.updateQAfields();
        // }

        // deck.reset();
        deck.setUndoEnd(undoName);
        publishProgress(new TaskData(deck.getCurrentCard()));

        return null;
    }


    private TaskData doInBackgroundAnswerCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card oldCard = params[0].getCard();
        int ease = params[0].getInt();
        boolean lastCardInQueue = params[0].isLastCardInQueue();
        Card newCard;

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
            if (oldCard != null) {
                deck.answerCard(oldCard, ease);
                Log.i(AnkiDroidApp.TAG, "leech flag: " + oldCard.getLeechFlag());
            }

            newCard = deck.getCard();
            if (oldCard != null) {
                publishProgress(new TaskData(newCard, oldCard.getLeechFlag(), oldCard.getSuspendedFlag(), lastCardInQueue));
            } else {
                publishProgress(new TaskData(newCard));
            }

            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return null;
    }


    private TaskData doInBackgroundLoadDeck(TaskData... params) {
        String deckFilename = params[0].getString();
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);

        Log.i(AnkiDroidApp.TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
        try {
            // Open the right deck.
            Deck deck = Deck.openDeck(deckFilename);
            // Start by getting the first card and displaying it.
            Card card = deck.getCard();
            Log.i(AnkiDroidApp.TAG, "Deck loaded!");
            
            return new TaskData(DECK_LOADED, deck, card);
        } catch (SQLException e) {
            Log.i(AnkiDroidApp.TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
            return new TaskData(DECK_NOT_LOADED);
        } catch (CursorIndexOutOfBoundsException e) {
            // XXX: Where is this exception thrown?
            Log.i(AnkiDroidApp.TAG, "The deck has no cards = " + e.getMessage());
            return new TaskData(DECK_EMPTY);
        }
    }


    private TaskData doInBackgroundSuspendCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card oldCard = params[0].getCard();
        Card newCard;

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
            if (oldCard != null) {
                oldCard.suspend();
            }

            newCard = deck.getCard();
            publishProgress(new TaskData(newCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return null;
    }


    private TaskData doInBackgroundMarkCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card currentCard = params[0].getCard();

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
            if (currentCard != null) {
            	if (currentCard.hasTag(Deck.TAG_MARKED)) {
                    deck.deleteTag(currentCard.getFactId(), Deck.TAG_MARKED);
                } else {
                    deck.addTag(currentCard.getFactId(), Deck.TAG_MARKED);
                }
            }

            publishProgress(new TaskData(currentCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return null;
    }

    private TaskData doInBackgroundUndo(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card newCard;
        long currentCardId = params[0].getCard().getId();

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
        	long oldCardId = deck.undo(currentCardId);
            newCard = deck.getCard();
            if (oldCardId != 0) {
            	newCard = deck.cardFromId(oldCardId);
            }
            publishProgress(new TaskData(newCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return null;
    }

    
    private TaskData doInBackgroundRedo(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card newCard;
        long currentCardId = params[0].getCard().getId();

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
        	long oldCardId = deck.redo(currentCardId);
            newCard = deck.getCard();
            if (oldCardId != 0) {
            	newCard = deck.cardFromId(oldCardId);
            }
            publishProgress(new TaskData(newCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return null;
    }
    

    public static interface TaskListener {
        public void onPreExecute();


        public void onPostExecute(TaskData result);


        public void onProgressUpdate(TaskData... values);
    }

    public static class TaskData {
        private Deck mDeck;
        private Card mCard;
        private int mInteger;
        private String mMsg;
        private boolean previousCardLeech;     // answer card resulted in card marked as leech
        private boolean previousCardSuspended; // answer card resulted in card marked as leech and suspended
        private boolean mLastCardInQueue;


        public TaskData(int value, Deck deck, Card card) {
            this(value);
            mDeck = deck;
            mCard = card;
            mLastCardInQueue = false;
        }


        public TaskData(int value, Deck deck, boolean lastCardInQueue, Card card) {
            this(value);
            mDeck = deck;
            mCard = card;
            mLastCardInQueue = lastCardInQueue;
        }


        public TaskData(Card card) {
            mCard = card;
            previousCardLeech = false;
            previousCardSuspended = false;
        }


        public TaskData(Card card, boolean markedLeech, boolean suspendedLeech, boolean lastCardInQueue) {
            mCard = card;
            previousCardLeech = markedLeech;
            previousCardSuspended = suspendedLeech;
            mLastCardInQueue = lastCardInQueue;
        }


        public TaskData(int value) {
            mInteger = value;
        }


        public TaskData(String msg) {
            mMsg = msg;
        }


        public Deck getDeck() {
            return mDeck;
        }


        public Card getCard() {
            return mCard;
        }


        public int getInt() {
            return mInteger;
        }


        public String getString() {
            return mMsg;
        }


        public boolean isPreviousCardLeech() {
            return previousCardLeech;
        }


        public boolean isPreviousCardSuspended() {
            return previousCardSuspended;
        }


        public boolean isLastCardInQueue() {
            return mLastCardInQueue;
        }
    }

}
