/****************************************************************************************
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki;

import android.content.res.Resources;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.ProgressSenderAndCancelListener;
import com.ichi2.async.TaskDelegate;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.utils.ArrayUtil;
import com.ichi2.utils.Computation;
import com.ichi2.utils.LanguageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

public abstract class UndoAction {
    @StringRes public final int mUndoNameId;

    /**
     * For all descendants, we assume that a card/note/object passed as argument is never going to be changed again.
     * It's the caller reponsability to clone the object if necessary.*/
    public UndoAction(@StringRes int undoNameId) {
        mUndoNameId = undoNameId;
    }

    private Locale getLocale(Resources resources) {
        return LanguageUtil.getLocaleCompat(resources);
    }
    public String name(Resources res) {
        return res.getString(mUndoNameId).toLowerCase(getLocale(res));
    }

    /**
     * Return MULTI_CARD when no other action is needed, e.g. for multi card action
     * Return NO_REVIEW when we just need to reset the collection
     * Returned positive integers are card id. Those ids is the card that was discarded and that may be sent back to the reviewer.*/
    public abstract @Nullable Card undo(@NonNull Collection col);

    /**
     * Create an UndoAction that set back `card` and its siblings to the current states.
     * @param undoNameId The id of the string representing an action that could be undone
     * @param card the card currently in the reviewer
     * @return An UndoAction which, if executed, put back the `card` in the state given here
     */
    public static @NonNull UndoAction revertNoteToProvidedState(@StringRes int undoNameId, Card card){
        return revertToProvidedState(undoNameId, card, card.note().cards());
    }

    /**
     * Create an UndoAction that set back `card` and its siblings to the current states.
     * @param undoNameId The id of the string representing an action that could be undone
     * @param card the card currently in the reviewer
     * @return An UndoAction which, if executed, put back the `card` in the state given here
     */
    public static @NonNull UndoAction revertCardToProvidedState(@StringRes int undoNameId, Card card){
        return revertToProvidedState(undoNameId, card, Arrays.asList(card.clone()));
    }


    /**
     * Create an UndoAction that set back `card` and its siblings to the current states.
     * @param undoNameId The id of the string representing an action that could be undone
     * @param card the card currently in the reviewer
     * @param cards The cards that must be reverted
     * @return An UndoAction which, if executed, put back the `card` in the state given here
     */
    private static @NonNull UndoAction revertToProvidedState(@StringRes int undoNameId, Card card, Iterable<Card> cards){
        return new UndoAction(undoNameId) {
            public @Nullable
            Card undo(@NonNull Collection col) {
                Timber.i("Undo: %d", undoNameId);
                for (Card cc : cards) {
                    cc.flush(false);
                }
                return card;
            }
        };
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


    public static class Undo implements TaskDelegate<Card, Computation<?>> {
        public Computation<?> task(@NonNull Collection col, @NonNull ProgressSenderAndCancelListener<Card> collectionTask) {
            try {
                col.getDb().executeInTransaction(() -> {
                    Card card = nonTaskUndo(col);
                    collectionTask.doProgress(card);
                });
            } catch (RuntimeException e) {
                Timber.e(e, "doInBackgroundUndo - RuntimeException on undoing");
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUndo");
                return Computation.ERR;
            }
            return Computation.OK;
        }
    }
}
