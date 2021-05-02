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
import android.util.Pair;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.AnkiSerialization;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.anki.TemporaryModel;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.anki.servicelayer.SearchService.SearchCardsResult;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.SortOrder;
import com.ichi2.libanki.UndoAction;
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
import com.ichi2.utils.Computation;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.SyncStatus;
import com.ichi2.utils.Triple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import static com.ichi2.utils.Computation.OK;
import static com.ichi2.utils.Computation.ERR;

/**
 * This is essentially an AsyncTask with some more logging. It delegates to TaskDelegate the actual business logic.
 * It adds some extra check.
 * TODO: explain the goal of those extra checks. They seems redundant with AsyncTask specification.
 *
 * The CollectionTask should be created by the TaskManager. All creation of background tasks (except for Connection and Widget) should be done by sending a TaskDelegate to the ThreadManager.launchTask.
 *
 * @param <Progress> The type of progress that is sent by the TaskDelegate. E.g. a Card, a pairWithBoolean.
 * @param <Result>   The type of result that the TaskDelegate sends. E.g. a tree of decks, counts of a deck.
 */
public class CollectionTask<Progress, Result> extends BaseAsyncTask<Void, Progress, Result> implements Cancellable {

    /**
     * A reference to the application context to use to fetch the current Collection object.
     */
    private Context mContext;


