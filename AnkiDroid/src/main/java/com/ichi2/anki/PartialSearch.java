/*
 Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.ProgressSender;
import com.ichi2.async.ProgressSenderAndCancelListener;
import com.ichi2.libanki.Collection;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * A class allowing to send partial search result to the browser to display while the search ends
 */
public class PartialSearch implements ProgressSenderAndCancelListener<List<Long>> {
    private final List<CardBrowser.CardCache> mCards;
    private final int mColumn1Index, mColumn2Index;
    private final int mNumCardsToRender;
    private final ProgressSenderAndCancelListener<List<CardBrowser.CardCache>> mCollectionTask;
    private final Collection mCol;

    public PartialSearch(List<CardBrowser.CardCache> cards, int columnIndex1, int columnIndex2, int numCardsToRender, ProgressSenderAndCancelListener<List<CardBrowser.CardCache>> collectionTask, Collection col) {
        mCards = new ArrayList<>(cards);
        mColumn1Index = columnIndex1;
        mColumn2Index = columnIndex2;
        mNumCardsToRender = numCardsToRender;
        mCollectionTask = collectionTask;
        mCol = col;
    }

    @Override
    public boolean isCancelled() {
        return mCollectionTask.isCancelled();
    }


    /**
     * @param cards Card ids to display in the browser. It is assumed that it is as least as long as mCards, and that
     *             mCards[i].cid = cards[i].  It add the cards in cards after `mPosition` to mCards
     */
    public void add(@NonNull List<Long> cards) {
        while (mCards.size() < cards.size()) {
            mCards.add(new CardBrowser.CardCache(cards.get(mCards.size()), mCol, mCards.size()));
        }
    }


    @Override
    public void doProgress(@NonNull List<Long> value) {
        // PERF: This is currently called on the background thread and blocks further execution of the search
        // PERF: This performs an individual query to load each note
        add(value);
        for (CardBrowser.CardCache card : mCards) {
            if (isCancelled()) {
                Timber.d("doInBackgroundSearchCards was cancelled so return");
                return;
            }
            card.load(false, mColumn1Index, mColumn2Index);
        }
        mCollectionTask.doProgress(mCards);
    }

    public int getNumCardsToRender() {
        return mNumCardsToRender;
    }


    public ProgressSender<Long> getProgressSender() {
        return new ProgressSender<Long>() {
            private final List<Long> mRes = new ArrayList<>();
            private boolean mSendProgress = true;
            @Override
            public void doProgress(@Nullable Long value) {
                if (!mSendProgress) {
                    return;
                }
                mRes.add(value);
                if (mRes.size() >= getNumCardsToRender()) {
                    PartialSearch.this.doProgress(mRes);
                    mSendProgress = false;
                }
            }
        };
    }
}
