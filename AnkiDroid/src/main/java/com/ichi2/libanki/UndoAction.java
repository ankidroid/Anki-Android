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

import com.ichi2.anki.R;
import com.ichi2.utils.ArrayUtil;
import com.ichi2.utils.LanguageUtil;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import timber.log.Timber;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public abstract class UndoAction {
    @StringRes @UNDO_NAME_ID public final int mUndoNameId;

    @Retention(SOURCE)
    @IntDef( {
            R.string.undo_action_change_deck_multi,
            R.string.menu_delete_note,
            R.string.card_browser_delete_card,
            R.string.card_browser_mark_card,
            R.string.card_browser_unmark_card,
            R.string.menu_suspend_card,
            R.string.card_browser_unsuspend_card,
            R.string.undo_action_review,
            R.string.menu_bury_note,
            R.string.menu_suspend_note,
            R.string.card_editor_reposition_card,
            R.string.card_editor_reschedule_card,
            R.string.menu_bury_card,
            R.string.card_editor_reset_card,

    })
    public @interface UNDO_NAME_ID {}


    /**
     * For all descendants, we assume that a card/note/object passed as argument is never going to be changed again.
     * It's the caller reponsability to clone the object if necessary.*/
    public UndoAction(@StringRes @UNDO_NAME_ID int undoNameId) {
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
    public static @NonNull UndoAction revertNoteToProvidedState(@StringRes @UNDO_NAME_ID int undoNameId, Card card){
        return revertToProvidedState(undoNameId, card, card.note().cards());
    }

    /**
     * Create an UndoAction that set back `card` and its siblings to the current states.
     * @param undoNameId The id of the string representing an action that could be undone
     * @param card the card currently in the reviewer
     * @return An UndoAction which, if executed, put back the `card` in the state given here
     */
    public static @NonNull UndoAction revertCardToProvidedState(@StringRes @UNDO_NAME_ID int undoNameId, Card card){
        return revertToProvidedState(undoNameId, card, Arrays.asList(card.clone()));
    }


    /**
     * Create an UndoAction that set back `card` and its siblings to the current states.
     * @param undoNameId The id of the string representing an action that could be undone
     * @param card the card currently in the reviewer
     * @param cards The cards that must be reverted
     * @return An UndoAction which, if executed, put back the `card` in the state given here
     */
    private static @NonNull UndoAction revertToProvidedState(@StringRes @UNDO_NAME_ID int undoNameId, Card card, Iterable<Card> cards){
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
}
