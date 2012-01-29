/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>       							*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.async;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.DeckCreator;
import com.ichi2.anki2.R;
import com.ichi2.anki.Reviewer;
import com.ichi2.anki.Statistics;
import com.ichi2.anki.StudyOptions;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Utils;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDiskIOException;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class DeckTask extends AsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData> {

    public static final int TASK_TYPE_OPEN_COLLECTION = 0;
    public static final int TASK_TYPE_OPEN_COLLECTION_AND_UPDATE_CARDS = 1;
    public static final int TASK_TYPE_SAVE_DECK = 2;
    public static final int TASK_TYPE_ANSWER_CARD = 3;
    public static final int TASK_TYPE_SUSPEND_CARD = 4;
    public static final int TASK_TYPE_MARK_CARD = 5;
    public static final int TASK_TYPE_ADD_FACT = 6;
    public static final int TASK_TYPE_UPDATE_FACT = 7;
    public static final int TASK_TYPE_UNDO = 8;
    public static final int TASK_TYPE_LOAD_CARDS = 10;
    public static final int TASK_TYPE_BURY_CARD = 11;
    public static final int TASK_TYPE_DELETE_CARD = 12;
    public static final int TASK_TYPE_LOAD_STATISTICS = 13;
    public static final int TASK_TYPE_OPTIMIZE_DECK = 14;
    public static final int TASK_TYPE_SET_ALL_DECKS_JOURNAL_MODE = 15;
    public static final int TASK_TYPE_DELETE_BACKUPS = 16;
    public static final int TASK_TYPE_RESTORE_DECK = 17;
    public static final int TASK_TYPE_SORT_CARDS = 18;
    public static final int TASK_TYPE_LOAD_TUTORIAL = 19;
    public static final int TASK_TYPE_REPAIR_DECK = 20;
    public static final int TASK_TYPE_CLOSE_DECK = 21;
    public static final int TASK_TYPE_LOAD_DECK_COUNTS = 22;
    public static final int TASK_TYPE_UPDATE_VALUES_FROM_DECK = 23;


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
            	Log.i(AnkiDroidApp.TAG, "DeckTask: wait to finish");
                sInstance.get();
            }
        } catch (Exception e) {
            return;
        }
    }


    public static void cancelTask() {
        try {
            if ((sInstance != null) && (sInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                sInstance.cancel(true);
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
            	Log.i(AnkiDroidApp.TAG, "Waiting for " + sOldInstance.mType + " to finish");
                sOldInstance.get();
            }
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG,
                    "doInBackground - Got exception while waiting for thread to finish: " + e.getMessage());
        }

        switch (mType) {
            case TASK_TYPE_OPEN_COLLECTION:
                return doInBackgroundOpenCollection(params);

            case TASK_TYPE_LOAD_DECK_COUNTS:
            	return doInBackgroundLoadDeckCounts(params);

            case TASK_TYPE_OPEN_COLLECTION_AND_UPDATE_CARDS:
                TaskData taskData = doInBackgroundOpenCollection(params);
//                if (taskData.mInteger == DECK_LOADED) {
//                    taskData.mDeck.updateAllCards();
//                    taskData.mCard = taskData.mDeck.getCurrentCard();
//                }
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

            case TASK_TYPE_CLOSE_DECK:
                return doInBackgroundCloseDeck(params);

            case TASK_TYPE_UPDATE_VALUES_FROM_DECK:
            	return doInBackgroundUpdateValuesFromDeck(params);

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
    	Log.i(AnkiDroidApp.TAG, "doInBackgroundAddFact");
    	Note note = params[0].getNote();
    	Collection col = note.getCol();

    	try {
        	AnkiDb ankiDB = col.getDb();
            ankiDB.getDatabase().beginTransaction();
            try {
            	publishProgress(new TaskData(col.addNote(note)));
                ankiDB.getDatabase().setTransactionSuccessful();
            } finally {
                ankiDB.getDatabase().endTransaction();
            }
    	} catch (RuntimeException e) {
    		throw new RuntimeException(e);
//    		Log.e(AnkiDroidApp.TAG, "doInBackgroundAddFact - RuntimeException on adding fact: " + e);
//    		AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundAddFact");
//            return new TaskData(false);
        }
    	return new TaskData(true);
    }


    private TaskData doInBackgroundUpdateFact(TaskData[] params) {
        // Save the fact
    	Sched sched = params[0].getSched();
    	Collection col = sched.getCol();
        Card editCard = params[0].getCard();
        Note editNote = editCard.getNote();
        boolean showQuestion = params[0].getBoolean();

        try {
	        col.getDb().getDatabase().beginTransaction();
	        try {
	        	// TODO: undo integration
	        	editNote.flush();
	        	// flush card too, if did has been changed
	        	editCard.flush();
	        	Card newCard;
	        	if (col.getDecks().active().contains(editCard.getDid())) {
	        		newCard = editCard;
		        	// reload qa-cache
	        		newCard.getQuestion(true);
	        	} else {
	        		newCard = sched.getCard();
	        	}
	        	publishProgress(new TaskData(null, newCard, showQuestion));
	            col.getDb().getDatabase().setTransactionSuccessful();
	        } finally {
	        	col.getDb().getDatabase().endTransaction();
	        }
		} catch (RuntimeException e) {
			Log.e(AnkiDroidApp.TAG, "doInBackgroundUpdateFact - RuntimeException on updating fact: " + e);
			AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundUpdateFact");
			return new TaskData(false);
		}
        return new TaskData(true);
    }


    private TaskData doInBackgroundAnswerCard(TaskData... params) {
    	Sched sched = params[0].getSched();
        Card oldCard = params[0].getCard();
    	int ease = params[0].getInt();
        Card newCard = null;
        int oldCardLeech = 0;
        // 0: normal; 1: leech; 2: leech & suspended
//        try {
	        AnkiDb ankiDB = sched.getCol().getDb();
	        ankiDB.getDatabase().beginTransaction();
	        double muh = Utils.now() * 1000;
	        try {
	            if (oldCard != null) {
	            	oldCardLeech = sched.answerCard(oldCard, ease) ? 1 : 0;
	            	if (oldCardLeech != 0) {
	            		oldCardLeech += sched.leechActionSuspend(oldCard) ? 1 : 0;
	            	}
//	            } else if (DeckManager.deckIsOpenedInBigWidget(deck.getDeckPath())) {
//	                // first card in reviewer is retrieved
//	            	Log.i(AnkiDroidApp.TAG, "doInBackgroundAnswerCard: get card from big widget");
//                	newCard = AnkiDroidWidgetBig.getCard();
	            }
	            Log.e("antworten", "" + (Utils.now()* 1000 - muh));
	            muh = Utils.now()* 1000;
	            if (newCard == null) {
		            newCard = sched.getCard();
	            }
	            Log.e("neue", "" + (Utils.now()* 1000 - muh));
	            muh = Utils.now()* 1000;
	            if (newCard != null) {
		            // render cards before locking database
	            	newCard._getQA(true);
	            }
	            Log.e("qa", "" + (Utils.now()* 1000 - muh));
	            muh = Utils.now()* 1000;
                publishProgress(new TaskData(newCard, oldCardLeech));
	            ankiDB.getDatabase().setTransactionSuccessful();
	        } finally {
	            ankiDB.getDatabase().endTransaction();
	        }
            Log.e("write", "" + (Utils.now()* 1000 - muh));
//		} catch (RuntimeException e) {
//			Log.e(AnkiDroidApp.TAG, "doInBackgroundAnswerCard - RuntimeException on answering card: " + e);
//			AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundAnswerCard");
//			return new TaskData(false);
//		}
        return new TaskData(true);
    }


    private TaskData doInBackgroundOpenCollection(TaskData... params) {
//        String deckFilename = params[0].getString();
//        int requestingActivity = params[0].getInt();

//        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename + ", requesting activity = " + requestingActivity);

        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();

//        publishProgress(new TaskData(AnkiDroidApp.getInstance().getBaseContext().getResources().getString(R.string.finish_operation)));
//        DeckManager.waitForDeckClosingThread(deckFilename);

//        int backupResult = BackupManager.RETURN_NULL;
//        if (PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getBoolean("useBackup", true)) {
//        	publishProgress(new TaskData(res.getString(R.string.backup_deck)));
//        	backupResult = BackupManager.backupDeck(deckFilename);
//        }
//        if (BackupManager.getFreeDiscSpace(deckFilename) < (StudyOptions.MIN_FREE_SPACE * 1024 * 1024)) {
//        	backupResult = BackupManager.RETURN_LOW_SYSTEM_SPACE;
//        }

        Log.i(AnkiDroidApp.TAG, "loadDeck - SD card mounted and existent file -> Loading collection...");

    	// load deck and set it as main deck
    	publishProgress(new TaskData(res.getString(R.string.loading_deck)));
    	Collection col = Collection.openCollection("/emmc/AnkiDroid/collection.anki2");
//        Decks deck = DeckManager.getDeck(deckFilename, requestingActivity == DeckManager.REQUESTING_ACTIVITY_STUDYOPTIONS, requestingActivity);
//        if (deck == null) {
//            Log.i(AnkiDroidApp.TAG, "The database " + deckFilename + " could not be opened");
//            BackupManager.cleanUpAfterBackupCreation(false);
//            return new TaskData(DECK_NOT_LOADED, deckFilename);            	
//        }
//        BackupManager.cleanUpAfterBackupCreation(true);
//        if (deck.hasFinishScheduler()) {
//        	deck.finishScheduler();
//        }
//        publishProgress(new TaskData(backupResult));

    	// load decks
    	TreeSet<Object[]> decks = col.getSched().deckDueTree(false);
        return new TaskData(col, decks, DECK_LOADED);
    }


    private TaskData doInBackgroundLoadDeckCounts(TaskData... params) {
    	Collection col = params[0].getCollection();
    	TreeSet<Object[]> decks = col.getSched().deckDueTree(true);
    	return new TaskData(decks);
    }


    private TaskData doInBackgroundSaveDeck(TaskData... params) {
//    	Decks deck = params[0].getDeck();
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundSaveAndResetDeck");
//        if (deck != null) {
//            try {
//            	deck.commitToDB();
//            	deck.updateCutoff();
//            	if (deck.hasFinishScheduler()) {
//            		deck.finishScheduler();
//            	}
//            	deck.reset();
//            } catch (SQLiteDiskIOException e) {
//            	Log.e(AnkiDroidApp.TAG, "Error on saving deck in background: " + e);
//            }
//        }
        return null;
    }


    private TaskData doInBackgroundSuspendCard(TaskData... params) {
//        Decks deck = params[0].getDeck();
//        Card oldCard = params[0].getCard();
//        Card newCard = null;
//
//        try {
//            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
//            ankiDB.getDatabase().beginTransaction();
//            try {
//                if (oldCard != null) {
//                    String undoName = Decks.UNDO_TYPE_SUSPEND_CARD;
//                    deck.setUndoStart(undoName, oldCard.getId());
//                    if (oldCard.getSuspendedState()) {
//                        oldCard.unsuspend();
//                        newCard = oldCard;
//                    } else {
//                        oldCard.suspend();
//                        newCard = deck.getCard();
//                    }
//                    deck.setUndoEnd(undoName);
//                }
//                
//                publishProgress(new TaskData(newCard));
//                ankiDB.getDatabase().setTransactionSuccessful();
//            } finally {
//                ankiDB.getDatabase().endTransaction();
//            }
//    	} catch (RuntimeException e) {
//    		Log.e(AnkiDroidApp.TAG, "doInBackgroundSuspendCard - RuntimeException on suspending card: " + e);
//			AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSuspendCard");
//    		return new TaskData(false);
//    	}
        return new TaskData(true);
    }
        	


    private TaskData doInBackgroundMarkCard(TaskData... params) {
//        Decks deck = params[0].getDeck();
//        Card currentCard = params[0].getCard();
//
//        try {
//            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
//            ankiDB.getDatabase().beginTransaction();
//            try {
//                if (currentCard != null) {
//                    String undoName = Decks.UNDO_TYPE_MARK_CARD;
//                    deck.setUndoStart(undoName, currentCard.getId());
//                	if (currentCard.isMarked()) {
//                        deck.deleteTag(currentCard.getFactId(), Decks.TAG_MARKED);
//                    } else {
//                        deck.addTag(currentCard.getFactId(), Decks.TAG_MARKED);
//                    }
//                	deck.resetMarkedTagId();
//                	deck.setUndoEnd(undoName);
//                }
//
//                publishProgress(new TaskData(currentCard));
//                ankiDB.getDatabase().setTransactionSuccessful();
//            } finally {
//                ankiDB.getDatabase().endTransaction();
//            }
//    	} catch (RuntimeException e) {
//    		Log.e(AnkiDroidApp.TAG, "doInBackgroundMarkCard - RuntimeException on marking card: " + e);
//			AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundMarkCard");
//    		return new TaskData(false);
//        }
		return new TaskData(true);
    }


    private TaskData doInBackgroundUndo(TaskData... params) {
    	Sched sched = params[0].getSched();
    	Collection col = sched.getCol();
        try {
            col.getDb().getDatabase().beginTransaction();
            try {
            	Card newCard = col.undo();
            	col.reset();
            	if (newCard != null) {
            		// a review was undone, 
                	if (!sched.removeCardFromQueues(newCard)) {
                		// card was not found in queues
                		newCard = sched.getCard();
                	}
            	} else {
            		newCard = sched.getCard();
            	}
            	// TODO: handle leech undoing properly
                publishProgress(new TaskData(newCard, 0));
                col.getDb().getDatabase().setTransactionSuccessful();
            } finally {
            	col.getDb().getDatabase().endTransaction();
            }
    	} catch (RuntimeException e) {
    		Log.e(AnkiDroidApp.TAG, "doInBackgroundUndo - RuntimeException on undoing: " + e);
			AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundUndo");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundLoadCards(TaskData... params) {
//        Decks deck = params[0].getDeck();
//        int chunk = params[0].getInt();
//    	Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadCards");
//    	String startId = "";
//    	while (!this.isCancelled()) {
//    		ArrayList<HashMap<String, String>> cards = deck.getCards(chunk, startId);
//    		if (cards.size() == 0) {
//    			break;
//    		} else {
//               	publishProgress(new TaskData(cards));
//               	startId = cards.get(cards.size() - 1).get("id");    			
//    		}
//    	}
    	return null;
    }


    private TaskData doInBackgroundDeleteCard(TaskData... params) {
//        Decks deck = params[0].getDeck();
//        Card card = params[0].getCard();
//        Card newCard = null;
//        Long id = 0l;
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteCard");
//
//        try {
//            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
//            ankiDB.getDatabase().beginTransaction();
//            try {
//                id = card.getId();
//                card.delete();
//                deck.reset();
//                newCard = deck.getCard();
//                publishProgress(new TaskData(newCard));
//                ankiDB.getDatabase().setTransactionSuccessful();
//            } finally {
//                ankiDB.getDatabase().endTransaction();
//            }
//    	} catch (RuntimeException e) {
//    		Log.e(AnkiDroidApp.TAG, "doInBackgroundDeleteCard - RuntimeException on deleting card: " + e);
//			AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundDeleteCard");
//            return new TaskData(String.valueOf(id), 0, false);
//    	}
//        return new TaskData(String.valueOf(id), 0, true);
    	return null;
    }


    private TaskData doInBackgroundBuryCard(TaskData... params) {
////        Deck deck = params[0].getDeck();
////        Card card = params[0].getCard();
////        Card newCard = null;
//        Long id = 0l;
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundBuryCard");
//
//        try {
//            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
//            ankiDB.getDatabase().beginTransaction();
//            try {
//                id = card.getId();
//                deck.buryFact(card.getFactId(), id);
//                deck.reset();
//                newCard = deck.getCard();
//                publishProgress(new TaskData(newCard));
//                ankiDB.getDatabase().setTransactionSuccessful();
//            } finally {
//                ankiDB.getDatabase().endTransaction();
//            }
//    	} catch (RuntimeException e) {
//    		Log.e(AnkiDroidApp.TAG, "doInBackgroundSuspendCard - RuntimeException on suspending card: " + e);
//			AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundBuryCard");
//            return new TaskData(String.valueOf(id), 0, false);
//    	}
//        return new TaskData(String.valueOf(id), 0, true);
    	return null;
    }


    private TaskData doInBackgroundLoadStatistics(TaskData... params) {
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadStatistics");
//        int type = params[0].getType();
//        int period = params[0].getInt();
//        Context context = params[0].getContext();
//        String[] deckList = params[0].getDeckList();;
//        boolean result = false;
//
//        Resources res = context.getResources();
//        if (deckList.length == 1) {
//        	if (deckList[0].length() == 0) {
//            	result = Statistics.refreshDeckStatistics(context, DeckManager.getMainDeck(DeckManager.REQUESTING_ACTIVITY_STUDYOPTIONS), type, Integer.parseInt(res.getStringArray(R.array.statistics_period_values)[period]), res.getStringArray(R.array.statistics_type_labels)[type]);        		
//        	}
//        } else {
//        	result = Statistics.refreshAllDeckStatistics(context, deckList, type, Integer.parseInt(res.getStringArray(R.array.statistics_period_values)[period]), res.getStringArray(R.array.statistics_type_labels)[type] + " " + res.getString(R.string.statistics_all_decks));        	
//        }
//       	publishProgress(new TaskData(result));
//        return new TaskData(result);
    	return null;
    }


    private TaskData doInBackgroundOptimizeDeck(TaskData... params) {
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundOptimizeDeck");
//    	Collection col = params[0].getDeck();
//        long result = 0;
//    	result = deck.optimize();
//        return new TaskData(deck, result);
    	return null;
    }


    private TaskData doInBackgroundRepairDeck(TaskData... params) {
//    	Log.i(AnkiDroidApp.TAG, "doInBackgroundRepairDeck");
//    	String deckPath = params[0].getString();
//    	DeckManager.closeDeck(deckPath, false);
//    	return new TaskData(BackupManager.repairDeck(deckPath));
    	return null;
    }


    private TaskData doInBackgroundCloseDeck(TaskData... params) {
    	Log.i(AnkiDroidApp.TAG, "doInBackgroundCloseDeck");
    	Collection col = params[0].getCollection();
    	col.close(true);
    	return null;
    }


    private TaskData doInBackgroundSetJournalMode(TaskData... params) {
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundSetJournalMode");
//        String path = params[0].getString();
//
//        int len = 0;
//		File[] fileList;
//
//		File dir = new File(path);
//		fileList = dir.listFiles(new AnkiFilter());
//
//		if (dir.exists() && dir.isDirectory() && fileList != null) {
//			len = fileList.length;
//		} else {
//			return null;
//		}
//
//		if (len > 0 && fileList != null) {
//			Log.i(AnkiDroidApp.TAG, "Set journal mode: number of anki files = " + len);
//			for (File file : fileList) {
//				// on deck open, journal mode will be automatically set, set requesting activity to syncclient to force delete journal mode
//				String filePath = file.getAbsolutePath();
//				DeckManager.getDeck(filePath, DeckManager.REQUESTING_ACTIVITY_SYNCCLIENT);
//				DeckManager.closeDeck(filePath, false);
//			}
//		}
        return null;
    }

    
    private TaskData doInBackgroundUpdateValuesFromDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundUpdateValuesFromDeck");
        boolean reset = params[0].getBoolean();
        Sched sched = params[0].getSched();
        if (reset) {
    		sched.reset();        	
        }
		int[] counts = sched.counts();
		int totalNewCount = sched.newCount();
		int totalCount = sched.cardCount();
		double progressMature= ((double) sched.matureCount())
				/ ((double) totalCount);
		double progressAll = 1 - (((double) (totalNewCount + counts[1])) / ((double) totalCount));

		return new TaskData(new Object[]{counts[0], counts[1], counts[2], totalNewCount, totalCount, progressMature, progressAll});
    }


    private TaskData doInBackgroundDeleteBackups() {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteBackups");
    	return new TaskData(BackupManager.deleteAllBackups());
    }


    private TaskData doInBackgroundRestoreDeck(TaskData... params) {
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundRestoreDeck");
//        String[] paths = params[0].getDeckList();
//    	return new TaskData(BackupManager.restoreDeckBackup(paths[0], paths[1]));
    	return null;
    }


    private TaskData doInBackgroundSortCards(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSortCards");
        Comparator<? super HashMap<String, String>> comparator = params[0].getComparator();
		Collections.sort(params[0].getCards(), comparator);
		return null;
    }


    private TaskData doInBackgroundLoadTutorial(TaskData... params) {
//        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadTutorial");
//        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
//        File sampleDeckFile = new File(params[0].getString());
//    	publishProgress(new TaskData(res.getString(R.string.tutorial_load)));
//    	AnkiDb ankiDB = null;
//    	try{
//    		// close open deck
//    		DeckManager.closeMainDeck(false);
//
//    		// delete any existing tutorial file
//            if (!sampleDeckFile.exists()) {
//            	sampleDeckFile.delete();
//            }
//    		// copy the empty deck from the assets to the SD card.
//    		InputStream stream = res.getAssets().open(DeckCreator.EMPTY_DECK_NAME);
//    		Utils.writeToFile(stream, sampleDeckFile.getAbsolutePath());
//    		stream.close();
//        	Decks.initializeEmptyDeck(sampleDeckFile.getAbsolutePath());
//    		String[] questions = res.getStringArray(R.array.tutorial_questions);
//    		String[] answers = res.getStringArray(R.array.tutorial_answers);
//    		String[] sampleQuestions = res.getStringArray(R.array.tutorial_capitals_questions);
//    		String[] sampleAnswers = res.getStringArray(R.array.tutorial_capitals_answers);
//    		Decks deck = DeckManager.getDeck(sampleDeckFile.getAbsolutePath(), DeckManager.REQUESTING_ACTIVITY_STUDYOPTIONS, true);
//            ankiDB = AnkiDatabaseManager.getDatabase(deck.getDeckPath());
//            ankiDB.getDatabase().beginTransaction();
//            try {
//            	CardModel cardModel = null;
//            	int len = Math.min(questions.length, answers.length);
//            	for (int i = 0; i < len + Math.min(sampleQuestions.length, sampleAnswers.length); i++) {
//            		Notes fact = deck.newFact();
//            		if (cardModel == null) {
//            			cardModel = deck.activeCardModels(fact).entrySet().iterator().next().getValue();
//            		}
//            		int fidx = 0;
//            		for (Notes.Field f : fact.getFields()) {
//            			if (fidx == 0) {
//            				f.setValue((i < len) ? questions[i] : sampleQuestions[i - len]);
//            			} else if (fidx == 1) {
//            				f.setValue((i < len) ? answers[i] : sampleAnswers[i - len]);
//            			}
//            			fidx++;
//            		}
//            		if (!deck.importFact(fact, cardModel)) {
//            			sampleDeckFile.delete();
//            			return new TaskData(TUTORIAL_NOT_CREATED);
//            		}
//            	}
//            	deck.setSessionTimeLimit(0);
//            	deck.flushMod();
//            	deck.reset();
//            	ankiDB.getDatabase().setTransactionSuccessful();
//            } finally {
//        		ankiDB.getDatabase().endTransaction();
//        	}
//        	return new TaskData(DECK_LOADED, deck, null);
//        } catch (IOException e) {
//        	Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
//        	Log.e(AnkiDroidApp.TAG, "Empty deck could not be copied to the sd card.");
//        	DeckManager.closeMainDeck(false);
//        	sampleDeckFile.delete();
//        	return new TaskData(TUTORIAL_NOT_CREATED);
//    	} catch (RuntimeException e) {
//        	Log.e(AnkiDroidApp.TAG, "Error on creating tutorial deck: " + e);
//        	DeckManager.closeMainDeck(false);
//        	sampleDeckFile.delete();
//        	return new TaskData(TUTORIAL_NOT_CREATED);
//    	}
    	return null;
    }


    public static interface TaskListener {
        public void onPreExecute();


        public void onPostExecute(TaskData result);


        public void onProgressUpdate(TaskData... values);
    }

    public static class TaskData {
        private Card mCard;
        private Note mNote;
        private int mInteger;
        private String mMsg;
        private boolean mBool = false;
        private ArrayList<HashMap<String, String>> mCards;
        private long mLong;
        private Context mContext;
        private int mType;
//        private LinkedHashMap<Long, CardModel> mCardModels;
        private Comparator<? super HashMap<String, String>> mComparator;
        private int[] mIntList;
        private Collection mCol;
        private Sched mSched;
        private TreeSet<Object[]> mDeckList;
        private Object[] mObjects;

        public TaskData(Object[] obj) {
        	mObjects = obj;
        }


        public TaskData(int value, Card card) {
            this(value);
            mCard = card;
        }


        public TaskData(int value, long cardId, boolean bool) {
            this(value);
            mLong = cardId;
            mBool = bool;
        }


        public TaskData(Card card) {
            mCard = card;
        }


        public TaskData(Card card, int integer) {
            mCard = card;
            mInteger = integer;
        }


        public TaskData(Context context, int type, int period) {
            mContext = context;
            mType = type;
        	mInteger = period;
        }


//        public TaskData(Notes fact, LinkedHashMap<Long, CardModel> cardModels) {
//        	mDeck = deck;
//        	mFact = fact;
//        	mCardModels = cardModels;
//        }
//
//
//        public TaskData(ArrayList<HashMap<String, String>> cards) {
//        	mCards = cards;
//        }
//
//
//        public TaskData(ArrayList<HashMap<String, String>> cards, Comparator<? super HashMap<String, String>> comparator) {
//        	mCards = cards;
//        	mComparator = comparator;
//        }
//
//
//        public TaskData(Card card, boolean markedLeech, boolean suspendedLeech) {
//            mCard = card;
//            previousCardLeech = markedLeech;
//            previousCardSuspended = suspendedLeech;
//        }
//
//
//        public TaskData(Decks deck, String order) {
//            mDeck = deck;
//            mMsg = order;
//        }
//
// 
//        public TaskData(Decks deck, int chunk) {
//            mDeck = deck;
//            mInteger = chunk;
//        }
//
// 
//        public TaskData(Decks deck, long value) {
//            mDeck = deck;
//            mLong = value;
//        }

 
        public TaskData(boolean bool) {
            mBool = bool;
        }

 
        public TaskData(TreeSet<Object[]> deckList) {
        	mDeckList = deckList;
        }


        public TaskData(Collection col) {
        	mCol = col;
        }


        public TaskData(Collection col, int value) {
        	mCol = col;
            mInteger = value;
        }


        public TaskData(Sched sched, Card card, int value) {
        	mSched = sched;
        	mCard = card;
            mInteger = value;
        }


        public TaskData(Sched sched) {
        	mSched = sched;
        }


        public TaskData(Sched sched, boolean bool) {
        	mSched = sched;
        	mBool = bool;
        }


        public TaskData(Sched sched, Card card, boolean bool) {
        	mSched = sched;
        	mBool = bool;
        	mCard = card;
        }


        public TaskData(Collection col, TreeSet<Object[]> decklist, int value) {
        	mCol = col;
        	mDeckList = decklist;
            mInteger = value;
        }


        public TaskData(int value) {
            mInteger = value;
        }


        public TaskData(String msg) {
            mMsg = msg;
        }


        public TaskData(Note note) {
            mNote = note;
        }


        public TaskData(int value, String msg) {
            mMsg = msg;
            mInteger = value;
        }


        public TaskData(String msg, long cardId, boolean bool) {
            mMsg = msg;
            mLong = cardId;
            mBool = bool;
        }


        public TaskData(int[] intlist) {
            mIntList = intlist;
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


        public Note getNote() {
            return mNote;
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


        public boolean getBoolean() {
            return mBool;
        }


        public Context getContext() {
            return mContext;
        }


        public int getType() {
            return mType;
        }

//
//        public LinkedHashMap<Long, CardModel> getCardModels() {
//            return mCardModels;
//        }


        public int[] getIntList() {
            return mIntList;
        }


        public Collection getCollection() {
        	return mCol;
        }


        public Sched getSched() {
        	return mSched;
        }


        public TreeSet<Object[]> getDeckList() {
        	return mDeckList;
        }


        public Object[] getObjArray() {
        	return mObjects;
        }
    }

}
