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
import android.os.Handler;
import android.text.Layout;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.Themes;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Collections;

import timber.log.Timber;

/**
 * The previewer intent must supply an array of cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */

/** Implement the AutoPlay function, including basic AutoPlay and shuffle AutoPlay.
 *
 * Basic AutoPlay automatically plays the CardList from the beginning to the end once.
 * Shuffle AutoPlay plays the CardList from the beginning to the end once with a random order.
 *
 * By SUSTech Group, 05/31/20.
 * */
public class Previewer extends AbstractFlashcardViewer {
    private long[] mCardList;
    private int mIndex;
    private boolean mShowingAnswer;

    /** LinearLayout of Shuffle AutoPlay mode.*/
    private LinearLayout mShufflePlayLayout;

    /** LinearLayout of Basic AutoPlay mode.*/
    private LinearLayout mAutoPlayLayout;

    /** Represent the review play model, default is 0.
     * 0 is Manual mode, 1 is AutoPlay mode, 2 is Shuffle AutoPlay Mode.*/
    private int mPlayMode;

    /** Flag to represent Manual Mode or Auto Mode*/
    private boolean mAuto;

    /** Timer for Auto Play mode.*/
    private Timer mTimer;

    /** Contain the main function code of 2 types of AutoPlay.*/
    private TimerTask mTimerTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        mCardList = getIntent().getLongArrayExtra("cardList");
        mIndex = getIntent().getIntExtra("index", -1);

