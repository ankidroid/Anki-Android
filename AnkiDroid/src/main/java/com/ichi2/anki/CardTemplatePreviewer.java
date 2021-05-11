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
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.utils.NoteUtils;
import com.ichi2.themes.Themes;
import com.ichi2.utils.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.END;

/**
 * The card template previewer intent must supply one or more cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */
public class CardTemplatePreviewer extends AbstractFlashcardViewer {
    private String mEditedModelFileName = null;
    private Model mEditedModel = null;
    private int mOrdinal;
    @Nullable
    private long[] mCardList;
    private Bundle mNoteEditorBundle = null;

    private boolean mShowingAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        Bundle parameters = savedInstanceState;
        if (parameters == null) {
            parameters = getIntent().getExtras();
        }
        if (parameters != null) {
            mNoteEditorBundle = parameters.getBundle("noteEditorBundle");
            mEditedModelFileName = parameters.getString(TemporaryModel.INTENT_MODEL_FILENAME);
            mCardList = parameters.getLongArray("cardList");
            mOrdinal = parameters.getInt("ordinal");
            mShowingAnswer = parameters.getBoolean("showingAnswer", mShowingAnswer);
        }

        if (mEditedModelFileName != null) {
            Timber.d("onCreate() loading edited model from %s", mEditedModelFileName);
            try {
                mEditedModel = TemporaryModel.getTempModel(mEditedModelFileName);
            } catch (IOException e) {
                Timber.w(e, "Unable to load temp model from file %s", mEditedModelFileName);
                closeCardTemplatePreviewer();
            }
        }

        if (mEditedModel != null && mOrdinal != -1) {
            Timber.d("onCreate() CardTemplatePreviewer started with edited model and template index, displaying blank to preview formatting");
            mCurrentCard = getDummyCard(mEditedModel, mOrdinal);
            if (mCurrentCard == null) {
                UIUtils.showThemedToast(getApplicationContext(), getString(R.string.invalid_template), false);
                closeCardTemplatePreviewer();
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
            closeCardTemplatePreviewer();
        }
    }


    private void closeCardTemplatePreviewer() {
        Timber.d("CardTemplatePreviewer:: closeCardTemplatePreviewer()");
        setResult(RESULT_OK);
        TemporaryModel.clearTempModelFiles();
        finishWithAnimation(END);
    }


    @Override
    public void onBackPressed() {
        Timber.i("CardTemplatePreviewer:: onBackPressed()");
        closeCardTemplatePreviewer();
    }


    @Override
    protected void performReload() {
        // This should not happen.
        finishWithAnimation(END);
    }


    @Override
    protected void onNavigationPressed() {
        Timber.i("CardTemplatePreviewer:: Navigation button pressed");
        closeCardTemplatePreviewer();
    }


