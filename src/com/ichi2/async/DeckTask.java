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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.stream.JsonReader;
import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.R;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Stats;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.importer.Anki2Importer;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class DeckTask extends BaseAsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData> {

    public static final int TASK_TYPE_OPEN_COLLECTION = 0;
    public static final int TASK_TYPE_SAVE_COLLECTION = 2;
    public static final int TASK_TYPE_ANSWER_CARD = 3;
    public static final int TASK_TYPE_MARK_CARD = 5;
    public static final int TASK_TYPE_ADD_FACT = 6;
    public static final int TASK_TYPE_UPDATE_FACT = 7;
    public static final int TASK_TYPE_UNDO = 8;
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
    public static final int TASK_TYPE_IMPORT = 28;
    public static final int TASK_TYPE_IMPORT_REPLACE = 29;
    public static final int TASK_TYPE_SEARCH_CARDS = 30;
    public static final int TASK_TYPE_EXPORT_APKG = 31;
    public static final int TASK_TYPE_REORDER = 32;
    public static final int TASK_TYPE_CONF_CHANGE = 33;
    public static final int TASK_TYPE_CONF_RESET = 34;
    public static final int TASK_TYPE_CONF_REMOVE = 35;
    public static final int TASK_TYPE_CONF_SET_SUBDECKS = 36;
    public static final int TASK_TYPE_RENDER_BROWSER_QA = 37;

    /**
     * The most recently started {@link DeckTask} instance.
     */
    private static DeckTask sLatestInstance;

    private static boolean sHadCardQueue = false;


    /**
     * Starts a new {@link DeckTask}.
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @param listener to the status and result of the task
     * @param params to pass to the task
     * @return the newly created task
     */
    public static DeckTask launchDeckTask(int type, Listener listener, TaskData... params) {
        // Before starting a new task, cancel rendering of Q&A for browser
        Log.i(AnkiDroidApp.TAG, "launchDeckTask(" + type + ")");
        if (sLatestInstance != null && sLatestInstance.mType == TASK_TYPE_RENDER_BROWSER_QA
                && !sLatestInstance.isCancelled()) {
            Log.i(AnkiDroidApp.TAG, "DeckTask: cancelling render browser QA...");
            sLatestInstance.cancel(true);
            waitToFinish();
            Log.i(AnkiDroidApp.TAG, "DeckTask: cancelled render browser QA");

        }
        // Start new task
        sLatestInstance = new DeckTask(type, listener, sLatestInstance);
        sLatestInstance.execute(params);
        return sLatestInstance;
    }


    /**
     * Block the current thread until the currently running DeckTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        try {
            if ((sLatestInstance != null) && (sLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                Log.i(AnkiDroidApp.TAG, "DeckTask: wait to finish");
                sLatestInstance.get();
            }
        } catch (Exception e) {
            return;
        }
    }


    public static void cancelTask() {
        try {
            if ((sLatestInstance != null) && (sLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                sLatestInstance.cancel(true);
            }
        } catch (Exception e) {
            return;
        }
    }


    public static void cancelTask(int taskType) {
        try {
            Boolean match = sLatestInstance.mType == taskType;
            if ((sLatestInstance != null) && (sLatestInstance.getStatus() != AsyncTask.Status.FINISHED) && (match)) {
                sLatestInstance.cancel(true);
            }
        } catch (Exception e) {
            return;
        }
    }


    public static boolean taskIsRunning() {
        try {
            if ((sLatestInstance != null) && (sLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                return true;
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    private final int mType;
    private final Listener mListener;
    private DeckTask mPreviousTask;


    public DeckTask(int type, Listener listener, DeckTask previousTask) {
        mType = type;
        mListener = listener;
        mPreviousTask = previousTask;
    }


    // This method and those that are called here are executed in a new thread
    @Override
    protected TaskData doInBackground(TaskData... params) {
        super.doInBackground(params);
        // Wait for previous thread (if any) to finish before continuing
        if (mPreviousTask != null && mPreviousTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.i(AnkiDroidApp.TAG, "Waiting for " + mPreviousTask.mType + " to finish before starting " + mType);

            // Let user know if the last deck close is still performing a backup.
            if (mType == TASK_TYPE_OPEN_COLLECTION && mPreviousTask.mType == TASK_TYPE_CLOSE_DECK) {
                publishProgress(new TaskData(AnkiDroidApp.getInstance().getBaseContext().getResources()
                        .getString(R.string.finish_operation)));
            }
            try {
                mPreviousTask.get();
                Log.i(AnkiDroidApp.TAG, "Finished waiting for " + mPreviousTask.mType + " to finish. Status= "
                        + mPreviousTask.getStatus());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // We have been interrupted, return immediately.
                Log.e(AnkiDroidApp.TAG, "interrupted while waiting for previous task: " + mPreviousTask.mType, e);
                return null;
            } catch (ExecutionException e) {
                // Ignore failures in the previous task.
                Log.e(AnkiDroidApp.TAG, "previously running task failed with exception: " + mPreviousTask.mType, e);
            } catch (CancellationException e) {
                // Ignore cancellation of previous task
                Log.e(AnkiDroidApp.TAG, "previously running task was cancelled: " + mPreviousTask.mType, e);
            }
        }

        // Actually execute the task now that we are at the front of the queue.
        switch (mType) {
            case TASK_TYPE_OPEN_COLLECTION:
                return doInBackgroundOpenCollection(params);

            case TASK_TYPE_LOAD_DECK_COUNTS:
                return doInBackgroundLoadDeckCounts(params);

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

            case TASK_TYPE_SEARCH_CARDS:
                return doInBackgroundSearchCards(params);

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

            case TASK_TYPE_IMPORT:
                return doInBackgroundImportAdd(params);

            case TASK_TYPE_IMPORT_REPLACE:
                return doInBackgroundImportReplace(params);

            case TASK_TYPE_EXPORT_APKG:
                return doInBackgroundExportApkg(params);

            case TASK_TYPE_REORDER:
                return doInBackgroundReorder(params);

            case TASK_TYPE_CONF_CHANGE:
                return doInBackgroundConfChange(params);

            case TASK_TYPE_CONF_RESET:
                return doInBackgroundConfReset(params);

            case TASK_TYPE_CONF_REMOVE:
                return doInBackgroundConfRemove(params);

            case TASK_TYPE_CONF_SET_SUBDECKS:
                return doInBackgroundConfSetSubdecks(params);

            case TASK_TYPE_RENDER_BROWSER_QA:
                return doInBackgroundRenderBrowserQA(params);

            default:
                Log.e(AnkiDroidApp.TAG, "unknown task type: " + mType);
                return null;
        }
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mListener.onPreExecute(this);
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onProgressUpdate(TaskData... values) {
        super.onProgressUpdate(values);
        mListener.onProgressUpdate(this, values);
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onPostExecute(TaskData result) {
        super.onPostExecute(result);
        mListener.onPostExecute(this, result);
        Log.i(AnkiDroidApp.TAG, "enabling garbage collection of mPreviousTask...");
        mPreviousTask = null;
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
        col.markUndo(Collection.UNDO_EDIT_NOTE, new Object[] { col.getNote(editNote.getId()), editCard.getId(),
                fromReviewer });

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
                        newCard.q(true);
                    } else {
                        newCard = getCard(sched);
                    }
                    publishProgress(new TaskData(newCard));
                } else {
                    publishProgress(new TaskData(editCard, editNote.stringTags()));
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
                    sched.answerCard(oldCard, ease);
                    if (oldCard.note().hasTag("leech")) {
                        if (oldCard.getQueue() < 0) {
                            oldCardLeech = 2;
                        } else {
                            oldCardLeech = 1;
                        }
                    }
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
        long time = Utils.intNow(1000);
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        String collectionFile = params[0].getString();

        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());

        // see, if a collection is still opened
        Collection oldCol = AnkiDroidApp.getCol();

        Collection col = null;

        publishProgress(new TaskData(res.getString(R.string.open_collection)));

        if (!(AnkiDroidApp.colIsOpen() && oldCol.getPath().equals(collectionFile))) {

            // do a safety backup if last backup is too old --> addresses
            // android's delete db bug
            if (BackupManager.safetyBackupNeeded(collectionFile)) {
                publishProgress(new TaskData(res.getString(R.string.backup_collection)));
                BackupManager.performBackup(collectionFile);
            }
            publishProgress(new TaskData(res.getString(R.string.open_collection)));

            // load collection
            try {
                col = AnkiDroidApp.openCollection(collectionFile);
            } catch (RuntimeException e) {
                BackupManager.restoreCollectionIfMissing(collectionFile);
                Log.e(AnkiDroidApp.TAG, "doInBackgroundOpenCollection - RuntimeException on opening collection: " + e);
                AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundOpenCollection");
                return new TaskData(false);
            }
            // create tutorial deck if needed
            if (prefs.contains("createTutorial") && prefs.getBoolean("createTutorial", false)) {
                prefs.edit().remove("createTutorial").commit();
                publishProgress(new TaskData(res.getString(R.string.tutorial_load)));
                doInBackgroundLoadTutorial(new TaskData(col));
            }
        } else {
            Log.i(AnkiDroidApp.TAG, "doInBackgroundOpenCollection: collection still open - reusing it");
            col = oldCol;
        }
        Object[] counts = null;
        DeckTask.TaskData result = doInBackgroundLoadDeckCounts(new TaskData(col));
        if (result != null) {
            counts = result.getObjArray();
        }
        return new TaskData(col, counts);
    }


    private TaskData doInBackgroundLoadDeckCounts(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundLoadDeckCounts");
        Collection col = params[0].getCollection();
        if (col == null) {
            return null;
        }
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
                    case 4:
                        // collect undo information
                        col.markUndo(Collection.UNDO_BURY_CARD,
                                new Object[] { col.getDirty(), note.cards(), card.getId() });
                        // then bury
                        sched.buryCards(new long[] { card.getId() });
                        sHadCardQueue = true;
                        break;
                    case 0:
                        // collect undo information
                        col.markUndo(Collection.UNDO_BURY_NOTE,
                                new Object[] { col.getDirty(), note.cards(), card.getId() });
                        // then bury
                        sched.buryNote(note.getId());
                        sHadCardQueue = true;
                        break;
                    case 1:
                        // collect undo information
                        col.markUndo(Collection.UNDO_SUSPEND_CARD, new Object[] { card });
                        // suspend card
                        if (card.getQueue() == -1) {
                            sched.unsuspendCards(new long[] { card.getId() });
                        } else {
                            sched.suspendCards(new long[] { card.getId() });
                        }
                        sHadCardQueue = true;
                        break;
                    case 2:
                        // collect undo information
                        ArrayList<Card> cards = note.cards();
                        long[] cids = new long[cards.size()];
                        for (int i = 0; i < cards.size(); i++) {
                            cids[i] = cards.get(i).getId();
                        }
                        col.markUndo(Collection.UNDO_SUSPEND_NOTE, new Object[] { cards, card.getId() });
                        // suspend note
                        sched.suspendCards(cids);
                        sHadCardQueue = true;
                        break;
                    case 3:
                        // collect undo information
                        ArrayList<Card> allCs = note.cards();
                        long[] cardIds = new long[allCs.size()];
                        for (int i = 0; i < allCs.size(); i++) {
                            cardIds[i] = allCs.get(i).getId();
                        }
                        col.markUndo(Collection.UNDO_DELETE_NOTE, new Object[] { note, allCs, card.getId() });
                        // delete note
                        col.remNotes(new long[] { note.getId() });
                        sHadCardQueue = true;
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
                    sched.getCol().markUndo(Collection.UNDO_MARK_NOTE,
                            new Object[] { note.getId(), note.stringTags(), card.getId() });
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
                    col.getSched().decrementCounts(newCard);
                    sHadCardQueue = true;
                } else {
                    // TODO: do not fetch new card if a non review operation has
                    // been undone
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


    private TaskData doInBackgroundSearchCards(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSearchCards");
        Collection col = (Collection) params[0].getObjArray()[0];
        HashMap<String, String> deckNames = (HashMap<String, String>) params[0].getObjArray()[1];
        String query = (String) params[0].getObjArray()[2];
        String order = (String) params[0].getObjArray()[3];
        TaskData result = new TaskData(col.findCardsForCardBrowser(query, order, deckNames));
        if (isCancelled()) {
            return null;
        } else {
            publishProgress(result);
        }
        return new TaskData(col.cardCount(col.getDecks().allIds()));
    }


    private TaskData doInBackgroundRenderBrowserQA(TaskData... params) {
        final int initialInterval = 15; // initial number of cards we render one by one
        final int refreshInterval = 250; // number of cards to render at a time after initialInterval
        int numRendered = 0;
        Log.i(AnkiDroidApp.TAG, "doInBackgroundRenderBrowserQA");
        Collection col = (Collection) params[0].getObjArray()[0];
        ArrayList<HashMap<String, String>> items = (ArrayList<HashMap<String, String>>) params[0].getObjArray()[1];
        // for each card in the browser list
        try {
            for (HashMap<String, String> item : items) {
                // Extract card item
                Card c = col.getCard(Long.parseLong(item.get("id"), 10));
                // Update item
                CardBrowser.updateSearchItemQA(item, c);
                // Send progress periodically so that QA list in browser updates
                if (isCancelled()) {
                    return null;
                } else {
                    numRendered++;
                    if (numRendered % refreshInterval == 0 || numRendered <= initialInterval) {
                        TaskData result = new TaskData(items);
                        publishProgress(result);
                    }
                }
            }
        } catch (OutOfMemoryError e) {
            // TODO: Check if this is actually effective at dealing with the error, maybe the ArrayList has grown too
            // big to recover?
            Log.e(AnkiDroidApp.TAG, "OutOfMemoryError rendering the Q&A for browser... probably too many cards");
            return null;
        }
        TaskData result = new TaskData(items);
        publishProgress(result);
        return result;
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
            try {
                WidgetStatus.waitToFinish();
                String path = col.getPath();
                AnkiDroidApp.closeCollection(true);
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
        try {
            Sched sched = params[0].getCollection().getSched();
            Object[] obj = params[0].getObjArray();
            boolean reset = (Boolean) obj[0];
            if (reset) {
                sched.reset();
            }
            int[] counts = sched.counts();
            int totalNewCount = sched.totalNewForCurrentDeck();
            int totalCount = sched.cardCount();
            double progressMature = ((double) sched.matureCount()) / ((double) totalCount);
            double progressAll = 1 - (((double) (totalNewCount + counts[1])) / ((double) totalCount));
            double[][] serieslist = null;
            // only calculate stats if necessary
            if ((Boolean) obj[1]) {
                serieslist = Stats.getSmallDueStats(sched.getCol());
            }
            return new TaskData(new Object[] { counts[0], counts[1], counts[2], totalNewCount, totalCount,
                    progressMature, progressAll, sched.eta(counts), serieslist });
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundUpdateValuesFromDeck - an error occurred: " + e);
            return null;
        }
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
        boolean fragmented = params[0].getBoolean();
        long did = params[0].getLong();
        col.getSched().rebuildDyn(did);
        return doInBackgroundUpdateValuesFromDeck(new DeckTask.TaskData(col, new Object[] { true, fragmented }));
    }


    private TaskData doInBackgroundEmptyCram(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundEmptyCram");
        Collection col = params[0].getCollection();
        boolean fragmented = params[0].getBoolean();
        long did = params[0].getLong();
        col.getSched().emptyDyn(did);
        return doInBackgroundUpdateValuesFromDeck(new DeckTask.TaskData(col, new Object[] { true, fragmented }));
    }


    private TaskData doInBackgroundImportAdd(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundImportAdd");
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        Collection col = params[0].getCollection();
        String path = params[0].getString();
        boolean sharedDeckImport = params[0].getBoolean();

        ProgressCallback pc = null;
        // don't report progress on shared deck import (or maybe should we?)
        if (!sharedDeckImport) {
            pc = new ProgressCallback(this, res);
        }

        int addedCount = -1;
        try {
            Anki2Importer imp = new Anki2Importer(col, path, pc);
            AnkiDb ankiDB = col.getDb();
            ankiDB.getDatabase().beginTransaction();
            try {
                addedCount = imp.run();
                ankiDB.getDatabase().setTransactionSuccessful();
            } finally {
                ankiDB.getDatabase().endTransaction();
                if (sharedDeckImport) {
                    File tmpFile = new File(path);
                    tmpFile.delete();
                }
            }
            if (addedCount >= 0) {
                ankiDB.execute("VACUUM");
                ankiDB.execute("ANALYZE");
            }

            publishProgress(new TaskData(res.getString(R.string.import_update_counts)));
            // Update the counts
            DeckTask.TaskData result = doInBackgroundLoadDeckCounts(new TaskData(col));
            if (result == null) {
                return null;
            }
            return new TaskData(addedCount, result.getObjArray(), true);
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundImportAdd - RuntimeException on importing cards: ", e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundImportAdd");
            return new TaskData(false);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundImportAdd - IOException on importing cards: ", e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundImportAdd");
            return new TaskData(false);
        }
    }


    private TaskData doInBackgroundImportReplace(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundImportReplace");
        Collection col = params[0].getCollection();
        String path = params[0].getString();
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();

        // extract the deck from the zip file
        String fileDir = AnkiDroidApp.getCurrentAnkiDroidDirectory() + "/tmpzip";
        File dir = new File(fileDir);
        if (dir.exists()) {
            BackupManager.removeDir(dir);
        }

        publishProgress(new TaskData(res.getString(R.string.import_unpacking)));
        // from anki2.py
        String colFile = fileDir + "/collection.anki2";
        ZipFile zip;
        try {
            zip = new ZipFile(new File(path), ZipFile.OPEN_READ);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundImportReplace - Error while unzipping: ", e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundImportReplace0");
            return new TaskData(false);
        }
        if (!Utils.unzipFiles(zip, fileDir, new String[] { "collection.anki2", "media" }, null)
                || !(new File(colFile)).exists()) {
            return new TaskData(-2, null, false);
        }

        Collection tmpCol = null;
        try {
            tmpCol = Storage.Collection(colFile);
            if (!tmpCol.validCollection()) {
                tmpCol.close();
                return new TaskData(-2, null, false);
            }
        } finally {
            if (tmpCol != null) {
                tmpCol.close();
            }
        }

        publishProgress(new TaskData(res.getString(R.string.importing_collection)));
        String colPath;
        if (col != null) {
            // unload collection and trigger a backup
            colPath = col.getPath();
            AnkiDroidApp.closeCollection(true);
            BackupManager.performBackup(colPath, true);
        }
        // overwrite collection
        colPath = AnkiDroidApp.getCollectionPath();
        File f = new File(colFile);
        f.renameTo(new File(colPath));
        int addedCount = -1;
        try {
            col = AnkiDroidApp.openCollection(colPath);

            // because users don't have a backup of media, it's safer to import new
            // data and rely on them running a media db check to get rid of any
            // unwanted media. in the future we might also want to duplicate this step
            // import media
            HashMap<String, String> nameToNum = new HashMap<String, String>();
            HashMap<String, String> numToName = new HashMap<String, String>();
            File mediaMapFile = new File(fileDir, "media");
            if (mediaMapFile.exists()) {
                JsonReader jr = new JsonReader(new FileReader(mediaMapFile));
                jr.beginObject();
                String name;
                String num;
                while (jr.hasNext()) {
                    num = jr.nextName();
                    name = jr.nextString();
                    nameToNum.put(name, num);
                    numToName.put(num, name);
                }
                jr.endObject();
                jr.close();
            }
            String mediaDir = col.getMedia().getDir();
            int total = nameToNum.size();
            int i = 0;
            for (Map.Entry<String, String> entry : nameToNum.entrySet()) {
                String file = entry.getKey();
                String c = entry.getValue();
                File of = new File(mediaDir, file);
                if (!of.exists()) {
                    Utils.unzipFiles(zip, mediaDir, new String[] { c }, numToName);
                }
                ++i;
                publishProgress(new TaskData(res.getString(R.string.import_media_count, (i + 1) * 100 / total)));
            }
            zip.close();
            // delete tmp dir
            BackupManager.removeDir(dir);

            publishProgress(new TaskData(res.getString(R.string.import_update_counts)));
            // Update the counts
            DeckTask.TaskData result = doInBackgroundLoadDeckCounts(new TaskData(col));
            if (result == null) {
                return null;
            }
            return new TaskData(addedCount, result.getObjArray(), true);
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundImportReplace - RuntimeException: ", e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundImportReplace1");
            return new TaskData(false);
        } catch (FileNotFoundException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundImportReplace - FileNotFoundException: ", e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundImportReplace2");
            return new TaskData(false);
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundImportReplace - IOException: ", e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundImportReplace3");
            return new TaskData(false);
        }
    }


    private TaskData doInBackgroundExportApkg(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundExportApkg");
        Object[] data = params[0].getObjArray();
        String colPath = (String) data[0];
        String apkgPath = (String) data[1];
        boolean includeMedia = (Boolean) data[2];

        byte[] buf = new byte[1024];
        try {
            try {
                AnkiDb d = AnkiDatabaseManager.getDatabase(colPath);
            } catch (SQLiteDatabaseCorruptException e) {
                // collection is invalid
                return new TaskData(false);
            } finally {
                AnkiDatabaseManager.closeDatabase(colPath);
            }

            // export collection
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apkgPath));
            FileInputStream colFin = new FileInputStream(colPath);
            ZipEntry ze = new ZipEntry("collection.anki2");
            zos.putNextEntry(ze);
            int len;
            while ((len = colFin.read(buf)) >= 0) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
            colFin.close();

            // export media
            JSONObject media = new JSONObject();
            if (includeMedia) {
                File mediaDir = new File(AnkiDroidApp.getCurrentAnkiDroidMediaDir());
                if (mediaDir.exists() && mediaDir.isDirectory()) {
                    File[] mediaFiles = mediaDir.listFiles();
                    int c = 0;
                    for (File f : mediaFiles) {
                        FileInputStream mediaFin = new FileInputStream(f);
                        ze = new ZipEntry(Integer.toString(c));
                        zos.putNextEntry(ze);
                        while ((len = mediaFin.read(buf)) >= 0) {
                            zos.write(buf, 0, len);
                        }
                        zos.closeEntry();
                        media.put(Integer.toString(c), f.getName());
                    }
                }
            }
            ze = new ZipEntry("media");
            zos.putNextEntry(ze);
            InputStream mediaIn = new ByteArrayInputStream(Utils.jsonToString(media).getBytes("UTF-8"));
            while ((len = mediaIn.read(buf)) >= 0) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
            zos.close();
        } catch (FileNotFoundException e) {
            return new TaskData(false);
        } catch (IOException e) {
            return new TaskData(false);
        } catch (JSONException e) {
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundRestoreDeck(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundRestoreDeck");
        Object[] data = params[0].getObjArray();
        Collection col = (Collection) data[0];
        if (col != null) {
            col.close(false);
        }
        return new TaskData(BackupManager.restoreBackup((String) data[1], (String) data[2]));
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
                tags.add(s.toLowerCase(Locale.getDefault()));
            }
            for (int i = 0; i < allCard.size(); i++) {
                HashMap<String, String> card = allCard.get(i);
                if (Arrays.asList(card.get("tags").toLowerCase(Locale.getDefault()).trim().split("\\s"))
                        .containsAll(tags)) {
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
                // deck does already exist. Remove all cards and recreate them
                // to ensure the correct order
                col.remCards(col.getDecks().cids(did));
            }
            JSONObject model = col.getModels().byName(title);
            // TODO: check, if model is valid or delete and recreate it
            // TODO: deactivated at the moment as if forces a schema change
            // create model (remove old ones first)
            // while (model != null) {
            // JSONObject m = col.getModels().byName(title);
            // // rename old tutorial model if there are some non tutorial cards
            // in it
            // if
            // (col.getDb().queryScalar("SELECT id FROM cards WHERE nid IN (SELECT id FROM notes WHERE mid = "
            // +
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
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundLoadTutorial");
            return new DeckTask.TaskData(false);
        } catch (JSONException e) {
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundLoadTutorial");
            return new DeckTask.TaskData(false);
        } finally {
            col.getDb().getDatabase().endTransaction();
        }
    }


    private TaskData doInBackgroundReorder(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundReorder");
        Object[] data = params[0].getObjArray();
        Collection col = (Collection) data[0];
        JSONObject conf = (JSONObject) data[1];
        col.getSched().resortConf(conf);
        return new TaskData(true);
    }


    private TaskData doInBackgroundConfChange(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundConfChange");
        Object[] data = params[0].getObjArray();
        Collection col = (Collection) data[0];
        JSONObject deck = (JSONObject) data[1];
        JSONObject conf = (JSONObject) data[2];
        try {
            long newConfId = conf.getLong("id");
            // If new config has a different sorting order, reorder the cards
            int oldOrder = col.getDecks().getConf(deck.getLong("conf")).getJSONObject("new").getInt("order");
            int newOrder = col.getDecks().getConf(newConfId).getJSONObject("new").getInt("order");
            if (oldOrder != newOrder) {
                switch (newOrder) {
                    case 0:
                        col.getSched().randomizeCards(deck.getLong("id"));
                        break;
                    case 1:
                        col.getSched().orderCards(deck.getLong("id"));
                        break;
                }
            }
            col.getDecks().setConf(deck, newConfId);
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }


    private TaskData doInBackgroundConfReset(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundConfReset");
        Object[] data = params[0].getObjArray();
        Collection col = (Collection) data[0];
        JSONObject conf = (JSONObject) data[1];
        col.getDecks().restoreToDefault(conf);
        return new TaskData(true);
    }


    private TaskData doInBackgroundConfRemove(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundConfRemove");
        Object[] data = params[0].getObjArray();
        Collection col = (Collection) data[0];
        JSONObject conf = (JSONObject) data[1];
        try {
            // When a conf is deleted, all decks using it revert to the default conf.
            // Cards must be reordered according to the default conf.
            int order = conf.getJSONObject("new").getInt("order");
            int defaultOrder = col.getDecks().getConf(1).getJSONObject("new").getInt("order");
            if (order != defaultOrder) {
                conf.getJSONObject("new").put("order", defaultOrder);
                col.getSched().resortConf(conf);
            }
            col.getDecks().remConf(conf.getLong("id"));
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }


    private TaskData doInBackgroundConfSetSubdecks(TaskData... params) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundConfSetSubdecks");
        Object[] data = params[0].getObjArray();
        Collection col = (Collection) data[0];
        JSONObject deck = (JSONObject) data[1];
        JSONObject conf = (JSONObject) data[2];
        try {
            TreeMap<String, Long> children = col.getDecks().children(deck.getLong("id"));
            for (Map.Entry<String, Long> entry : children.entrySet()) {
                JSONObject child = col.getDecks().get(entry.getValue());
                if (child.getInt("dyn") == 1) {
                    continue;
                }
                TaskData newParams = new TaskData(new Object[] { col, child, conf });
                boolean changed = doInBackgroundConfChange(newParams).getBoolean();
                if (!changed) {
                    return new TaskData(false);
                }
            }
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }

    /**
     * Listener for the status and result of a {@link DeckTask}.
     * <p>
     * Its methods are guaranteed to be invoked on the main thread.
     * <p>
     * Their semantics is equivalent to the methods of {@link AsyncTask}.
     */
    public static interface Listener {

        /** Invoked before the task is started. */
        void onPreExecute(DeckTask task);


        /**
         * Invoked after the task has completed.
         * <p>
         * The semantics of the result depends on the task itself.
         */
        void onPostExecute(DeckTask task, TaskData result);


        /**
         * Invoked when the background task publishes an update.
         * <p>
         * The semantics of the update data depends on the task itself.
         */
        void onProgressUpdate(DeckTask task, TaskData... values);

    }

    /**
     * Adapter for the old interface, where the DeckTask itself was not passed to the listener.
     * <p>
     * All methods are invoked on the main thread.
     * <p>
     * The semantics of the methods is equivalent to the semantics of the methods in the regular {@link Listener}.
     */
    public static abstract class TaskListener implements Listener {

        /** Invoked before the task is started. */
        public abstract void onPreExecute();


        /**
         * Invoked after the task has completed.
         * <p>
         * The semantics of the result depends on the task itself.
         */
        public abstract void onPostExecute(TaskData result);


        /**
         * Invoked when the background task publishes an update.
         * <p>
         * The semantics of the update data depends on the task itself.
         */
        public abstract void onProgressUpdate(TaskData... values);


        @Override
        public void onPreExecute(DeckTask task) {
            onPreExecute();
        }


        @Override
        public void onPostExecute(DeckTask task, TaskData result) {
            onPostExecute(result);
        }


        @Override
        public void onProgressUpdate(DeckTask task, TaskData... values) {
            onProgressUpdate(values);
        }

    }

    /**
     * Helper class for allowing inner function to publish progress of an AsyncTask.
     */
    public class ProgressCallback {
        private Resources res;
        private DeckTask task;


        public ProgressCallback(DeckTask task, Resources res) {
            this.res = res;
            if (res != null) {
                this.task = task;
            } else {
                this.task = null;
            }
        }


        public Resources getResources() {
            return res;
        }


        public void publishProgress(TaskData values) {
            if (task != null) {
                task.doProgress(values);
            }
        }
    }


    public void doProgress(TaskData values) {
        publishProgress(values);
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
        private List<Long> mIdList;


        public TaskData(Object[] obj) {
            mObjects = obj;
        }


        public TaskData(int value, Object[] obj, boolean bool) {
            mObjects = obj;
            mInteger = value;
            mBool = bool;
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


        public TaskData(Card card, String tags) {
            mCard = card;
            mMsg = tags;
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


        public TaskData(Collection col, String string, boolean bool) {
            mCol = col;
            mMsg = string;
            mBool = bool;
        }


        public TaskData(Collection col, int value) {
            mCol = col;
            mInteger = value;
        }


        public TaskData(Collection col, long value) {
            mCol = col;
            mLong = value;
        }


        public TaskData(Collection col, long value, boolean bool) {
            mCol = col;
            mLong = value;
            mBool = bool;
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


        public TaskData(List<Long> idList) {
            mIdList = idList;
        }


        public ArrayList<HashMap<String, String>> getCards() {
            return mCards;
        }


        public void setCards(ArrayList<HashMap<String, String>> cards) {
            mCards = cards;
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


        public List<Long> getIdList() {
            return mIdList;
        }


        public void setIdList(List<Long> idList) {
            mIdList = idList;
        }
    }

}
