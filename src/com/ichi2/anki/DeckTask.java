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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.ichi2.anki.DeckPicker.AnkiFilter;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.res.Resources;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
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
    public static final int TASK_TYPE_SORT_CARDS = 19;
    public static final int TASK_TYPE_LOAD_TUTORIAL = 20;
    public static final int TASK_TYPE_REPAIR_DECK = 21;


    /**
     * Possible outputs trying to load a deck.
     */
    public static final int DECK_LOADED = 0;
    public static final int DECK_NOT_LOADED = 1;
    public static final int DECK_EMPTY = 2;
    public static final int TUTORIAL_NOT_CREATED = 3;

    private static DeckTask sInstance;
    private static DeckTask sOldInstance;

    private int mType;
    private TaskListener mListener;


    
    public static DeckTask launchDeckTask(int type, TaskListener listener, TaskData... params) {
        sOldInstance = sInstance;

        sInstance = new DeckTask();
        sInstance.mListener = listener;
        sInstance.mType = type;

        sInstance.execute(params);
        return sInstance;
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


    public static boolean taskIsRunning() {
        try {
            if ((sInstance != null) && (sInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                return true;
            }
        } catch (Exception e) {
            return true;
        }
        return false;
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
                return doInBackgroundDeleteBackups();
                
            case TASK_TYPE_RESTORE_DECK:
                return doInBackgroundRestoreDeck(params);

            case TASK_TYPE_SORT_CARDS:
                return doInBackgroundSortCards(params);

            case TASK_TYPE_LOAD_TUTORIAL:
                return doInBackgroundLoadTutorial(params);

            case TASK_TYPE_REPAIR_DECK:
                return doInBackgroundRepairDeck(params);

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
        	publishProgress(new TaskData(deck.addFact(editFact, cardModels, false) != null));
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
        int showQuestion = params[0].getInt();

        try {
	        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
	        ankiDB.getDatabase().beginTransaction();
	        try {
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
	            if (showQuestion == Reviewer.UPDATE_CARD_NEW_CARD) {
	                publishProgress(new TaskData(showQuestion, null, deck.getCard()));
	            } else {
	                publishProgress(new TaskData(showQuestion, null, deck.cardFromId(editCard.getId())));        	
	            }

	        	ankiDB.getDatabase().setTransactionSuccessful();
	        } finally {
	            ankiDB.getDatabase().endTransaction();
	        }
		} catch (RuntimeException e) {
			Log.e(AnkiDroidApp.TAG, "doInBackgroundUpdateFact - RuntimeException on updating fact: " + e);
			AnkiDroidApp.saveExceptionReportFile(e);
			return new TaskData(false);
		}
        return new TaskData(true);
    }


    private TaskData doInBackgroundAnswerCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card oldCard = params[0].getCard();
        int ease = params[0].getInt();
        Card newCard;
        try {
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
		} catch (RuntimeException e) {
			Log.e(AnkiDroidApp.TAG, "doInBackgroundAnswerCard - RuntimeException on answering card: " + e);
			AnkiDroidApp.saveExceptionReportFile(e);
			return new TaskData(false);
		}
        return new TaskData(true);
    }


    private TaskData doInBackgroundLoadDeck(TaskData... params) {
        String deckFilename = params[0].getString();
        Deck oldDeck = params[0].getDeck();

        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        if (oldDeck != null) {
        	publishProgress(new TaskData(res.getString(R.string.close_previous_deck)));
        	oldDeck.closeDeck(false);
        }
        int backupResult = BackupManager.RETURN_NULL;
        if (PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getBoolean("useBackup", true)) {
        	publishProgress(new TaskData(res.getString(R.string.backup_deck)));
        	backupResult = BackupManager.backupDeck(deckFilename);
        }
        if (BackupManager.getFreeDiscSpace(deckFilename) < (StudyOptions.MIN_FREE_SPACE * 1024 * 1024)) {
        	backupResult = BackupManager.RETURN_LOW_SYSTEM_SPACE;
        }
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);

        Log.i(AnkiDroidApp.TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
        try {
            // Open the right deck.
            Deck deck = Deck.openDeck(deckFilename);
            // Start by getting the first card and displaying it.
            // Card card = deck.getCard();
            Log.i(AnkiDroidApp.TAG, "Deck loaded!");
            if (deck == null) {
                BackupManager.cleanUpAfterBackupCreation(false);
                return new TaskData(DECK_NOT_LOADED);
            }
            BackupManager.cleanUpAfterBackupCreation(true);
            publishProgress(new TaskData(backupResult));
            return new TaskData(DECK_LOADED, deck, null);
		} catch (SQLException e) {
            Log.i(AnkiDroidApp.TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
                BackupManager.cleanUpAfterBackupCreation(false);
            return new TaskData(DECK_NOT_LOADED);
        } catch (CursorIndexOutOfBoundsException e) {
            // XXX: Where is this exception thrown?
            Log.i(AnkiDroidApp.TAG, "The deck has no cards = " + e.getMessage());
            return new TaskData(DECK_EMPTY);
		} catch (RuntimeException e) {
            Log.i(AnkiDroidApp.TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
            return new TaskData(DECK_NOT_LOADED);
        }
    }


    private TaskData doInBackgroundSaveDeck(TaskData... params) {
    	Deck deck = params[0].getDeck();
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSaveAndResetDeck");
        if (deck != null) {
            try {
            	deck.commitToDB();
            	deck.updateCutoff();
            	if (AnkiDroidApp.deck().hasFinishScheduler()) {
            		AnkiDroidApp.deck().finishScheduler();
            	}
            	deck.reset();
            } catch (SQLiteDiskIOException e) {
            	Log.e(AnkiDroidApp.TAG, "Error on saving deck in background: " + e);
            }
        }
        return null;
    }


    private TaskData doInBackgroundSuspendCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card oldCard = params[0].getCard();
        Card newCard = null;

        try {
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
    	} catch (RuntimeException e) {
    		Log.e(AnkiDroidApp.TAG, "doInBackgroundSuspendCard - RuntimeException on suspending card: " + e);
    		AnkiDroidApp.saveExceptionReportFile(e);
    		return new TaskData(false);
    	}
        return new TaskData(true);
    }
        	


    private TaskData doInBackgroundMarkCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card currentCard = params[0].getCard();

        try {
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
    	} catch (RuntimeException e) {
    		Log.e(AnkiDroidApp.TAG, "doInBackgroundMarkCard - RuntimeException on marking card: " + e);
    		AnkiDroidApp.saveExceptionReportFile(e);
    		return new TaskData(false);
        }
		return new TaskData(true);
    }


    private TaskData doInBackgroundUndo(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card newCard;
        long currentCardId = params[0].getLong();
        boolean inReview = params[0].getBoolean();
        long oldCardId = 0;
        String undoType = null;
        
        try {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
            ankiDB.getDatabase().beginTransaction();
            try {
            	oldCardId = deck.undo(currentCardId, inReview);
            	undoType = deck.getUndoType();
            	if (undoType == Deck.UNDO_TYPE_SUSPEND_CARD) {
            		oldCardId = currentCardId;
            	}
                newCard = deck.getCard();
                if (oldCardId != 0 && newCard != null && oldCardId != newCard.getId()) {
                	newCard = deck.cardFromId(oldCardId);
                }
                publishProgress(new TaskData(newCard));
                ankiDB.getDatabase().setTransactionSuccessful();
            } finally {
                ankiDB.getDatabase().endTransaction();
            }
    	} catch (RuntimeException e) {
    		Log.e(AnkiDroidApp.TAG, "doInBackgroundUndo - RuntimeException on undoing: " + e);
    		AnkiDroidApp.saveExceptionReportFile(e);
            return new TaskData(undoType, oldCardId, false);
        }
        return new TaskData(undoType, oldCardId, true);
    }


    private TaskData doInBackgroundRedo(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card newCard;
        long currentCardId = params[0].getLong();
        boolean inReview = params[0].getBoolean();
        long oldCardId = 0;
        String undoType = null;

        try {
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
            undoType = deck.getUndoType();
            if (undoType == Deck.UNDO_TYPE_SUSPEND_CARD) {
            	undoType = "redo suspend";
            }
    	} catch (RuntimeException e) {
    		Log.e(AnkiDroidApp.TAG, "doInBackgroundRedo - RuntimeException on redoing: " + e);
    		AnkiDroidApp.saveExceptionReportFile(e);
            return new TaskData(undoType, oldCardId, false);
        }
        return new TaskData(undoType, oldCardId, true);
    }


    private TaskData doInBackgroundLoadCards(TaskData... params) {
        Deck deck = params[0].getDeck();
        int chunk = params[0].getInt();
    	Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadCards");
    	String startId = "";
    	while (true) {
    		ArrayList<HashMap<String, String>> cards = deck.getCards(chunk, startId);
    		if (cards.size() == 0) {
    			return null;	
    		} else {
               	publishProgress(new TaskData(cards));
               	startId = cards.get(cards.size() - 1).get("id");    			
    		}
    	}
    }


    private TaskData doInBackgroundDeleteCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card card = params[0].getCard();
        Card newCard = null;
        Long id = 0l;
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteCard");

        try {
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
    	} catch (RuntimeException e) {
    		Log.e(AnkiDroidApp.TAG, "doInBackgroundDeleteCard - RuntimeException on deleting card: " + e);
    		AnkiDroidApp.saveExceptionReportFile(e);
            return new TaskData(String.valueOf(id), 0, false);
    	}
        return new TaskData(String.valueOf(id), 0, true);
    }


    private TaskData doInBackgroundBuryCard(TaskData... params) {
        Deck deck = params[0].getDeck();
        Card card = params[0].getCard();
        Card newCard = null;
        Long id = 0l;
        Log.i(AnkiDroidApp.TAG, "doInBackgroundBuryCard");

        try {
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
    	} catch (RuntimeException e) {
    		Log.e(AnkiDroidApp.TAG, "doInBackgroundSuspendCard - RuntimeException on suspending card: " + e);
    		AnkiDroidApp.saveExceptionReportFile(e);
            return new TaskData(String.valueOf(id), 0, false);
    	}
        return new TaskData(String.valueOf(id), 0, true);
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


    private TaskData doInBackgroundRepairDeck(TaskData... params) {
    	Log.i(AnkiDroidApp.TAG, "doInBackgroundRepairDeck");
    	String deckPath = params[0].getString();
    	Deck currentDeck = AnkiDroidApp.deck();
    	if (currentDeck != null && currentDeck.getDeckPath().equals(deckPath)) {
    		doInBackgroundCloseDeck(new TaskData(currentDeck, 0));
    	}
    	return new TaskData(BackupManager.repairDeck(deckPath));
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
    	if (deck != null) {
    		try {
    			deck.closeDeck(false);
    			Log.i(AnkiDroidApp.TAG, "doInBackgroundCloseDeck - Deck closed");
    		} catch (SQLiteException e) {
    			Log.e(AnkiDroidApp.TAG, "Error on closing deck: " + e);
    		}
    	}
    	return null;
    }


    private TaskData doInBackgroundDeleteBackups() {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteBackups");
    	return new TaskData(BackupManager.deleteAllBackups());
    }


    private TaskData doInBackgroundRestoreDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundRestoreDeck");
        String[] paths = params[0].getDeckList();
    	return new TaskData(BackupManager.restoreDeckBackup(paths[0], paths[1]));
    }


    private TaskData doInBackgroundSortCards(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSortCards");
		Collections.sort(params[0].getCards(), params[0].getComparator());
		return null;
    }


    private TaskData doInBackgroundLoadTutorial(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadTutorial");
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        File sampleDeckFile = new File(params[0].getString());
    	publishProgress(new TaskData(res.getString(R.string.tutorial_load)));
    	AnkiDb ankiDB = null;
    	try{
    		// close open deck
    		Deck openDeck = AnkiDroidApp.deck();
    		if (openDeck != null) {
    			openDeck.closeDeck(false);
    			AnkiDroidApp.setDeck(null);
    		}
    		// delete any existing tutorial file
            if (!sampleDeckFile.exists()) {
            	sampleDeckFile.delete();
            }
    		// copy the empty deck from the assets to the SD card.
    		InputStream stream = res.getAssets().open(DeckCreator.EMPTY_DECK_NAME);
    		Utils.writeToFile(stream, sampleDeckFile.getAbsolutePath());
    		stream.close();
        	Deck.initializeEmptyDeck(sampleDeckFile.getAbsolutePath());
    		String[] questions = res.getStringArray(R.array.tutorial_questions);
    		String[] answers = res.getStringArray(R.array.tutorial_answers);
    		String[] sampleQuestions = res.getStringArray(R.array.tutorial_capitals_questions);
    		String[] sampleAnswers = res.getStringArray(R.array.tutorial_capitals_answers);
    		Deck deck = Deck.openDeck(sampleDeckFile.getAbsolutePath());
            ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
            ankiDB.getDatabase().beginTransaction();
            try {
            	CardModel cardModel = null;
            	int len = Math.min(questions.length, answers.length);
            	for (int i = 0; i < len + Math.min(sampleQuestions.length, sampleAnswers.length); i++) {
            		Fact fact = deck.newFact();
            		if (cardModel == null) {
            			cardModel = deck.activeCardModels(fact).entrySet().iterator().next().getValue();
            		}
            		int fidx = 0;
            		for (Fact.Field f : fact.getFields()) {
            			if (fidx == 0) {
            				f.setValue((i < len) ? questions[i] : sampleQuestions[i - len]);
            			} else if (fidx == 1) {
            				f.setValue((i < len) ? answers[i] : sampleAnswers[i - len]);
            			}
            			fidx++;
            		}
            		if (!deck.importFact(fact, cardModel)) {
            			sampleDeckFile.delete();
            			return new TaskData(TUTORIAL_NOT_CREATED);
            		}
            	}
            	deck.setSessionTimeLimit(0);
            	deck.flushMod();
            	deck.reset();
            	ankiDB.getDatabase().setTransactionSuccessful();
            } finally {
        		ankiDB.getDatabase().endTransaction();
        	}
        	AnkiDroidApp.setDeck(deck);
        	return new TaskData(DECK_LOADED, deck, null);
        } catch (IOException e) {
        	Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
        	Log.e(AnkiDroidApp.TAG, "Empty deck could not be copied to the sd card.");
        	sampleDeckFile.delete();
        	return new TaskData(TUTORIAL_NOT_CREATED);
    	} catch (RuntimeException e) {
        	Log.e(AnkiDroidApp.TAG, "Error on creating tutorial deck: " + e);
        	sampleDeckFile.delete();
        	return new TaskData(TUTORIAL_NOT_CREATED);
    	}
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
        private ArrayList<HashMap<String, String>> mCards;
        private long mLong;
        private Context mContext;
        private int mType;
        private String[] mDeckList;
        private LinkedHashMap<Long, CardModel> mCardModels;
        private Comparator<? super HashMap<String, String>> mComparator;


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


        public TaskData(ArrayList<HashMap<String, String>> cards) {
        	mCards = cards;
        }


        public TaskData(ArrayList<HashMap<String, String>> cards, Comparator<? super HashMap<String, String>> comparator) {
        	mCards = cards;
        	mComparator = comparator;
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

 
        public TaskData(Deck deck, int chunk) {
            mDeck = deck;
            mInteger = chunk;
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


        public TaskData(String msg, long cardId, boolean bool) {
            mMsg = msg;
            mLong = cardId;
            mBool = bool;
        }


        public Deck getDeck() {
            return mDeck;
        }


        public ArrayList<HashMap<String, String>> getCards() {
        	return mCards;
        }


        public Comparator<? super HashMap<String, String>> getComparator() {
        	return mComparator;
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
