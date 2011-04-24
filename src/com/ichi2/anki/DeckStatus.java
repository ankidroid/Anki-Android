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

    public DeckStatus(String deckPath, String deckName, int newCards, int dueCards,
            int failedCards) {
        mDeckPath = deckPath;
        mDeckName = deckName;
        mNewCards = newCards;
        mDueCards = dueCards;
        mFailedCards = failedCards;
    }
}