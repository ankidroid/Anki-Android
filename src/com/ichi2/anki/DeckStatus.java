package com.ichi2.anki;

/**
 * Simple class to hold the current status of a deck.
 */
public class DeckStatus {
    public String mDeckPath;
    public String mDeckName;
    public int mNewCards;
    public int mDueCards;
    public int mFailedCards;
    public int mEta;
    public int mReps;

    public DeckStatus(String deckPath, String deckName, int newCards, int dueCards,
            int failedCards, int eta, int reps) {
        mDeckPath = deckPath;
        mDeckName = deckName;
        mNewCards = newCards;
        mDueCards = dueCards;
        mFailedCards = failedCards;
        mEta = eta;
        mReps = reps;
    }
}