/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import org.json.JSONArray;
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
import com.ichi2.widget.WidgetStatus;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.SQLException;
import android.database.sqlite.SQLiteDiskIOException;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class DeckTask extends AsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData> {

    public static final int TASK_TYPE_OPEN_COLLECTION = 0;
    public static final int TASK_TYPE_OPEN_COLLECTION_AND_UPDATE_CARDS = 1;
    public static final int TASK_TYPE_SAVE_COLLECTION = 2;
    public static final int TASK_TYPE_ANSWER_CARD = 3;
    public static final int TASK_TYPE_MARK_CARD = 5;
    public static final int TASK_TYPE_ADD_FACT = 6;
    public static final int TASK_TYPE_UPDATE_FACT = 7;
    public static final int TASK_TYPE_UNDO = 8;
    public static final int TASK_TYPE_LOAD_CARDS = 10;
    public static final int TASK_TYPE_DISMISS_NOTE = 11;
    public static final int TASK_TYPE_LOAD_STATISTICS = 13;
    public static final int TASK_TYPE_CHECK_DATABASE = 14;
    public static final int TASK_TYPE_DELETE_BACKUPS = 16;
    public static final int TASK_TYPE_RESTORE_DECK = 17;
    public static final int TASK_TYPE_UPDATE_CARD_BROWSER_LIST = 18;
    public static final int TASK_TYPE_LOAD_TUTORIAL = 19;
    public static final int TASK_TYPE_REPAIR_DECK = 20;
    public static final int TASK_TYPE_CLOSE_DECK = 21;
    public static final int TASK_TYPE_LOAD_DECK_COUNTS = 22;
    public static final int TASK_TYPE_UPDATE_VALUES_FROM_DECK = 23;
    public static final int TASK_TYPE_RESTORE_IF_MISSING = 24;
    public static final int TASK_TYPE_DELETE_DECK = 25;
    public static final int TASK_TYPE_REBUILD_CRAM = 26;
    public static final int TASK_TYPE_EMPTY_CRAM = 27;

    private static DeckTask sInstance;
    private static DeckTask sOldInstance;

    private static boolean sHadCardQueue = false;

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


    public static boolean taskIsCancelled() {
        return sInstance.isCancelled();
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
                Log.i(AnkiDroidApp.TAG, "Waiting for " + sOldInstance.mType + " to finish before starting "
                        + sInstance.mType);
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
                // if (taskData.mInteger == DECK_LOADED) {
                // taskData.mDeck.updateAllCards();
                // taskData.mCard = taskData.mDeck.getCurrentCard();
                // }
                return taskData;

            case TASK_TYPE_SAVE_COLLECTION:
                return doInBackgroundSaveCollection(params);

            case TASK_TYPE_ANSWER_CARD:
                return doInBackgroundAnswerCard(params);

            case TASK_TYPE_MARK_CARD:
                return doInBackgroundMarkCard(params);

            case TASK_TYPE_ADD_FACT:
                return doInBackgroundAddNote(params);

            case TASK_TYPE_UPDATE_FACT:
                return doInBackgroundUpdateNote(params);

            case TASK_TYPE_UNDO:
                return doInBackgroundUndo(params);

            case TASK_TYPE_LOAD_CARDS:
                return doInBackgroundLoadCards(params);

            case TASK_TYPE_DISMISS_NOTE:
                return doInBackgroundDismissNote(params);

            case TASK_TYPE_LOAD_STATISTICS:
                return doInBackgroundLoadStatistics(params);

            case TASK_TYPE_CHECK_DATABASE:
                return doInBackgroundCheckDatabase(params);

            case TASK_TYPE_DELETE_BACKUPS:
                return doInBackgroundDeleteBackups();

            case TASK_TYPE_RESTORE_DECK:
                return doInBackgroundRestoreDeck(params);

            case TASK_TYPE_UPDATE_CARD_BROWSER_LIST:
                return doInBackgroundUpdateCardBrowserList(params);

            case TASK_TYPE_LOAD_TUTORIAL:
                return doInBackgroundLoadTutorial(params);

            case TASK_TYPE_REPAIR_DECK:
                return doInBackgroundRepairDeck(params);

            case TASK_TYPE_CLOSE_DECK:
                return doInBackgroundCloseCollection(params);

            case TASK_TYPE_UPDATE_VALUES_FROM_DECK:
                return doInBackgroundUpdateValuesFromDeck(params);

            case TASK_TYPE_RESTORE_IF_MISSING:
                return doInBackgroundRestoreIfMissing(params);

            case TASK_TYPE_DELETE_DECK:
                return doInBackgroundDeleteDeck(params);

            case TASK_TYPE_REBUILD_CRAM:
                return doInBackgroundRebuildCram(params);

            case TASK_TYPE_EMPTY_CRAM:
                return doInBackgroundEmptyCram(params);

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


    private TaskData doInBackgroundAddNote(TaskData[] params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundAddNote");
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
            Log.e(AnkiDroidApp.TAG, "doInBackgroundAddNote - RuntimeException on adding fact: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundAddNote");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundUpdateNote(TaskData[] params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundUpdateNote");
        // Save the note
        Sched sched = params[0].getSched();
        Collection col = sched.getCol();
        Card editCard = params[0].getCard();
        Note editNote = editCard.note();
        boolean fromReviewer = params[0].getBoolean();

        // mark undo
        col.markUndo(Collection.UNDO_EDIT_NOTE, new Object[]{col.getNote(editNote.getId()), editCard.getId(), fromReviewer});

        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                // TODO: undo integration
                editNote.flush();
                // flush card too, in case, did has been changed
                editCard.flush();
                if (fromReviewer) {
                    Card newCard;
                    if (col.getDecks().active().contains(editCard.getDid())) {
                        newCard = editCard;
                        newCard.load();
                        // reload qa-cache
                        newCard.getQuestion(true);
                    } else {
                        newCard = getCard(sched);
                    }
                    publishProgress(new TaskData(newCard));
                } else {
                    publishProgress(new TaskData(editCard));
                }
                col.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                col.getDb().getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundUpdateNote - RuntimeException on updating fact: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundUpdateNote");
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
        try {
            AnkiDb ankiDB = sched.getCol().getDb();
            ankiDB.getDatabase().beginTransaction();
            try {
                if (oldCard != null) {
                    oldCardLeech = sched.answerCard(oldCard, ease) ? 1 : 0;
                    if (oldCardLeech != 0) {
                        oldCardLeech += sched.leechActionSuspend(oldCard) ? 1 : 0;
                    }
                    // } else if (DeckManager.deckIsOpenedInBigWidget(deck.getDeckPath())) {
                    // // first card in reviewer is retrieved
                    // Log.i(AnkiDroidApp.TAG, "doInBackgroundAnswerCard: get card from big widget");
                    // newCard = AnkiDroidWidgetBig.getCard();
                }
                if (newCard == null) {
                    newCard = getCard(sched);
                }
                if (newCard != null) {
                    // render cards before locking database
                    newCard._getQA(true);
                }
                publishProgress(new TaskData(newCard, oldCardLeech));
                ankiDB.getDatabase().setTransactionSuccessful();
            } finally {
                ankiDB.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundAnswerCard - RuntimeException on answering card: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundAnswerCard");
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    private Card getCard(Sched sched) {
    	if (sHadCardQueue) {
    		sched.reset();
    		sHadCardQueue = false;
    	}
    	return sched.getCard();
    }

    private TaskData doInBackgroundOpenCollection(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundOpenCollection");
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        String collectionFile = params[0].getString();

        // see, if a collection is still opened
        Collection oldCol = Collection.currentCollection();
        boolean reset = params[0].getBoolean();

        Collection col = null;

        // publishProgress(new
        // TaskData(AnkiDroidApp.getInstance().getBaseContext().getResources().getString(R.string.finish_operation)));
        // DeckManager.waitForDeckClosingThread(deckFilename);

        if (oldCol == null || !oldCol.getPath().equals(collectionFile)) {

            // do a safety backup if last backup is too old --> addresses android's delete db bug
            if (BackupManager.safetyBackupNeeded(collectionFile)) {
                publishProgress(new TaskData(res.getString(R.string.backup_collection)));
                BackupManager.performBackup(collectionFile);
            }
            publishProgress(new TaskData(res.getString(R.string.open_collection)));

            // load collection
            Log.i(AnkiDroidApp.TAG, "doInBackgroundOpenCollection - File exists -> Loading collection...");
            try {
                col = Collection.openCollection(collectionFile);

                // create tutorial deck if needed
                SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
                if (prefs.contains("createTutorial") && prefs.getBoolean("createTutorial", false)) {
                    prefs.edit().remove("createTutorial").commit();
                    publishProgress(new TaskData(res.getString(R.string.tutorial_load)));
                    doInBackgroundLoadTutorial(new TaskData(col));
                }
            } catch (RuntimeException e) {
                BackupManager.restoreCollectionIfMissing(collectionFile);
            }
        } else {
            Log.i(AnkiDroidApp.TAG, "doInBackgroundOpenCollection: collection still open - reusing it");
            col = oldCol;
        }
        if (col == null) {
            return new TaskData(col);
        }
        try {
            // if (reset) {
            // col.getSched().reset();
            // }
            return new TaskData(col, doInBackgroundLoadDeckCounts(new TaskData(col)).getObjArray());
        } catch (RuntimeException e) {
            col = null;
            return new TaskData(col);
        }
    }


    private TaskData doInBackgroundLoadDeckCounts(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadDeckCounts");
        Collection col = params[0].getCollection();
        try {
            return new TaskData(col.getSched().deckCounts());
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundLoadDeckCounts - error: " + e);
            return null;
        }
    }


    private TaskData doInBackgroundSaveCollection(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSaveCollection");
        Collection col = params[0].getCollection();
        if (col != null) {
            try {
                col.save();
            } catch (RuntimeException e) {
                Log.e(AnkiDroidApp.TAG, "Error on saving deck in background: " + e);
            }
        }
        return null;
    }


    private TaskData doInBackgroundDismissNote(TaskData... params) {
        Sched sched = params[0].getSched();
        Collection col = sched.getCol();
        Card card = params[0].getCard();
        Note note = card.note();
        int type = params[0].getInt();
        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                switch (type) {
                    case 0:
                    	// store information for undoing first
                        long[] cardIds = Utils.arrayList2array(col.getDb().queryColumn(Long.class,
                                "SELECT id FROM cards WHERE nid = " + note.getId(), 0));
                        ArrayList<Card> undoCards = new ArrayList<Card>();
                        for (long c : cardIds) {
                        	undoCards.add(col.getCard(c));
                        }
                    	col.markUndo(Collection.UNDO_BURY_NOTE, new Object[]{col.getDirty(), undoCards, card.getId()});
                    	// then bury
                        sched.buryNote(note.getId());
                        break;
                    case 1:
                        // suspend card
                        if (card.getQueue() == -1) {
                            sched.unsuspendCards(new long[] { card.getId() });
                        } else {
                            sched.suspendCards(new long[] { card.getId() });
                        }
                        break;
                    case 2:
                        // suspend note
                        ArrayList<Card> cards = note.cards();
                        long[] cids = new long[cards.size()];
                        for (int i = 0; i < cards.size(); i++) {
                            cids[i] = cards.get(i).getId();
                        }
                        sched.suspendCards(cids);
                        break;
                    case 3:
                        // delete note
                        col.remNotes(new long[] { note.getId() });
                        break;
                }
                publishProgress(new TaskData(getCard(col.getSched()), 0));
                col.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                col.getDb().getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundSuspendCard - RuntimeException on suspending card: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSuspendCard");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundMarkCard(TaskData... params) {
        Card card = params[0].getCard();
        Sched sched = params[0].getSched();
        try {
            AnkiDb ankiDB = sched.getCol().getDb();
            ankiDB.getDatabase().beginTransaction();
            try {
                if (card != null) {
                    Note note = card.note();
                    if (note.hasTag("marked")) {
                        note.delTag("marked");
                    } else {
                        note.addTag("marked");
                    }
                    note.flush();
                }
                publishProgress(new TaskData(card));
                ankiDB.getDatabase().setTransactionSuccessful();
            } finally {
                ankiDB.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundMarkCard - RuntimeException on marking card: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundMarkCard");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundUndo(TaskData... params) {
        Sched sched = params[0].getSched();
        Collection col = sched.getCol();
        try {
            col.getDb().getDatabase().beginTransaction();
            Card newCard;
            try {
                long cid = col.undo();
                if (cid != 0) {
                    // a review was undone,
                    newCard = col.getCard(cid);
                    col.reset();
                    sHadCardQueue = true;
                } else {
                	// TODO: do not fetch new card if a non review operation has been undone
                    col.reset();
                    newCard = getCard(sched);
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
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadCards");
        Collection col = params[0].getCollection();
        boolean wholeCollection = params[0].getBoolean();
        publishProgress(new TaskData(col.findCardsForCardBrowser(wholeCollection)));
        return null;
    }


    private TaskData doInBackgroundLoadStatistics(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadStatistics");
        Collection col = params[0].getCollection();
        int type = params[0].getInt();
        boolean wholeCollection = params[0].getBoolean();

        Stats stats = new Stats(col, wholeCollection);
        switch (type) {
            default:
            case Stats.TYPE_FORECAST:
                return new TaskData(stats.calculateDue(AnkiDroidApp.getSharedPrefs(
                        AnkiDroidApp.getInstance().getBaseContext()).getInt("statsType", Stats.TYPE_MONTH)));
            case Stats.TYPE_REVIEW_COUNT:
                return new TaskData(stats.calculateDone(
                        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getInt("statsType",
                                Stats.TYPE_MONTH), true));
            case Stats.TYPE_REVIEW_TIME:
                return new TaskData(stats.calculateDone(
                        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getInt("statsType",
                                Stats.TYPE_MONTH), false));
        }
    }


    private TaskData doInBackgroundCheckDatabase(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundCheckDatabase");
        Collection col = params[0].getCollection();
        long result = col.fixIntegrity();
        if (result == -1) {
            return new TaskData(false);
        } else {
            return new TaskData(0, result, true);
        }
    }


    private TaskData doInBackgroundRepairDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundRepairDeck");
        String deckPath = params[0].getString();
        Collection col = params[0].getCollection();
        if (col != null) {
            col.close(false);
        }
        return new TaskData(BackupManager.repairDeck(deckPath));
    }


    private TaskData doInBackgroundCloseCollection(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundCloseCollection");
        Collection col = params[0].getCollection();
        if (col != null) {
            WidgetStatus.waitToFinish();
            try {
                String path = col.getPath();
                col.close(true);
                BackupManager.performBackup(path);
            } catch (RuntimeException e) {
                Log.i(AnkiDroidApp.TAG,
                        "doInBackgroundCloseCollection: error occurred - collection not properly closed");
            }
        }
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
        double progressMature = ((double) sched.matureCount()) / ((double) totalCount);
        double progressAll = 1 - (((double) (totalNewCount + counts[1])) / ((double) totalCount));
        double[][] serieslist = Stats.getSmallDueStats(sched.getCol());
        return new TaskData(new Object[] { counts[0], counts[1], counts[2], totalNewCount, totalCount, progressMature,
                progressAll, sched.eta(counts), serieslist });
    }


    private TaskData doInBackgroundRestoreIfMissing(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundRestoreIfMissing");
        String path = params[0].getString();
        BackupManager.restoreCollectionIfMissing(path);
        return null;
    }


    private TaskData doInBackgroundDeleteBackups() {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteBackups");
        return null;// ew TaskData(BackupManager.deleteAllBackups());
    }


    private TaskData doInBackgroundDeleteDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundDeleteDeck");
        Collection col = params[0].getCollection();
        long did = params[0].getLong();
        col.getDecks().rem(did, true);
        return doInBackgroundLoadDeckCounts(new TaskData(col));
    }


    private TaskData doInBackgroundRebuildCram(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundRebuildCram");
        Collection col = params[0].getCollection();
        long did = params[0].getLong();
        col.getSched().rebuildDyn(did);
        return doInBackgroundUpdateValuesFromDeck(new DeckTask.TaskData(col.getSched(), true));
    }


    private TaskData doInBackgroundEmptyCram(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundEmptyCram");
        Collection col = params[0].getCollection();
        long did = params[0].getLong();
        col.getSched().emptyDyn(did);
        return doInBackgroundUpdateValuesFromDeck(new DeckTask.TaskData(col.getSched(), true));
    }


    private TaskData doInBackgroundRestoreDeck(TaskData... params) {
        // Log.i(AnkiDroidApp.TAG, "doInBackgroundRestoreDeck");
        // String[] paths = params[0].getDeckList();
        // return new TaskData(BackupManager.restoreDeckBackup(paths[0], paths[1]));
        return null;
    }


    private TaskData doInBackgroundUpdateCardBrowserList(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSortCards");
        if (params.length == 1) {
            Comparator comparator = params[0].getComparator();
            ArrayList<HashMap<String, String>> card = params[0].getCards();
            Collections.sort(card, comparator);
        } else {
            ArrayList<HashMap<String, String>> allCard = params[0].getCards();
            ArrayList<HashMap<String, String>> cards = params[1].getCards();
            cards.clear();
            HashSet<String> tags = new HashSet<String>();
            for (String s : (HashSet<String>) params[2].getObjArray()[0]) {
                tags.add(s.toLowerCase());
            }
            for (int i = 0; i < allCard.size(); i++) {
                HashMap<String, String> card = allCard.get(i);
                if (Arrays.asList(card.get("tags").toLowerCase().trim().split("\\s")).containsAll(tags)) {
                    cards.add(allCard.get(i));
                }
            }
        }
        return null;
    }


    private TaskData doInBackgroundLoadTutorial(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadTutorial");
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        Collection col = params[0].getCollection();
        col.getDb().getDatabase().beginTransaction();
        String title = res.getString(R.string.help_tutorial);
        try {
            // get deck or create it
            long did = col.getDecks().id(title);
            // reset todays counts
            JSONObject d = col.getDecks().get(did);
            for (String t : new String[] { "new", "rev", "lrn", "time" }) {
                String k = t + "Today";
                JSONArray ja = new JSONArray();
                ja.put(col.getSched().getToday());
                ja.put(0);
                d.put(k, ja);
            }
            // save deck
            col.getDecks().save(d);
            if (col.getSched().cardCount("(" + did + ")") > 0) {
                // deck does already exist. Remove all cards and recreate them to ensure the correct order
                col.remCards(col.getDecks().cids(did));
            }
            JSONObject model = col.getModels().byName(title);
            // TODO: check, if model is valid or delete and recreate it
            // TODO: deactivated at the moment as if forces a schema change
            // create model (remove old ones first)
            // while (model != null) {
            // JSONObject m = col.getModels().byName(title);
            // // rename old tutorial model if there are some non tutorial cards in it
            // if (col.getDb().queryScalar("SELECT id FROM cards WHERE nid IN (SELECT id FROM notes WHERE mid = " +
            // m.getLong("id") + ")", false) == 0) {
            // col.getModels().rem(m);
            // } else {
            // m.put("name", title + " (renamed)");
            // col.getModels().save(m);
            // }
            // }
            if (model == null) {
                model = col.getModels().addBasicModel(col, title);
            }
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
                col.addNote(note);
            }
            // deck.setSessionTimeLimit(0);
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
        private Comparator mComparator;
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


        public TaskData(ArrayList<HashMap<String, String>> cards) {
            mCards = cards;
        }


        public TaskData(ArrayList<HashMap<String, String>> cards, Comparator comparator) {
            mCards = cards;
            mComparator = comparator;
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


        public TaskData(Collection col, Object[] obj) {
            mCol = col;
            mObjects = obj;
        }


        public TaskData(Collection col, String string) {
            mCol = col;
            mMsg = string;
        }


        public TaskData(Collection col, int value) {
            mCol = col;
            mInteger = value;
        }


        public TaskData(Collection col, long value) {
            mCol = col;
            mLong = value;
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


        public TaskData(long l) {
            mLong = l;
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


        public Comparator getComparator() {
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
        // public LinkedHashMap<Long, CardModel> getCardModels() {
        // return mCardModels;
        // }

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
