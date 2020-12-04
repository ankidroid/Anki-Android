package com.ichi2.libanki;

import android.content.res.Resources;

import com.ichi2.libanki.Collection.DismissType;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public abstract class Undoable {
    private final DismissType mDt;

    /**
     * For all descendants, we assume that a card/note/object passed as argument is never going to be changed again.
     * It's the caller reponsability to clone the object if necessary.*/
    public Undoable(DismissType dt) {
        mDt = dt;
    }

    public String name(Resources res) {
        return mDt.getString(res);
    }

    public DismissType getDismissType() {
        return mDt;
    }

    /**
     * Return MULTI_CARD when no other action is needed, e.g. for multi card action
     * Return NO_REVIEW when we just need to reset the collection
     * Returned positive integers are card id. Those ids is the card that was discarded and that may be sent back to the reviewer.*/
    public abstract @Nullable Card undo(@NonNull Collection col);

    public static @NonNull Undoable revertToProvidedState (DismissType dt, Card card){
        Note note = card.note();
        List<Card> cards = note.cards();
        return new Undoable(dt) {
            public @Nullable
            Card undo(@NonNull Collection col) {
                Timber.i("Undo: %s", dt);
                for (Card cc : cards) {
                    cc.flush(false);
                }
                return card;
            }
        };
    }
}