        // Get two LinearLayouts to implement the auto play function.
        mShufflePlayLayout = findViewById(R.id.shufflePlay_layout);
        mAutoPlayLayout = findViewById(R.id.autoPlay_layout);
        // Initial play mode is 0.
        mPlayMode = 0;

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
        if (mShowingAnswer){
            displayCardQuestion();
            displayCardAnswer();
        } else {
            displayCardQuestion();
        }
        showBackIcon();
    }

    /** Using Timer + Handler to implement AutoPlay and Modify the UI.
     * The Default time of each card is  2s for question, 2s for answer, 4s in total.
     * */
    protected void autoPlay(){
        // The basic autoPlay mode, no shuffle cards.
        Handler handler = new Handler();
        mTimer = new Timer();

        if (mPlayMode == 1){

            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    if (mIndex < mCardList.length-1){
                        handler.postDelayed(() -> {
                            if (mShowingAnswer) {
                                mIndex++;
                                mCurrentCard = getCol().getCard(mCardList[mIndex]);
                                displayCardQuestion();
                            } else {
                                displayCardAnswer();
                            }
                        },1);
                    }else if (!mShowingAnswer){
                        handler.postDelayed(()->{
                            displayCardAnswer();
                        },1);
                    }else {
                        this.cancel();
                    }
                }
            };
            mTimer.schedule(mTimerTask,2000,2000);
        }else if (mPlayMode == 2){
            // Shuffle AutoPlay Mode.
            List<Integer> shuffle = new ArrayList<>();
            for (int i=0; i<mCardList.length; i++){
                shuffle.add(i);
            }
            Collections.shuffle(shuffle);
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    if (mIndex < mCardList.length-1){
                        handler.postDelayed(() -> {
                            if (mShowingAnswer) {
                                mIndex++;
                                mCurrentCard = getCol().getCard(mCardList[shuffle.get(mIndex)]);
                                displayCardQuestion();
                            } else {
                                displayCardAnswer();
                            }
                        },1);
                    }else if (!mShowingAnswer){
                        handler.postDelayed(()->{
                            displayCardAnswer();
                        },1);
                    }else {
                        this.cancel();
                    }
                }
            };
            mTimer.schedule(mTimerTask, 2000,2000);
        }
    }


    @Override
    protected void setTitle() {
        getSupportActionBar().setTitle(R.string.preview_title);
    }


    @Override
    protected void initLayout() {
        super.initLayout();
        mTopBarLayout.setVisibility(View.GONE);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("index", mIndex);
        outState.putBoolean("showingAnswer", mShowingAnswer);
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
            if (mShowingAnswer) {
                // If we are showing the answer, any click will show a question...
                if (view.getId() == R.id.flashcard_layout_ease2) {
                    // ...but if they clicked "forward" we need to move to the next card first
                    mIndex++;
                    mCurrentCard = getCol().getCard(mCardList[mIndex]);
                }
                displayCardQuestion();
            } else {
                // If we are showing the question, any click will show an answer...
                if (view.getId() == R.id.flashcard_layout_ease1) {
                    // ...but if they clicked "reverse" we need to go to the previous card first
                    mIndex--;
                    mCurrentCard = getCol().getCard(mCardList[mIndex]);
                }
                displayCardAnswer();
            }
        }
    };

    /** Set the ClickListener of mAutoPlay layout.
     *  Switch to Auto Play mode, if in Manual mode. Otherwise, do not modify the mPlayMode.
     *  Change flag mAuto each click, which mean a switch a AutoPlay and Manual Play.
     * */
    private View.OnClickListener mAutoPlayHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mAuto = !mAuto;
            if (mPlayMode!=2){
                mPlayMode = 1;
            }
            if (mAuto){
                mAutoPlayLayout.setBackgroundColor(0xffcfd8dc);
                autoPlay();
            }else {
                mAutoPlayLayout.setBackgroundColor(0xffeceff1);
                mTimer.cancel();
                mTimer = null;
                mTimerTask.cancel();
                mTimerTask = null;
            }
        }
    };


    /**Set the ClickListener of mShuffleAutoPlay layout.
     * Only change the value of mPlayMode, do not directly invoke autoPlay() function,
     * change the background color with the click event, to give a feedback.
     * One click to set Shuffle AutoPlay mode, another click to cancel, like a Switch.
     * */
    private View.OnClickListener mShuffleHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mPlayMode!=2){
                mShufflePlayLayout.setBackgroundColor(0xffcfd8dc);
                mPlayMode = 2;
            }else {
                mShufflePlayLayout.setBackgroundColor(0xffeceff1);
                mPlayMode = 1;
            }
        }
    };

    private void updateButtonState() {
        // If we are in single-card mode, we show the "Show Answer" button on the question side
        // and hide all the button s on the answer side.

        //Visualize the two layout
        mShufflePlayLayout.setVisibility(View.VISIBLE);
        mAutoPlayLayout.setVisibility(View.VISIBLE);

        //Set ClickListener for the Two Layouts.
        mShufflePlayLayout.setOnClickListener(mShuffleHandler);
        mAutoPlayLayout.setOnClickListener(mAutoPlayHandler);

        if (mCardList.length == 1) {
            if (!mShowingAnswer) {
                mFlipCardLayout.setVisibility(View.VISIBLE);
            } else {
                findViewById(R.id.answer_options_layout).setVisibility(View.GONE);
                mFlipCardLayout.setVisibility(View.GONE);
                hideEaseButtons();
            }
            return;
        }

        mFlipCardLayout.setVisibility(View.GONE);
        mEase1Layout.setVisibility(View.VISIBLE);
        mEase2Layout.setVisibility(View.VISIBLE);
        mEase3Layout.setVisibility(View.GONE);
        mEase4Layout.setVisibility(View.GONE);

        final int[] background = Themes.getResFromAttr(this, new int[]{R.attr.hardButtonRef});
        final int[] textColor = Themes.getColorFromAttr(this, new int[]{R.attr.hardButtonTextColor});

        mNext1.setTextSize(30);
        mEase1.setVisibility(View.GONE);
        mNext1.setTextColor(textColor[0]);
        mEase1Layout.setOnClickListener(mSelectScrollHandler);
        mEase1Layout.setBackgroundResource(background[0]);

        mNext2.setTextSize(30);
        mEase2.setVisibility(View.GONE);
        mNext2.setTextColor(textColor[0]);
        mEase2Layout.setOnClickListener(mSelectScrollHandler);
        mEase2Layout.setBackgroundResource(background[0]);

        if (mIndex == 0 && !mShowingAnswer) {
            mEase1Layout.setEnabled(false);
            mNext1.setText("-");
        } else {
            mEase1Layout.setEnabled(true);
            mNext1.setText("<");
        }

        if (mIndex == mCardList.length - 1 && mShowingAnswer) {
            mEase2Layout.setEnabled(false);
            mNext2.setText("-");
        } else {
            mEase2Layout.setEnabled(true);
            mNext2.setText(">");
        }
    }
}
