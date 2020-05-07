package com.ichi2.libanki.exception;

/**
 * A deck was accessed which did not exist.
 * <br/>
 * Remarks: We use this checked exception to mimic an Optional before Java 1.8.
 */
public class NoSuchDeckException extends Exception {
    private final long mDeckId;

    public NoSuchDeckException(long deckId) {
        this.mDeckId = deckId;
    }

    /** The ID of the accessed deck */
    public long getDeckId() {
        return mDeckId;
    }
}
