/***************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Jolta Technologies                                                *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.themes.Themes;

import org.json.JSONObject;

import java.util.ArrayList;

import javax.annotation.Nullable;

import timber.log.Timber;

/**
 * The previewer intent must supply an array of cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */
public class Previewer extends AbstractFlashcardViewer {
    private long[] mCardList;
    private int mIndex;
    protected boolean mShowingAnswer;
    private String mEditedModelFileName = null;
    private JSONObject mEditedModel = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        Bundle parameters = savedInstanceState;
        if (parameters == null) {
            parameters = getIntent().getExtras();
        }
        mEditedModelFileName = parameters.getString(CardTemplateEditor.INTENT_MODEL_FILENAME);
        mCardList = parameters.getLongArray("cardList");
        mIndex = parameters.getInt("index");

        if (mEditedModelFileName != null) {
            Timber.d("onCreate() loading edited model from %s", mEditedModelFileName);
            mEditedModel = CardTemplateEditor.getTempModel(mEditedModelFileName);
        }

        if (mEditedModel != null && mIndex != -1) {
            Timber.d("onCreate() Previewer started with edited model and index, displaying blank to preview formatting");
            mCurrentCard = getDummyCard(mEditedModel, mIndex);
            if (mCurrentCard == null) {
                UIUtils.showSimpleSnackbar(this, R.string.invalid_template, false);
                finishWithoutAnimation();
                return;
            }
        }

        if (mCurrentCard == null && (mCardList == null || mCardList.length == 0 || mIndex < 0 || mIndex > mCardList.length - 1)) {
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
    public void onSaveInstanceState(Bundle outState) {
        outState.putLongArray("cardList", mCardList);
        outState.putString(CardTemplateEditor.INTENT_MODEL_FILENAME, mEditedModelFileName);
        outState.putInt("index", mIndex);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        if (mCurrentCard == null) {
            mCurrentCard = new PreviewerCard(col, mCardList[mIndex]);
        }
        displayCardQuestion();
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


    // No Gestures!
    @Override
    protected void executeCommand(int which) { /* do nothing */ }

    private View.OnClickListener mSelectScrollHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mShowingAnswer) {
                // If we are showing the answer, any click will show a question...
                if (view.getId() == R.id.flashcard_layout_ease2) {
                    // ...but if they clicked "forward" we need to move to the next card first
                    mIndex++;
                    mCurrentCard = new PreviewerCard(getCol(), mCardList[mIndex]);
                }
                displayCardQuestion();
            } else {
                // If we are showing the question, any click will show an answer...
                if (view.getId() == R.id.flashcard_layout_ease1) {
                    // ...but if they clicked "reverse" we need to go to the previous card first
                    mIndex--;
                    mCurrentCard = new PreviewerCard(getCol(), mCardList[mIndex]);
                }
                displayCardAnswer();
            }
        }
    };

    private void updateButtonState() {
        // If we are in single-card mode, we show the "Show Answer" button on the question side
        // and hide all the buttons on the answer side.
        if (mCardList == null || mCardList.length == 1) {
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

    /** Get a dummy card */
    protected @Nullable Card getDummyCard(JSONObject model, int ordinal) {
        Timber.d("getDummyCard() Creating dummy note for position %s", ordinal);
        if (model == null) {
            return null;
        }
        Note n = getCol().newNote(model);
        ArrayList<String> fieldNames = Models.fieldNames(model);
        for (int i = 0; i < fieldNames.size(); i++) {
            n.setField(i, fieldNames.get(i));
        }
        try {
            JSONObject template = (JSONObject)model.getJSONArray("tmpls").get(ordinal);
            PreviewerCard card = (PreviewerCard)getCol()._newCard(new PreviewerCard(getCol()), n, template, 1, false);
            card.setNote(n);
            return card;
        } catch (Exception e) {
            Timber.e("getDummyCard() unable to create card");
        }
        return null;
    }


    /** Override certain aspects of Card behavior so we may display unsaved data */
    public class PreviewerCard extends Card {

        private Note mNote;


        public PreviewerCard(Collection col) {
            super(col);
        }


        public PreviewerCard(Collection col, long id) {
            super(col, id);
        }


        @Override
        /** if we have an unsaved note saved, use it instead of a collection lookup */
        public Note note(boolean reload) {
            if (mNote != null) {
                return mNote;
            }
            return super.note(reload);
        }


        @Override
        /** if we have an unsaved note saved, use it instead of a collection lookup */
        public Note note() {
            if (mNote != null) {
                return mNote;
            }
            return super.note();
        }


        /** set an unsaved note to use for rendering */
        public void setNote(Note note) {
            mNote = note;
        }


        @Override
        /** if we have an unsaved note, never return empty */
        public boolean isEmpty() {
            if (mNote != null) {
                return false;
            }
            return super.isEmpty();
        }


        @Override
        /** Override the method that fetches the model so we can render unsaved models */
        public JSONObject model() {
            if (mEditedModel != null) {
                return mEditedModel;
            }
            return super.model();
        }
    }
}
