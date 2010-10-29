/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.ContentValues;

/**
 * Review history of a card.
 */
public class CardHistoryEntry {

    // BEGIN: SQL table columns
    private long mCardId;
    private double mTime;
    private double mLastInterval;
    private double mNextInterval;
    private int mEase;
    private double mDelay;
    private double mLastFactor;
    private double mNextFactor;
    private double mReps;
    private double mThinkingTime;
    private double mYesCount;
    private double mNoCount;
    // END: SQL table columns

    private Deck mDeck;


    /**
     * Constructor.
     */
    public CardHistoryEntry(Deck deck, Card card, int ease, double delay) {
        mDeck = deck;

        if (card == null) {
            return;
        }

        mCardId = card.getId();
        mLastInterval = card.getLastInterval();
        mNextInterval = card.getInterval();
        mLastFactor = card.getLastFactor();
        mNextFactor = card.getFactor();
        mReps = card.getReps();
        mYesCount = card.getYesCount();
        mNoCount = card.getNoCount();
        mEase = ease;
        mDelay = delay;
        mThinkingTime = card.thinkingTime();
    }


    /**
     * Write review history to the database.
     */
    public void writeSQL() {
        ContentValues values = new ContentValues();
        values.put("cardId", mCardId);
        values.put("lastInterval", mLastInterval);
        values.put("nextInterval", mNextInterval);
        values.put("ease", mEase);
        values.put("delay", mDelay);
        values.put("lastFactor", mLastFactor);
        values.put("nextFactor", mNextFactor);
        values.put("reps", mReps);
        values.put("thinkingTime", mThinkingTime);
        values.put("yesCount", mYesCount);
        values.put("noCount", mNoCount);
        values.put("time", Utils.now());

        AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().insert("reviewHistory", null, values);
    }
}
