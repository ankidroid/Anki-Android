package com.ichi2.libanki;

import android.content.res.Resources;

import com.ichi2.utils.ArrayUtil;
import com.ichi2.utils.LanguageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
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
}
