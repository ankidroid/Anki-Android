package com.ichi2.libanki;

import android.content.res.Resources;

import com.ichi2.utils.LanguageUtil;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import timber.log.Timber;

public abstract class Undoable {
    @StringRes public final int mUndoNameId;

    /**
     * For all descendants, we assume that a card/note/object passed as argument is never going to be changed again.
     * It's the caller reponsability to clone the object if necessary.*/
    public Undoable(@StringRes int undoNameId) {
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

    public static @NonNull Undoable revertToProvidedState (@StringRes int undoNameId, Card card){
        Note note = card.note();
        List<Card> cards = note.cards();
        return new Undoable(undoNameId) {
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