    @Override
    protected void setTitle() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.preview_title);
        }
    }

    @Override
    protected void initLayout() {
        super.initLayout();
        mTopBarLayout.setVisibility(View.GONE);

        findViewById(R.id.answer_options_layout).setVisibility(View.GONE);
        mPreviewButtonsLayout.setVisibility(View.VISIBLE);

        mPreviewButtonsLayout.setOnClickListener(mToggleAnswerHandler);

        mPreviewPrevCard.setVisibility(View.GONE);
        mPreviewNextCard.setVisibility(View.GONE);

        if (animationEnabled()) {
            int resId = Themes.getResFromAttr(this, R.attr.hardButtonRippleRef);
            mPreviewButtonsLayout.setBackgroundResource(resId);
            mPreviewPrevCard.setBackgroundResource(resId);
            mPreviewNextCard.setBackgroundResource(resId);
        }
    }


    @Override
    protected void displayCardQuestion() {
        super.displayCardQuestion();
        mShowingAnswer = false;
        updateButtonsState();
    }


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
        mPreviewToggleAnswerText.setText(mShowingAnswer ? R.string.hide_answer : R.string.show_answer);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(TemporaryModel.INTENT_MODEL_FILENAME, mEditedModelFileName);
        outState.putLongArray("cardList", mCardList);
        outState.putInt("ordinal", mOrdinal);
        outState.putBundle("noteEditorBundle", mNoteEditorBundle);
        outState.putBoolean("showingAnswer", mShowingAnswer);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        if ((mCurrentCard == null) && (mCardList == null)) {
            Timber.d("onCollectionLoaded - incorrect state to load, closing");
            closeCardTemplatePreviewer();
            return;
        }
        if (mCardList != null && mOrdinal >= 0 && mOrdinal < mCardList.length) {
            mCurrentCard = new PreviewerCard(col, mCardList[mOrdinal]);
        }

        if (mNoteEditorBundle != null) {
            long newDid = mNoteEditorBundle.getLong("did");
            if (col.getDecks().isDyn(newDid)) {
                mCurrentCard.setODid(mCurrentCard.getDid());
            }
            mCurrentCard.setDid(newDid);

            Note currentNote = mCurrentCard.note();
            ArrayList<String> tagsList = mNoteEditorBundle.getStringArrayList("tags");
            NoteUtils.setTags(currentNote, tagsList);

            Bundle noteFields = mNoteEditorBundle.getBundle("editFields");
            if (noteFields != null) {
                for (String fieldOrd : noteFields.keySet()) {
                    // In case the fields on the card are out of sync with the bundle
                    int fieldOrdInt = Integer.parseInt(fieldOrd);
                    if (fieldOrdInt < currentNote.getFields().length) {
                        currentNote.setField(fieldOrdInt, noteFields.getString(fieldOrd));
                    }
                }
            }
        }

        displayCardQuestion();
        if (mShowingAnswer) {
            displayCardAnswer();
        }

        showBackIcon();
    }

    protected Card getCard(Collection col, long cardListIndex) {
        return new PreviewerCard(col, cardListIndex);
    }

    /** Get a dummy card */
    protected @Nullable Card getDummyCard(Model model, int ordinal) {
        Timber.d("getDummyCard() Creating dummy note for ordinal %s", ordinal);
        if (model == null) {
            return null;
        }
        Note n = getCol().newNote(model);
        List<String> fieldNames = model.getFieldsNames();
        for (int i = 0; i < fieldNames.size() && i < n.getFields().length; i++) {
            n.setField(i, fieldNames.get(i));
        }
        try {
            JSONObject template = model.getJSONArray("tmpls").getJSONObject(ordinal);
            return getCol().getNewLinkedCard(new PreviewerCard(getCol(), n), n, template, 1, 0L, false);
        } catch (Exception e) {
            Timber.e("getDummyCard() unable to create card");
        }
        return null;
    }


    /** Override certain aspects of Card behavior so we may display unsaved data */
    public class PreviewerCard extends Card {

        @Nullable private final Note mNote;


        private PreviewerCard(Collection col, @NonNull Note note) {
            super(col);
            mNote = note;
        }


        private PreviewerCard(Collection col, long id) {
            super(col, id);
            mNote = null;
        }


        @Override
        /* if we have an unsaved note saved, use it instead of a collection lookup */
        public Note note(boolean reload) {
            if (mNote != null) {
                return mNote;
            }
            return super.note(reload);
        }


        /** if we have an unsaved note saved, use it instead of a collection lookup */
        @Override
        public Note note() {
            if (mNote != null) {
                return mNote;
            }
            return super.note();
        }


        /** if we have an unsaved note, never return empty */
        @Override
        public boolean isEmpty() {
            if (mNote != null) {
                return false;
            }
            return super.isEmpty();
        }


        /** Override the method that fetches the model so we can render unsaved models */
        @Override
        public Model model() {
            if (mEditedModel != null) {
                return mEditedModel;
            }
            return super.model();
        }
    }
}
