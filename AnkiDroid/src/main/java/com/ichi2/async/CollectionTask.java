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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.AnkiSerialization;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.anki.TemporaryModel;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.apache.commons.compress.archivers.zip.ZipFile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

import static com.ichi2.anki.DeckOptions.confChange;
import static com.ichi2.anki.StudyOptionsFragment.updateValuesFromDeck;
import static com.ichi2.async.TaskManager.setLatestInstance;
import static com.ichi2.libanki.Card.deepCopyCardArray;
import static com.ichi2.libanki.UndoAction.*;
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
public class CollectionTask<Progress, Result> extends BaseAsyncTask<Void, Progress, Result> {

    /**
     * A reference to the application context to use to fetch the current Collection object.
     */
    private Context mContext;


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

    @Override
    protected Result doInBackground(Void... params) {
        try {
            return actualDoInBackground();
        } finally {
            TaskManager.removeTask(this);
        }
    }

    // This method and those that are called here are executed in a new thread
    protected Result actualDoInBackground() {
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


    public static class UpdateNote implements TaskDelegate<Card, Computation<?>> {
        private final Card mEditCard;
        private final boolean mFromReviewer;
        private final boolean mCanAccessScheduler;


        public UpdateNote(Card editCard, boolean fromReviewer, boolean canAccessScheduler) {
            this.mEditCard = editCard;
            this.mFromReviewer = fromReviewer;
            this.mCanAccessScheduler = canAccessScheduler;
        }

        public Computation<?> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Card> collectionTask) {
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


    /**
     * Represents an action that remove a card from the Reviewer without reviewing it.
     */
    public static abstract class DismissNote implements TaskDelegate<Card, Computation<?>> {
        protected final Card mCard;


        /**
         * @param card The card that was in the reviewer. It usually is cloned and then restored if the action is undone
         */
        public DismissNote(Card card) {
            this.mCard = card;
        }


        /**
         * The part of the task that is specific to this object. E.g. suspending, deleting, burying...
         * @param col The collection
         *
         */
        protected abstract void actualTask(Collection col);


        /**
         * @param col
         * @param collectionTask A listener for the task. It waits for a card to display in the reviewer.
         *                       Fetching a new card can possibly be cancelled, however the actual task is not cancellable.
                                 Indeed, if you clicked on suspend and leave the reviewer, the card should still be reviewed and there is no need for a next card.
         * @return whether the action ended succesfully
         */
        public Computation<?> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Card> collectionTask) {
            try {
                col.getDb().executeInTransaction(() -> {
                    col.getSched().deferReset();
                    actualTask(col);
                    // With sHadCardQueue set, getCard() resets the scheduler prior to getting the next card
                    collectionTask.doProgress(col.getSched().getCard());
                });
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundDismissNote - RuntimeException on dismissing note, dismiss type %s", this.getClass());
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundDismissNote");
                return ERR;
            }
            return OK;
        }
    }

    public static class BuryCard extends DismissNote {
        public BuryCard(Card card) {
            super(card);
        }

        @Override
        protected void actualTask(Collection col) {
            // collect undo information
            col.markUndo(revertCardToProvidedState(R.string.menu_bury_card, mCard));
            // then bury
            col.getSched().buryCards(new long[] {mCard.getId()});
        }
    }

    public static class BuryNote extends DismissNote {
        public BuryNote(Card card) {
            super(card);
        }

        @Override
        protected void actualTask(Collection col) {
            // collect undo information
            col.markUndo(revertNoteToProvidedState(R.string.menu_bury_note, mCard));
            // then bury
            col.getSched().buryNote(mCard.note().getId());
        }
    }

    public static class SuspendCard extends DismissNote {
        public SuspendCard(Card card) {
            super(card);
        }

        @Override
        protected void actualTask(Collection col) {
            // collect undo information
            Card suspendedCard = mCard.clone();
            col.markUndo(revertCardToProvidedState(R.string.menu_suspend_card, suspendedCard));
            // suspend card
            if (mCard.getQueue() == Consts.QUEUE_TYPE_SUSPENDED) {
                col.getSched().unsuspendCards(new long[] {mCard.getId()});
            } else {
                col.getSched().suspendCards(new long[] {mCard.getId()});
            }
        }
    }

    public static class SuspendNote extends DismissNote {
        public SuspendNote(Card card) {
            super(card);
        }

        @Override
        protected void actualTask(Collection col) {
            // collect undo information
            ArrayList<Card> cards = mCard.note().cards();
            long[] cids = new long[cards.size()];
            for (int i = 0; i < cards.size(); i++) {
                cids[i] = cards.get(i).getId();
            }
            col.markUndo(revertNoteToProvidedState(R.string.menu_suspend_note, mCard));
            // suspend note
            col.getSched().suspendCards(cids);
        }
    }


    private static class UndoRepositionRescheduleResetCards extends UndoAction {
        private final Card[] mCardsCopied;


        public UndoRepositionRescheduleResetCards(@StringRes int undoNameId, Card[] cards_copied) {
            super(undoNameId);
            this.mCardsCopied = cards_copied;
        }


        public @Nullable Card undo(@NonNull Collection col) {
            Timber.i("Undoing action of type %s on %d cards", getClass(), mCardsCopied.length);
            for (Card card : mCardsCopied) {
                card.flush(false);
            }
            // /* card schedule change undone, reset and get
            // new card */
            Timber.d("Single card non-review change undo succeeded");
            col.reset();
            return col.getSched().getCard();
        }
    }

    public static abstract class DismissNotes<Progress> implements TaskDelegate<Progress, Computation<Card[]>> {
        protected final List<Long> mCardIds;

        public DismissNotes(List<Long> cardIds) {
            this.mCardIds = cardIds;
        }


        /**
         * @param col
         * @param collectionTask Represents the background tasks.
         * @return whether the task succeeded, and the array of cards affected.
         */
        public Computation<Card[]> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Progress> collectionTask) {
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
                        return ERR;
                    }
                    col.getDb().getDatabase().setTransactionSuccessful();
                } finally {
                    DB.safeEndInTransaction(col.getDb());
                }
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSuspendCard");
                return ERR;
            }
            // pass cards back so more actions can be performed by the caller
            // (querying the cards again is unnecessarily expensive)
            return new Computation<>(cards);
        }

        /**
         * @param col The collection
         * @param collectionTask, where to send progress and listen for cancellation
         * @param cards Cards to which the task should be applied
         * @return Whether the tasks succeeded.
         */
        protected abstract boolean actualTask(Collection col, ProgressSenderAndCancelListener<Progress> collectionTask, Card[] cards);
    }

    public abstract static class RescheduleRepositionReset extends DismissNotes<Card> {
        @StringRes private final int mUndoNameId;
        public RescheduleRepositionReset(List<Long> cardIds, @StringRes int undoNameId) {
            super(cardIds);
            mUndoNameId = undoNameId;
        }

        protected boolean actualTask(Collection col, ProgressSenderAndCancelListener<Card> collectionTask, Card[] cards) {
            AbstractSched sched = col.getSched();
            // collect undo information, sensitive to memory pressure, same for all 3 cases
            try {
                Timber.d("Saving undo information of type %s on %d cards", getClass(), cards.length);
                Card[] cards_copied = deepCopyCardArray(cards, collectionTask);
                UndoAction repositionRescheduleResetCards = new UndoRepositionRescheduleResetCards(mUndoNameId, cards_copied);
                col.markUndo(repositionRescheduleResetCards);
            } catch (CancellationException ce) {
                Timber.i(ce, "Cancelled while handling type %s, skipping undo", mUndoNameId);
            }
            actualActualTask(sched);
            // In all cases schedule a new card so Reviewer doesn't sit on the old one
            col.reset();
            collectionTask.doProgress(sched.getCard());
            return true;
        }

        protected abstract void actualActualTask(AbstractSched sched);
    }

    public static class RescheduleCards extends RescheduleRepositionReset {
        private final int mSchedule;
        public RescheduleCards(List<Long> cardIds, int schedule) {
            super(cardIds, R.string.card_editor_reschedule_card);
            this.mSchedule = schedule;
        }

        @Override
        protected void actualActualTask(AbstractSched sched) {
            sched.reschedCards(mCardIds, mSchedule, mSchedule);
        }
    }

    public static class ResetCards extends RescheduleRepositionReset {
        public ResetCards(List<Long> cardIds) {
            super(cardIds, R.string.card_editor_reset_card);
        }

        @Override
        protected void actualActualTask(AbstractSched sched) {
            sched.forgetCards(mCardIds);
        }
    }

    public static class RebuildCram implements TaskDelegate<Void, StudyOptionsFragment.DeckStudyData> {
        public StudyOptionsFragment.DeckStudyData task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundRebuildCram");
            col.getSched().rebuildDyn(col.getDecks().selected());
            return updateValuesFromDeck(col, true);
        }
    }

    public static class EmptyCram implements TaskDelegate<Void, StudyOptionsFragment.DeckStudyData> {
        public StudyOptionsFragment.DeckStudyData task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Void> collectionTask) {
            Timber.d("doInBackgroundEmptyCram");
            col.getSched().emptyDyn(col.getDecks().selected());
            return updateValuesFromDeck(col, true);
        }
    }
}
