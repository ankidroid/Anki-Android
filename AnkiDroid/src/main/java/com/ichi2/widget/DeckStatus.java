
package com.ichi2.widget;

/**
 * Simple class to hold the current status of a deck.
 */
public class DeckStatus {
    public long mDeckId;
    public String mDeckName;
    public int mNewCards;
    public int mLrnCards;
    public int mDueCards;
    public int mProgress;
    public int mEta;


    public DeckStatus(long deckId, String deckName, int newCards, int lrnCards, int dueCards, int progress, int eta) {
        mDeckId = deckId;
        mDeckName = deckName;
        mNewCards = newCards;
        mLrnCards = lrnCards;
        mDueCards = dueCards;
        mProgress = progress;
        mEta = eta;
    }
}
