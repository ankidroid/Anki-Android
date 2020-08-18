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

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.Undoable.*;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Deck;

import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import timber.log.Timber;

import static com.ichi2.libanki.Collection.DismissType.BURY_CARD;
import static com.ichi2.libanki.Collection.DismissType.BURY_NOTE;
import static com.ichi2.libanki.Collection.DismissType.SUSPEND_NOTE;
import static com.ichi2.libanki.Undoable.*;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class CollectionTask extends BaseAsyncTask<TaskData, TaskData, TaskData> {

    public enum TASK_TYPE {
        UNDO,
        DISMISS_MULTI,
        CHECK_DATABASE,
        REPAIR_COLLECTION,
        LOAD_DECK_COUNTS,
        REBUILD_CRAM,
        SEARCH_CARDS,
        RENDER_BROWSER_QA,
        COUNT_MODELS,
        CHECK_CARD_SELECTION,
    }

    /**
     * A reference to the application context to use to fetch the current Collection object.
     */
    private Context mContext;
    /**
     * Tasks which are running or waiting to run.
     * */
    private static final List<CollectionTask> sTasks = Collections.synchronizedList(new LinkedList<>());


    /**
     * The most recently started {@link CollectionTask} instance.
     */
    private static CollectionTask sLatestInstance;


    /**
     * Starts a new {@link CollectionTask}, with no listener
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @return the newly created task
     */
    public static CollectionTask launchCollectionTask(TASK_TYPE type) {
        return launchCollectionTask(type, null, null);
    }

    /**
     * Starts a new {@link CollectionTask}, with no listener
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @param param to pass to the task
     * @return the newly created task
     */
    public static CollectionTask launchCollectionTask(TASK_TYPE type, TaskData param) {
        return launchCollectionTask(type, null, param);
    }

    /**
     * Starts a new {@link CollectionTask}, with a listener provided for callbacks during execution
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @param listener to the status and result of the task, may be null
     * @return the newly created task
     */
    public static CollectionTask launchCollectionTask(TASK_TYPE type, @Nullable TaskListener listener) {
        // Start new task
        return launchCollectionTask(type, listener, null);
    }

    /**
     * Starts a new {@link CollectionTask}, with a listener provided for callbacks during execution
     * <p>
     * Tasks will be executed serially, in the order in which they are started.
     * <p>
     * This method must be called on the main thread.
     *
     * @param type of the task to start
     * @param listener to the status and result of the task, may be null
     * @param param to pass to the task
     * @return the newly created task
     */
    public static CollectionTask launchCollectionTask(TASK_TYPE type, @Nullable TaskListener listener, TaskData param) {
        // Start new task
        CollectionTask newTask = new CollectionTask(type, listener, sLatestInstance);
        newTask.execute(param);
        return newTask;
    }


    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        waitToFinish(null);
    }

    /**
     * Block the current thread until the currently running CollectionTask instance (if any) has finished.
     * @param timeout timeout in seconds
     * @return whether or not the previous task was successful or not
     */
    public static boolean waitToFinish(Integer timeout) {
        try {
            if ((sLatestInstance != null) && (sLatestInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                Timber.d("CollectionTask: waiting for task %s to finish...", sLatestInstance.mType);
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

    /** Cancel the current task.
     * @return whether cancelling did occur.*/
    public boolean safeCancel() {
        try {
            if (getStatus() != AsyncTask.Status.FINISHED) {
                return cancel(true);
            }
        } catch (Exception e) {
            // Potentially catching SecurityException, from
            // Thread.interrupt from FutureTask.cancel from
            // AsyncTask.cancel
            Timber.w(e, "Exception cancelling task");
        } finally {
            sTasks.remove(this);
        }
        return false;
    }


    /** Cancel the current task only if it's of type taskType */
    public static void cancelCurrentlyExecutingTask() {
        CollectionTask latestInstance = sLatestInstance;
        if (latestInstance != null) {
            if (latestInstance.safeCancel()) {
                Timber.i("Cancelled task %s", latestInstance.mType);
            }
        };
    }

    public Context getContext() {
        return mContext;
    }

    public Collection getCol() {
        return CollectionHelper.getInstance().getCol(mContext);
    }

    /** Cancel all tasks of type taskType*/
    public static void cancelAllTasks(TASK_TYPE taskType) {
        int count = 0;
        // safeCancel modifies sTasks, so iterate over a concrete copy
        for (CollectionTask task: new ArrayList<>(sTasks)) {
            if (task.mType != taskType) {
                continue;
            }
            if (task.safeCancel()) {
                count++;
            }
        }
        if (count > 0) {
            Timber.i("Cancelled %d instances of task %s", count, taskType);
        }
    }


    private final TASK_TYPE mType;
    private final TaskListener mListener;
    private CollectionTask mPreviousTask;


    private CollectionTask(TASK_TYPE type, TaskListener listener, CollectionTask previousTask) {
        mType = type;
        mListener = listener;
        mPreviousTask = previousTask;
        sTasks.add(this);
    }

    @Override
    protected TaskData doInBackground(TaskData... params) {
        try {
            return actualDoInBackground(params[0]);
        } finally {
            sTasks.remove(this);
        }
    }

    // This method and those that are called here are executed in a new thread
    protected TaskData actualDoInBackground(TaskData param) {
        super.doInBackground(param);
        // Wait for previous thread (if any) to finish before continuing
        if (mPreviousTask != null && mPreviousTask.getStatus() != AsyncTask.Status.FINISHED) {
            Timber.d("Waiting for %s to finish before starting %s", mPreviousTask.mType, mType);
            try {
                mPreviousTask.get();
                Timber.d("Finished waiting for %s to finish. Status= %s", mPreviousTask.mType, mPreviousTask.getStatus());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // We have been interrupted, return immediately.
                Timber.d(e, "interrupted while waiting for previous task: %s", mPreviousTask.mType);
                return null;
            } catch (ExecutionException e) {
                // Ignore failures in the previous task.
                Timber.e(e, "previously running task failed with exception: %s", mPreviousTask.mType);
            } catch (CancellationException e) {
                // Ignore cancellation of previous task
                Timber.d(e, "previously running task was cancelled: %s", mPreviousTask.mType);
            }
        }
        sLatestInstance = this;
        mContext = AnkiDroidApp.getInstance().getApplicationContext();

        // Skip the task if the collection cannot be opened
        if (mType != TASK_TYPE.REPAIR_COLLECTION && CollectionHelper.getInstance().getColSafe(mContext) == null) {
            Timber.e("CollectionTask CollectionTask %s as Collection could not be opened", mType);
            return null;
        }
        // Actually execute the task now that we are at the front of the queue.
        switch (mType) {

        case UNDO:
            return doInBackgroundUndo();

        case SEARCH_CARDS:
            return doInBackgroundSearchCards(param);

        case CHECK_DATABASE:
            return doInBackgroundCheckDatabase();

        case REPAIR_COLLECTION:
            return doInBackgroundRepairCollection();

        default:
            return doInBackgroundCode(param);
        }
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mListener != null) {
            mListener.onPreExecute();
        }
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onProgressUpdate(TaskData... values) {
        super.onProgressUpdate(values);
        if (mListener != null) {
            mListener.onProgressUpdate(values[0]);
        }
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onPostExecute(TaskData result) {
        super.onPostExecute(result);
        if (mListener != null) {
            mListener.onPostExecute(result);
        }
        Timber.d("enabling garbage collection of mPreviousTask...");
        mPreviousTask = null;
    }

    @Override
    protected void onCancelled(){
        sTasks.remove(this);
        if (mListener != null) {
            mListener.onCancelled();
        }
    }



    public static class UpdateNote implements Task {
        private final Card mEditCard;
        private final boolean mFromReviewer;


        public UpdateNote(Card editCard, boolean fromReviewer) {
            mEditCard = editCard;
            mFromReviewer = fromReviewer;
        }


        @Override
        public TaskData background(CollectionTask task) {
            Timber.d("doInBackgroundUpdateNote");
            // Save the note
            Collection col = task.getCol();
            AbstractSched sched = col.getSched();
            Note editNote = mEditCard.note();

            try {
                col.getDb().getDatabase().beginTransaction();
                try {
                    // TODO: undo integration
                    editNote.flush();
                    // flush card too, in case, did has been changed
                    mEditCard.flush();
                    if (mFromReviewer) {
                        Card newCard;
                        if (col.getDecks().active().contains(mEditCard.getDid())) {
                            newCard = mEditCard;
                            newCard.load();
                            // reload qa-cache
                            newCard.q(true);
                        } else {
                            newCard = sched.getCard();
                        }
                        task.doProgress(new TaskData(newCard));
                    } else {
                        task.doProgress(new TaskData(mEditCard, editNote.stringTags()));
                    }
                    col.getDb().getDatabase().setTransactionSuccessful();
                } finally {
                    col.getDb().getDatabase().endTransaction();
                }
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUpdateNote - RuntimeException on updating note");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUpdateNote");
                return new TaskData(false);
            }
            return new TaskData(true);
        }
    }



    private static class UndoSuspendCard extends Undoable {
        private final Card suspendedCard;


        public UndoSuspendCard(Card suspendedCard) {
            super(Collection.DismissType.SUSPEND_CARD);
            this.suspendedCard = suspendedCard;
        }


        public long undo(Collection col) {
            Timber.i("UNDO: Suspend Card %d", suspendedCard.getId());
            suspendedCard.flush(false);
            return suspendedCard.getId();
        }
    }

    
    private static class UndoChangeDeckMulti extends Undoable {
        private final Card[] cards;
        private final long[] originalDids;


        public UndoChangeDeckMulti(Card[] cards, long[] originalDids) {
            super(Collection.DismissType.CHANGE_DECK_MULTI);
            this.cards = cards;
            this.originalDids = originalDids;
        }


        public long undo(Collection col) {
            Timber.i("Undo: Change Decks");
            // move cards to original deck
            for (int i = 0; i < cards.length; i++) {
                Card card = cards[i];
                card.load();
                card.setDid(originalDids[i]);
                Note note = card.note();
                note.flush();
                card.flush();
            }
            return MULTI_CARD;  // don't fetch new card

        }
    }

    public static class ChangeDeckMulti extends DismissMulti {
        private long mNewDid;
        public ChangeDeckMulti(long[] cardIds, long newDid) {
            super(cardIds);
            mNewDid = newDid;
        }


        public TaskData actualBackground(CollectionTask task, Card[] cards) {
            Collection col = task.getCol();

            Timber.i("Changing %d cards to deck: '%d'", cards.length, mNewDid);
            Deck deckData = col.getDecks().get(mNewDid);

            if (Decks.isDynamic(deckData)) {
                //#5932 - can't change to a dynamic deck. Use "Rebuild"
                Timber.w("Attempted to move to dynamic deck. Cancelling task.");
                return new TaskData(false);
            }

            //Confirm that the deck exists (and is not the default)
            try {
                long actualId = deckData.getLong("id");
                if (actualId != mNewDid) {
                    Timber.w("Attempted to move to deck %d, but got %d", mNewDid, actualId);
                    return new TaskData(false);
                }
            } catch (Exception e) {
                Timber.e(e, "failed to check deck");
                return new TaskData(false);
            }

            long[] changedCardIds = new long[cards.length];
            for (int i = 0; i < cards.length; i++) {
                changedCardIds[i] = cards[i].getId();
            }
            col.getSched().remFromDyn(changedCardIds);

            long[] originalDids = new long[cards.length];

            for (int i = 0; i < cards.length; i++) {
                Card card = cards[i];
                card.load();
                // save original did for undo
                originalDids[i] = card.getDid();
                // then set the card ID to the new deck
                card.setDid(mNewDid);
                Note note = card.note();
                note.flush();
                // flush card too, in case, did has been changed
                card.flush();
            }

            // mark undo for all at once
            col.markUndo(new UndoChangeDeckMulti(cards, originalDids));
            return null;
        }
    }

    public abstract static class DismissMulti implements Task {
        private final long[] mCardIds;

        public DismissMulti(long[] cardIds) {
            mCardIds = cardIds;
        }

        public long[] getCardIds() {
            return mCardIds;
        }

        public abstract TaskData actualBackground(CollectionTask task, Card[] cards);

        public TaskData background(CollectionTask task) {
            Collection col = task.getCol();
            AbstractSched sched = col.getSched();
            // query cards
            Card[] cards = new Card[mCardIds.length];
            for (int i = 0; i < mCardIds.length; i++) {
                cards[i] = col.getCard(mCardIds[i]);
            }

            try {
                col.getDb().getDatabase().beginTransaction();
                try {
                    TaskData res = actualBackground(task, cards);
                    if (res != null) {
                        return res;
                    }
                    actualBackground(task, cards);
                    col.getDb().getDatabase().setTransactionSuccessful();
                } finally {
                    col.getDb().getDatabase().endTransaction();
                }
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSuspendCard");
                return new TaskData(false);
            }
            // pass cards back so more actions can be performed by the caller
            // (querying the cards again is unnecessarily expensive)
            return new TaskData(true, cards);
        }
    }

    private TaskData doInBackgroundUndo() {
        Collection col = getCol();
        AbstractSched sched = col.getSched();
        try {
            col.getDb().getDatabase().beginTransaction();
            Card newCard = null;
            try {
                long cid = col.undo();
                if (cid == NO_REVIEW) {
                    // /* card schedule change undone, reset and get
                    // new card */
                    Timber.d("Single card non-review change undo succeeded");
                    col.reset();
                    newCard = sched.getCard();
                } else if (cid == MULTI_CARD) {
                    /* multi-card action undone, no action to take here */
                    Timber.d("Multi-select undo succeeded");
                } else {
                    // cid is actually a card id.
                    // a review was undone,
                    /* card review undone, set up to review that card again */
                    Timber.d("Single card review undo succeeded");
                    newCard = col.getCard(cid);
                    newCard.startTimer();
                    col.reset();
                    sched.deferReset(newCard);
                    col.getSched().setCurrentCard(newCard);
                }
                // TODO: handle leech undoing properly
                doProgress(new TaskData(newCard, 0));
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


    private TaskData doInBackgroundSearchCards(TaskData param) {
        Timber.d("doInBackgroundSearchCards");
        Collection col = getCol();
        String query = (String) param.getObjArray()[0];
        Boolean order = (Boolean) param.getObjArray()[1];
        int numCardsToRender = (int) param.getObjArray()[2];
        if (isCancelled()) {
            Timber.d("doInBackgroundSearchCards was cancelled so return null");
            return null;
        }
        int column1Index = (Integer) param.getObjArray()[3];
        int column2Index = (Integer) param.getObjArray()[4];
        List<Long> searchResult_ = col.findCards(query, order, this);
        int resultSize = searchResult_.size();
        List<CardBrowser.CardCache> searchResult = new ArrayList<>(resultSize);
        Timber.d("The search found %d cards", resultSize);
        int position = 0;
        for (Long cid: searchResult_) {
            CardBrowser.CardCache card = new CardBrowser.CardCache(cid, col, position++);
            searchResult.add(card);
        }
        // Render the first few items
        for (int i = 0; i < Math.min(numCardsToRender, searchResult.size()); i++) {
            if (isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null");
                return null;
            }
            searchResult.get(i).load(false, column1Index, column2Index);
        }
        // Finish off the task
        if (isCancelled()) {
            Timber.d("doInBackgroundSearchCards was cancelled so return null");
            return null;
        } else {
            return new TaskData(searchResult);
        }
    }


    private TaskData doInBackgroundCheckDatabase() {
        Timber.d("doInBackgroundCheckDatabase");
        Collection col = getCol();
        // Don't proceed if collection closed
        if (col == null) {
            Timber.e("doInBackgroundCheckDatabase :: supplied collection was null");
            return new TaskData(false);
        }

        Collection.CheckDatabaseResult result = col.fixIntegrity(new ProgressCallback(this, AnkiDroidApp.getAppResources()));
        if (result.getFailed()) {
            //we can fail due to a locked database, which requires knowledge of the failure.
            return new TaskData(false, new Object[] { result });
        } else {
            // Close the collection and we restart the app to reload
            CollectionHelper.getInstance().closeCollection(true, "Check Database Completed");
            return new TaskData(true, new Object[] { result });
        }
    }


    private TaskData doInBackgroundRepairCollection() {
        Timber.d("doInBackgroundRepairCollection");
        Collection col = getCol();
        if (col != null) {
            Timber.i("RepairCollection: Closing collection");
            col.close(false);
        }
        return new TaskData(BackupManager.repairCollection(col));
    }


    public static final Task sRebuildCram = (collectionTask) -> {
        Timber.d("doInBackgroundRebuildCram");
        Collection col = collectionTask.getCol();
        col.getSched().rebuildDyn(col.getDecks().selected());
        return StudyOptionsFragment.updateValuesFromDeck(collectionTask, true);
    };


    public static final class EmptyCram implements Task {
        @Override
        public TaskData background(CollectionTask collectionTask) {
            Timber.d("doInBackgroundEmptyCram");
            Collection col = collectionTask.getCol();
            col.getSched().emptyDyn(col.getDecks().selected());
            return StudyOptionsFragment.updateValuesFromDeck(collectionTask, true);
        }
    };


    public TaskData doInBackgroundCode(TaskData param) {
        return param.getTask().background(this);
    }

    /**
     * Helper class for allowing inner function to publish progress of an AsyncTask.
     */
    public static class ProgressCallback {
        private Resources res;
        private CollectionTask task;


        public ProgressCallback(CollectionTask task, Resources res) {
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


        public void publishProgress(TaskData value) {
            if (task != null) {
                task.doProgress(value);
            }
        }
    }


    public void doProgress(TaskData value) {
        publishProgress(value);
    }
}