    /** Cancel the current task.
     * @return whether cancelling did occur.*/
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public boolean safeCancel() {
        try {
            if (getStatus() != android.os.AsyncTask.Status.FINISHED) {
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

    private final TaskDelegate<Progress, Result> mTask;
    public TaskDelegate<Progress, Result> getTask() {
        return mTask;
    }
    private final TaskListener<? super Progress, ? super Result> mListener;
    private CollectionTask mPreviousTask;


    protected CollectionTask(TaskDelegate<Progress, Result> task, TaskListener<? super Progress, ? super Result> listener, CollectionTask previousTask) {
        mTask = task;
        mListener = listener;
        mPreviousTask = previousTask;
    }

    @SuppressWarnings("deprecation") // #7108: AsyncTask
    @Override
    protected Result doInBackground(Void... params) {
        try {
            return actualDoInBackground();
        } finally {
            TaskManager.removeTask(this);
        }
    }

    // This method and those that are called here are executed in a new thread
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    protected Result actualDoInBackground() {
        super.doInBackground();
        // Wait for previous thread (if any) to finish before continuing
        if (mPreviousTask != null && mPreviousTask.getStatus() != android.os.AsyncTask.Status.FINISHED) {
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
        if (mTask.requiresOpenCollection() && CollectionHelper.getInstance().getColSafe(mContext) == null) {
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
    protected void onProgressUpdate(Progress... values) {
        super.onProgressUpdate(values);
        if (mListener != null) {
            mListener.onProgressUpdate(values[0]);
        }
    }


    /** Delegates to the {@link TaskListener} for this task. */
    @Override
    protected void onPostExecute(Result result) {
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

    public static class AddNote extends TaskDelegate<Integer, Boolean> {
        private final Note mNote;

        public AddNote(Note note) {
            this.mNote = note;
        }


        @Override
        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Integer> collectionTask) {
            Timber.d("doInBackgroundAddNote");
            try {
                DB db = col.getDb();
                db.executeInTransaction(() -> {
                        int value = col.addNote(mNote, Models.AllowEmpty.ONLY_CLOZE);
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


    public static class UpdateNote extends TaskDelegate<Card, Computation<?>> {
        private final Card mEditCard;
        private final boolean mFromReviewer;
        private final boolean mCanAccessScheduler;


        public UpdateNote(Card editCard, boolean fromReviewer, boolean canAccessScheduler) {
            this.mEditCard = editCard;
            this.mFromReviewer = fromReviewer;
            this.mCanAccessScheduler = canAccessScheduler;
        }

        protected Computation<?> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Card> collectionTask) {
            Timber.d("doInBackgroundUpdateNote");
            // Save the note
            AbstractSched sched = col.getSched();
            Note editNote = mEditCard.note();

            try {
                col.getDb().executeInTransaction(() -> {
                    // TODO: undo integration
                    editNote.flush();
                    // flush card too, in case, did has been changed
                    mEditCard.flush();
                    if (mFromReviewer) {
                        Card newCard;
                        if (col.getDecks().active().contains(mEditCard.getDid()) || !mCanAccessScheduler) {
                            newCard = mEditCard;
                            newCard.load();
                            // reload qa-cache
                            newCard.q(true);
                        } else {
                            newCard = sched.getCard();
                        }
                        collectionTask.doProgress(newCard); // check: are there deleted too?
                    } else {
                        collectionTask.doProgress(mEditCard);
                    }
                });
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUpdateNote - RuntimeException on updating note");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUpdateNote");
                return ERR;
            }
            return OK;
        }

        public boolean isFromReviewer() {
            return mFromReviewer;
        }
    }


    public static class UpdateMultipleNotes extends TaskDelegate<List<Note>, Computation<?>> {
        private final List<Note> mNotesToUpdate;
        private final boolean mShouldUpdateCards;


        public UpdateMultipleNotes(List<Note> notesToUpdate) {
            this(notesToUpdate, false);
        }


        public UpdateMultipleNotes(List<Note> notesToUpdate, boolean shouldUpdateCards) {
            mNotesToUpdate = notesToUpdate;
            mShouldUpdateCards = shouldUpdateCards;
        }


        @Override
        protected Computation<?> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<List<Note>> collectionTask) {
            Timber.d("doInBackgroundUpdateMultipleNotes");

            try {
                col.getDb().executeInTransaction(() -> {
                    for (Note note : mNotesToUpdate) {
                        note.flush();
                        if (mShouldUpdateCards) {
                            for (Card card : note.cards()) {
                                card.flush();
                            }
                        }
                    }
                    collectionTask.doProgress(mNotesToUpdate);
                });
            } catch (RuntimeException e) {
                Timber.w(e, "doInBackgroundUpdateMultipleNotes - RuntimeException on updating multiple note");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUpdateMultipleNotes");
                return ERR;
            }
            return OK;
        }
    }

    public static class LoadDeck extends TaskDelegate<Void, List<DeckTreeNode>> {
        protected List<DeckTreeNode> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
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


    public static class LoadDeckCounts extends TaskDelegate<Void, List<DeckDueTreeNode>> {
        protected List<DeckDueTreeNode> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
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

    public static class SaveCollection extends TaskDelegate<Void, Void> {
        private final boolean mSyncIgnoresDatabaseModification;


        public SaveCollection(boolean syncIgnoresDatabaseModification) {
            this.mSyncIgnoresDatabaseModification = syncIgnoresDatabaseModification;
        }


        protected Void task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundSaveCollection");
            if (col != null) {
                try {
                    if (mSyncIgnoresDatabaseModification) {
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

    protected static class UndoSuspendCardMulti extends UndoAction {
        private final Card[] mCards;
        private final boolean[] mOriginalSuspended;

        /** @param hasUnsuspended  whether there were any unsuspended card (in which card the action was "Suspend",
         *                          otherwise the action was "Unsuspend")  */
        public UndoSuspendCardMulti(Card[] cards, boolean[] originalSuspended,
                                    boolean hasUnsuspended) {
            super((hasUnsuspended) ? R.string.menu_suspend_card : R.string.card_browser_unsuspend_card);
            this.mCards = cards;
            this.mOriginalSuspended = originalSuspended;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Suspend multiple cards");
            int nbOfCards = mCards.length;
            List<Long> toSuspendIds = new ArrayList<>(nbOfCards);
            List<Long> toUnsuspendIds = new ArrayList<>(nbOfCards);
            for (int i = 0; i < nbOfCards; i++) {
                Card card = mCards[i];
                if (mOriginalSuspended[i]) {
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


    private static class UndoDeleteNoteMulti extends UndoAction {
        private final Note[] mNotesArr;
        private final List<Card> mAllCards;


        public UndoDeleteNoteMulti(Note[] notesArr, List<Card> allCards) {
            super(R.string.card_browser_delete_card);
            this.mNotesArr = notesArr;
            this.mAllCards = allCards;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Delete notes");
            // undo all of these at once instead of one-by-one
            ArrayList<Long> ids = new ArrayList<>(mNotesArr.length + mAllCards.size());
            for (Note n : mNotesArr) {
                n.flush(n.getMod(), false);
                ids.add(n.getId());
            }
            for (Card c : mAllCards) {
                c.flush(false);
                ids.add(c.getId());
            }
            col.getDb().execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(ids));
            return null;  // don't fetch new card

        }
    }

    
    private static class UndoChangeDeckMulti extends UndoAction {
        private final Card[] mCards;
        private final long[] mOriginalDids;


        public UndoChangeDeckMulti(Card[] cards, long[] originalDids) {
            super(R.string.undo_action_change_deck_multi);
            this.mCards = cards;
            this.mOriginalDids = originalDids;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Change Decks");
            // move cards to original deck
            for (int i = 0; i < mCards.length; i++) {
                Card card = mCards[i];
                card.load();
                card.setDid(mOriginalDids[i]);
                Note note = card.note();
                note.flush();
                card.flush();
            }
            return null;  // don't fetch new card

        }
    }

    private static class UndoMarkNoteMulti extends UndoAction {
        private final List<Note> mOriginalMarked;
        private final List<Note> mOriginalUnmarked;

        /** @param hasUnmarked whether there were any unmarked card (in which card the action was "mark",
         *                      otherwise the action was "Unmark")  */
        public UndoMarkNoteMulti(List<Note> originalMarked, List<Note> originalUnmarked, boolean hasUnmarked) {
            super((hasUnmarked) ? R.string.card_browser_mark_card : R.string.card_browser_unmark_card);
            this.mOriginalMarked = originalMarked;
            this.mOriginalUnmarked = originalUnmarked;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undo: Mark notes");
            CardUtils.markAll(mOriginalMarked, true);
            CardUtils.markAll(mOriginalUnmarked, false);
            return null;  // don't fetch new card
        }
    }

    private static abstract class DismissNotes<Progress> extends TaskDelegate<Progress, Computation<? extends Card[]>> {
        protected final List<Long> mCardIds;

        public DismissNotes(List<Long> cardIds) {
            this.mCardIds = cardIds;
        }


        /**
         * @param col
         * @param collectionTask Represents the background tasks.
         * @return whether the task succeeded, and the array of cards affected.
         */
        protected Computation<Card[]> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Progress> collectionTask) {
            // query cards
            Card[] cards = new Card[mCardIds.size()];
            for (int i = 0; i < mCardIds.size(); i++) {
                cards[i] = col.getCard(mCardIds.get(i));
            }

            try {
                col.getDb().getDatabase().beginTransaction();
                try {
                    boolean succeeded = actualTask(col, collectionTask, cards);
                    if (!succeeded) {
                        return Computation.err();
                    }
                    col.getDb().getDatabase().setTransactionSuccessful();
                } finally {
                    DB.safeEndInTransaction(col.getDb());
                }
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSuspendCard");
                return Computation.err();
            }
            // pass cards back so more actions can be performed by the caller
            // (querying the cards again is unnecessarily expensive)
            return Computation.ok(cards);
        }

        /**
         * @param col The collection
         * @param collectionTask, where to send progress and listen for cancellation
         * @param cards Cards to which the task should be applied
         * @return Whether the tasks succeeded.
         */
        protected abstract boolean actualTask(Collection col, ProgressSenderAndCancelListener<Progress> collectionTask, Card[] cards);
    }

    public static class SuspendCardMulti extends DismissNotes<Void> {
        public SuspendCardMulti(List<Long> cardIds) {
            super(cardIds);
        }

        protected boolean actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
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
            return true;
        }
    }

    public static class Flag extends DismissNotes<Void> {
        private final int mFlag;

        public Flag(List<Long> cardIds, int flag) {
            super(cardIds);
            mFlag = flag;
        }

        protected boolean actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
            col.setUserFlag(mFlag, mCardIds);
            for (Card c : cards) {
                c.load();
            }
            return true;
        }
    }

    public static class MarkNoteMulti extends DismissNotes<Void> {
        public MarkNoteMulti(List<Long> cardIds) {
            super(cardIds);
        }

        protected boolean actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
            Set<Note> notes = CardUtils.getNotes(Arrays.asList(cards));
            // collect undo information
            List<Note> originalMarked = new ArrayList<>();
            List<Note> originalUnmarked = new ArrayList<>();

            for (Note n : notes) {
                if (NoteService.isMarked(n))
                    originalMarked.add(n);
                else
                    originalUnmarked.add(n);
            }

            boolean hasUnmarked = !originalUnmarked.isEmpty();
            CardUtils.markAll(new ArrayList<>(notes), hasUnmarked);

            // mark undo for all at once
            col.markUndo(new UndoMarkNoteMulti(originalMarked, originalUnmarked, hasUnmarked));

            // reload cards because they'll be passed back to caller
            for (Card c : cards) {
                c.load();
            }
            return true;
        }
    }

    public static class DeleteNoteMulti extends DismissNotes<Card[]> {

        public DeleteNoteMulti(List<Long> cardIds) {
            super(cardIds);
        }

        protected boolean actualTask(Collection col, ProgressSenderAndCancelListener<Card[]> collectionTask, Card[] cards) {
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
            return true;
        }
    }

    public static class ChangeDeckMulti extends DismissNotes<Void> {
        private final long mNewDid;
        public ChangeDeckMulti(List<Long> cardIds, long newDid) {
            super(cardIds);
            mNewDid = newDid;
        }

        protected boolean actualTask(Collection col, ProgressSenderAndCancelListener<Void> collectionTask, Card[] cards) {
            Timber.i("Changing %d cards to deck: '%d'", cards.length, mNewDid);
            Deck deckData = col.getDecks().get(mNewDid);

            if (Decks.isDynamic(deckData)) {
                //#5932 - can't change to a dynamic deck. Use "Rebuild"
                Timber.w("Attempted to move to dynamic deck. Cancelling task.");
                return false;
            }

            //Confirm that the deck exists (and is not the default)
            try {
                long actualId = deckData.getLong("id");
                if (actualId != mNewDid) {
                    Timber.w("Attempted to move to deck %d, but got %d", mNewDid, actualId);
                    return false;
                }
            } catch (Exception e) {
                Timber.e(e, "failed to check deck");
                return false;
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

            UndoAction changeDeckMulti = new UndoChangeDeckMulti(cards, originalDids);
            // mark undo for all at once
            col.markUndo(changeDeckMulti);
            return true;
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
            mCards = new ArrayList<>(cards);
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
            // PERF: This is currently called on the background thread and blocks further execution of the search
            // PERF: This performs an individual query to load each note
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


        public ProgressSender<Long> getProgressSender() {
            return new ProgressSender<Long>() {
                private final List<Long> mRes = new ArrayList<>();
                private boolean mSendProgress = true;
                @Override
                public void doProgress(@Nullable Long value) {
                    if (!mSendProgress) {
                        return;
                    }
                    mRes.add(value);
                    if (mRes.size() >= mNumCardsToRender) {
                        PartialSearch.this.doProgress(mRes);
                        mSendProgress = false;
                    }
                }
            };
        }
    }


    public static class SearchCards extends TaskDelegate<List<CardBrowser.CardCache>, SearchCardsResult> {
        private final String mQuery;
        private final SortOrder mOrder;
        private final int mNumCardsToRender;
        private final int mColumn1Index;
        private final int mColumn2Index;


        public SearchCards(String query, SortOrder order, int numCardsToRender, int column1Index, int column2Index) {
            this.mQuery = query;
            this.mOrder = order;
            this.mNumCardsToRender = numCardsToRender;
            this.mColumn1Index = column1Index;
            this.mColumn2Index = column2Index;
        }


        protected SearchCardsResult task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<List<CardBrowser.CardCache>> collectionTask) {
            Timber.d("doInBackgroundSearchCards");
            if (collectionTask.isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null");
                return SearchCardsResult.invalidResult();
            }
            List<CardBrowser.CardCache> searchResult = new ArrayList<>();
            List<Long> searchResult_;
            try {
                searchResult_ = col.findCards(mQuery, mOrder, new PartialSearch(searchResult, mColumn1Index, mColumn2Index, mNumCardsToRender, collectionTask, col));
            } catch (Exception e) {
                // exception can occur via normal operation
                Timber.w(e);
                return SearchCardsResult.error(e);
            }

            Timber.d("The search found %d cards", searchResult_.size());
            int position = 0;
            for (Long cid : searchResult_) {
                CardBrowser.CardCache card = new CardBrowser.CardCache(cid, col, position++);
                searchResult.add(card);
            }
            // Render the first few items
            for (int i = 0; i < Math.min(mNumCardsToRender, searchResult.size()); i++) {
                if (collectionTask.isCancelled()) {
                    Timber.d("doInBackgroundSearchCards was cancelled so return null");
                    return SearchCardsResult.invalidResult();
                }
                searchResult.get(i).load(false, mColumn1Index, mColumn2Index);
            }
            // Finish off the task
            if (collectionTask.isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return null");
                return SearchCardsResult.invalidResult();
            } else {
                return SearchCardsResult.success(searchResult);
            }
        }
    }


    public static class RenderBrowserQA extends TaskDelegate<Integer, Pair<CardBrowser.CardCollection<CardBrowser.CardCache>, List<Long>>> {
        private final CardBrowser.CardCollection<CardBrowser.CardCache> mCards;
        private final Integer mStartPos;
        private final Integer mN;
        private final int mColumn1Index;
        private final int mColumn2Index;


        public RenderBrowserQA(CardBrowser.CardCollection<CardBrowser.CardCache> cards, Integer startPos, Integer n, int column1Index, int column2Index) {
            this.mCards = cards;
            this.mStartPos = startPos;
            this.mN = n;
            this.mColumn1Index = column1Index;
            this.mColumn2Index = column2Index;
        }


        protected Pair<CardBrowser.CardCollection<CardBrowser.CardCache>, List<Long>> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Integer> collectionTask) {
            Timber.d("doInBackgroundRenderBrowserQA");

            List<Long> invalidCardIds = new ArrayList<>();
            // for each specified card in the browser list
            for (int i = mStartPos; i < mStartPos + mN; i++) {
                // Stop if cancelled
                if (collectionTask.isCancelled()) {
                    Timber.d("doInBackgroundRenderBrowserQA was aborted");
                    return null;
                }
                if (i < 0 || i >= mCards.size()) {
                    continue;
                }
                CardBrowser.CardCache card;
                try {
                    card = mCards.get(i);
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
                card.load(false, mColumn1Index, mColumn2Index);
                float progress = (float) i / mN * 100;
                collectionTask.doProgress((int) progress);
            }
            return new Pair<>(mCards, invalidCardIds);
        }
    }

    public static class CheckDatabase extends TaskDelegate<String, Pair<Boolean, Collection.CheckDatabaseResult>> {
        protected Pair<Boolean, Collection.CheckDatabaseResult> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<String> collectionTask) {
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


    public static class RepairCollection extends TaskDelegate<Void, Boolean> {
        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundRepairCollection");
            if (col != null) {
                Timber.i("RepairCollection: Closing collection");
                col.close(false);
            }
            return BackupManager.repairCollection(col);
        }

        @Override
        protected boolean requiresOpenCollection() {
            return false;
        }
    }


    public static class UpdateValuesFromDeck extends TaskDelegate<Void, StudyOptionsFragment.DeckStudyData> {
        private final boolean mReset;


        public UpdateValuesFromDeck(boolean reset) {
            this.mReset = reset;
        }


        public StudyOptionsFragment.DeckStudyData task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundUpdateValuesFromDeck");
            try {
                AbstractSched sched = col.getSched();
                if (mReset) {
                    // reset actually required because of counts, which is used in getCollectionTaskListener
                    sched.resetCounts();
                }
                Counts counts = sched.counts();
                int totalNewCount = sched.totalNewForCurrentDeck();
                int totalCount = sched.cardCount();
                return new StudyOptionsFragment.DeckStudyData(counts.getNew(), counts.getLrn(), counts.getRev(), totalNewCount,
                        totalCount, sched.eta(counts));
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUpdateValuesFromDeck - an error occurred");
                return null;
            }
        }
    }


    public static class DeleteDeck extends TaskDelegate<Void, int[]> {
        private final long mDid;

        public DeleteDeck(long did) {
            this.mDid = did;
        }

        protected int[] task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundDeleteDeck");
            col.getDecks().rem(mDid, true);
            // TODO: if we had "undo delete note" like desktop client then we won't need this.
            col.clearUndo();
            return null;
        }
    }


    public static class RebuildCram extends TaskDelegate<Void, StudyOptionsFragment.DeckStudyData> {
        protected StudyOptionsFragment.DeckStudyData task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundRebuildCram");
            col.getSched().rebuildDyn(col.getDecks().selected());
            return new UpdateValuesFromDeck(true).task(col, collectionTask);
        }
    }

    public static class EmptyCram extends TaskDelegate<Void, StudyOptionsFragment.DeckStudyData> {
        protected StudyOptionsFragment.DeckStudyData task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundEmptyCram");
            col.getSched().emptyDyn(col.getDecks().selected());
            return new UpdateValuesFromDeck(true).task(col, collectionTask);
        }
    }

    public static class ImportAdd extends TaskDelegate<String, Triple<AnkiPackageImporter, Boolean, String>> {
        private final String mPath;


        public ImportAdd(String path) {
            this.mPath = path;
        }


        protected Triple<AnkiPackageImporter, Boolean, String> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<String> collectionTask) {
            Timber.d("doInBackgroundImportAdd");
            Resources res = AnkiDroidApp.getInstance().getBaseContext().getResources();
            AnkiPackageImporter imp = new AnkiPackageImporter(col, mPath);
            imp.setProgressCallback(new TaskManager.ProgressCallback(collectionTask, res));
            try {
                imp.run();
            } catch (ImportExportException e) {
                Timber.w(e);
                return new Triple(null, true, e.getMessage());
            }
            return new Triple<>(imp, false, null);
        }
    }


    public static class ImportReplace extends TaskDelegate<String, Computation<?>> {
        private final String mPath;


        public ImportReplace(String path) {
            this.mPath = path;
        }


        protected Computation<?> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<String> collectionTask) {
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
                zip = new ZipFile(new File(mPath));
            } catch (IOException e) {
                Timber.e(e, "doInBackgroundImportReplace - Error while unzipping");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace0");
                return ERR;
            }
            try {
                // v2 scheduler?
                if (zip.getEntry(colname) == null) {
                    colname = CollectionHelper.COLLECTION_FILENAME;
                }
                Utils.unzipFiles(zip, dir.getAbsolutePath(), new String[] {colname, "media"}, null);
            } catch (IOException e) {
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace - unzip");
                return ERR;
            }
            String colFile = new File(dir, colname).getAbsolutePath();
            if (!(new File(colFile)).exists()) {
                return ERR;
            }

            Collection tmpCol = null;
            try {
                tmpCol = Storage.Collection(context, colFile);
                if (!tmpCol.validCollection()) {
                    tmpCol.close();
                    return ERR;
                }
            } catch (Exception e) {
                Timber.e("Error opening new collection file... probably it's invalid");
                try {
                    tmpCol.close();
                } catch (Exception e2) {
                    Timber.w(e2);
                    // do nothing
                }
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace - open col");
                return ERR;
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
                Timber.w(e);
            }
            // overwrite collection
            File f = new File(colFile);
            if (!f.renameTo(new File(colPath))) {
                // Exit early if this didn't work
                return ERR;
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
                    try(JsonParser jp = AnkiSerialization.getFactory().createParser(mediaMapFile)) {
                        String name;
                        String num;
                        if (jp.nextToken() != JsonToken.START_OBJECT) {
                            throw new IllegalStateException("Expected content to be an object");
                        }
                        while (jp.nextToken() != JsonToken.END_OBJECT) {
                            num = jp.currentName();
                            name = jp.nextTextValue();
                            nameToNum.put(name, num);
                            numToName.put(num, name);
                        }
                    }
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
                return OK;
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundImportReplace - RuntimeException");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace1");
                return ERR;
            } catch (FileNotFoundException e) {
                Timber.e(e, "doInBackgroundImportReplace - FileNotFoundException");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace2");
                return ERR;
            } catch (IOException e) {
                Timber.e(e, "doInBackgroundImportReplace - IOException");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundImportReplace3");
                return ERR;
            }
        }

        @Override
        protected boolean requiresOpenCollection() {
            return false;
        }
    }


    public static class ExportApkg extends TaskDelegate<Void, Pair<Boolean, String>> {
        private final String mApkgPath;
        private final Long mDid;
        private final Boolean mIncludeSched;
        private final Boolean mIncludeMedia;


        public ExportApkg(String apkgPath, Long did, Boolean includeSched, Boolean includeMedia) {
            this.mApkgPath = apkgPath;
            this.mDid = did;
            this.mIncludeSched = includeSched;
            this.mIncludeMedia = includeMedia;
        }


        protected Pair<Boolean, String> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundExportApkg");

            try {
                AnkiPackageExporter exporter = new AnkiPackageExporter(col, mDid, mIncludeSched, mIncludeMedia);
                exporter.exportInto(mApkgPath, col.getContext());
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
            return new Pair<>(false, mApkgPath);
        }
    }


    public static class Reorder extends TaskDelegate<Void, Boolean> {
        private final DeckConfig mConf;


        public Reorder(DeckConfig conf) {
            this.mConf = conf;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundReorder");
            col.getSched().resortConf(mConf);
            return true;
        }
    }


    public static class ConfChange extends TaskDelegate<Void, Boolean> {
        private final Deck mDeck;
        private final DeckConfig mConf;


        public ConfChange(Deck deck, DeckConfig conf) {
            this.mDeck = deck;
            this.mConf = conf;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfChange");
            try {
                long newConfId = mConf.getLong("id");
                // If new config has a different sorting order, reorder the cards
                int oldOrder = col.getDecks().getConf(mDeck.getLong("conf")).getJSONObject("new").getInt("order");
                int newOrder = col.getDecks().getConf(newConfId).getJSONObject("new").getInt("order");
                if (oldOrder != newOrder) {
                    switch (newOrder) {
                        case 0:
                            col.getSched().randomizeCards(mDeck.getLong("id"));
                            break;
                        case 1:
                            col.getSched().orderCards(mDeck.getLong("id"));
                            break;
                    }
                }
                col.getDecks().setConf(mDeck, newConfId);
                col.save();
                return true;
            } catch (JSONException e) {
                Timber.w(e);
                return false;
            }
        }
    }

    public static class ConfReset extends TaskDelegate<Void, Boolean> {
        private final DeckConfig mConf;


        public ConfReset(DeckConfig conf) {
            this.mConf = conf;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfReset");
            col.getDecks().restoreToDefault(mConf);
            col.save();
            return null;
        }
    }


    public static class ConfRemove extends TaskDelegate<Void, Boolean> {
        private final DeckConfig mConf;


        public ConfRemove(DeckConfig conf) {
            this.mConf = conf;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfRemove");
            try {
                // Note: We do the actual removing of the options group in the main thread so that we
                // can ask the user to confirm if they're happy to do a full sync, and just do the resorting here

                // When a conf is deleted, all decks using it revert to the default conf.
                // Cards must be reordered according to the default conf.
                int order = mConf.getJSONObject("new").getInt("order");
                int defaultOrder = col.getDecks().getConf(1).getJSONObject("new").getInt("order");
                if (order != defaultOrder) {
                    mConf.getJSONObject("new").put("order", defaultOrder);
                    col.getSched().resortConf(mConf);
                }
                col.save();
                return true;
            } catch (JSONException e) {
                Timber.w(e);
                return false;
            }
        }
    }

    public static class ConfSetSubdecks extends TaskDelegate<Void, Boolean> {
        private final Deck mDeck;
        private final DeckConfig mConf;


        public ConfSetSubdecks(Deck deck, DeckConfig conf) {
            this.mDeck = deck;
            this.mConf = conf;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundConfSetSubdecks");
            try {
                TreeMap<String, Long> children = col.getDecks().children(mDeck.getLong("id"));
                for (long childDid : children.values()) {
                    Deck child = col.getDecks().get(childDid);
                    if (child.isDyn()) {
                        continue;
                    }
                    boolean changed = new ConfChange(child, mConf).task(col, collectionTask);
                    if (!changed) {
                        return false;
                    }
                }
                return true;
            } catch (JSONException e) {
                Timber.w(e);
                return false;
            }
        }
    }


    /**
     * @return The results list from the check, or false if any errors.
     */
    public static class CheckMedia extends TaskDelegate<Void, Computation<List<List<String>>>> {
        @Override
        protected Computation<List<List<String>>> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundCheckMedia");
            // Ensure that the DB is valid - unknown why, but some users were missing the meta table.
            try {
                col.getMedia().rebuildIfInvalid();
            } catch (IOException e) {
                Timber.w(e);
                return Computation.err();
            }
            // A media check on AnkiDroid will also update the media db
            col.getMedia().findChanges(true);
            // Then do the actual check
            return Computation.ok(col.getMedia().check());
        }
    }


    public static class DeleteMedia extends TaskDelegate<Void, Integer> {
        private final List<String> mUnused;


        public DeleteMedia(List<String> unused) {
            this.mUnused = unused;
        }


        protected Integer task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            com.ichi2.libanki.Media m = col.getMedia();
            for (String fname : mUnused) {
                m.removeFile(fname);
            }
            return mUnused.size();
        }
    }


    /**
     * Handles everything for a model change at once - template add / deletes as well as content updates
     */
    public static class SaveModel extends TaskDelegate<Void, Pair<Boolean, String>> {
        private final Model mModel;
        private final ArrayList<Object[]> mTemplateChanges;


        public SaveModel(Model model, ArrayList<Object[]> templateChanges) {
            this.mModel = model;
            this.mTemplateChanges = templateChanges;
        }


        protected Pair<Boolean, String> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundSaveModel");
            Model oldModel = col.getModels().get(mModel.getLong("id"));
            Objects.requireNonNull(oldModel);

            // TODO need to save all the cards that will go away, for undo
            //  (do I need to remove them from graves during undo also?)
            //    - undo (except for cards) could just be Models.update(model) / Models.flush() / Collection.reset() (that was prior "undo")
            JSONArray newTemplates = mModel.getJSONArray("tmpls");

            col.getDb().getDatabase().beginTransaction();

            try {
                for (Object[] change : mTemplateChanges) {
                    JSONArray oldTemplates = oldModel.getJSONArray("tmpls");
                    switch ((TemporaryModel.ChangeType) change[1]) {
                        case ADD:
                            Timber.d("doInBackgroundSaveModel() adding template %s", change[0]);
                            try {
                                col.getModels().addTemplate(oldModel, newTemplates.getJSONObject((int) change[0]));
                            } catch (Exception e) {
                                Timber.e(e, "Unable to add template %s to model %s", change[0], mModel.getLong("id"));
                                return new Pair<>(false, e.getLocalizedMessage());
                            }
                            break;
                        case DELETE:
                            Timber.d("doInBackgroundSaveModel() deleting template currently at ordinal %s", change[0]);
                            try {
                                col.getModels().remTemplate(oldModel, oldTemplates.getJSONObject((int) change[0]));
                            } catch (Exception e) {
                                Timber.e(e, "Unable to delete template %s from model %s", change[0], mModel.getLong("id"));
                                return new Pair<>(false, e.getLocalizedMessage());
                            }
                            break;
                        default:
                            Timber.w("Unknown change type? %s", change[1]);
                            break;
                    }
                }

                // required for Rust: the modified time can't go backwards, and we updated the model by adding fields
                // This could be done better
                mModel.put("mod", oldModel.getLong("mod"));
                col.getModels().save(mModel, true);
                col.getModels().update(mModel);
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
    public static class CountModels extends TaskDelegate<Void, Pair<List<Model>, ArrayList<Integer>>> {
        protected Pair<List<Model>, ArrayList<Integer>> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundLoadModels");

            List<Model> models = col.getModels().all();
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
    public static class DeleteModel extends TaskDelegate<Void, Boolean> {
        private final long mModID;


        public DeleteModel(long modID) {
            this.mModID = modID;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackGroundDeleteModel");
            try {
                col.getModels().rem(col.getModels().get(mModID));
                col.save();
            } catch (ConfirmModSchemaException e) {
                e.log();
                Timber.e("doInBackGroundDeleteModel :: ConfirmModSchemaException");
                return false;
            }
            return true;
        }
    }

    /**
     * Deletes the given field in the given model
     */
    public static class DeleteField extends TaskDelegate<Void, Boolean> {
        private final Model mModel;
        private final JSONObject mField;


        public DeleteField(Model model, JSONObject field) {
            this.mModel = model;
            this.mField = field;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask){
            Timber.d("doInBackGroundDeleteField");


            try {
                col.getModels().remField(mModel, mField);
                col.save();
            } catch (ConfirmModSchemaException e) {
                //Should never be reached
                e.log();
                return false;
            }
            return true;
        }
    }

    /**
     * Repositions the given field in the given model
     */
    public static class RepositionField extends TaskDelegate<Void, Boolean> {
        private final Model mModel;
        private final JSONObject mField;
        private final int mIndex;


        public RepositionField(Model model, JSONObject field, int index) {
            this.mModel = model;
            this.mField = field;
            this.mIndex = index;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask){
            Timber.d("doInBackgroundRepositionField");

            try {
                col.getModels().moveField(mModel, mField, mIndex);
                col.save();
            } catch (ConfirmModSchemaException e) {
                e.log();
                //Should never be reached
                return false;
            }
            return true;
        }
    }

    /**
     * Adds a field with name in given model
     */
    public static class AddField extends TaskDelegate<Void, Boolean> {
        private final Model mModel;
        private final String mFieldName;


        public AddField(Model model, String fieldName) {
            this.mModel = model;
            this.mFieldName = fieldName;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask){
            Timber.d("doInBackgroundRepositionField");
            col.getModels().addFieldModChanged(mModel, col.getModels().newField(mFieldName));
            col.save();
            return true;
        }
    }

    /**
     * Adds a field of with name in given model
     */
    public static class ChangeSortField extends TaskDelegate<Void, Boolean> {
        private final Model mModel;
        private final int mIdx;


        public ChangeSortField(Model model, int idx) {
            this.mModel = model;
            this.mIdx = idx;
        }


        protected Boolean task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask){
            try {
                Timber.d("doInBackgroundChangeSortField");
                col.getModels().setSortIdx(mModel, mIdx);
                col.save();
            } catch(Exception e){
                Timber.e(e, "Error changing sort field");
                return false;
            }
            return true;
        }
    }
    
    public static class FindEmptyCards extends TaskDelegate<Integer, List<Long>> {
        protected List<Long> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Integer> collectionTask) {
            return col.emptyCids(collectionTask);
        }
    }

    /**
     * Goes through selected cards and checks selected and marked attribute
     * @return If there are unselected cards, if there are unmarked cards
     */
    public static class CheckCardSelection extends TaskDelegate<Void, Pair<Boolean, Boolean>> {
        private final @NonNull Set<CardBrowser.CardCache> mCheckedCards;


        public CheckCardSelection(@NonNull Set<CardBrowser.CardCache> checkedCards) {
            this.mCheckedCards = checkedCards;
        }


        protected @Nullable Pair<Boolean, Boolean> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            boolean hasUnsuspended = false;
            boolean hasUnmarked = false;
            for (CardBrowser.CardCache c: mCheckedCards) {
                if (collectionTask.isCancelled()) {
                    Timber.v("doInBackgroundCheckCardSelection: cancelled.");
                    return null;
                }
                Card card = c.getCard();
                hasUnsuspended = hasUnsuspended || card.getQueue() != Consts.QUEUE_TYPE_SUSPENDED;
                hasUnmarked = hasUnmarked || !NoteService.isMarked(card.note());
                if (hasUnsuspended && hasUnmarked)
                    break;
            }

            return new Pair<>(hasUnsuspended, hasUnmarked);
        }
    }

    public static class PreloadNextCard extends TaskDelegate<Void, Void> {
        public Void task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            try {
                col.getSched().counts(); // Ensure counts are recomputed if necessary, to know queue to look for
                col.getSched().preloadNextCard();
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundPreloadNextCard - RuntimeException on preloading card");
            }
            return null;
        }
    }

    public static class LoadCollectionComplete extends TaskDelegate<Void, Void> {
        protected Void task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            if (col != null) {
                CollectionHelper.loadCollectionComplete(col);
            }
            return null;
        }
    }

    public static class Reset extends TaskDelegate<Void, Void> {
        public Void task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            col.getSched().reset();
            return null;
        }
    }
}
