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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ichi2.anki.cardviewer.PreviewLayout;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;

import java.util.HashSet;
import java.util.List;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
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
    private SeekBar mProgressSeekBar;
    private TextView mProgressText;

    /** Communication with Browser */
    private boolean mReloadRequired;
    private boolean mNoteChanged;

    protected PreviewLayout mPreviewLayout;

    @CheckResult
    @NonNull
    public static Intent getPreviewIntent(Context context, int index, long[] cardList) {
        Intent intent = new Intent(context, Previewer.class);
        intent.putExtra("index", index);
        intent.putExtra("cardList", cardList);
        return intent;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        mCardList = getIntent().getLongArrayExtra("cardList");
        mIndex = getIntent().getIntExtra("index", -1);

        if (savedInstanceState != null) {
            mIndex = savedInstanceState.getInt("index", mIndex);
            mShowingAnswer = savedInstanceState.getBoolean("showingAnswer", mShowingAnswer);
            mReloadRequired = savedInstanceState.getBoolean("reloadRequired");
            mNoteChanged = savedInstanceState.getBoolean("noteChanged");
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
        initPreviewProgress();
    }

    private void initPreviewProgress() {
        mProgressSeekBar = findViewById(R.id.preview_progress_seek_bar);
        mProgressText = findViewById(R.id.preview_progress_text);
        LinearLayout progressLayout = findViewById(R.id.preview_progress_layout);

        //Show layout only when the cardList is bigger than 1
        if (mCardList.length > 1) {
            progressLayout.setVisibility(View.VISIBLE);
            mProgressSeekBar.setMax(mCardList.length - 1);
            setSeekBarListener();
            updateProgress();
        }
    }

    private void setSeekBarListener() {
        mProgressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mIndex = progress;
                    updateProgress();
                    mCurrentCard = getCol().getCard(mCardList[mIndex]);
                    displayCardQuestion();

                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Mandatory override, but unused
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mIndex >= 0 && mIndex < mCardList.length) {
                    mCurrentCard = getCol().getCard(mCardList[mIndex]);
                    displayCardQuestion();
                }
            }
        });
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

    /** Given a new collection of card Ids, find the 'best' valid card given the current collection
     * We define the best as searching to the left, then searching to the right of the current element
     * This occurs as many cards can be deleted when editing a note (from the Card Template Editor) */
    private int getNextIndex(List<Long> newCardList) {
        HashSet<Long> validIndices = new HashSet<>(newCardList);

        for (int i = mIndex; i >= 0; i--) {
            if (validIndices.contains(mCardList[i])) {
                return newCardList.indexOf(mCardList[i]);
            }
        }

        for (int i = mIndex + 1; i < validIndices.size(); i++) {
            if (validIndices.contains(mCardList[i])) {
                return newCardList.indexOf(mCardList[i]);
            }
        }

        throw new IllegalStateException("newCardList was empty");
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

        mPreviewLayout = PreviewLayout.createAndDisplay(this, mToggleAnswerHandler);
        mPreviewLayout.setOnNextCard((view) -> changePreviewedCard(true));
        mPreviewLayout.setOnPreviousCard((view) -> changePreviewedCard(false));
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            editCard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        setResult(RESULT_OK, getResultIntent());
        super.onBackPressed();
    }


    @Override
    protected void onNavigationPressed() {
        setResult(RESULT_OK, getResultIntent());
        super.onNavigationPressed();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.previewer, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLongArray("cardList", mCardList);
        outState.putInt("index", mIndex);
        outState.putBoolean("showingAnswer", mShowingAnswer);
        outState.putBoolean("reloadRequired", mReloadRequired);
        outState.putBoolean("noteChanged", mNoteChanged);
        super.onSaveInstanceState(outState);
    }


    @Override
    public void displayCardQuestion() {
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


    @Override
    public boolean executeCommand(@NonNull ViewerCommand which) {
        /* do nothing */
        return false;
    }


    @Override
    protected void performReload() {
        mReloadRequired = true;
        List<Long> newCardList = getCol().filterToValidCards(mCardList);

        if (newCardList.isEmpty()) {
            finishWithoutAnimation();
            return;
        }

        mIndex = getNextIndex(newCardList);
        mCardList = Utils.collection2Array(newCardList);
        mCurrentCard = getCol().getCard(mCardList[mIndex]);
        displayCardQuestion();
    }


    @Override
    protected void onEditedNoteChanged() {
        super.onEditedNoteChanged();
        mNoteChanged = true;
    }


    protected void changePreviewedCard(boolean nextCard) {
        mIndex = nextCard ? mIndex + 1 : mIndex - 1;
        mCurrentCard = getCol().getCard(mCardList[mIndex]);
        displayCardQuestion();
        updateProgress();
    }


    private final View.OnClickListener mToggleAnswerHandler = new View.OnClickListener() {
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
        mPreviewLayout.setShowingAnswer(mShowingAnswer);

        // If we are in single-card mode, we show the "Show Answer" button on the question side
        // and hide navigation buttons.
        if (mCardList.length == 1) {
            mPreviewLayout.hideNavigationButtons();
            return;
        }

        mPreviewLayout.setPrevButtonEnabled(mIndex > 0);
        mPreviewLayout.setNextButtonEnabled(mIndex < mCardList.length - 1);
    }

    private void updateProgress() {
        if (mProgressSeekBar.getProgress() != mIndex) {
            mProgressSeekBar.setProgress(mIndex);
        }

        String progress = getString(R.string.preview_progress_bar_text, mIndex + 1, mCardList.length);
        mProgressText.setText(progress);
    }

    @NonNull
    private Intent getResultIntent() {
        Intent intent = new Intent();
        intent.putExtra("reloadRequired", mReloadRequired);
        intent.putExtra("noteChanged", mNoteChanged);
        return intent;
    }
}
