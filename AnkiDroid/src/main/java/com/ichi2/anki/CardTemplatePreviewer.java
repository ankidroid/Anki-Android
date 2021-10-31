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

import com.ichi2.anki.cardviewer.PreviewLayout;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.TemplateManager;
import com.ichi2.libanki.utils.NoteUtils;
import com.ichi2.utils.JSONObject;

import net.ankiweb.rsdroid.RustCleanup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
    /** The index of the card in cardList to show */
    private int mCardListIndex;
    /** The list (currently singular) of cards to be previewed
     * A single template was selected, and there was an associated card which exists
     */
    @Nullable
    private long[] mCardList;
    @Nullable
    private Bundle mNoteEditorBundle = null;
    private boolean mShowingAnswer;
    /**
     * The number of valid templates for the note
     * Only used if mNoteEditorBundle != null
     *
     * If launched from the Template Editor, only one the selected card template is selectable
     */
    private int mTemplateCount;
    private int mTemplateIndex = 0;

    protected PreviewLayout mPreviewLayout;

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
            mCardListIndex = parameters.getInt("cardListIndex");
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

        mPreviewLayout = PreviewLayout.createAndDisplay(this, mToggleAnswerHandler);
        mPreviewLayout.setOnPreviousCard((view) -> onPreviousTemplate());
        mPreviewLayout.setOnNextCard((view) -> onNextTemplate());
        mPreviewLayout.hideNavigationButtons();
        mPreviewLayout.setPrevButtonEnabled(false);
    }


    @Override
    public void displayCardQuestion() {
        super.displayCardQuestion();
        mShowingAnswer = false;
        mPreviewLayout.setShowingAnswer(false);
    }


    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        mShowingAnswer = true;
        mPreviewLayout.setShowingAnswer(true);
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

    /** When the next template is requested */
    public void onNextTemplate() {
        int index = mTemplateIndex;
        if (!isNextBtnEnabled(index)) {
            return;
        }
        mTemplateIndex = ++index;
        onTemplateIndexChanged();
    }

    /** When the previous template is requested */
    public void onPreviousTemplate() {
        int index = mTemplateIndex;
        if (!isPrevBtnEnabled(index)) {
            return;
        }
        mTemplateIndex = --index;
        onTemplateIndexChanged();
    }

    /**
     * Loads the next card after the current template index has been changed
     */
    private void onTemplateIndexChanged() {
        boolean prevBtnEnabled = isPrevBtnEnabled(mTemplateIndex);
        boolean nextBtnEnabled = isNextBtnEnabled(mTemplateIndex);

        mPreviewLayout.setPrevButtonEnabled(prevBtnEnabled);
        mPreviewLayout.setNextButtonEnabled(nextBtnEnabled);

        setCurrentCardFromNoteEditorBundle(getCol());
        displayCardQuestion();
    }


    public boolean isPrevBtnEnabled(int templateIndex) {
        return templateIndex > 0;
    }


    public boolean isNextBtnEnabled(int newTemplateIndex) {
        return newTemplateIndex < mTemplateCount -1;
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(TemporaryModel.INTENT_MODEL_FILENAME, mEditedModelFileName);
        outState.putLongArray("cardList", mCardList);
        outState.putInt("ordinal", mOrdinal);
        outState.putInt("cardListIndex", mCardListIndex);
        outState.putBundle("noteEditorBundle", mNoteEditorBundle);
        outState.putBoolean("showingAnswer", mShowingAnswer);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);



        if (mNoteEditorBundle != null) {
            // loading from the note editor
            Card toPreview = setCurrentCardFromNoteEditorBundle(col);
            if (toPreview != null) {
                mTemplateCount = getCol().findTemplates(toPreview.note()).size();

                if (mTemplateCount >= 2) {
                    mPreviewLayout.showNavigationButtons();
                }
            }
        } else {
            // loading from the card template editor

            // card template with associated card due to opening from note editor
            if (mCardList != null && mCardListIndex >= 0 && mCardListIndex < mCardList.length) {
                mCurrentCard = new PreviewerCard(col, mCardList[mCardListIndex]);
            } else if (mEditedModel != null) { // bare note type (not coming from note editor), or new card template
                Timber.d("onCreate() CardTemplatePreviewer started with edited model and template index, displaying blank to preview formatting");
                mCurrentCard = getDummyCard(mEditedModel, mOrdinal);
                if (mCurrentCard == null) {
                    UIUtils.showThemedToast(getApplicationContext(), getString(R.string.invalid_template), false);
                    closeCardTemplatePreviewer();
                }
            }
        }

        if (mCurrentCard == null) {
            UIUtils.showThemedToast(getApplicationContext(), getString(R.string.invalid_template), false);
            closeCardTemplatePreviewer();
            return;
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

    public int getTemplateIndex() {
        return mTemplateIndex;
    }

    @Nullable
    private Card setCurrentCardFromNoteEditorBundle(Collection col) {
        assert(mNoteEditorBundle != null);
        mCurrentCard = getDummyCard(mEditedModel, mTemplateIndex, getBundleEditFields(mNoteEditorBundle));
        // example: a basic card with no fields provided
        if (mCurrentCard == null) {
            return null;
        }
        long newDid = mNoteEditorBundle.getLong("did");
        if (col.getDecks().isDyn(newDid)) {
            mCurrentCard.setODid(mCurrentCard.getDid());
        }
        mCurrentCard.setDid(newDid);

        Note currentNote = mCurrentCard.note();
        ArrayList<String> tagsList = mNoteEditorBundle.getStringArrayList("tags");
        NoteUtils.setTags(currentNote, tagsList);
        return mCurrentCard;
    }


    private List<String> getBundleEditFields(Bundle noteEditorBundle) {
        Bundle noteFields = noteEditorBundle.getBundle("editFields");
        if (noteFields == null) {
            return new ArrayList<>();
        }
        // we map from "int" -> field, but the order isn't guaranteed, and there may be skips.
        // so convert this to a list of strings, with null in place of the invalid fields
        int elementCount = noteFields.keySet().stream().map(Integer::parseInt).max(Integer::compareTo).orElse(-1) + 1;
        String[] ret = new String[elementCount];
        Arrays.fill(ret, ""); // init array, nulls cause a crash
        for (String fieldOrd : noteFields.keySet()) {
            ret[Integer.parseInt(fieldOrd)] = noteFields.getString(fieldOrd);
        }
        return new ArrayList<>(Arrays.asList(ret));
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param index The index in the templates for the model. NOT `ord`
     */
    protected @Nullable Card getDummyCard(Model model, int index) {
        return getDummyCard(model, index, model.getFieldsNames());
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param index The index in the templates for the model. NOT `ord`
     */
    protected @Nullable Card getDummyCard(Model model, int index, List<String> fieldValues) {
        Timber.d("getDummyCard() Creating dummy note for index %s", index);
        if (model == null) {
            return null;
        }
        Note n = getCol().newNote(model);
        for (int i = 0; i < fieldValues.size() && i < n.getFields().length; i++) {
            n.setField(i, fieldValues.get(i));
        }

        try {
            // TODO: Inefficient, we discard all but one of the elements.
            JSONObject template = getCol().findTemplates(n).get(index);
            return getCol().getNewLinkedCard(new PreviewerCard(getCol(), n), n, template, 1, 0L, false);
        } catch (Exception e) {
            Timber.e(e, "getDummyCard() unable to create card");
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


        @NonNull
        @Override
        @RustCleanup("determine how Anki Desktop does this")
        public TemplateManager.TemplateRenderContext.TemplateRenderOutput render_output(boolean reload, boolean browser) {
            if (getRenderOutput() == null || reload) {
                setRenderOutput(getCol().render_output_legacy(this, reload, browser));
            }
            return getRenderOutput();
        }
    }
}
