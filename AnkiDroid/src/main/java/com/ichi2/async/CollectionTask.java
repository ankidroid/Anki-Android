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
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckOptions;
import com.ichi2.anki.R;
import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.anki.TemporaryModel;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Undoable;
import com.ichi2.libanki.Undoable.*;
import com.ichi2.libanki.WrongId;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.AnkiPackageExporter;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.importer.AnkiPackageImporter;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.compress.archivers.zip.ZipFile;

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
        ANSWER_CARD,
        UPDATE_NOTE,
        UNDO,
        DISMISS_MULTI,
        CHECK_DATABASE,
        REPAIR_COLLECTION,
        LOAD_DECK_COUNTS,
        REBUILD_CRAM,
        EMPTY_CRAM,
        SEARCH_CARDS,
        RENDER_BROWSER_QA,
        COUNT_MODELS,
        DELETE_MODEL,
        DELETE_FIELD,
        REPOSITION_FIELD,
        ADD_FIELD,
        CHANGE_SORT_FIELD,
        SAVE_MODEL,
        FIND_EMPTY_CARDS,
        CHECK_CARD_SELECTION,
        LOAD_COLLECTION_COMPLETE,
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
            case LOAD_DECK_COUNTS:
                return doInBackgroundLoadDeckCounts();

            case ANSWER_CARD:
                return doInBackgroundAnswerCard(param);

            case UPDATE_NOTE:
                return doInBackgroundUpdateNote(param);

            case UNDO:
                return doInBackgroundUndo();

            case SEARCH_CARDS:
                return doInBackgroundSearchCards(param);

            case DISMISS_MULTI:
                return doInBackgroundDismissNotes(param);

            case CHECK_DATABASE:
                return doInBackgroundCheckDatabase();

            case REPAIR_COLLECTION:
                return doInBackgroundRepairCollection();

            case REBUILD_CRAM:
                return doInBackgroundRebuildCram();

            case EMPTY_CRAM:
                return doInBackgroundEmptyCram();

            case RENDER_BROWSER_QA:
                return doInBackgroundRenderBrowserQA(param);

            case COUNT_MODELS:
                return doInBackgroundCountModels();

            case DELETE_MODEL:
                return doInBackGroundDeleteModel(param);

            case DELETE_FIELD:
                return doInBackGroundDeleteField(param);

            case REPOSITION_FIELD:
                return doInBackGroundRepositionField(param);

            case ADD_FIELD:
                return doInBackGroundAddField(param);

            case CHANGE_SORT_FIELD:
                return doInBackgroundChangeSortField(param);

            case SAVE_MODEL:
                return doInBackgroundSaveModel(param);

            case FIND_EMPTY_CARDS:
                return doInBackGroundFindEmptyCards(param);

            case CHECK_CARD_SELECTION:
                return doInBackgroundCheckCardSelection(param);

            case LOAD_COLLECTION_COMPLETE:
                doInBackgroundLoadCollectionComplete();
                break;

            default:
                return doInBackgroundCode(param);
        }
        return null;
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



    private TaskData doInBackgroundUpdateNote(TaskData param) {
        Timber.d("doInBackgroundUpdateNote");
        // Save the note
        Collection col = getCol();
        AbstractSched sched = col.getSched();
        Card editCard = param.getCard();
        Note editNote = editCard.note();
        boolean fromReviewer = param.getBoolean();

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
                        newCard = sched.getCard();
                    }
                    doProgress(new TaskData(newCard));
                } else {
                    doProgress(new TaskData(editCard, editNote.stringTags()));
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


    private TaskData doInBackgroundAnswerCard(TaskData param) {
        Collection col = getCol();
        AbstractSched sched = col.getSched();
        Card oldCard = param.getCard();
        @Consts.BUTTON_TYPE int ease = param.getInt();
        Card newCard = null;
        Timber.i(oldCard != null ? "Answering card" : "Obtaining card");
        try {
            DB db = col.getDb();
            db.getDatabase().beginTransaction();
            try {
                if (oldCard != null) {
                    Timber.i("Answering card %d", oldCard.getId());
                    sched.answerCard(oldCard, ease);
                }
                newCard = sched.getCard();
                if (newCard != null) {
                    // render cards before locking database
                    newCard._getQA(true);
                }
                doProgress(new TaskData(newCard));
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


    private TaskData doInBackgroundLoadDeckCounts() {
        Timber.d("doInBackgroundLoadDeckCounts");
        Collection col = getCol();
        try {
            // Get due tree
            Object[] o = new Object[] {col.getSched().deckDueTree(this)};
            return new TaskData(o);
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundLoadDeckCounts - error");
            return null;
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

    private static class UndoSuspendCardMulti extends Undoable {
        private final Card[] cards;
        private final boolean[] originalSuspended;


        public UndoSuspendCardMulti(Card[] cards, boolean[] originalSuspended) {
            super(Collection.DismissType.SUSPEND_CARD_MULTI);
            this.cards = cards;
            this.originalSuspended = originalSuspended;
        }


        public long undo(Collection col) {
            Timber.i("Undo: Suspend multiple cards");
            List<Long> toSuspendIds = new ArrayList<>();
            List<Long> toUnsuspendIds = new ArrayList<>();
            for (int i = 0; i < cards.length; i++) {
                Card card = cards[i];
                if (originalSuspended[i]) {
                    toSuspendIds.add(card.getId());
                } else {
                    toUnsuspendIds.add(card.getId());
                }
            }

            // unboxing
            long[] toSuspendIdsArray = new long[toSuspendIds.size()];
            long[] toUnsuspendIdsArray = new long[toUnsuspendIds.size()];
            for (int i = 0; i < toSuspendIds.size(); i++) {
                toSuspendIdsArray[i] = toSuspendIds.get(i);
            }
            for (int i = 0; i < toUnsuspendIds.size(); i++) {
                toUnsuspendIdsArray[i] = toUnsuspendIds.get(i);
            }

            col.getSched().suspendCards(toSuspendIdsArray);
            col.getSched().unsuspendCards(toUnsuspendIdsArray);

            return MULTI_CARD;  // don't fetch new card

        }
    }


    private static class UndoDeleteNoteMulti extends Undoable {
        private final Note[] notesArr;
        private final List<Card> allCards;


        public UndoDeleteNoteMulti(Note[] notesArr, List<Card> allCards) {
            super(Collection.DismissType.DELETE_NOTE_MULTI);
            this.notesArr = notesArr;
            this.allCards = allCards;
        }


        public long undo(Collection col) {
            Timber.i("Undo: Delete notes");
            // undo all of these at once instead of one-by-one
            ArrayList<Long> ids = new ArrayList<>();
            for (Note n : notesArr) {
                n.flush(n.getMod(), false);
                ids.add(n.getId());
            }
            for (Card c : allCards) {
                c.flush(false);
                ids.add(c.getId());
            }
            col.getDb().execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(Utils.collection2Array(ids)));
            return MULTI_CARD;  // don't fetch new card

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

    private static class UndoMarkNoteMulti extends Undoable {
        private final List<Note> originalMarked;
        private final List<Note> originalUnmarked;


        public UndoMarkNoteMulti(List<Note> originalMarked, List<Note> originalUnmarked) {
            super(Collection.DismissType.MARK_NOTE_MULTI);
            this.originalMarked = originalMarked;
            this.originalUnmarked = originalUnmarked;
        }


        public long undo(Collection col) {
            Timber.i("Undo: Mark notes");
            CardUtils.markAll(originalMarked, true);
            CardUtils.markAll(originalUnmarked, false);
            return MULTI_CARD;  // don't fetch new card
        }
    }


    private static class UndoRepositionRescheduleResetCards extends Undoable {
        private final Card[] cards_copied;


        public UndoRepositionRescheduleResetCards(Collection.DismissType type, Card[] cards_copied) {
            super(type);
            this.cards_copied = cards_copied;
        }


        public long undo(Collection col) {
            Timber.i("Undoing action of type %s on %d cards", getDismissType(), cards_copied.length);
            for (int i = 0; i < cards_copied.length; i++) {
                Card card = cards_copied[i];
                card.flush(false);
            }
            return NO_REVIEW;
        }
    }

    private TaskData doInBackgroundDismissNotes(TaskData param) {
        Collection col = getCol();
        AbstractSched sched = col.getSched();
        Object[] data = param.getObjArray();
        long[] cardIds = (long[]) data[0];
        // query cards
        Card[] cards = new Card[cardIds.length];
        for (int i = 0; i < cardIds.length; i++) {
            cards[i] = col.getCard(cardIds[i]);
        }

        Collection.DismissType type = (Collection.DismissType) data[1];
        try {
            col.getDb().getDatabase().beginTransaction();
            try {
                switch (type) {
                    case SUSPEND_CARD_MULTI: {
                        // collect undo information
                        long[] cids = new long[cards.length];
                        boolean[] originalSuspended = new boolean[cards.length];
                        boolean hasUnsuspended = false;
                        for (int i = 0; i < cards.length; i++) {
                            Card card = cards[i];
                            cids[i] = card.getId();
                            if (card.getQueue() != Consts.QUEUE_TYPE_SUSPENDED) {
                                hasUnsuspended = true;
                                originalSuspended[i] = false;
                            } else {
                                originalSuspended[i] = true;
                            }
                        }

                        // if at least one card is unsuspended -> suspend all
                        // otherwise unsuspend all
                        if (hasUnsuspended) {
                            sched.suspendCards(cids);
                        } else {
                            sched.unsuspendCards(cids);
                        }

                        Undoable suspendCardMulti = new UndoSuspendCardMulti(cards, originalSuspended);
                        // mark undo for all at once
                        col.markUndo(suspendCardMulti);

                        // reload cards because they'll be passed back to caller
                        for (Card c : cards) {
                            c.load();
                        }

                        sched.deferReset();
                        break;
                    }

                    case FLAG: {
                        int flag = (Integer) data[2];
                        col.setUserFlag(flag, cardIds);
                        for (Card c : cards) {
                            c.load();
                        }
                        break;
                    }

                    case MARK_NOTE_MULTI: {
                        Set<Note> notes = CardUtils.getNotes(Arrays.asList(cards));
                        // collect undo information
                        List<Note> originalMarked = new ArrayList<>();
                        List<Note> originalUnmarked = new ArrayList<>();

                        for (Note n : notes) {
                            if (n.hasTag("marked"))
                                originalMarked.add(n);
                            else
                                originalUnmarked.add(n);
                        }

                        CardUtils.markAll(new ArrayList<>(notes), !originalUnmarked.isEmpty());

                        Undoable markNoteMulti = new UndoMarkNoteMulti(originalMarked, originalUnmarked);
                        // mark undo for all at once
                        col.markUndo(markNoteMulti);

                        // reload cards because they'll be passed back to caller
                        for (Card c : cards) {
                            c.load();
                        }

                        break;
                    }

                    case DELETE_NOTE_MULTI: {
                        // list of all ids to pass to remNotes method.
                        // Need Set (-> unique) so we don't pass duplicates to col.remNotes()
                        Set<Note> notes = CardUtils.getNotes(Arrays.asList(cards));
                        List<Card> allCards = CardUtils.getAllCards(notes);
                        // delete note
                        long[] uniqueNoteIds = new long[notes.size()];
                        Note[] notesArr = notes.toArray(new Note[notes.size()]);
                        int count = 0;
                        for (Note note : notes) {
                            uniqueNoteIds[count] = note.getId();
                            count++;
                        }


                        Undoable deleteNoteMulti = new UndoDeleteNoteMulti(notesArr, allCards);

                        col.markUndo(deleteNoteMulti);

                        col.remNotes(uniqueNoteIds);
                        sched.deferReset();
                        // pass back all cards because they can't be retrieved anymore by the caller (since the note is deleted)
                        doProgress(new TaskData(allCards.toArray(new Card[allCards.size()])));
                        break;
                    }

                    case CHANGE_DECK_MULTI: {
                        long newDid = (long) data[2];

                        Timber.i("Changing %d cards to deck: '%d'", cards.length, newDid);
                        Deck deckData = col.getDecks().get(newDid);

                        if (Decks.isDynamic(deckData)) {
                            //#5932 - can't change to a dynamic deck. Use "Rebuild"
                            Timber.w("Attempted to move to dynamic deck. Cancelling task.");
                            return new TaskData(false);
                        }

                        //Confirm that the deck exists (and is not the default)
                        try {
                            long actualId = deckData.getLong("id");
                            if (actualId != newDid) {
                                Timber.w("Attempted to move to deck %d, but got %d", newDid, actualId);
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
                            card.setDid(newDid);
                            Note note = card.note();
                            note.flush();
                            // flush card too, in case, did has been changed
                            card.flush();
                        }

                        Undoable changeDeckMulti = new UndoChangeDeckMulti(cards, originalDids);
                        // mark undo for all at once
                        col.markUndo(changeDeckMulti);
                        break;
                    }

                    case RESCHEDULE_CARDS:
                    case REPOSITION_CARDS:
                    case RESET_CARDS: {
                        // collect undo information, sensitive to memory pressure, same for all 3 cases
                        try {
                            Timber.d("Saving undo information of type %s on %d cards", type, cards.length);
                            Card[] cards_copied = deepCopyCardArray(cards);
                            Undoable repositionRescheduleResetCards = new UndoRepositionRescheduleResetCards(type, cards_copied);
                            col.markUndo(repositionRescheduleResetCards);
                        } catch (CancellationException ce) {
                            Timber.i(ce, "Cancelled while handling type %s, skipping undo", type);
                        }
                        switch (type) {
                            case RESCHEDULE_CARDS:
                                sched.reschedCards(cardIds, (Integer) data[2], (Integer) data[2]);
                                break;
                            case REPOSITION_CARDS:
                                sched.sortCards(cardIds, (Integer) data[2], 1, false, true);
                                break;
                            case RESET_CARDS:
                                sched.forgetCards(cardIds);
                                break;
                        }
                        // In all cases schedule a new card so Reviewer doesn't sit on the old one
                        col.reset();
                        doProgress(new TaskData(sched.getCard(), 0));
                        break;
                    }
                }
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

    private Card[] deepCopyCardArray(Card[] originals) throws CancellationException {
        Collection col = CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance());
        Card[] copies = new Card[originals.length];
        for (int i = 0; i < originals.length; i++) {
            if (isCancelled()) {
                Timber.i("Cancelled during deep copy, probably memory pressure?");
                throw new CancellationException("Cancelled during deep copy");
            }

            // TODO: the performance-naive implementation loads from database instead of working in memory
            // the high performance version would implement .clone() on Card and test it well
            copies[i] = new Card(col, originals[i].getId());
        }
        return copies;
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


    private TaskData doInBackgroundRenderBrowserQA(TaskData param) {
        //TODO: Convert this to accept the following to make thread-safe:
        //(Range<Position>, Function<Position, BrowserCard>)
        Timber.d("doInBackgroundRenderBrowserQA");
        Collection col = getCol();
        List<CardBrowser.CardCache> cards = (List<CardBrowser.CardCache>) param.getObjArray()[0];
        Integer startPos = (Integer) param.getObjArray()[1];
        Integer n = (Integer) param.getObjArray()[2];
        int column1Index = (Integer) param.getObjArray()[3];
        int column2Index = (Integer) param.getObjArray()[4];

        List<Long> invalidCardIds = new ArrayList<>();
        // for each specified card in the browser list
        for (int i = startPos; i < startPos + n; i++) {
            // Stop if cancelled
            if (isCancelled()) {
                Timber.d("doInBackgroundRenderBrowserQA was aborted");
                return null;
            }
            if (i < 0 || i >= cards.size()) {
                continue;
            }
            CardBrowser.CardCache card;
            try {
                card = cards.get(i);
            }
            catch (IndexOutOfBoundsException e) {
                //even though we test against card.size() above, there's still a race condition
                //We might be able to optimise this to return here. Logically if we're past the end of the collection,
                //we won't reach any more cards.
                continue;
            }
            if (card.isLoaded()) {
                //We've already rendered the answer, we don't need to do it again.
                continue;
            }
            // Extract card item
            try {
                // Ensure that card still exists.
                card.getCard();
            } catch (WrongId e) {
                //#5891 - card can be inconsistent between the deck browser screen and the collection.
                //Realistically, we can skip any exception as it's a rendering task which should not kill the
                //process
                long cardId = card.getId();
                Timber.e(e, "Could not process card '%d' - skipping and removing from sight", cardId);
                invalidCardIds.add(cardId);
                continue;
            }
            // Update item
            card.load(false, column1Index, column2Index);
            float progress = (float) i / n * 100;
            doProgress(new TaskData((int) progress));
        }
        return new TaskData(new Object[] { cards, invalidCardIds });
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


    private TaskData doInBackgroundRebuildCram() {
        Timber.d("doInBackgroundRebuildCram");
        Collection col = getCol();
        col.getSched().rebuildDyn(col.getDecks().selected());
        return StudyOptionsFragment.updateValuesFromDeck(this, true);
    }


    private TaskData doInBackgroundEmptyCram() {
        Timber.d("doInBackgroundEmptyCram");
        Collection col = getCol();
        col.getSched().emptyDyn(col.getDecks().selected());
        return StudyOptionsFragment.updateValuesFromDeck(this, true);
    }



    /**
     * Handles everything for a model change at once - template add / deletes as well as content updates
     */
    private TaskData doInBackgroundSaveModel(TaskData param) {
        Timber.d("doInBackgroundSaveModel");
        Collection col = getCol();
        Object [] args = param.getObjArray();
        Model model = (Model) args[0];
        ArrayList<Object[]> templateChanges = (ArrayList<Object[]>)args[1];
        Model oldModel = col.getModels().get(model.getLong("id"));

        // TODO need to save all the cards that will go away, for undo
        //  (do I need to remove them from graves during undo also?)
        //    - undo (except for cards) could just be Models.update(model) / Models.flush() / Collection.reset() (that was prior "undo")
        JSONArray newTemplates = model.getJSONArray("tmpls");

        col.getDb().getDatabase().beginTransaction();

        try {
            for (Object[] change : templateChanges) {
                JSONArray oldTemplates = oldModel.getJSONArray("tmpls");
                switch ((TemporaryModel.ChangeType) change[1]) {
                    case ADD:
                        Timber.d("doInBackgroundSaveModel() adding template %s", change[0]);
                        try {
                            col.getModels().addTemplate(oldModel, newTemplates.getJSONObject((int) change[0]));
                        } catch (Exception e) {
                            Timber.e(e, "Unable to add template %s to model %s", change[0], model.getLong("id"));
                            return new TaskData(e.getLocalizedMessage(), false);
                        }
                        break;
                    case DELETE:
                        Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0]);
                        try {
                            col.getModels().remTemplate(oldModel, oldTemplates.getJSONObject((int) change[0]));
                        } catch (Exception e) {
                            Timber.e(e, "Unable to delete template %s from model %s", change[0], model.getLong("id"));
                            return new TaskData(e.getLocalizedMessage(), false);
                        }
                        break;
                    default:
                        Timber.w("Unknown change type? %s", change[1]);
                        break;
                }
            }

            col.getModels().save(model, true);
            col.getModels().update(model);
            col.reset();
            col.save();
            if (col.getDb().getDatabase().inTransaction()) {
                col.getDb().getDatabase().setTransactionSuccessful();
            } else {
                Timber.i("CollectionTask::SaveModel was not in a transaction? Cannot mark transaction successful.");
            }
        } finally {
            if (col.getDb().getDatabase().inTransaction()) {
                col.getDb().getDatabase().endTransaction();
            } else {
                Timber.i("CollectionTask::SaveModel was not in a transaction? Cannot end transaction.");
            }
        }
        return new TaskData(true);
    }


    /*
     * Async task for the ModelBrowser Class
     * Returns an ArrayList of all models alphabetically ordered and the number of notes
     * associated with each model.
     *
     * @return {ArrayList<JSONObject> models, ArrayList<Integer> cardCount}
     */
    private TaskData doInBackgroundCountModels(){
        Timber.d("doInBackgroundLoadModels");
        Collection col = getCol();

        ArrayList<Model> models = col.getModels().all();
        ArrayList<Integer> cardCount = new ArrayList<>();
        Collections.sort(models, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return a.getString("name").compareTo(b.getString("name"));
            }
        });

        for (Model n : models) {
            if (isCancelled()) {
                Timber.e("doInBackgroundLoadModels :: Cancelled");
                // onPostExecute not executed if cancelled. Return value not used.
                return new TaskData(false);
            }
            cardCount.add(col.getModels().useCount(n));
        }

        Object[] data = new Object[2];
        data[0] = models;
        data[1] = cardCount;
        return (new TaskData(data, true));
    }


    /**
     * Deletes the given model (stored in the long field of TaskData)
     * and all notes associated with it
     */
    private TaskData doInBackGroundDeleteModel(TaskData param){
        Timber.d("doInBackGroundDeleteModel");
        long modID = param.getLong();
        Collection col = getCol();
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
    private TaskData doInBackGroundDeleteField(TaskData param){
        Timber.d("doInBackGroundDeleteField");
        Object[] objects = param.getObjArray();

        Model model = (Model) objects[0];
        JSONObject field = (JSONObject) objects[1];


        Collection col = getCol();
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
    private TaskData doInBackGroundRepositionField(TaskData param){
        Timber.d("doInBackgroundRepositionField");
        Object[] objects = param.getObjArray();

        Model model = (Model) objects[0];
        JSONObject field = (JSONObject) objects[1];
        int index = (Integer) objects[2];


        Collection col = getCol();
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
     * Adds a field with name in given model
     */
    private TaskData doInBackGroundAddField(TaskData param){
        Timber.d("doInBackgroundRepositionField");
        Object[] objects = param.getObjArray();

        Model model = (Model) objects[0];
        String fieldName = (String) objects[1];

        Collection col = getCol();
        col.getModels().addFieldModChanged(model, col.getModels().newField(fieldName));
        col.save();
        return new TaskData(true);
    }

    /**
     * Adds a field of with name in given model
     */
    private TaskData doInBackgroundChangeSortField(TaskData param){
        try {
            Timber.d("doInBackgroundChangeSortField");
            Object[] objects = param.getObjArray();

            Model model = (Model) objects[0];
            int idx = (int) objects[1];

            Collection col = getCol();
            col.getModels().setSortIdx(model, idx);
            col.save();
        } catch(Exception e){
            Timber.e(e, "Error changing sort field");
            return new TaskData(false);
        }
        return new TaskData(true);
    }

    public TaskData doInBackGroundFindEmptyCards(TaskData param) {
        Collection col = getCol();
        List<Long> cids = col.emptyCids();
        return new TaskData(new Object[] { cids});
    }

    /**
     * Goes through selected cards and checks selected and marked attribute
     * @return If there are unselected cards, if there are unmarked cards
     */
    public TaskData doInBackgroundCheckCardSelection(TaskData param) {
        Object[] objects = param.getObjArray();
        Set<CardBrowser.CardCache> checkedCards = (Set<CardBrowser.CardCache>) objects[0];

        boolean hasUnsuspended = false;
        boolean hasUnmarked = false;
        for (CardBrowser.CardCache c: checkedCards) {
            Card card = c.getCard();
            hasUnsuspended = hasUnsuspended || card.getQueue() != Consts.QUEUE_TYPE_SUSPENDED;
            hasUnmarked = hasUnmarked || !card.note().hasTag("marked");
            if (hasUnsuspended && hasUnmarked)
                break;
        }

        return new TaskData(new Object[] { hasUnsuspended, hasUnmarked});
    }

    public void doInBackgroundLoadCollectionComplete() {
        Collection col = getCol();
        if (col != null) {
            CollectionHelper.loadCollectionComplete(col);
        }
    }

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
