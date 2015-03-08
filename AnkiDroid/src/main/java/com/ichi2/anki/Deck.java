package com.ichi2.anki;

/****************************************************************************************
 * Copyright (c) 2015 Allison Van Pelt <abvanpelt@gmail.com>                            *
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
public class Deck {

    private String mDeckName;
    private long mDeckId;

    private int mNewCardsDue;
    private int mLearnedCardsDue;
    private int mReviewCardsDue;

    private boolean mIsDynamic;

    // private float mComplMat; // are these needed anymore?
    // private float mComplAll;

    public void Deck() {
    }

    public String getDeckName() {
        return mDeckName;
    }

    public void setDeckName(String mDeckName) {
        this.mDeckName = mDeckName;
    }

    public long getDeckId() {
        return mDeckId;
    }

    public void setDeckId(long mDeckId) {
        this.mDeckId = mDeckId;
    }

    public int getNewCardsDue() {
        return mNewCardsDue;
    }

    public void setNewCardsDue(int mNewCards) {
        this.mNewCardsDue = mNewCards;
    }

    public int getLearnedCardsDue() {
        return mLearnedCardsDue;
    }

    public void setLearnedCardsDue(int mLearnedCards) {
        this.mLearnedCardsDue = mLearnedCards;
    }

    public int getReviewCardsDue() {
        return mReviewCardsDue;
    }

    public void setReviewCardsDue(int mReviewCards) {
        this.mReviewCardsDue = mReviewCards;
    }

    public boolean isIsDynamic() {
        return mIsDynamic;
    }

    public void setIsDynamic(boolean mIsDynamic) {
        this.mIsDynamic = mIsDynamic;
    }
}
