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
import java.util.TreeSet;

import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki2.R;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Stats;
import com.ichi2.libanki.Utils;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.SQLException;
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
	        	// flush card too, in case, did has been changed
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
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        String collectionFile = params[0].getString();

//        publishProgress(new TaskData(AnkiDroidApp.getInstance().getBaseContext().getResources().getString(R.string.finish_operation)));
//        DeckManager.waitForDeckClosingThread(deckFilename);

        File dbFile = new File(collectionFile);
        if (!dbFile.exists()) {
            Log.i(AnkiDroidApp.TAG, "doInBackgroundOpenCollection: db file does not exist. Creating it...");
        	publishProgress(new TaskData(res.getString(R.string.create_collection)));
            // If decks directory does not exist, create it.
            AnkiDroidApp.createDirectoryIfMissing(dbFile.getParentFile());
        	// create file
            try {
                // Copy an empty collection file from the assets to the SD card.
                InputStream stream = res.getAssets().open("collection.anki2");
                Utils.writeToFile(stream, collectionFile);
                stream.close();
                AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(collectionFile);
                // set create time to 0 in order to let it be set later by the collection constructor
                ankiDB.execute("UPDATE col SET crt = 0");
                AnkiDatabaseManager.closeDatabase(collectionFile);
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
                Log.e(AnkiDroidApp.TAG, "doInBackgroundOpenCollection - The copy of collection.anki2 to the SD card failed.");
                // TODO: proper error handling
                Collection col = null;
                return new TaskData(col);
            }
        	publishProgress(new TaskData(res.getString(R.string.open_collection)));
        } else if (BackupManager.safetyBackupNeeded(collectionFile)) {
        	publishProgress(new TaskData(res.getString(R.string.backup_collection)));
        	BackupManager.performBackup(collectionFile);
        	publishProgress(new TaskData(res.getString(R.string.open_collection)));
        }

    	// load collection
        Log.i(AnkiDroidApp.TAG, "doInBackgroundOpenCollection - File exists -> Loading collection...");
        Collection col = Collection.openCollection(collectionFile);

        // create tutorial deck if needed
        SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        if (prefs.contains("createTutorial") && prefs.getBoolean("createTutorial", false)) {
        	prefs.edit().remove("createTutorial").commit();
        	publishProgress(new TaskData(res.getString(R.string.tutorial_load)));
        	doInBackgroundLoadTutorial(new TaskData(col));
        }

    	// load decks
    	TreeSet<Object[]> decks = col.getSched().deckDueTree(false);
        return new TaskData(col, decks, DECK_LOADED);
    }


    private TaskData doInBackgroundLoadDeckCounts(TaskData... params) {
    	Collection col = params[0].getCollection();
    	Sched sched = col.getSched();
    	// check if new day has rolled over and reset counts if yes
    	if (Utils.now() > sched.getDayCutoff()) {
    		sched._updateCutoff();
    	}
    	TreeSet<Object[]> decks = sched.deckDueTree(true);
    	int[] counts = new int[]{0, 0, 0};
    	for (Object[] deck : decks) {
    		if (((String[])deck[0]).length == 1) {
    			counts[0] += (Integer) deck[2];
    			counts[1] += (Integer) deck[3];
    			counts[2] += (Integer) deck[4];
    		}
    	}
    	return new TaskData(new Object[]{decks, sched.eta(counts), col.cardCount()});
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
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadStatistics");
        Collection col = params[0].getCollection();
        int type = params[0].getInt();
        boolean wholeCollection = params[0].getBoolean();

        Stats stats = new Stats(col, wholeCollection);
        switch(type) {
        default:
        case Stats.TYPE_FORECAST:
        	return new TaskData(stats.calculateDue(PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getInt("statsType", Stats.TYPE_MONTH)));
        case Stats.TYPE_REVIEW_COUNT:
        	return new TaskData(stats.calculateDone(PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getInt("statsType", Stats.TYPE_MONTH), true));
        case Stats.TYPE_REVIEW_TIME:
        	return new TaskData(stats.calculateDone(PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getInt("statsType", Stats.TYPE_MONTH), false));
        }
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
    	if (col != null) {
        	String path = col.getPath();
        	col.close(true);
        	BackupManager.performBackup(path);    		
    	}
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
		sched.loadNonSelectedDues();
		int totalNewCount = sched.newCount();
		int totalCount = sched.cardCount();
		double progressMature= ((double) sched.matureCount())
				/ ((double) totalCount);
		double progressAll = 1 - (((double) (totalNewCount + counts[1])) / ((double) totalCount));

		return new TaskData(new Object[]{counts[0], counts[1], counts[2], totalNewCount, totalCount, progressMature, progressAll, sched.eta(counts)});
    }


    private TaskData doInBackgroundDeleteBackups() {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteBackups");
    	return null;//ew TaskData(BackupManager.deleteAllBackups());
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
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadTutorial");
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        Collection col = params[0].getCollection();
        col.getDb().getDatabase().beginTransaction();
        String title = res.getString(R.string.tutorial_title);
        try {
        	long did = col.getDecks().id(title);
	       	if (col.getSched().cardCount("(" + did + ")") > 0) {
	       		// deck does already exist. Remove all cards and recreate them to ensure the correct order
	       		col.remCards(col.getDecks().cids(did));
	       	}
	       	// create model (remove old ones first)
	       	while (col.getModels().byName(title) != null) {
	       		JSONObject m = col.getModels().byName(title);
	       		// rename old tutorial model if there are some non tutorial cards in it
	       		if (col.getDb().queryScalar("SELECT id FROM cards WHERE nid IN (SELECT id FROM notes WHERE mid = " + m.getLong("id") + ")", false) == 0) {
		       		col.getModels().rem(m);
	       		} else {
	       			m.put("name", title + " (renamed)");
	       			col.getModels().save(m);
	       		}
	       	}
	       	JSONObject model = col.getModels().addBasicModel(title, false);
	       	model.put("did", did);
			String[] questions = res.getStringArray(R.array.tutorial_questions);
			String[] answers = res.getStringArray(R.array.tutorial_answers);
			String[] sampleQuestions = res.getStringArray(R.array.tutorial_capitals_questions);
			String[] sampleAnswers = res.getStringArray(R.array.tutorial_capitals_answers);
        	int len = Math.min(questions.length, answers.length);
        	for (int i = 0; i < len + Math.min(sampleQuestions.length, sampleAnswers.length); i++) {
        		Note note = col.newNote(model);
        		if (note.values().length < 2) {
        			return new TaskData(false);
        		}
        		note.values()[0] = (i < len) ? questions[i] : sampleQuestions[i - len];
        		note.values()[1] = (i < len) ? answers[i] : sampleAnswers[i - len];
        		note.setDid(did);
        		col.addNote(note);
        	}
//        	deck.setSessionTimeLimit(0);
        	if (col.getSched().cardCount("(" + did + ")") == 0) {
        		// error, delete deck
        		col.getDecks().rem(did, true);
        		return new TaskData(false);
        	} else {
            	col.save();
            	col.getDecks().select(did);
            	col.getDb().getDatabase().setTransactionSuccessful();
        		return new TaskData(true);
        	}
        } catch (SQLException e) {
			throw new RuntimeException(e);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		} finally {
        	col.getDb().getDatabase().endTransaction();
    	}
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


        public TaskData(Collection col, int value, boolean bool) {
        	mCol = col;
            mInteger = value;
            mBool = bool;
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
