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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.ichi2.anki.DeckPicker.AnkiFilter;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.res.Resources;
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
    public static final int TASK_TYPE_SAVE_DECK = 2;
    public static final int TASK_TYPE_ANSWER_CARD = 3;
    public static final int TASK_TYPE_SUSPEND_CARD = 4;
    public static final int TASK_TYPE_MARK_CARD = 5;
    public static final int TASK_TYPE_ADD_FACT = 6;
    public static final int TASK_TYPE_UPDATE_FACT = 7;
    public static final int TASK_TYPE_UNDO = 8;
    public static final int TASK_TYPE_REDO = 9;
    public static final int TASK_TYPE_LOAD_CARDS = 10;
    public static final int TASK_TYPE_BURY_CARD = 11;
    public static final int TASK_TYPE_DELETE_CARD = 12;
    public static final int TASK_TYPE_LOAD_STATISTICS = 13;
    public static final int TASK_TYPE_OPTIMIZE_DECK = 14;
    public static final int TASK_TYPE_SET_ALL_DECKS_JOURNAL_MODE = 15;
    public static final int TASK_TYPE_CLOSE_DECK = 16;
    public static final int TASK_TYPE_DELETE_BACKUPS = 17;
    public static final int TASK_TYPE_RESTORE_DECK = 18;


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

            case TASK_TYPE_SAVE_DECK:
                return doInBackgroundSaveDeck(params);

            case TASK_TYPE_ANSWER_CARD:
                return doInBackgroundAnswerCard(params);

            case TASK_TYPE_SUSPEND_CARD:
                return doInBackgroundSuspendCard(params);

            case TASK_TYPE_MARK_CARD:
                return doInBackgroundMarkCard(params);

            case TASK_TYPE_ADD_FACT:
                return doInBackgroundAddFact(params);

            case TASK_TYPE_UPDATE_FACT:
                return doInBackgroundUpdateFact(params);
                
            case TASK_TYPE_UNDO:
                return doInBackgroundUndo(params);                

            case TASK_TYPE_REDO:
                return doInBackgroundRedo(params);
                
            case TASK_TYPE_LOAD_CARDS:
                return doInBackgroundLoadCards(params);

            case TASK_TYPE_BURY_CARD:
                return doInBackgroundBuryCard(params);

            case TASK_TYPE_DELETE_CARD:
                return doInBackgroundDeleteCard(params);

            case TASK_TYPE_LOAD_STATISTICS:
                return doInBackgroundLoadStatistics(params);

            case TASK_TYPE_OPTIMIZE_DECK:
                return doInBackgroundOptimizeDeck(params);

            case TASK_TYPE_SET_ALL_DECKS_JOURNAL_MODE:
                return doInBackgroundSetJournalMode(params);
                
            case TASK_TYPE_CLOSE_DECK:
                return doInBackgroundCloseDeck(params);
                
            case TASK_TYPE_DELETE_BACKUPS:
                return doInBackgroundDeleteBackups(params);
                
            case TASK_TYPE_RESTORE_DECK:
                return doInBackgroundRestoreDeck(params);
                
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


    private TaskData doInBackgroundAddFact(TaskData[] params) {
        // Save the fact
        Deck deck = params[0].getDeck();
        Fact editFact = params[0].getFact();
        LinkedHashMap<Long, CardModel> cardModels = params[0].getCardModels();

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
        	publishProgress(new TaskData(deck.addFact(editFact, cardModels) != null));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }
        return null;
    }


    private TaskData doInBackgroundUpdateFact(TaskData[] params) {
        // Save the fact
        Deck deck = params[0].getDeck();
        Card editCard = params[0].getCard();
        Fact editFact = editCard.getFact();

        // Start undo routine
        String undoName = Deck.UNDO_TYPE_EDIT_CARD;
        deck.setUndoStart(undoName, editCard.getId());

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
                publishProgress(new TaskData(newCard, oldCard.getLeechFlag(), oldCard.getSuspendedFlag()));
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
        Deck oldDeck = params[0].getDeck();
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        if (oldDeck != null) {
        	publishProgress(new TaskData(res.getString(R.string.close_current_deck)));
        	oldDeck.closeDeck(false);
        }
        if (PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getBoolean("useBackup", true)) {
        	publishProgress(new TaskData(res.getString(R.string.backup_deck)));
            publishProgress(new TaskData(BackupManager.backupDeck(deckFilename)));
        }
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);

        Log.i(AnkiDroidApp.TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
        try {
            // Open the right deck.
            Deck deck = Deck.openDeck(deckFilename);
            // Start by getting the first card and displaying it.
            // Card card = deck.getCard();
            Log.i(AnkiDroidApp.TAG, "Deck loaded!");
            
            return new TaskData(DECK_LOADED, deck, null);
        } catch (SQLException e) {
            Log.i(AnkiDroidApp.TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
            return new TaskData(DECK_NOT_LOADED);
        } catch (CursorIndexOutOfBoundsException e) {
            // XXX: Where is this exception thrown?
            Log.i(AnkiDroidApp.TAG, "The deck has no cards = " + e.getMessage());
            return new TaskData(DECK_EMPTY);
        }
    }


    private TaskData doInBackgroundSaveDeck(TaskData... params) {
    	Deck deck = params[0].getDeck();
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSaveAndResetDeck");
        if (deck != null) {
            deck.commitToDB();
            deck.updateCutoff();
            if (AnkiDroidApp.deck().hasFinishScheduler()) {
                AnkiDroidApp.deck().finishScheduler();
            }
            deck.reset();
        }
        return null;
    }


    private TaskData doInBackgroundSuspendCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card oldCard = params[0].getCard();
        Card newCard = null;

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
            if (oldCard != null) {
                String undoName = Deck.UNDO_TYPE_SUSPEND_CARD;
                deck.setUndoStart(undoName, oldCard.getId());
                if (oldCard.getSuspendedState()) {
                    oldCard.unsuspend();
                    newCard = oldCard;
                } else {
                    oldCard.suspend();
                    newCard = deck.getCard();
                }
                deck.setUndoEnd(undoName);
            }
            
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
                String undoName = Deck.UNDO_TYPE_MARK_CARD;
                deck.setUndoStart(undoName, currentCard.getId());
            	if (currentCard.isMarked()) {
                    deck.deleteTag(currentCard.getFactId(), Deck.TAG_MARKED);
                } else {
                    deck.addTag(currentCard.getFactId(), Deck.TAG_MARKED);
                }
            	deck.resetMarkedTagId();
            	deck.setUndoEnd(undoName);
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
        long currentCardId = params[0].getLong();
        boolean inReview = params[0].getBoolean();
        long oldCardId = 0;

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
        	oldCardId = deck.undo(currentCardId, inReview);
            newCard = deck.getCard();
            if (oldCardId != 0 && newCard != null && oldCardId != newCard.getId()) {
            	newCard = deck.cardFromId(oldCardId);
            }
            publishProgress(new TaskData(newCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return new TaskData(deck.getUndoType(), oldCardId);
    }


    private TaskData doInBackgroundRedo(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card newCard;
        long currentCardId = params[0].getLong();
        boolean inReview = params[0].getBoolean();
        long oldCardId = 0;

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
        	oldCardId = deck.redo(currentCardId, inReview);
            newCard = deck.getCard();
            if (oldCardId != 0 && newCard != null && oldCardId != newCard.getId()) {
            	newCard = deck.cardFromId(oldCardId);
            }
            publishProgress(new TaskData(newCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }

        return new TaskData(deck.getUndoType(), oldCardId);
    }


    private TaskData doInBackgroundLoadCards(TaskData... params) {
        Deck deck = params[0].getDeck();
        String order = params[0].getString();
    	Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadCards");
       	publishProgress(new TaskData(deck.getAllCards(order)));
        return null;
    }


    private TaskData doInBackgroundDeleteCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card card = params[0].getCard();
        Card newCard = null;
        Long id = 0l;
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteCard");

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
            id = card.getId();
            card.delete();
            deck.reset();
            newCard = deck.getCard();
            publishProgress(new TaskData(newCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }
        return new TaskData(String.valueOf(id));
    }


    private TaskData doInBackgroundBuryCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card card = params[0].getCard();
        Card newCard = null;
        Long id = 0l;
        Log.i(AnkiDroidApp.TAG, "doInBackgroundBuryCard");

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
        ankiDB.getDatabase().beginTransaction();
        try {
            id = card.getId();
            deck.buryFact(card.getFactId(), id);
            deck.reset();
            newCard = deck.getCard();
            publishProgress(new TaskData(newCard));
            ankiDB.getDatabase().setTransactionSuccessful();
        } finally {
            ankiDB.getDatabase().endTransaction();
        }
        return new TaskData(String.valueOf(id));
    }


    private TaskData doInBackgroundLoadStatistics(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadStatistics");
        int type = params[0].getType();
        int period = params[0].getInt();
        Context context = params[0].getContext();
        String[] deckList = params[0].getDeckList();;
        boolean result = false;

        Resources res = context.getResources();
        if (deckList.length == 1 && deckList[0].equals("") && AnkiDroidApp.deck() != null) {
        	result = Statistics.refreshDeckStatistics(context, AnkiDroidApp.deck(), type, Integer.parseInt(res.getStringArray(R.array.statistics_period_values)[period]), res.getStringArray(R.array.statistics_type_labels)[type]);        	
        } else {
        	result = Statistics.refreshAllDeckStatistics(context, deckList, type, Integer.parseInt(res.getStringArray(R.array.statistics_period_values)[period]), res.getStringArray(R.array.statistics_type_labels)[type] + " " + res.getString(R.string.statistics_all_decks));        	
        }
       	publishProgress(new TaskData(result));
        return new TaskData(result);
    }


    private TaskData doInBackgroundOptimizeDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundOptimizeDeck");
    	Deck deck = params[0].getDeck();
        long result = 0;
    	result = deck.optimizeDeck();
        return new TaskData(deck, result);
    }


    private TaskData doInBackgroundSetJournalMode(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSetJournalMode");
        String path = params[0].getString();
        Deck currentDeck = params[0].getDeck();
        if (currentDeck != null) {
        	currentDeck.closeDeck(false);
        }

        int len = 0;
		File[] fileList;

		File dir = new File(path);
		fileList = dir.listFiles(new AnkiFilter());

		if (dir.exists() && dir.isDirectory() && fileList != null) {
			len = fileList.length;
		} else {
			return null;
		}

		if (len > 0 && fileList != null) {
			Log.i(AnkiDroidApp.TAG, "Set journal mode: number of anki files = " + len);
			for (File file : fileList) {
				// on deck open, journal mode will be automatically set
				String filePath = file.getAbsolutePath();
				Deck deck = Deck.openDeck(filePath, false);
				if (deck != null) {
					Log.i(AnkiDroidApp.TAG, "Journal mode of file " + filePath + " set");
					deck.closeDeck(false);					
				}
			}
		}
        return null;
    }

    
    private TaskData doInBackgroundCloseDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundCloseDeck");
    	Deck deck = params[0].getDeck();
    	boolean wait = params[0].getBoolean();
    	if (deck != null) {
    		deck.closeDeck(wait);
    	}
    	return null;
    }


    private TaskData doInBackgroundDeleteBackups(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteBackups");
    	return new TaskData(BackupManager.deleteAllBackups());
    }


    private TaskData doInBackgroundRestoreDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundRestoreDeck");
        String[] paths = params[0].getDeckList();
    	return new TaskData(BackupManager.restoreDeckBackup(paths[0], paths[1]));
    }


    public static interface TaskListener {
        public void onPreExecute();


        public void onPostExecute(TaskData result);


        public void onProgressUpdate(TaskData... values);
    }

    public static class TaskData {
        private Deck mDeck;
        private Card mCard;
        private Fact mFact;
        private int mInteger;
        private String mMsg;
        private boolean previousCardLeech;     // answer card resulted in card marked as leech
        private boolean previousCardSuspended; // answer card resulted in card marked as leech and suspended
        private boolean mBool = false;
        private ArrayList<String[]> mAllCards;
        private long mLong;
        private Context mContext;
        private int mType;
        private String[] mDeckList;
        private LinkedHashMap<Long, CardModel> mCardModels;


        public TaskData(int value, Deck deck, Card card) {
            this(value);
            mDeck = deck;
            mCard = card;
        }


        public TaskData(int value, Deck deck, long cardId, boolean bool) {
            this(value);
            mDeck = deck;
            mLong = cardId;
            mBool = bool;
        }


        public TaskData(Card card) {
            mCard = card;
            previousCardLeech = false;
            previousCardSuspended = false;
        }


        public TaskData(Context context, String[] deckList, int type, int period) {
            mContext = context;
            mDeckList = deckList;
            mType = type;
        	mInteger = period;
        }


        public TaskData(Deck deck, Fact fact, LinkedHashMap<Long, CardModel> cardModels) {
        	mDeck = deck;
        	mFact = fact;
        	mCardModels = cardModels;
        }


        public TaskData(ArrayList<String[]> allCards) {
        	if (allCards != null) {
        		mAllCards = new ArrayList<String[]>();
        		mAllCards.addAll(allCards);
        	}
        }


        public TaskData(Card card, boolean markedLeech, boolean suspendedLeech) {
            mCard = card;
            previousCardLeech = markedLeech;
            previousCardSuspended = suspendedLeech;
        }


        public TaskData(Deck deck, String order) {
            mDeck = deck;
            mMsg = order;
        }

 
        public TaskData(Deck deck, long value) {
            mDeck = deck;
            mLong = value;
        }

 
        public TaskData(boolean bool) {
            mBool = bool;
        }

 
        public TaskData(int value) {
            mInteger = value;
        }


        public TaskData(String msg) {
            mMsg = msg;
        }


        public TaskData(String msg, long cardId) {
            mMsg = msg;
            mLong = cardId;
        }


        public Deck getDeck() {
            return mDeck;
        }


        public ArrayList<String[]> getAllCards() {
        	return mAllCards;
        }


        public Card getCard() {
            return mCard;
        }


        public Fact getFact() {
            return mFact;
        }


        public long getLong() {
            return mLong;
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


        public boolean getBoolean() {
            return mBool;
        }


        public Context getContext() {
            return mContext;
        }


        public int getType() {
            return mType;
        }


        public LinkedHashMap<Long, CardModel> getCardModels() {
            return mCardModels;
        }


        public String[] getDeckList() {
            return mDeckList;
        }
    }

}
