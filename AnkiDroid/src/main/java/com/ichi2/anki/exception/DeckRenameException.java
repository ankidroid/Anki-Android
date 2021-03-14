
package com.ichi2.anki.exception;

import android.content.res.Resources;

import com.ichi2.anki.R;

public class DeckRenameException extends Exception {

    public static final int ALREADY_EXISTS = 0;
    public static final int FILTERED_NOSUBDECKS = 1;

    private final int mErrorCode;

    public DeckRenameException(int errorCode) {
        super();
        mErrorCode = errorCode;
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
