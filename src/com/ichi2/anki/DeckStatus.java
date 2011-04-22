package com.ichi2.anki;

/**
 * Simple class to hold the current status of a deck.
 */
public class DeckStatus {
    public String mDeckName;
    public int mNewCards;
    public int mDueCards;
    public int mFailedCards;


    public DeckStatus(String deckName, int newCards, int dueCards, int failedCards) {
        mDeckName = deckName;
        mNewCards = newCards;
        mDueCards = dueCards;
        mFailedCards = failedCards;
    }
}