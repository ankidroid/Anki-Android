/***************************************************************************************
 * Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>                                   *
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
import com.ichi2.utils.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * The card template previewer intent must supply one or more cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */
public class CardTemplatePreviewer extends AbstractFlashcardViewer {
    private String mEditedModelFileName = null;
    private JSONObject mEditedModel = null;
    private int mOrdinal;
    private long[] mCardList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        Bundle parameters = savedInstanceState;
        if (parameters == null) {
            parameters = getIntent().getExtras();
        }
        mEditedModelFileName = parameters.getString(TemporaryModel.INTENT_MODEL_FILENAME);
        mCardList = parameters.getLongArray("cardList");
        mOrdinal = parameters.getInt("ordinal");

        if (mEditedModelFileName != null) {
            Timber.d("onCreate() loading edited model from %s", mEditedModelFileName);
            try {
                mEditedModel = TemporaryModel.getTempModel(mEditedModelFileName);
            } catch (IOException e) {
                Timber.w(e, "Unable to load temp model from file %s", mEditedModelFileName);
            }
        }

        if (mEditedModel != null && mOrdinal != -1) {
            Timber.d("onCreate() CardTemplatePreviewer started with edited model and template index, displaying blank to preview formatting");
            mCurrentCard = getDummyCard(mEditedModel, mOrdinal);
            if (mCurrentCard == null) {
                UIUtils.showSimpleSnackbar(this, R.string.invalid_template, false);
                finishWithoutAnimation();
            }
        }

        showBackIcon();
        // Ensure navigation drawer can't be opened. Various actions in the drawer cause crashes.
        disableDrawerSwipe();
        startLoadingCollection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCurrentCard == null || mOrdinal < 0) {
            Timber.e("CardTemplatePreviewer started with empty card list or invalid index");
            finishWithoutAnimation();
            return;
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
    protected void displayCardQuestion() {
        super.displayCardQuestion();
        mFlipCardLayout.setVisibility(View.VISIBLE);
    }


    // Called via mFlipCardListener in parent class when answer button pressed
    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        findViewById(R.id.answer_options_layout).setVisibility(View.GONE);
        mFlipCardLayout.setVisibility(View.GONE);
        hideEaseButtons();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(TemporaryModel.INTENT_MODEL_FILENAME, mEditedModelFileName);
        outState.putLongArray("cardList", mCardList);
        outState.putInt("ordinal", mOrdinal);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        if (mCurrentCard == null) {
            mCurrentCard = new PreviewerCard(col, mCardList[mOrdinal]);
        }
        displayCardQuestion();
        showBackIcon();
    }

    protected Card getCard(Collection col, long cardListIndex) {
        return new PreviewerCard(col, cardListIndex);
    }

    /** Get a dummy card */
    protected @Nullable Card getDummyCard(JSONObject model, int ordinal) {
        Timber.d("getDummyCard() Creating dummy note for ordinal %s", ordinal);
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
            PreviewerCard card = (PreviewerCard)getCol().getNewLinkedCard(new PreviewerCard(getCol()), n, template, 1, 0, false);
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