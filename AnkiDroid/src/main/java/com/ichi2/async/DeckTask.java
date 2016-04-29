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
import android.content.res.Resources;
import android.os.AsyncTask;

import com.google.gson.stream.JsonReader;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.AnkiPackageExporter;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.importer.AnkiPackageImporter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import timber.log.Timber;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class DeckTask extends BaseAsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData> {

    public static final int TASK_TYPE_SAVE_COLLECTION = 2;
    public static final int TASK_TYPE_ANSWER_CARD = 3;
    public static final int TASK_TYPE_ADD_FACT = 6;
    public static final int TASK_TYPE_UPDATE_FACT = 7;
    public static final int TASK_TYPE_UNDO = 8;
    public static final int TASK_TYPE_DISMISS = 11;
    public static final int TASK_TYPE_CHECK_DATABASE = 14;
    public static final int TASK_TYPE_REPAIR_DECK = 20;
    public static final int TASK_TYPE_LOAD_DECK_COUNTS = 22;
    public static final int TASK_TYPE_UPDATE_VALUES_FROM_DECK = 23;
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
    public static final int TASK_TYPE_CHECK_MEDIA = 38;
    public static final int TASK_TYPE_ADD_TEMPLATE = 39;
    public static final int TASK_TYPE_REMOVE_TEMPLATE = 40;
    public static final int TASK_TYPE_COUNT_MODELS = 41;
    public static final int TASK_TYPE_DELETE_MODEL = 42;
    public static final int TASK_TYPE_DELETE_FIELD = 43;
    public static final int TASK_TYPE_REPOSITION_FIELD = 44;
    public static final int TASK_TYPE_ADD_FIELD = 45;
    public static final int TASK_TYPE_CHANGE_SORT_FIELD = 46;
    public static final int TASK_TYPE_SAVE_MODEL = 47;
    public static final int TASK_TYPE_FIND_EMPTY_CARDS = 48;

    /**
     * A reference to the application context to use to fetch the current Collection object.
     */
    private Context mContext;


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
        // Start new task
        DeckTask newTask = new DeckTask(type, listener, sLatestInstance);
        newTask.execute(params);
        return newTask;
    }


    /**
     * Block the current thread until the currently running DeckTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        waitToFinish(null);
    }

    /**
     * Block the current thread until the currently running DeckTask instance (if any) has finished.
     * @param timeout timeout in seconds
     * @return whether or not the previous task was successful or not
     */
    public static boolean waitToFinish(Integer timeout) {
        try {
            if ((sLatestInstance != null) && (sLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                Timber.d("DeckTask: waiting for task %d to finish...", sLatestInstance.mType);
                if (timeout != null) {
                    sLatestInstance.get(timeout, TimeUnit.SECONDS);
                } else {
                    sLatestInstance.get();
                }

            }
            return true;
        } catch (Exception e) {
            Timber.e(e, "Exception waiting for task to finish");
            return false;
        }
    }


    public static void cancelTask() {
        //cancel the current task
        try {
            if ((sLatestInstance != null) && (sLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                sLatestInstance.cancel(true);
                Timber.i("Cancelled task %d", sLatestInstance.mType);
            }
        } catch (Exception e) {
            return;
        }
    }


    public static void cancelTask(int taskType) {
        // cancel the current task only if it's of type taskType
        if (sLatestInstance != null && sLatestInstance.mType == taskType) {
            cancelTask();
        }
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
            Timber.d("Waiting for %d to finish before starting %d", mPreviousTask.mType, mType);
            try {
                mPreviousTask.get();
                Timber.d("Finished waiting for %d to finish. Status= %s", mPreviousTask.mType, mPreviousTask.getStatus());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // We have been interrupted, return immediately.
                Timber.e(e, "interrupted while waiting for previous task: %d", mPreviousTask.mType);
                return null;
            } catch (ExecutionException e) {
                // Ignore failures in the previous task.
                Timber.e(e, "previously running task failed with exception: %d", mPreviousTask.mType);
            } catch (CancellationException e) {
                // Ignore cancellation of previous task
                Timber.e(e, "previously running task was cancelled: %d", mPreviousTask.mType);
            }
        }
        sLatestInstance = this;
        mContext = AnkiDroidApp.getInstance().getApplicationContext();

        // Skip the task if the collection cannot be opened
        if (mType != TASK_TYPE_REPAIR_DECK && CollectionHelper.getInstance().getColSafe(mContext) == null) {
            Timber.e("Aborting DeckTask %d as Collection could not be opened", mType);
            return null;
        }
        // Actually execute the task now that we are at the front of the queue.
        switch (mType) {
            case TASK_TYPE_LOAD_DECK_COUNTS:
                return doInBackgroundLoadDeckCounts(params);

            case TASK_TYPE_SAVE_COLLECTION:
                return doInBackgroundSaveCollection(params);

            case TASK_TYPE_ANSWER_CARD:
                return doInBackgroundAnswerCard(params);

            case TASK_TYPE_ADD_FACT:
                return doInBackgroundAddNote(params);

            case TASK_TYPE_UPDATE_FACT:
                return doInBackgroundUpdateNote(params);

            case TASK_TYPE_UNDO:
                return doInBackgroundUndo(params);

            case TASK_TYPE_SEARCH_CARDS:
                return doInBackgroundSearchCards(params);

            case TASK_TYPE_DISMISS:
                return doInBackgroundDismissNote(params);

            case TASK_TYPE_CHECK_DATABASE:
                return doInBackgroundCheckDatabase(params);

            case TASK_TYPE_REPAIR_DECK:
                return doInBackgroundRepairDeck(params);

            case TASK_TYPE_UPDATE_VALUES_FROM_DECK:
                return doInBackgroundUpdateValuesFromDeck(params);

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

            case TASK_TYPE_CHECK_MEDIA:
                return doInBackgroundCheckMedia(params);

            case TASK_TYPE_ADD_TEMPLATE:
                return doInBackgroundAddTemplate(params);

            case TASK_TYPE_REMOVE_TEMPLATE:
                return doInBackgroundRemoveTemplate(params);

            case TASK_TYPE_COUNT_MODELS:
                return doInBackgroundCountModels(params);

            case TASK_TYPE_DELETE_MODEL:
                return  doInBackGroundDeleteModel(params);

            case TASK_TYPE_DELETE_FIELD:
                return doInBackGroundDeleteField(params);

            case TASK_TYPE_REPOSITION_FIELD:
                return doInBackGroundRepositionField(params);

            case TASK_TYPE_ADD_FIELD:
                return doInBackGroundAddField(params);

            case TASK_TYPE_CHANGE_SORT_FIELD:
                return doInBackgroundChangeSortField(params);

            case TASK_TYPE_SAVE_MODEL:
                return doInBackgroundSaveModel(params);
            case TASK_TYPE_FIND_EMPTY_CARDS:
                return doInBackGroundFindEmptyCards(params);

            default:
                Timber.e("unknown task type: %d", mType);
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
        Timber.d("enabling garbage collection of mPreviousTask...");
        mPreviousTask = null;
    }

    @Override
    protected void onCancelled(){
        mListener.onCancelled();
    }

    private TaskData doInBackgroundAddNote(TaskData[] params) {
        Timber.d("doInBackgroundAddNote");
        Note note = params[0].getNote();
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        try {
            DB db = col.getDb();
            db.getDatabase().beginTransaction();
            try {
                publishProgress(new TaskData(col.addNote(note)));
                db.getDatabase().setTransactionSuccessful();
            } finally {
                db.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundAddNote - RuntimeException on adding fact");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAddNote");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundUpdateNote(TaskData[] params) {
        Timber.d("doInBackgroundUpdateNote");
        // Save the note
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Sched sched = col.getSched();
        Card editCard = params[0].getCard();
        Note editNote = editCard.note();
        boolean fromReviewer = params[0].getBoolean();

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
            Timber.e(e, "doInBackgroundUpdateNote - RuntimeException on updating fact");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUpdateNote");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundAnswerCard(TaskData... params) {
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Sched sched = col.getSched();
        Card oldCard = params[0].getCard();
        int ease = params[0].getInt();
        Card newCard = null;
        try {
            DB db = col.getDb();
            db.getDatabase().beginTransaction();
            try {
                if (oldCard != null) {
                    sched.answerCard(oldCard, ease);
                }
                if (newCard == null) {
                    newCard = getCard(sched);
                }
                if (newCard != null) {
                    // render cards before locking database
                    newCard._getQA(true);
                }
                publishProgress(new TaskData(newCard));
                db.getDatabase().setTransactionSuccessful();
            } finally {
                db.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundAnswerCard - RuntimeException on answering card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAnswerCard");
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


    private TaskData doInBackgroundLoadDeckCounts(TaskData... params) {
        Timber.d("doInBackgroundLoadDeckCounts");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        try {
            // Get due tree
            Object[] o = new Object[] {col.getSched().deckDueTree()};
            return new TaskData(o);
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundLoadDeckCounts - error");
            return null;
        }
    }


    private TaskData doInBackgroundSaveCollection(TaskData... params) {
        Timber.d("doInBackgroundSaveCollection");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        if (col != null) {
            try {
                col.save();
            } catch (RuntimeException e) {
                Timber.e(e, "Error on saving deck in background");
            }
        }
        return null;
    }


    private TaskData doInBackgroundDismissNote(TaskData... params) {
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Sched sched = col.getSched();
        Object[] data = params[0].getObjArray();
        Card card = (Card) data[0];
        Collection.DismissType type = (Collection.DismissType) data[1];
        Note note = card.note();
        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                switch (type) {
                    case BURY_CARD:
                        // collect undo information
                        col.markUndo(type, new Object[] { col.getDirty(), note.cards(), card.getId() });
                        // then bury
                        sched.buryCards(new long[] { card.getId() });
                        sHadCardQueue = true;
                        break;
                    case BURY_NOTE:
                        // collect undo information
                        col.markUndo(type, new Object[] { col.getDirty(), note.cards(), card.getId() });
                        // then bury
                        sched.buryNote(note.getId());
                        sHadCardQueue = true;
                        break;
                    case SUSPEND_CARD:
                        // collect undo information
                        col.markUndo(type, new Object[] { card });
                        // suspend card
                        if (card.getQueue() == -1) {
                            sched.unsuspendCards(new long[] { card.getId() });
                        } else {
                            sched.suspendCards(new long[] { card.getId() });
                        }
                        sHadCardQueue = true;
                        break;
                    case SUSPEND_NOTE:
                        // collect undo information
                        ArrayList<Card> cards = note.cards();
                        long[] cids = new long[cards.size()];
                        for (int i = 0; i < cards.size(); i++) {
                            cids[i] = cards.get(i).getId();
                        }
                        col.markUndo(type, new Object[] { cards, card.getId() });
                        // suspend note
                        sched.suspendCards(cids);
                        sHadCardQueue = true;
                        break;
                    case DELETE_NOTE:
                        // collect undo information
                        ArrayList<Card> allCs = note.cards();
                        col.markUndo(type, new Object[] { note, allCs, card.getId() });
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
            Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSuspendCard");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundUndo(TaskData... params) {
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Sched sched = col.getSched();
        try {
            col.getDb().getDatabase().beginTransaction();
            Card newCard;
            try {
                long cid = col.undo();
                if (cid != 0) {
                    // a review was undone,
                    newCard = col.getCard(cid);
                    newCard.startTimer();
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
            Timber.e(e, "doInBackgroundUndo - RuntimeException on undoing");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUndo");
            return new TaskData(false);
        }
        return new TaskData(true);
    }


    private TaskData doInBackgroundSearchCards(TaskData... params) {
        Timber.d("doInBackgroundSearchCards");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Map<String, String> deckNames = (HashMap<String, String>) params[0].getObjArray()[0];
        String query = (String) params[0].getObjArray()[1];
        Boolean order = (Boolean) params[0].getObjArray()[2];
        int numCardsToRender = (int) params[0].getObjArray()[3];
        List<Map<String,String>> searchResult = col.findCardsForCardBrowser(query, order, deckNames);
        // Render the first few items
        for (int i = 0; i < Math.min(numCardsToRender, searchResult.size()); i++) {
            Card c = col.getCard(Long.parseLong(searchResult.get(i).get("id"), 10));
            CardBrowser.updateSearchItemQA(searchResult.get(i), c);
        }
        // Finish off the task
        if (isCancelled()) {
            Timber.d("doInBackgroundSearchCards was cancelled so return null");
            return null;
        } else {
            publishProgress(new TaskData(searchResult));
        }
        return new TaskData(col.cardCount(col.getDecks().allIds()));
    }


    private TaskData doInBackgroundRenderBrowserQA(TaskData... params) {
        Timber.d("doInBackgroundRenderBrowserQA");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        List<Map<String, String>> items = (List<Map<String, String>>) params[0].getObjArray()[0];
        Integer startPos = (Integer) params[0].getObjArray()[1];
        Integer n = (Integer) params[0].getObjArray()[2];

        // for each specified card in the browser list
        for (int i = startPos; i < startPos + n; i++) {
            if (i >= 0 && i < items.size() && items.get(i).get("answer").equals("")) {
                // Extract card item
                Card c = col.getCard(Long.parseLong(items.get(i).get("id"), 10));
                // Update item
                CardBrowser.updateSearchItemQA(items.get(i), c);
                // Stop if cancelled
                if (isCancelled()) {
                    Timber.d("doInBackgroundRenderBrowserQA was aborted");
                    return null;
                } else {
                    float progress = (float) i / n * 100;
                    publishProgress(new TaskData((int) progress));
                }
            }
        }
        return new TaskData(items);
    }


    private TaskData doInBackgroundCheckDatabase(TaskData... params) {
        Timber.d("doInBackgroundCheckDatabase");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        // Don't proceed if collection closed
        if (col == null) {
            Timber.e("doInBackgroundCheckDatabase :: supplied collection was null");
            return new TaskData(false);
        }

        long result = col.fixIntegrity();
        if (result == -1) {
            return new TaskData(false);
        } else {
            // Close the collection and we restart the app to reload
            CollectionHelper.getInstance().closeCollection(true);
            return new TaskData(0, result, true);
        }
    }


    private TaskData doInBackgroundRepairDeck(TaskData... params) {
        Timber.d("doInBackgroundRepairDeck");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        if (col != null) {
            col.close(false);
        }
        return new TaskData(BackupManager.repairCollection(col));
    }


    private TaskData doInBackgroundUpdateValuesFromDeck(TaskData... params) {
        Timber.d("doInBackgroundUpdateValuesFromDeck");
        try {
            Collection col = CollectionHelper.getInstance().getCol(mContext);
            Sched sched = col.getSched();
            Object[] obj = params[0].getObjArray();
            boolean reset = (Boolean) obj[0];
            if (reset) {
                sched.reset();
            }
            int[] counts = sched.counts();
            int totalNewCount = sched.totalNewForCurrentDeck();
            int totalCount = sched.cardCount();
            return new TaskData(new Object[]{counts[0], counts[1], counts[2], totalNewCount,
                    totalCount, sched.eta(counts)});
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundUpdateValuesFromDeck - an error occurred");
            return null;
        }
    }


    private TaskData doInBackgroundDeleteDeck(TaskData... params) {
        Timber.d("doInBackgroundDeleteDeck");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        long did = params[0].getLong();
        col.getDecks().rem(did, true);
        return new TaskData(true);
    }


    private TaskData doInBackgroundRebuildCram(TaskData... params) {
        Timber.d("doInBackgroundRebuildCram");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        col.getSched().rebuildDyn(col.getDecks().selected());
        return doInBackgroundUpdateValuesFromDeck(new DeckTask.TaskData(new Object[]{true}));
    }


    private TaskData doInBackgroundEmptyCram(TaskData... params) {
        Timber.d("doInBackgroundEmptyCram");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        col.getSched().emptyDyn(col.getDecks().selected());
        return doInBackgroundUpdateValuesFromDeck(new DeckTask.TaskData(new Object[]{true}));
    }


    private TaskData doInBackgroundImportAdd(TaskData... params) {
        Timber.d("doInBackgroundImportAdd");
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        String path = params[0].getString();
        AnkiPackageImporter imp = new AnkiPackageImporter(col, path);
        imp.setProgressCallback(new ProgressCallback(this, res));
        imp.run();
        return new TaskData(new Object[] {imp});
    }


    private TaskData doInBackgroundImportReplace(TaskData... params) {
        Timber.d("doInBackgroundImportReplace");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        String path = params[0].getString();
        Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();

        // extract the deck from the zip file
        String colPath = col.getPath();
        File dir = new File(new File(colPath).getParentFile(), "tmpzip");
        if (dir.exists()) {
            BackupManager.removeDir(dir);
        }

        // from anki2.py
        String colFile = new File(dir, "collection.anki2").getAbsolutePath();
        ZipFile zip;
        try {
            zip = new ZipFile(new File(path), ZipFile.OPEN_READ);
        } catch (IOException e) {
            Timber.e(e, "doInBackgroundImportReplace - Error while unzipping");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace0");
            return new TaskData(false);
        }
        try {
            Utils.unzipFiles(zip, dir.getAbsolutePath(), new String[] { "collection.anki2", "media" }, null);
        } catch (IOException e) {
            return new TaskData(-2, null, false);
        }
        if (!(new File(colFile)).exists()) {
            return new TaskData(-2, null, false);
        }

        Collection tmpCol = null;
        try {
            tmpCol = Storage.Collection(mContext, colFile);
            if (!tmpCol.validCollection()) {
                tmpCol.close();
                return new TaskData(-2, null, false);
            }
        } catch (Exception e) {
            Timber.e("Error opening new collection file... probably it's invalid");
            try {
                tmpCol.close();
            } catch (Exception e2) {
                // do nothing
            }
            return new TaskData(-2, null, false);
        } finally {
            if (tmpCol != null) {
                tmpCol.close();
            }
        }

        publishProgress(new TaskData(res.getString(R.string.importing_collection)));
        if (col != null) {
            // unload collection and trigger a backup
            CollectionHelper.getInstance().closeCollection(true);
            CollectionHelper.getInstance().lockCollection();
            BackupManager.performBackupInBackground(colPath, true);
        }
        // overwrite collection
        File f = new File(colFile);
        if (!f.renameTo(new File(colPath))) {
            // Exit early if this didn't work
            return new TaskData(-2, null, false);
        }
        int addedCount = -1;
        try {
            col.reopen();
            CollectionHelper.getInstance().unlockCollection();

            // because users don't have a backup of media, it's safer to import new
            // data and rely on them running a media db check to get rid of any
            // unwanted media. in the future we might also want to duplicate this step
            // import media
            HashMap<String, String> nameToNum = new HashMap<>();
            HashMap<String, String> numToName = new HashMap<>();
            File mediaMapFile = new File(dir.getAbsolutePath(), "media");
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
            String mediaDir = col.getMedia().dir();
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
            return new TaskData(true);
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundImportReplace - RuntimeException");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace1");
            return new TaskData(false);
        } catch (FileNotFoundException e) {
            Timber.e(e, "doInBackgroundImportReplace - FileNotFoundException");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace2");
            return new TaskData(false);
        } catch (IOException e) {
            Timber.e(e, "doInBackgroundImportReplace - IOException");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace3");
            return new TaskData(false);
        }
    }


    private TaskData doInBackgroundExportApkg(TaskData... params) {
        Timber.d("doInBackgroundExportApkg");
        Object[] data = params[0].getObjArray();
        Collection col = (Collection) data[0];
        String apkgPath = (String) data[1];
        Long did = (Long) data[2];
        boolean includeSched = (Boolean) data[3];
        boolean includeMedia = (Boolean) data[4];
        
        try {
            AnkiPackageExporter exporter = new AnkiPackageExporter(col);
            exporter.setIncludeSched(includeSched);
            exporter.setIncludeMedia(includeMedia);
            exporter.setDid(did);
            exporter.exportInto(apkgPath, mContext);
        } catch (FileNotFoundException e) {
            Timber.e(e, "FileNotFoundException in doInBackgroundExportApkg");
            return new TaskData(false);
        } catch (IOException e) {
            Timber.e(e, "IOException in doInBackgroundExportApkg");
            return new TaskData(false);
        } catch (JSONException e) {
            Timber.e(e, "JSOnException in doInBackgroundExportApkg");
            return new TaskData(false);
        }
        return new TaskData(apkgPath);
    }


    private TaskData doInBackgroundReorder(TaskData... params) {
        Timber.d("doInBackgroundReorder");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object[] data = params[0].getObjArray();
        JSONObject conf = (JSONObject) data[0];
        col.getSched().resortConf(conf);
        return new TaskData(true);
    }


    private TaskData doInBackgroundConfChange(TaskData... params) {
        Timber.d("doInBackgroundConfChange");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object[] data = params[0].getObjArray();
        JSONObject deck = (JSONObject) data[0];
        JSONObject conf = (JSONObject) data[1];
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
            col.save();
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }


    private TaskData doInBackgroundConfReset(TaskData... params) {
        Timber.d("doInBackgroundConfReset");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object[] data = params[0].getObjArray();
        JSONObject conf = (JSONObject) data[0];
        col.getDecks().restoreToDefault(conf);
        col.save();
        return new TaskData(true);
    }


    private TaskData doInBackgroundConfRemove(TaskData... params) {
        Timber.d("doInBackgroundConfRemove");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object[] data = params[0].getObjArray();
        JSONObject conf = (JSONObject) data[0];
        try {
            // Note: We do the actual removing of the options group in the main thread so that we 
            // can ask the user to confirm if they're happy to do a full sync, and just do the resorting here

            // When a conf is deleted, all decks using it revert to the default conf.
            // Cards must be reordered according to the default conf.
            int order = conf.getJSONObject("new").getInt("order");
            int defaultOrder = col.getDecks().getConf(1).getJSONObject("new").getInt("order");
            if (order != defaultOrder) {
                conf.getJSONObject("new").put("order", defaultOrder);
                col.getSched().resortConf(conf);
            }
            col.save();
            return new TaskData(true);
        } catch (JSONException e) {
            return new TaskData(false);
        }
    }


    private TaskData doInBackgroundConfSetSubdecks(TaskData... params) {
        Timber.d("doInBackgroundConfSetSubdecks");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object[] data = params[0].getObjArray();
        JSONObject deck = (JSONObject) data[0];
        JSONObject conf = (JSONObject) data[1];
        try {
            TreeMap<String, Long> children = col.getDecks().children(deck.getLong("id"));
            for (Map.Entry<String, Long> entry : children.entrySet()) {
                JSONObject child = col.getDecks().get(entry.getValue());
                if (child.getInt("dyn") == 1) {
                    continue;
                }
                TaskData newParams = new TaskData(new Object[] { child, conf });
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
     * @return The results list from the check, or false if any errors.
     */
    private TaskData doInBackgroundCheckMedia(TaskData... params) {
        Timber.d("doInBackgroundCheckMedia");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        // A media check on AnkiDroid will also update the media db
        col.getMedia().findChanges(true);
        // Then do the actual check
        List<List<String>> result = col.getMedia().check();
        return new TaskData(0, new Object[]{result}, true);
    }

    /**
     * Add a new card template
     */
    private TaskData doInBackgroundAddTemplate(TaskData... params) {
        Timber.d("doInBackgroundAddTemplate");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object [] args = params[0].getObjArray();
        JSONObject model = (JSONObject) args[0];
        JSONObject template = (JSONObject) args[1];
        // add the new template
        try {
            col.getModels().addTemplate(model, template);
            col.save();
        } catch (ConfirmModSchemaException e) {
            Timber.e("doInBackgroundAddTemplate :: ConfirmModSchemaException");
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    /**
     * Remove a card template. Note: it's necessary to call save model after this to re-generate the cards
     */
    private TaskData doInBackgroundRemoveTemplate(TaskData... params) {
        Timber.d("doInBackgroundRemoveTemplate");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object [] args = params[0].getObjArray();
        JSONObject model = (JSONObject) args[0];
        JSONObject template = (JSONObject) args[1];
        try {
            boolean success = col.getModels().remTemplate(model, template);
            if (! success) {
                return new TaskData("removeTemplateFailed", false);
            }
            col.save();
        } catch (ConfirmModSchemaException e) {
            Timber.e("doInBackgroundRemoveTemplate :: ConfirmModSchemaException");
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    /**
     * Regenerate all the cards in a model
     */
    private TaskData doInBackgroundSaveModel(TaskData... params) {
        Timber.d("doInBackgroundSaveModel");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        Object [] args = params[0].getObjArray();
        JSONObject model = (JSONObject) args[0];
        col.getModels().save(model, true);
        col.reset();
        col.save();
        return new TaskData(true);
    }


    /*
     * Async task for the ModelBrowser Class
     * Returns an ArrayList of all models alphabetically ordered and the number of notes
     * associated with each model.
     *
     * @return {ArrayList<JSONObject> models, ArrayList<Integer> cardCount}
     */
    private TaskData doInBackgroundCountModels(TaskData... params){
        Timber.d("doInBackgroundLoadModels");
        Collection col = CollectionHelper.getInstance().getCol(mContext);

        ArrayList<JSONObject> models = col.getModels().all();
        ArrayList<Integer> cardCount = new ArrayList<>();
        Collections.sort(models, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                try {
                    return a.getString("name").compareTo(b.getString("name"));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        try{
            for (JSONObject n : models) {
                long modID = n.getLong("id");
                cardCount.add(col.getModels().nids(col.getModels().get(modID)).size());
            }
        } catch (JSONException e) {
                Timber.e("doInBackgroundLoadModels :: JSONException");
                return new TaskData(false);
        }

        Object[] data = new Object[2];
        data[0] = models;
        data[1] = cardCount;
        return (new TaskData(0, data, true));
    }


    /**
     * Deletes the given model (stored in the long field of TaskData)
     * and all notes associated with it
     */
    private TaskData doInBackGroundDeleteModel(TaskData... params){
        Timber.d("doInBackGroundDeleteModel");
        long modID = params[0].getLong();
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        try {
            col.getModels().rem(col.getModels().get(modID));
            col.save();
        } catch (ConfirmModSchemaException e) {
            Timber.e("doInBackGroundDeleteModel :: ConfirmModSchemaException");
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    /**
     * Deletes thje given field in the given model
     */
    private TaskData doInBackGroundDeleteField(TaskData... params){
        Timber.d("doInBackGroundDeleteField");
        Object[] objects = params[0].getObjArray();

        JSONObject model = (JSONObject) objects[0];
        JSONObject field = (JSONObject) objects[1];


        Collection col = CollectionHelper.getInstance().getCol(mContext);
        try {
            col.getModels().remField(model, field);
            col.save();
        } catch (ConfirmModSchemaException e) {
            //Should never be reached
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    /**
     * Repositions the given field in the given model
     */
    private TaskData doInBackGroundRepositionField(TaskData... params){
        Timber.d("doInBackgroundRepositionField");
        Object[] objects = params[0].getObjArray();

        JSONObject model = (JSONObject) objects[0];
        JSONObject field = (JSONObject) objects[1];
        int index = (Integer) objects[2];


        Collection col = CollectionHelper.getInstance().getCol(mContext);
        try {
            col.getModels().moveField(model, field, index);
            col.save();
        } catch (ConfirmModSchemaException e) {
            //Should never be reached
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    /**
     * Adds a field of with name in given model
     */
    private TaskData doInBackGroundAddField(TaskData... params){
        Timber.d("doInBackgroundRepositionField");
        Object[] objects = params[0].getObjArray();

        JSONObject model = (JSONObject) objects[0];
        String fieldName = (String) objects[1];

        Collection col = CollectionHelper.getInstance().getCol(mContext);
        try {
            col.getModels().addField(model, col.getModels().newField(fieldName));
            col.save();
        } catch (ConfirmModSchemaException e) {
            //Should never be reached
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    /**
     * Adds a field of with name in given model
     */
    private TaskData doInBackgroundChangeSortField(TaskData... params){
        try {
            Timber.d("doInBackgroundChangeSortField");
            Object[] objects = params[0].getObjArray();

            JSONObject model = (JSONObject) objects[0];
            int idx = (int) objects[1];

            Collection col = CollectionHelper.getInstance().getCol(mContext);
            col.getModels().setSortIdx(model, idx);
            col.save();
        } catch(Exception e){
            Timber.e(e, "Error changing sort field");
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    public TaskData doInBackGroundFindEmptyCards(TaskData... params) {
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        List<Long> cids = col.emptyCids();
        return new TaskData(new Object[] { cids});
    }

    /**
     * Listener for the status and result of a {@link DeckTask}.
     * <p>
     * Its methods are guaranteed to be invoked on the main thread.
     * <p>
     * Their semantics is equivalent to the methods of {@link AsyncTask}.
     */
    public interface Listener {

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

        /**
         * Invoked when the background task is cancelled.
         */        
        void onCancelled();

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
        private List<Map<String, String>> mCards;
        private long mLong;
        private Context mContext;
        private int mType;
        private Comparator mComparator;
        private Object[] mObjects;


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


        public TaskData(List<Map<String, String>> cards) {
            mCards = cards;
        }


        public TaskData(List<Map<String, String>> cards, Comparator comparator) {
            mCards = cards;
            mComparator = comparator;
        }


        public TaskData(boolean bool) {
            mBool = bool;
        }


        public TaskData(String string, boolean bool) {
            mMsg = string;
            mBool = bool;
        }


        public TaskData(long value, boolean bool) {
            mLong = value;
            mBool = bool;
        }


        public TaskData(int value, boolean bool) {
            mInteger = value;
            mBool = bool;
        }


        public TaskData(Card card, boolean bool) {
            mBool = bool;
            mCard = card;
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


        public List<Map<String, String>> getCards() {
            return mCards;
        }


        public void setCards(List<Map<String, String>> cards) {
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


        public Object[] getObjArray() {
            return mObjects;
        }
    }

    public static synchronized DeckTask getInstance() {
        return sLatestInstance;
    }
}
