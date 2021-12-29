//noinspection MissingCopyrightHeader #8659
package com.ichi2.libanki.backend.exception;

import android.content.res.Resources;

import com.ichi2.anki.R;

import androidx.annotation.Nullable;

public class DeckRenameException extends Exception {

    public static final int ALREADY_EXISTS = 0;
    private static final int FILTERED_NOSUBDECKS = 1;

    private final int mErrorCode;

    // region only if FILTERED_NOSUBDECKS
    private String mFilteredAncestorName;
    private String mDeckName;
    // endregion

    public DeckRenameException(int errorCode) {
        super();
        mErrorCode = errorCode;
    }


    /** Generates a {@link com.ichi2.libanki.backend.exception.DeckRenameException} with additional information in the message */
    public static DeckRenameException filteredAncestor(String deckName, String filteredAncestorName) {
        DeckRenameException ex = new DeckRenameException(FILTERED_NOSUBDECKS);
        ex.mFilteredAncestorName = filteredAncestorName;
        ex.mDeckName = deckName;
        return ex;
    }


    @Nullable
    @Override
    public String getMessage() {
        if (mErrorCode == FILTERED_NOSUBDECKS) {
            return "Deck " + mDeckName + " has filtered ancestor " + mFilteredAncestorName;
        }
        return super.getMessage();
    }


    public String getLocalizedMessage(Resources res) {
        switch (mErrorCode) {
            case ALREADY_EXISTS:
                return res.getString(R.string.decks_rename_exists);
            case FILTERED_NOSUBDECKS:
                return res.getString(R.string.decks_rename_filtered_nosubdecks);
            default:
                return "";
        }
    }
}
