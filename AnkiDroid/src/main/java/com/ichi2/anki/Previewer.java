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

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.ichi2.libanki.Collection;
import com.ichi2.themes.Themes;

import androidx.core.content.res.ResourcesCompat;
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
    private float mAnimTranslation;

    // Buttons state for animation handling
    private boolean mPrevBtnShown = false;
    private boolean mNextBtnShown = true;

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

        mAnimTranslation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                getResources().getDisplayMetrics());
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

        mPreviewToggleAnswer.setOnClickListener(mToggleAnswerHandler);

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
        updateButtonState();
    }


    // Called via mFlipCardListener in parent class when answer button pressed
    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        mShowingAnswer = true;
        updateButtonState();
    }


    @Override
    protected void hideEaseButtons() {
        final int[] textColor = Themes.getColorFromAttr(this, new int[] {R.attr.largeButtonTextColor});

        setRippleBackground(this, mPreviewToggleAnswer, R.drawable.preview_flashcard_show_answer_background);
        mPreviewToggleAnswer.setText(R.string.show_answer);
        mPreviewToggleAnswer.setTextColor(textColor[0]);
    }


    @Override
    protected void displayAnswerBottomBar() {
        final int[] textColor = Themes.getColorFromAttr(this, new int[] {R.attr.largeButtonSecondaryTextColor});

        setRippleBackground(this, mPreviewToggleAnswer, R.drawable.preview_flashcard_hide_answer_background);
        mPreviewToggleAnswer.setText(R.string.hide_answer);
        mPreviewToggleAnswer.setTextColor(textColor[0]);
    }

    public static void setRippleBackground(Context context, View view, int resId) {
        if (Build.VERSION.SDK_INT >= 21) {
            RippleDrawable from = (RippleDrawable) view.getBackground().mutate();
            RippleDrawable to =
                    (RippleDrawable) ResourcesCompat.getDrawable(context.getResources(), resId, context.getTheme());

            if (to != null) {
                Drawable underlyingBackground = to.findDrawableByLayerId(R.id.underlying_background);
                from.setDrawableByLayerId(R.id.underlying_background, underlyingBackground);
            }
        } else {
            view.setBackgroundResource(resId);
        }
    }


    // we don't want the Activity title to be changed.
    @Override
    protected void updateScreenCounts() { /* do nothing */ }


    @Override
    public boolean executeCommand(int which) {
        /* do nothing */
        return false;
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

    private void updateButtonState() {
        // If we are in single-card mode, we show the "Show Answer" button on the question side
        // and hide all the buttons on the answer side.
        if (mCardList.length == 1) {
            mPreviewPrevCard.setVisibility(View.GONE);
            mPreviewNextCard.setVisibility(View.GONE);
            return;
        }

        boolean prevBtnDisabled = mIndex <= 0;
        boolean nextBtnDisabled = mIndex >= mCardList.length - 1;

        mPreviewPrevCard.setEnabled(!prevBtnDisabled);
        mPreviewNextCard.setEnabled(!nextBtnDisabled);

        if (mSafeDisplay) {
            mPreviewPrevCard.setVisibility(prevBtnDisabled ? View.INVISIBLE : View.VISIBLE);
            mPreviewNextCard.setVisibility(nextBtnDisabled ? View.INVISIBLE : View.VISIBLE);
        } else {
            // should we move these animation methods out?

            if (prevBtnDisabled && mPrevBtnShown) {
                DeckPicker.fadeOut(mPreviewPrevCard, mShortAnimDuration, mAnimTranslation, null);
                mPrevBtnShown = false;
            }
            if (!prevBtnDisabled && !mPrevBtnShown) {
                DeckPicker.fadeIn(mPreviewPrevCard, mShortAnimDuration, mAnimTranslation);
                mPrevBtnShown = true;
            }

            if (nextBtnDisabled && mNextBtnShown) {
                DeckPicker.fadeOut(mPreviewNextCard, mShortAnimDuration, mAnimTranslation, null);
                mNextBtnShown = false;
            }
            if (!nextBtnDisabled && !mNextBtnShown) {
                DeckPicker.fadeIn(mPreviewNextCard, mShortAnimDuration, mAnimTranslation);
                mNextBtnShown = true;
            }
        }
    }
}
