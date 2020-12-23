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
import android.util.Pair;

import com.google.gson.stream.JsonReader;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.TemporaryModel;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Undoable;
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

import com.ichi2.libanki.sched.Counts;
import com.ichi2.libanki.sched.DeckDueTreeNode;
import com.ichi2.libanki.sched.DeckTreeNode;
import com.ichi2.libanki.utils.Time;
import com.ichi2.utils.BooleanGetter;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.PairWithBoolean;
import com.ichi2.utils.PairWithCard;
import com.ichi2.utils.SyncStatus;
import com.ichi2.utils.ThreadUtil;
import com.ichi2.utils.Triple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.apache.commons.compress.archivers.zip.ZipFile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

import static com.ichi2.async.TaskManager.setLatestInstance;
import static com.ichi2.libanki.Card.deepCopyCardArray;
import static com.ichi2.libanki.Collection.DismissType.BURY_CARD;
import static com.ichi2.libanki.Collection.DismissType.BURY_NOTE;
import static com.ichi2.libanki.Collection.DismissType.REPOSITION_CARDS;
import static com.ichi2.libanki.Collection.DismissType.RESCHEDULE_CARDS;
import static com.ichi2.libanki.Collection.DismissType.RESET_CARDS;
import static com.ichi2.libanki.Collection.DismissType.SUSPEND_NOTE;
import static com.ichi2.libanki.Consts.DECK_DYN;
import static com.ichi2.libanki.Undoable.*;
import static com.ichi2.utils.BooleanGetter.False;
import static com.ichi2.utils.BooleanGetter.True;
import static com.ichi2.utils.BooleanGetter.fromBoolean;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class CollectionTask<ProgressListener, ProgressBackground extends ProgressListener, ResultListener, ResultBackground extends ResultListener> extends BaseAsyncTask<Void, ProgressBackground, ResultBackground> {

    public abstract static class Task<ProgressBackground, ResultBackground> {
        protected abstract ResultBackground task(Collection col, ProgressSenderAndCancelListener<ProgressBackground> collectionTask);
    }

    /**
     * A reference to the application context to use to fetch the current Collection object.
     */
    private Context mContext;

    /**
     * Block the current thread until all CollectionTasks have finished.
     * @param timeoutSeconds timeout in seconds
     * @return whether all tasks exited successfully
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean waitForAllToFinish(Integer timeoutSeconds) {
        // HACK: This should be better - there is currently a race condition in sLatestInstance, and no means to obtain this information.
        // This should work in all reasonable cases given how few tasks we have concurrently blocking.
        boolean result;
        result = TaskManager.waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        result &= TaskManager.waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        result &= TaskManager.waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        result &= TaskManager.waitToFinish(timeoutSeconds / 4);
        ThreadUtil.sleep(10);
        Timber.i("Waited for all tasks to finish");
        return result;
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
            TaskManager.removeTask(this);
        }
        return false;
    }

    private Collection getCol() {
        return CollectionHelper.getInstance().getCol(mContext);
    }

    protected Context getContext() {
        return mContext;
    }

    private final Task<ProgressBackground, ResultBackground> mTask;
    public Task<ProgressBackground, ResultBackground> getTask() {
        return mTask;
    }
    private final TaskListener<ProgressListener, ResultListener> mListener;
    private CollectionTask mPreviousTask;


    protected CollectionTask(Task<ProgressBackground, ResultBackground> task, TaskListener<ProgressListener, ResultListener> listener, CollectionTask previousTask) {
        mTask = task;
        mListener = listener;
        mPreviousTask = previousTask;
        TaskManager.addTasks(this);
    }

    @Override
    protected ResultBackground doInBackground(Void... params) {
        try {
            return actualDoInBackground();
        } finally {
            TaskManager.removeTask(this);
        }
    }

    // This method and those that are called here are executed in a new thread
    protected ResultBackground actualDoInBackground() {
        super.doInBackground();
        // Wait for previous thread (if any) to finish before continuing
        if (mPreviousTask != null && mPreviousTask.getStatus() != AsyncTask.Status.FINISHED) {
            Timber.d("Waiting for %s to finish before starting %s", mPreviousTask.mTask, mTask.getClass());
            try {
                mPreviousTask.get();
                Timber.d("Finished waiting for %s to finish. Status= %s", mPreviousTask.mTask, mPreviousTask.getStatus());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // We have been interrupted, return immediately.
                Timber.d(e, "interrupted while waiting for previous task: %s", mPreviousTask.mTask.getClass());
                return null;
            } catch (ExecutionException e) {
                // Ignore failures in the previous task.
                Timber.e(e, "previously running task failed with exception: %s", mPreviousTask.mTask.getClass());
            } catch (CancellationException e) {
                // Ignore cancellation of previous task
                Timber.d(e, "previously running task was cancelled: %s", mPreviousTask.mTask.getClass());
            }
        }
        setLatestInstance(this);
        mContext = AnkiDroidApp.getInstance().getApplicationContext();

        // Skip the task if the collection cannot be opened
        if ( mTask.getClass() != RepairCollectionn.class && mTask.getClass() != ImportReplace.class && CollectionHelper.getInstance().getColSafe(mContext) == null) {
            Timber.e("CollectionTask CollectionTask %s as Collection could not be opened", mTask.getClass());
            return null;
        }
        // Actually execute the task now that we are at the front of the queue.
        return mTask.task(getCol(), this);
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
    protected void onProgressUpdate(ProgressBackground... values) {
        super.onProgressUpdate(values);
        if (mListener != null) {
            mListener.onProgressUpdate(values[0]);
        }
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onPostExecute(ResultBackground result) {
        super.onPostExecute(result);
        if (mListener != null) {
            mListener.onPostExecute(result);
        }
        Timber.d("enabling garbage collection of mPreviousTask...");
        mPreviousTask = null;
    }

    @Override
    protected void onCancelled(){
        TaskManager.removeTask(this);
        if (mListener != null) {
            mListener.onCancelled();
        }
    }

    public static class AddNote extends Task<Integer, Boolean> {
        private final Note note;


        public AddNote(Note note) {
            this.note = note;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Integer> collectionTask) {
            Timber.d("doInBackgroundAddNote");
            try {
                DB db = col.getDb();
                db.executeInTransaction(() -> {
                        int value = col.addNote(note);
                        collectionTask.doProgress(value);
                    });
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundAddNote - RuntimeException on adding note");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundAddNote");
                return false;
            }
            return true;
        }
    }


    public static class UpdateNote extends Task<PairWithCard<String>, BooleanGetter> {
        private final Card editCard;
        private final boolean fromReviewer;
        private final boolean canAccessScheduler;


        public UpdateNote(Card editCard, boolean fromReviewer, boolean canAccessScheduler) {
            this.editCard = editCard;
            this.fromReviewer = fromReviewer;
            this.canAccessScheduler = canAccessScheduler;
        }

        protected BooleanGetter task(Collection col, ProgressSenderAndCancelListener<PairWithCard<String>> collectionTask) {
            Timber.d("doInBackgroundUpdateNote");
            // Save the note
            AbstractSched sched = col.getSched();
            Note editNote = editCard.note();

            try {
                col.getDb().executeInTransaction(() -> {
                    // TODO: undo integration
                    editNote.flush();
                    // flush card too, in case, did has been changed
                    editCard.flush();
                    if (fromReviewer) {
                        Card newCard;
                        if (col.getDecks().active().contains(editCard.getDid()) || !canAccessScheduler) {
                            newCard = editCard;
                            newCard.load();
                            // reload qa-cache
                            newCard.q(true);
                        } else {
                            newCard = sched.getCard();
                        }
                        collectionTask.doProgress(new PairWithCard<>(newCard, null)); // check: are there deleted too?
                    } else {
                        collectionTask.doProgress(new PairWithCard<>(editCard, editNote.stringTags()));
                    }
                });
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUpdateNote - RuntimeException on updating note");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUpdateNote");
                return False;
            }
            return True;
        }

        public boolean isFromReviewer() {
            return fromReviewer;
        }
    }

    public static class GetCard extends Task<Card, BooleanGetter> {
        protected BooleanGetter task(Collection col, ProgressSenderAndCancelListener<Card> collectionTask) {
            AbstractSched sched = col.getSched();
            Timber.i("Obtaining card");
            Card newCard = sched.getCard();
            if (newCard != null) {
                // render cards before locking database
                newCard._getQA(true);
            }
            collectionTask.doProgress(newCard);
            return True;
        }
    }

    public static class AnswerAndGetCard extends GetCard {
        private final @NonNull Card oldCard;
        private final @Consts.BUTTON_TYPE int ease;
        public AnswerAndGetCard(@NonNull Card oldCard, @Consts.BUTTON_TYPE int ease) {
            this.oldCard = oldCard;
            this.ease = ease;
        }

        protected BooleanGetter task(Collection col, ProgressSenderAndCancelListener<Card> collectionTask) {
            Timber.i("Answering card %d", oldCard.getId());
            col.getSched().answerCard(oldCard, ease);
            return super.task(col, collectionTask);
        }
    }


    public static class LoadDeck extends Task<Void, List<DeckTreeNode>> {
        protected List<DeckTreeNode> task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundLoadDeckCounts");
            try {
                // Get due tree
                return col.getSched().quickDeckDueTree();
            } catch (RuntimeException e) {
                Timber.w(e, "doInBackgroundLoadDeckCounts - error");
                return null;
            }
        }
    }


    public static class LoadDeckCounts extends Task<Void, List<DeckDueTreeNode>> {
        protected List<DeckDueTreeNode> task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundLoadDeckCounts");
            try {
                // Get due tree
                return col.getSched().deckDueTree(collectionTask);
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundLoadDeckCounts - error");
                return null;
            }
        }
    }

    public static class SaveCollection extends Task<Void, Void> {
        private final boolean syncIgnoresDatabaseModification;


        public SaveCollection(boolean syncIgnoresDatabaseModification) {
            this.syncIgnoresDatabaseModification = syncIgnoresDatabaseModification;
        }


        protected Void task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundSaveCollection");
            if (col != null) {
                try {
                    if (syncIgnoresDatabaseModification) {
                        SyncStatus.ignoreDatabaseModification(col::save);
                    } else {
                        col.save();
                    }
                } catch (RuntimeException e) {
                    Timber.e(e, "Error on saving deck in background");
                }
            }
            return null;
        }
    }



    private static class UndoSuspendCard extends Undoable {
        private final Card suspendedCard;


        public UndoSuspendCard(Card suspendedCard) {
            super(Collection.DismissType.SUSPEND_CARD);
            this.suspendedCard = suspendedCard;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("UNDO: Suspend Card %d", suspendedCard.getId());
            suspendedCard.flush(false);
            return suspendedCard;
        }
    }


    private static class UndoDeleteNote extends Undoable {
        private final Note note;
        private final ArrayList<Card> allCs;
        private final @NonNull Card card;


        public UndoDeleteNote(Note note, ArrayList<Card> allCs, @NonNull Card card) {
            super(Collection.DismissType.DELETE_NOTE);
            this.note = note;
            this.allCs = allCs;
            this.card = card;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Delete note");
            ArrayList<Long> ids = new ArrayList<>(allCs.size() + 1 );
            note.flush(note.getMod(), false);
            ids.add(note.getId());
            for (Card c : allCs) {
                c.flush(false);
                ids.add(c.getId());
            }
            col.getDb().execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(ids));
            return card;
        }
    }


    public static class DismissNote extends Task<Card, BooleanGetter> {
        private final Card card;
        private final Collection.DismissType type;


        public DismissNote(Card card, Collection.DismissType type) {
            this.card = card;
            this.type = type;
        }


        protected BooleanGetter task(Collection col, ProgressSenderAndCancelListener<Card> collectionTask) {
            AbstractSched sched = col.getSched();
            Note note = card.note();
            try {
                col.getDb().executeInTransaction(() -> {
                    sched.deferReset();
                    switch (type) {
                        case BURY_CARD:
                            // collect undo information
                            col.markUndo(revertToProvidedState(BURY_CARD, card));
                            // then bury
                            sched.buryCards(new long[] {card.getId()});
                            break;
                        case BURY_NOTE:
                            // collect undo information
                            col.markUndo(revertToProvidedState(BURY_NOTE, card));
                            // then bury
                            sched.buryNote(note.getId());
                            break;
                        case SUSPEND_CARD:
                            // collect undo information
                            Card suspendedCard = card.clone();
                            col.markUndo(new UndoSuspendCard(suspendedCard));
                            // suspend card
                            if (card.getQueue() == Consts.QUEUE_TYPE_SUSPENDED) {
                                sched.unsuspendCards(new long[] {card.getId()});
                            } else {
                                sched.suspendCards(new long[] {card.getId()});
                            }
                            break;
                        case SUSPEND_NOTE: {
                            // collect undo information
                            ArrayList<Card> cards = note.cards();
                            long[] cids = new long[cards.size()];
                            for (int i = 0; i < cards.size(); i++) {
                                cids[i] = cards.get(i).getId();
                            }
                            col.markUndo(revertToProvidedState(SUSPEND_NOTE, card));
                            // suspend note
                            sched.suspendCards(cids);
                            break;
                        }

                        case DELETE_NOTE: {
                            // collect undo information
                            ArrayList<Card> allCs = note.cards();
                            col.markUndo(new UndoDeleteNote(note, allCs, card));
                            // delete note
                            col.remNotes(new long[] {note.getId()});
                            break;
                        }
                    }
                    // With sHadCardQueue set, getCard() resets the scheduler prior to getting the next card
                    collectionTask.doProgress(col.getSched().getCard());
                });
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundDismissNote - RuntimeException on dismissing note, dismiss type %s", type);
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundDismissNote");
                return False;
            }
            return True;
        }
    }


    protected static class UndoSuspendCardMulti extends Undoable {
        private final Card[] cards;
        private final boolean[] originalSuspended;

        /** @param hasUnsuspended  whether there were any unsuspended card (in which card the action was "Suspend",
         *                          otherwise the action was "Unsuspend")  */
        public UndoSuspendCardMulti(Card[] cards, boolean[] originalSuspended,
                                    boolean hasUnsuspended) {
            super((hasUnsuspended) ? Collection.DismissType.SUSPEND_CARD_MULTI: Collection.DismissType.UNSUSPEND_CARD_MULTI);
            this.cards = cards;
            this.originalSuspended = originalSuspended;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Suspend multiple cards");
            int nbOfCards = cards.length;
            List<Long> toSuspendIds = new ArrayList<>(nbOfCards);
            List<Long> toUnsuspendIds = new ArrayList<>(nbOfCards);
            for (int i = 0; i < nbOfCards; i++) {
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

            return null;  // don't fetch new card

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


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Delete notes");
            // undo all of these at once instead of one-by-one
            ArrayList<Long> ids = new ArrayList<>(notesArr.length + allCards.size());
            for (Note n : notesArr) {
                n.flush(n.getMod(), false);
                ids.add(n.getId());
            }
            for (Card c : allCards) {
                c.flush(false);
                ids.add(c.getId());
            }
            col.getDb().execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(ids));
            return null;  // don't fetch new card

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


        public @Nullable Card undo(@NonNull Collection col) {
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
            return null;  // don't fetch new card

        }
    }

    private static class UndoMarkNoteMulti extends Undoable {
        private final List<Note> originalMarked;
        private final List<Note> originalUnmarked;

        /** @param hasUnmarked whether there were any unmarked card (in which card the action was "mark",
         *                      otherwise the action was "Unmark")  */
        public UndoMarkNoteMulti(List<Note> originalMarked, List<Note> originalUnmarked, boolean hasUnmarked) {
            super((hasUnmarked) ? Collection.DismissType.MARK_NOTE_MULTI : Collection.DismissType.UNMARK_NOTE_MULTI);
            this.originalMarked = originalMarked;
            this.originalUnmarked = originalUnmarked;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Mark notes");
            CardUtils.markAll(originalMarked, true);
            CardUtils.markAll(originalUnmarked, false);
            return null;  // don't fetch new card
        }
    }


    private static class UndoRepositionRescheduleResetCards extends Undoable {
        private final Card[] cards_copied;


        public UndoRepositionRescheduleResetCards(Collection.DismissType type, Card[] cards_copied) {
            super(type);
            this.cards_copied = cards_copied;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undoing action of type %s on %d cards", getDismissType(), cards_copied.length);
            for (Card card : cards_copied) {
                card.flush(false);
            }
            // /* card schedule change undone, reset and get
            // new card */
            Timber.d("Single card non-review change undo succeeded");
            col.reset();
            return col.getSched().getCard();
        }
    }

    private static abstract class DismissNotes<Progress> extends Task<Progress, PairWithBoolean<Card[]>> {
        protected final List<Long> mCardIds;

        public DismissNotes(List<Long> cardIds) {
            this.mCardIds = cardIds;
        }

        protected PairWithBoolean<Card[]> task(Collection col, ProgressSenderAndCancelListener<Progress> collectionTask) {
            // query cards
            Card[] cards = new Card[mCardIds.size()];
            for (int i = 0; i < mCardIds.size(); i++) {
                cards[i] = col.getCard(mCardIds.get(i));
            }

            try {
                col.getDb().getDatabase().beginTransaction();
                try {
                    PairWithBoolean<Card[]> ret = actualTask(col, collectionTask, cards);
                    if (ret != null) {
                        return ret;
                    }
                    col.getDb().getDatabase().setTransactionSuccessful();
                } finally {
                    DB.safeEndInTransaction(col.getDb());
                }
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSuspendCard");
                return new PairWithBoolean<>(false, null);
            }
            // pass cards back so more actions can be performed by the caller
            // (querying the cards again is unnecessarily expensive)
            return new PairWithBoolean<>(true, cards);
        }

        /**
         * @param col The collection
         * @param collectionTask, where to send progress and listen for cancellation
         * @param cards Cards to which the task should be applied
         * @return value to return, or null if `task` should deal with it directly.
         */
        protected abstract PairWithBoolean<Card[]> actualTask(Collection col, ProgressSenderAndCancelListener<Progress> collectionTask, Card[] cards);
    }

    public static class SuspendCardMulti extends DismissNotes<Void> {
        public SuspendCardMulti(List<Long> cardIds) {
            super(cardIds);
        }

        protected PairWithBoolean<Card[]> actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
            AbstractSched sched = col.getSched();
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

            // mark undo for all at once
            col.markUndo(new UndoSuspendCardMulti(cards, originalSuspended, hasUnsuspended));

            // reload cards because they'll be passed back to caller
            for (Card c : cards) {
                c.load();
            }

            sched.deferReset();
            return null;
        }
    }

    public static class Flag extends DismissNotes<Void> {
        private final int mFlag;

        public Flag(List<Long> cardIds, int flag) {
            super(cardIds);
            mFlag = flag;
        }

        protected PairWithBoolean<Card[]> actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
            col.setUserFlag(mFlag, mCardIds);
            for (Card c : cards) {
                c.load();
            }
            return null;
        }
    }

    public static class MarkNoteMulti extends DismissNotes<Void> {
        public MarkNoteMulti(List<Long> cardIds) {
            super(cardIds);
        }

        protected PairWithBoolean<Card[]> actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
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

            boolean hasUnmarked = !originalUnmarked.isEmpty();
            CardUtils.markAll(new ArrayList<>(notes), hasUnmarked);

            Undoable markNoteMulti = new UndoMarkNoteMulti(originalMarked, originalUnmarked, hasUnmarked);
            // mark undo for all at once
            col.markUndo(new UndoMarkNoteMulti(originalMarked, originalUnmarked, hasUnmarked));

            // reload cards because they'll be passed back to caller
            for (Card c : cards) {
                c.load();
            }
            return null;
        }
    }

    public static class DeleteNoteMulti extends DismissNotes<Card[]> {

        public DeleteNoteMulti(List<Long> cardIds) {
            super(cardIds);
        }

        protected PairWithBoolean<Card[]> actualTask(Collection col, ProgressSenderAndCancelListener<Card[]> collectionTask, Card[] cards) {
            AbstractSched sched = col.getSched();
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



            col.markUndo(new UndoDeleteNoteMulti(notesArr, allCards));

            col.remNotes(uniqueNoteIds);
            sched.deferReset();
            // pass back all cards because they can't be retrieved anymore by the caller (since the note is deleted)
            collectionTask.doProgress(allCards.toArray(new Card[allCards.size()]));
            return null;
        }
    }

    public static class ChangeDeckMulti extends DismissNotes<Void> {
        private final long mNewDid;
        public ChangeDeckMulti(List<Long> cardIds, long newDid) {
            super(cardIds);
            mNewDid = newDid;
        }

        protected PairWithBoolean<Card[]> actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
            Timber.i("Changing %d cards to deck: '%d'", cards.length, mNewDid);
            Deck deckData = col.getDecks().get(mNewDid);

            if (Decks.isDynamic(deckData)) {
                //#5932 - can't change to a dynamic deck. Use "Rebuild"
                Timber.w("Attempted to move to dynamic deck. Cancelling task.");
                return new PairWithBoolean<>(false, null);
            }

            //Confirm that the deck exists (and is not the default)
            try {
                long actualId = deckData.getLong("id");
                if (actualId != mNewDid) {
                    Timber.w("Attempted to move to deck %d, but got %d", mNewDid, actualId);
                    return new PairWithBoolean<>(false, null);
                }
            } catch (Exception e) {
                Timber.e(e, "failed to check deck");
                return new PairWithBoolean<>(false, null);
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

            Undoable changeDeckMulti = new UndoChangeDeckMulti(cards, originalDids);
            // mark undo for all at once
            col.markUndo(changeDeckMulti);
            return null;
        }
    }

    private abstract static class RescheduleRepositionReset extends DismissNotes<Card> {
        private final Collection.DismissType mType;
        public RescheduleRepositionReset(List<Long> cardIds, Collection.DismissType type) {
            super(cardIds);
            mType = type;
        }

        protected PairWithBoolean<Card[]> actualTask(Collection col, ProgressSenderAndCancelListener<Card> collectionTask, Card[] cards) {
            AbstractSched sched = col.getSched();
            // collect undo information, sensitive to memory pressure, same for all 3 cases
            try {
                Timber.d("Saving undo information of type %s on %d cards", mType, cards.length);
                Card[] cards_copied = deepCopyCardArray(cards, collectionTask);
                Undoable repositionRescheduleResetCards = new UndoRepositionRescheduleResetCards(mType, cards_copied);
                col.markUndo(repositionRescheduleResetCards);
            } catch (CancellationException ce) {
                Timber.i(ce, "Cancelled while handling type %s, skipping undo", mType);
            }
            actualActualTask(sched);
            // In all cases schedule a new card so Reviewer doesn't sit on the old one
            col.reset();
            collectionTask.doProgress(sched.getCard());
            return null;
        }

        protected abstract void actualActualTask(AbstractSched sched);
    }

    public static class RescheduleCards extends RescheduleRepositionReset {
        private final int mSchedule;
        public RescheduleCards(List<Long> cardIds, int schedule) {
            super(cardIds, RESCHEDULE_CARDS);
            this.mSchedule = schedule;
        }

        @Override
        protected void actualActualTask(AbstractSched sched) {
            sched.reschedCards(mCardIds, mSchedule, mSchedule);
        }
    }

    public static class RepositionCards extends RescheduleRepositionReset {
        private final int mPosition;
        public RepositionCards(List<Long> cardIds, int position) {
            super(cardIds, REPOSITION_CARDS);
            this.mPosition = position;
        }

        @Override
        protected void actualActualTask(AbstractSched sched) {
            sched.sortCards(mCardIds, mPosition, 1, false, true);
        }
    }

    public static class ResetCards extends RescheduleRepositionReset {
        public ResetCards(List<Long> cardIds) {
            super(cardIds, RESET_CARDS);
        }

        @Override
        protected void actualActualTask(AbstractSched sched) {
            sched.forgetCards(mCardIds);
        }
    }

    @VisibleForTesting
    public static Card nonTaskUndo(Collection col) {
        AbstractSched sched = col.getSched();
        Card card = col.undo();
        if (card == null) {
            /* multi-card action undone, no action to take here */
            Timber.d("Multi-select undo succeeded");
        } else {
            // cid is actually a card id.
            // a review was undone,
            /* card review undone, set up to review that card again */
            Timber.d("Single card review undo succeeded");
            card.startTimer();
            col.reset();
            sched.deferReset(card);
        }
        return card;
    }

    public static class Undo extends Task<Card, BooleanGetter> {
        protected BooleanGetter task(Collection col, ProgressSenderAndCancelListener<Card> collectionTask) {
            try {
                col.getDb().executeInTransaction(() -> {
                    Card card = nonTaskUndo(col);
                    collectionTask.doProgress(card);
                });
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUndo - RuntimeException on undoing");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUndo");
                return False;
            }
            return True;
        }
    }

    /**
     * A class allowing to send partial search result to the browser to display while the search ends
     */
    public static class PartialSearch implements ProgressSenderAndCancelListener<List<Long>> {
        private final List<CardBrowser.CardCache> mCards;
        private final int mColumn1Index, mColumn2Index;
        private final int mNumCardsToRender;
        private final ProgressSenderAndCancelListener<List<CardBrowser.CardCache>> mCollectionTask;
        private final Collection mCol;

        public PartialSearch(List<CardBrowser.CardCache> cards, int columnIndex1, int columnIndex2, int numCardsToRender, ProgressSenderAndCancelListener<List<CardBrowser.CardCache>> collectionTask, Collection col) {
            mCards = cards;
            mColumn1Index = columnIndex1;
            mColumn2Index = columnIndex2;
            mNumCardsToRender = numCardsToRender;
            mCollectionTask = collectionTask;
            mCol = col;
        }

        @Override
        public boolean isCancelled() {
            return mCollectionTask.isCancelled();
        }


        /**
         * @param cards Card ids to display in the browser. It is assumed that it is as least as long as mCards, and that
         *             mCards[i].cid = cards[i].  It add the cards in cards after `mPosition` to mCards
         */
        public void add(@NonNull List<Long> cards) {
            while (mCards.size() < cards.size()) {
                mCards.add(new CardBrowser.CardCache(cards.get(mCards.size()), mCol, mCards.size()));
            }
        }


        @Override
        public void doProgress(@NonNull List<Long> value) {
            add(value);
            for (CardBrowser.CardCache card : mCards) {
                if (isCancelled()) {
                    Timber.d("doInBackgroundSearchCards was cancelled so return");
                    return;
                }
                card.load(false, mColumn1Index, mColumn2Index);
            }
            mCollectionTask.doProgress(mCards);
        }

        public int getNumCardsToRender() {
            return mNumCardsToRender;
        }
    }


    public static class SearchCards extends Task<List<CardBrowser.CardCache>, List<CardBrowser.CardCache>> {
        private final String query;
        private final boolean order;
        private final int numCardsToRender;
        private final int column1Index;
        private final int column2Index;


        public SearchCards(String query, boolean order, int numCardsToRender, int column1Index, int column2Index) {
            this.query = query;
            this.order = order;
            this.numCardsToRender = numCardsToRender;
            this.column1Index = column1Index;
            this.column2Index = column2Index;
        }


        protected List<CardBrowser.CardCache> task(Collection col, ProgressSenderAndCancelListener<List<CardBrowser.CardCache>> collectionTask) {
            Timber.d("doInBackgroundSearchCards");
            if (collectionTask.isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null");
                return null;
            }
            List<CardBrowser.CardCache> searchResult = new ArrayList<>();
            List<Long> searchResult_ = col.findCards(query, order, new PartialSearch(searchResult, column1Index, column2Index, numCardsToRender, collectionTask, col));
            Timber.d("The search found %d cards", searchResult_.size());
            int position = 0;
            for (Long cid : searchResult_) {
                CardBrowser.CardCache card = new CardBrowser.CardCache(cid, col, position++);
                searchResult.add(card);
            }
            // Render the first few items
            for (int i = 0; i < Math.min(numCardsToRender, searchResult.size()); i++) {
                if (collectionTask.isCancelled()) {
                    Timber.d("doInBackgroundSearchCards was cancelled so return null");
                    return null;
                }
                searchResult.get(i).load(false, column1Index, column2Index);
            }
            // Finish off the task
            if (collectionTask.isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null");
                return null;
            } else {
                return searchResult;
            }
        }
    }


    public static class RenderBrowserQA extends Task<Integer, Pair<CardBrowser.CardCollection<CardBrowser.CardCache>, List<Long>>> {
        private final CardBrowser.CardCollection<CardBrowser.CardCache> cards;
        private final Integer startPos;
        private final Integer n;
        private final int column1Index;
        private final int column2Index;


        public RenderBrowserQA(CardBrowser.CardCollection<CardBrowser.CardCache> cards, Integer mStartPos, Integer n, int column1Index, int column2Index) {
            this.cards = cards;
            this.startPos = mStartPos;
            this.n = n;
            this.column1Index = column1Index;
            this.column2Index = column2Index;
        }


        protected Pair<CardBrowser.CardCollection<CardBrowser.CardCache>, List<Long>> task(Collection col, ProgressSenderAndCancelListener<Integer> collectionTask) {
            Timber.d("doInBackgroundRenderBrowserQA");

            List<Long> invalidCardIds = new ArrayList<>();
            // for each specified card in the browser list
            for (int i = startPos; i < startPos + n; i++) {
                // Stop if cancelled
                if (collectionTask.isCancelled()) {
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
                collectionTask.doProgress((int) progress);
            }
            return new Pair<>(cards, invalidCardIds);
        }
    }

    public static class CheckDatabase extends Task<String, Pair<Boolean, Collection.CheckDatabaseResult>> {
    protected Pair<Boolean, Collection.CheckDatabaseResult> task(Collection col, ProgressSenderAndCancelListener<String> collectionTask) {
        Timber.d("doInBackgroundCheckDatabase");
        // Don't proceed if collection closed
        if (col == null) {
            Timber.e("doInBackgroundCheckDatabase :: supplied collection was null");
            return new Pair<>(false, null);
        }

        Collection.CheckDatabaseResult result = col.fixIntegrity(new TaskManager.ProgressCallback(collectionTask, AnkiDroidApp.getAppResources()));
        if (result.getFailed()) {
            //we can fail due to a locked database, which requires knowledge of the failure.
            return new Pair<>(false, result);
        } else {
            // Close the collection and we restart the app to reload
            CollectionHelper.getInstance().closeCollection(true, "Check Database Completed");
            return new Pair<>(true, result);
        }
    }
    }


    public static class RepairCollectionn extends Task<Void, Boolean> {
        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundRepairCollection");
            if (col != null) {
                Timber.i("RepairCollection: Closing collection");
                col.close(false);
            }
            return BackupManager.repairCollection(col);
        }
    }


    public static class UpdateValuesFromDeck extends Task<Void, int[]> {
        private final boolean reset;


        public UpdateValuesFromDeck(boolean reset) {
            this.reset = reset;
        }


        public int[] task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundUpdateValuesFromDeck");
            try {
                AbstractSched sched = col.getSched();
                if (reset) {
                    // reset actually required because of counts, which is used in getCollectionTaskListener
                    sched.resetCounts();
                }
                Counts counts = sched.counts();
                int totalNewCount = sched.totalNewForCurrentDeck();
                int totalCount = sched.cardCount();
                return new int[] {counts.getNew(), counts.getLrn(), counts.getRev(), totalNewCount,
                        totalCount, sched.eta(counts)};
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUpdateValuesFromDeck - an error occurred");
                return null;
            }
        }
    }


    public static class DeleteDeck extends Task<Void, int[]> {
        private final long did;

        public DeleteDeck(long did) {
            this.did = did;
        }

        protected int[] task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundDeleteDeck");
            col.getDecks().rem(did, true);
            // TODO: if we had "undo delete note" like desktop client then we won't need this.
            col.clearUndo();
            return null;
        }
    }


    public static class RebuildCram extends Task<Void, int[]> {
        protected int[] task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundRebuildCram");
            col.getSched().rebuildDyn(col.getDecks().selected());
            return new UpdateValuesFromDeck(true).task(col, collectionTask);
        }
    }

    public static class EmptyCram extends Task<Void, int[]> {
        protected int[] task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundEmptyCram");
            col.getSched().emptyDyn(col.getDecks().selected());
            return new UpdateValuesFromDeck(true).task(col, collectionTask);
        }
    }

    public static class ImportAdd extends Task<String, Triple<AnkiPackageImporter, Boolean, String>> {
        private final String path;


        public ImportAdd(String path) {
            this.path = path;
        }


        protected Triple<AnkiPackageImporter, Boolean, String> task(Collection col, ProgressSenderAndCancelListener<String> collectionTask) {
            Timber.d("doInBackgroundImportAdd");
            Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
            AnkiPackageImporter imp = new AnkiPackageImporter(col, path);
            imp.setProgressCallback(new TaskManager.ProgressCallback(collectionTask, res));
            try {
                imp.run();
            } catch (ImportExportException e) {
                return new Triple(null, true, e.getMessage());
            }
            return new Triple<>(imp, false, null);
        }
    }


    public static class ImportReplace extends Task<String, BooleanGetter> {
        private final String path;


        public ImportReplace(String path) {
            this.path = path;
        }


        protected BooleanGetter task(Collection col, ProgressSenderAndCancelListener<String> collectionTask) {
            Timber.d("doInBackgroundImportReplace");
            Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
            Context context = col.getContext();

            // extract the deck from the zip file
            String colPath = CollectionHelper.getCollectionPath(context);
            File dir = new File(new File(colPath).getParentFile(), "tmpzip");
            if (dir.exists()) {
                BackupManager.removeDir(dir);
            }

            // from anki2.py
            String colname = "collection.anki21";
            ZipFile zip;
            try {
                zip = new ZipFile(new File(path));
            } catch (IOException e) {
                Timber.e(e, "doInBackgroundImportReplace - Error while unzipping");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace0");
                return False;
            }
            try {
                // v2 scheduler?
                if (zip.getEntry(colname) == null) {
                    colname = CollectionHelper.COLLECTION_FILENAME;
                }
                Utils.unzipFiles(zip, dir.getAbsolutePath(), new String[] {colname, "media"}, null);
            } catch (IOException e) {
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace - unzip");
                return False;
            }
            String colFile = new File(dir, colname).getAbsolutePath();
            if (!(new File(colFile)).exists()) {
                return False;
            }

            Collection tmpCol = null;
            try {
                tmpCol = Storage.Collection(context, colFile);
                if (!tmpCol.validCollection()) {
                    tmpCol.close();
                    return False;
                }
            } catch (Exception e) {
                Timber.e("Error opening new collection file... probably it's invalid");
                try {
                    tmpCol.close();
                } catch (Exception e2) {
                    // do nothing
                }
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace - open col");
                return False;
            } finally {
                if (tmpCol != null) {
                    tmpCol.close();
                }
            }

            collectionTask.doProgress(res.getString(R.string.importing_collection));

            try {
                CollectionHelper.getInstance().getCol(context);
                // unload collection and trigger a backup
                Time time = CollectionHelper.getInstance().getTimeSafe(context);
                CollectionHelper.getInstance().closeCollection(true, "Importing new collection");
                CollectionHelper.getInstance().lockCollection();
                BackupManager.performBackupInBackground(colPath, true, time);
            } catch (Exception e) {
            }
            // overwrite collection
            File f = new File(colFile);
            if (!f.renameTo(new File(colPath))) {
                // Exit early if this didn't work
                return False;
            }
            int addedCount = -1;
            try {
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
                String mediaDir = Media.getCollectionMediaPath(colPath);
                int total = nameToNum.size();
                int i = 0;
                for (Map.Entry<String, String> entry : nameToNum.entrySet()) {
                    String file = entry.getKey();
                    String c = entry.getValue();
                    File of = new File(mediaDir, file);
                    if (!of.exists()) {
                        Utils.unzipFiles(zip, mediaDir, new String[] {c}, numToName);
                    }
                    ++i;
                    collectionTask.doProgress(res.getString(R.string.import_media_count, (i + 1) * 100 / total));
                }
                zip.close();
                // delete tmp dir
                BackupManager.removeDir(dir);
                return True;
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundImportReplace - RuntimeException");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace1");
                return False;
            } catch (FileNotFoundException e) {
                Timber.e(e, "doInBackgroundImportReplace - FileNotFoundException");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace2");
                return False;
            } catch (IOException e) {
                Timber.e(e, "doInBackgroundImportReplace - IOException");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace3");
                return False;
            }
        }
    }


    public static class ExportApkg extends Task<Void, Pair<Boolean, String>> {
        private final String apkgPath;
        private final Long did;
        private final Boolean includeSched;
        private final Boolean includeMedia;


        public ExportApkg(String apkgPath, Long did, Boolean includeSched, Boolean includeMedia) {
            this.apkgPath = apkgPath;
            this.did = did;
            this.includeSched = includeSched;
            this.includeMedia = includeMedia;
        }


        protected Pair<Boolean, String> task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundExportApkg");

            try {
                AnkiPackageExporter exporter = new AnkiPackageExporter(col);
                exporter.setIncludeSched(includeSched);
                exporter.setIncludeMedia(includeMedia);
                exporter.setDid(did);
                exporter.exportInto(apkgPath, col.getContext());
            } catch (FileNotFoundException e) {
                Timber.e(e, "FileNotFoundException in doInBackgroundExportApkg");
                return new Pair<>(false, null);
            } catch (IOException e) {
                Timber.e(e, "IOException in doInBackgroundExportApkg");
                return new Pair<>(false, null);
            } catch (JSONException e) {
                Timber.e(e, "JSOnException in doInBackgroundExportApkg");
                return new Pair<>(false, null);
            } catch (ImportExportException e) {
                Timber.e(e, "ImportExportException in doInBackgroundExportApkg");
                return new Pair<>(true, e.getMessage());
            }
            return new Pair<>(false, apkgPath);
        }
    }


    public static class Reorder extends Task<Void, Boolean> {
        private final DeckConfig conf;


        public Reorder(DeckConfig conf) {
            this.conf = conf;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundReorder");
            col.getSched().resortConf(conf);
            return true;
        }
    }


    public static class ConfChange extends Task<Void, Boolean> {
        private final Deck deck;
        private final DeckConfig conf;


        public ConfChange(Deck deck, DeckConfig conf) {
            this.deck = deck;
            this.conf = conf;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfChange");
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
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    public static class ConfReset extends Task<Void, Boolean> {
        private final DeckConfig conf;


        public ConfReset(DeckConfig conf) {
            this.conf = conf;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfReset");
            col.getDecks().restoreToDefault(conf);
            col.save();
            return null;
        }
    }


    public static class ConfRemove extends Task<Void, Boolean> {
        private final DeckConfig conf;


        public ConfRemove(DeckConfig conf) {
            this.conf = conf;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfRemove");
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
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    public static class ConfSetSubdecks extends Task<Void, Boolean> {
        private final Deck deck;
        private final DeckConfig conf;


        public ConfSetSubdecks(Deck deck, DeckConfig conf) {
            this.deck = deck;
            this.conf = conf;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfSetSubdecks");
            try {
                TreeMap<String, Long> children = col.getDecks().children(deck.getLong("id"));
                for (long childDid : children.values()) {
                    Deck child = col.getDecks().get(childDid);
                    if (child.getInt("dyn") == DECK_DYN) {
                        continue;
                    }
                    boolean changed = new ConfChange(child, conf).task(col, collectionTask);
                    if (!changed) {
                        return false;
                    }
                }
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }


    /**
     * @return The results list from the check, or false if any errors.
     */
    public static class CheckMedia extends Task<Void, PairWithBoolean<List<List<String>>>> {
        @Override
        protected PairWithBoolean<List<List<String>>> task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundCheckMedia");
            // Ensure that the DB is valid - unknown why, but some users were missing the meta table.
            try {
                col.getMedia().rebuildIfInvalid();
            } catch (IOException e) {
                return new PairWithBoolean<>(false, null);
            }
            // A media check on AnkiDroid will also update the media db
            col.getMedia().findChanges(true);
            // Then do the actual check
            return new PairWithBoolean<>(true, col.getMedia().check());
        }
    }


    public static class DeleteMedia extends Task<Void, Integer> {
        private final List<String> unused;


        public DeleteMedia(List<String> unused) {
            this.unused = unused;
        }


        protected Integer task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            com.ichi2.libanki.Media m = col.getMedia();
            for (String fname : unused) {
                m.removeFile(fname);
            }
            return unused.size();
        }
    }


    /**
     * Handles everything for a model change at once - template add / deletes as well as content updates
     */
    public static class SaveModel extends Task<Void, Pair<Boolean, String>> {
        private final Model model;
        private final ArrayList<Object[]> templateChanges;


        public SaveModel(Model model, ArrayList<Object[]> templateChanges) {
            this.model = model;
            this.templateChanges = templateChanges;
        }


        protected Pair<Boolean, String> task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundSaveModel");
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
                                return new Pair<>(false, e.getLocalizedMessage());
                            }
                            break;
                        case DELETE:
                            Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0]);
                            try {
                                col.getModels().remTemplate(oldModel, oldTemplates.getJSONObject((int) change[0]));
                            } catch (Exception e) {
                                Timber.e(e, "Unable to delete template %s from model %s", change[0], model.getLong("id"));
                                return new Pair<>(false, e.getLocalizedMessage());
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
                DB.safeEndInTransaction(col.getDb());
            }
            return new Pair<>(true, null);
        }
    }


    /*
     * Async task for the ModelBrowser Class
     * Returns an ArrayList of all models alphabetically ordered and the number of notes
     * associated with each model.
     *
     * @return {ArrayList<JSONObject> models, ArrayList<Integer> cardCount}
     */
    public static class CountModels extends Task<Void, Pair<ArrayList<Model>, ArrayList<Integer>>> {
        protected Pair<ArrayList<Model>, ArrayList<Integer>> task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundLoadModels");

            ArrayList<Model> models = col.getModels().all();
            ArrayList<Integer> cardCount = new ArrayList<>();
            Collections.sort(models, (Comparator<JSONObject>) (a, b) -> a.getString("name").compareTo(b.getString("name")));

            for (Model n : models) {
                if (collectionTask.isCancelled()) {
                    Timber.e("doInBackgroundLoadModels :: Cancelled");
                    // onPostExecute not executed if cancelled. Return value not used.
                    return null;
                }
                cardCount.add(col.getModels().useCount(n));
            }

            return new Pair<>(models, cardCount);
        }
    }


    /**
     * Deletes the given model
     * and all notes associated with it
     */
    public static class DeleteModel extends Task<Void, Boolean> {
        private final long modID;


        public DeleteModel(long modID) {
            this.modID = modID;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackGroundDeleteModel");
            try {
                col.getModels().rem(col.getModels().get(modID));
                col.save();
            } catch (ConfirmModSchemaException e) {
                Timber.e("doInBackGroundDeleteModel :: ConfirmModSchemaException");
                return false;
            }
            return true;
        }
    }

    /**
     * Deletes the given field in the given model
     */
    public static class DeleteField extends Task<Void, Boolean> {
        private final Model model;
        private final JSONObject field;


        public DeleteField(Model model, JSONObject field) {
            this.model = model;
            this.field = field;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask){
            Timber.d("doInBackGroundDeleteField");


            try {
                col.getModels().remField(model, field);
                col.save();
            } catch (ConfirmModSchemaException e) {
                //Should never be reached
                return false;
            }
            return true;
        }
    }

    /**
     * Repositions the given field in the given model
     */
    public static class RepositionField extends Task<Void, Boolean> {
        private final Model model;
        private final JSONObject field;
        private final int index;


        public RepositionField(Model model, JSONObject field, int index) {
            this.model = model;
            this.field = field;
            this.index = index;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask){
            Timber.d("doInBackgroundRepositionField");

            try {
                col.getModels().moveField(model, field, index);
                col.save();
            } catch (ConfirmModSchemaException e) {
                //Should never be reached
                return false;
            }
            return true;
        }
    }

    /**
     * Adds a field with name in given model
     */
    public static class AddField extends Task<Void, Boolean> {
        private final Model model;
        private final String fieldName;


        public AddField(Model model, String fieldName) {
            this.model = model;
            this.fieldName = fieldName;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask){
            Timber.d("doInBackgroundRepositionField");
            col.getModels().addFieldModChanged(model, col.getModels().newField(fieldName));
            col.save();
            return true;
        }
    }

    /**
     * Adds a field of with name in given model
     */
    public static class ChangeSortField extends Task<Void, Boolean> {
        private final Model model;
        private final int idx;


        public ChangeSortField(Model model, int idx) {
            this.model = model;
            this.idx = idx;
        }


        protected Boolean task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask){
            try {
                Timber.d("doInBackgroundChangeSortField");
                col.getModels().setSortIdx(model, idx);
                col.save();
            } catch(Exception e){
                Timber.e(e, "Error changing sort field");
                return false;
            }
            return true;
        }
    }
    
    public static class FindEmptyCards extends Task<Integer, List<Long>> {
        protected List<Long> task(Collection col, ProgressSenderAndCancelListener<Integer> collectionTask) {
            return col.emptyCids(collectionTask);
        }
    }

    /**
     * Goes through selected cards and checks selected and marked attribute
     * @return If there are unselected cards, if there are unmarked cards
     */
    public static class CheckCardSelection extends Task<Void, Pair<Boolean, Boolean>> {
        private final CardBrowser.CardCollection<CardBrowser.CardCache> checkedCards;


        public CheckCardSelection(CardBrowser.CardCollection<CardBrowser.CardCache> checkedCards) {
            this.checkedCards = checkedCards;
        }


        protected @Nullable Pair<Boolean, Boolean> task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            boolean hasUnsuspended = false;
            boolean hasUnmarked = false;
            for (CardBrowser.CardCache c: checkedCards) {
                if (collectionTask.isCancelled()) {
                    Timber.v("doInBackgroundCheckCardSelection: cancelled.");
                    return null;
                }
                Card card = c.getCard();
                hasUnsuspended = hasUnsuspended || card.getQueue() != Consts.QUEUE_TYPE_SUSPENDED;
                hasUnmarked = hasUnmarked || !card.note().hasTag("marked");
                if (hasUnsuspended && hasUnmarked)
                    break;
            }

            return new Pair<>(hasUnsuspended, hasUnmarked);
        }
    }

    public static class PreloadNextCard extends Task<Void, Void> {
        public Void task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            try {
                col.getSched().counts(); // Ensure counts are recomputed if necessary, to know queue to look for
                col.getSched().preloadNextCard();
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundPreloadNextCard - RuntimeException on preloading card");
            }
            return null;
        }
    }

    public static class LoadCollectionComplete extends Task<Void, Void> {
        protected Void task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            if (col != null) {
                CollectionHelper.loadCollectionComplete(col);
            }
            return null;
        }
    }

    public static class Reset extends Task<Void, Void> {
        public Void task(Collection col, ProgressSenderAndCancelListener<Void> collectionTask) {
            col.getSched().reset();
            return null;
        }
    }
}
