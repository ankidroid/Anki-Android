/***************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Jolta Technologies                                                *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
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

import android.os.Bundle;
import android.view.View;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

/**
 * The previewer intent must supply an array of cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */
public class Previewer extends AbstractFlashcardViewer {
    private long[] mCardList;
    private int mIndex;
    private boolean mShowingAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        mCardList = getIntent().getLongArrayExtra("cardList");
        mIndex = getIntent().getIntExtra("index", -1);

        if (savedInstanceState != null){
            mIndex = savedInstanceState.getInt("index", mIndex);
            mShowingAnswer = savedInstanceState.getBoolean("showingAnswer", mShowingAnswer);
        }

        if (mCardList.length == 0 || mIndex < 0 || mIndex > mCardList.length - 1) {
            Timber.e("Previewer started with empty card list or invalid index");
            finishWithoutAnimation();
            return;
        }
        showBackIcon();
        // Ensure navigation drawer can't be opened. Various actions in the drawer cause crashes.
        disableDrawerSwipe();
        startLoadingCollection();
    }

    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        mCurrentCard = col.getCard(mCardList[mIndex]);

        displayCardQuestion();
        if (mShowingAnswer) {
            displayCardAnswer();
        }

        showBackIcon();
    }


    @Override
    protected void setTitle() {
        getSupportActionBar().setTitle(R.string.preview_title);
    }


    @Override
    protected void initLayout() {
        super.initLayout();
        mTopBarLayout.setVisibility(View.GONE);

        findViewById(R.id.answer_options_layout).setVisibility(View.GONE);

        mPreviewButtonsLayout.setVisibility(View.VISIBLE);
        mPreviewButtonsLayout.setOnClickListener(mToggleAnswerHandler);

        mPreviewPrevCard.setOnClickListener(mSelectScrollHandler);
        mPreviewNextCard.setOnClickListener(mSelectScrollHandler);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLongArray("cardList", mCardList);
        outState.putInt("index", mIndex);
        outState.putBoolean("showingAnswer", mShowingAnswer);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void displayCardQuestion() {
        super.displayCardQuestion();
        mShowingAnswer = false;
        updateButtonsState();
    }


    // Called via mFlipCardListener in parent class when answer button pressed
    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        mShowingAnswer = true;
        updateButtonsState();
    }


    @Override
    protected void hideEaseButtons() {
        /* do nothing */
    }

    @Override
    protected void displayAnswerBottomBar() {
        /* do nothing */
    }


    // we don't want the Activity title to be changed.
    @Override
    protected void updateScreenCounts() { /* do nothing */ }


    @Override
    public boolean executeCommand(int which) {
        /* do nothing */
        return false;
    }

    @Override
    protected void performReload() {
        // This should not happen.
        finishWithAnimation(ActivityTransitionAnimation.RIGHT);
    }

    private View.OnClickListener mSelectScrollHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view.getId() == R.id.preview_previous_flashcard) {
                mIndex--;
            } else if (view.getId() == R.id.preview_next_flashcard) {
                mIndex++;
            }

            mCurrentCard = getCol().getCard(mCardList[mIndex]);
            displayCardQuestion();
        }
    };

    private View.OnClickListener mToggleAnswerHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mShowingAnswer) {
                displayCardQuestion();
            } else {
                displayCardAnswer();
            }
        }
    };

    private void updateButtonsState() {
        // If we are in single-card mode, we show the "Show Answer" button on the question side
        // and hide navigation buttons.
        if (mCardList.length == 1) {
            mPreviewPrevCard.setVisibility(View.GONE);
            mPreviewNextCard.setVisibility(View.GONE);
            return;
        }

        mPreviewToggleAnswerText.setText(mShowingAnswer ? R.string.hide_answer : R.string.show_answer);

        boolean prevBtnDisabled = mIndex <= 0;
        boolean nextBtnDisabled = mIndex >= mCardList.length - 1;

        mPreviewPrevCard.setEnabled(!prevBtnDisabled);
        mPreviewNextCard.setEnabled(!nextBtnDisabled);

        mPreviewPrevCard.setAlpha(prevBtnDisabled ? 0.38F : 1);
        mPreviewNextCard.setAlpha(nextBtnDisabled ? 0.38F : 1);
    }
}
