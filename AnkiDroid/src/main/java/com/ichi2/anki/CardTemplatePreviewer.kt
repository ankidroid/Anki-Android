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

package com.ichi2.anki

import android.os.Bundle
import android.view.View
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.cardviewer.PreviewLayout
import com.ichi2.anki.cardviewer.PreviewLayout.Companion.createAndDisplay
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.libanki.Note
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.libanki.utils.NoteUtils
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * The card template previewer intent must supply one or more cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */
open class CardTemplatePreviewer : AbstractFlashcardViewer() {
    private var mEditedModelFileName: String? = null
    private var mEditedModel: Model? = null
    private var mOrdinal = 0

    /** The index of the card in cardList to show  */
    private var mCardListIndex = 0

    /** The list (currently singular) of cards to be previewed
     * A single template was selected, and there was an associated card which exists
     */
    private var mCardList: LongArray? = null
    private var mNoteEditorBundle: Bundle? = null
    private var mShowingAnswer = false

    /**
     * The number of valid templates for the note
     * Only used if mNoteEditorBundle != null
     *
     * If launched from the Template Editor, only one the selected card template is selectable
     */
    private var mTemplateCount = 0
    var templateIndex = 0
        private set
    private var mAllFieldsNull = true
    private var mCardType: String? = null
    @JvmField
    protected var mPreviewLayout: PreviewLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        Timber.d("onCreate()")
        super.onCreate(savedInstanceState)
        var parameters = savedInstanceState
        if (parameters == null) {
            parameters = intent.extras
        }
        if (parameters != null) {
            mNoteEditorBundle = parameters.getBundle("noteEditorBundle")
            mEditedModelFileName = parameters.getString(TemporaryModel.INTENT_MODEL_FILENAME)
            mCardList = parameters.getLongArray("cardList")
            mOrdinal = parameters.getInt("ordinal")
            mCardListIndex = parameters.getInt("cardListIndex")
            mShowingAnswer = parameters.getBoolean("showingAnswer", mShowingAnswer)
        }
        if (mEditedModelFileName != null) {
            Timber.d("onCreate() loading edited model from %s", mEditedModelFileName)
            try {
                mEditedModel = TemporaryModel.getTempModel(mEditedModelFileName!!)
                mCardType = mEditedModel!!.optString("name")
            } catch (e: IOException) {
                Timber.w(e, "Unable to load temp model from file %s", mEditedModelFileName)
                closeCardTemplatePreviewer()
            }
        }
        showBackIcon()
        // Ensure navigation drawer can't be opened. Various actions in the drawer cause crashes.
        disableDrawerSwipe()
        startLoadingCollection()
    }

    override fun onResume() {
        super.onResume()
        if (mCurrentCard == null || mOrdinal < 0) {
            Timber.e("CardTemplatePreviewer started with empty card list or invalid index")
            closeCardTemplatePreviewer()
        }
    }

    private fun closeCardTemplatePreviewer() {
        Timber.d("CardTemplatePreviewer:: closeCardTemplatePreviewer()")
        setResult(RESULT_OK)
        TemporaryModel.clearTempModelFiles()
        finishWithAnimation(ActivityTransitionAnimation.Direction.END)
    }

    override fun onBackPressed() {
        Timber.i("CardTemplatePreviewer:: onBackPressed()")
        closeCardTemplatePreviewer()
    }

    override fun performReload() {
        // This should not happen.
        finishWithAnimation(ActivityTransitionAnimation.Direction.END)
    }

    override fun onNavigationPressed() {
        Timber.i("CardTemplatePreviewer:: Navigation button pressed")
        closeCardTemplatePreviewer()
    }

    override fun setTitle() {
        if (supportActionBar != null) {
            supportActionBar?.setTitle(R.string.preview_title)
        }
    }

    override fun initLayout() {
        super.initLayout()
        mTopBarLayout!!.visibility = View.GONE
        findViewById<View>(R.id.answer_options_layout).visibility = View.GONE
        mPreviewLayout = createAndDisplay(this, mToggleAnswerHandler)
        mPreviewLayout!!.setOnPreviousCard { onPreviousTemplate() }
        mPreviewLayout!!.setOnNextCard { onNextTemplate() }
        mPreviewLayout!!.hideNavigationButtons()
        mPreviewLayout!!.setPrevButtonEnabled(false)
    }

    override fun displayCardQuestion() {
        super.displayCardQuestion()
        mShowingAnswer = false
        mPreviewLayout!!.setShowingAnswer(false)
    }

    override fun displayCardAnswer() {
        if (mAllFieldsNull && mCardType != null && mCardType == getString(R.string.basic_typing_model_name)) {
            mAnswerField!!.setText(getString(R.string.basic_answer_sample_text_user))
        }
        super.displayCardAnswer()
        mShowingAnswer = true
        mPreviewLayout!!.setShowingAnswer(true)
    }

    override fun hideEaseButtons() {
        /* do nothing */
    }

    override fun displayAnswerBottomBar() {
        /* do nothing */
    }

    private val mToggleAnswerHandler = View.OnClickListener {
        if (mShowingAnswer) {
            displayCardQuestion()
        } else {
            displayCardAnswer()
        }
    }

    /** When the next template is requested  */
    fun onNextTemplate() {
        var index = templateIndex
        if (!isNextBtnEnabled(index)) {
            return
        }
        templateIndex = ++index
        onTemplateIndexChanged()
    }

    /** When the previous template is requested  */
    fun onPreviousTemplate() {
        var index = templateIndex
        if (!isPrevBtnEnabled(index)) {
            return
        }
        templateIndex = --index
        onTemplateIndexChanged()
    }

    /**
     * Loads the next card after the current template index has been changed
     */
    private fun onTemplateIndexChanged() {
        val prevBtnEnabled = isPrevBtnEnabled(templateIndex)
        val nextBtnEnabled = isNextBtnEnabled(templateIndex)
        mPreviewLayout!!.setPrevButtonEnabled(prevBtnEnabled)
        mPreviewLayout!!.setNextButtonEnabled(nextBtnEnabled)
        setCurrentCardFromNoteEditorBundle(col)
        displayCardQuestion()
    }

    private fun isPrevBtnEnabled(templateIndex: Int): Boolean {
        return templateIndex > 0
    }

    private fun isNextBtnEnabled(newTemplateIndex: Int): Boolean {
        return newTemplateIndex < mTemplateCount - 1
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TemporaryModel.INTENT_MODEL_FILENAME, mEditedModelFileName)
        outState.putLongArray("cardList", mCardList)
        outState.putInt("ordinal", mOrdinal)
        outState.putInt("cardListIndex", mCardListIndex)
        outState.putBundle("noteEditorBundle", mNoteEditorBundle)
        outState.putBoolean("showingAnswer", mShowingAnswer)
        super.onSaveInstanceState(outState)
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        if (mNoteEditorBundle != null) {
            mAllFieldsNull = false
            // loading from the note editor
            val toPreview = setCurrentCardFromNoteEditorBundle(col)
            if (toPreview != null) {
                mTemplateCount = getCol().findTemplates(toPreview.note()).size
                if (mTemplateCount >= 2) {
                    mPreviewLayout!!.showNavigationButtons()
                }
            }
        } else {
            // loading from the card template editor
            mAllFieldsNull = true
            // card template with associated card due to opening from note editor
            if (mCardList != null && mCardListIndex >= 0 && mCardListIndex < mCardList!!.size) {
                currentCard = PreviewerCard(col, mCardList!![mCardListIndex])
            } else if (mEditedModel != null) { // bare note type (not coming from note editor), or new card template
                Timber.d("onCreate() CardTemplatePreviewer started with edited model and template index, displaying blank to preview formatting")
                currentCard = getDummyCard(mEditedModel!!, mOrdinal)
                if (mCurrentCard == null) {
                    showThemedToast(applicationContext, getString(R.string.invalid_template), false)
                    closeCardTemplatePreviewer()
                }
            }
        }
        if (mCurrentCard == null) {
            showThemedToast(applicationContext, getString(R.string.invalid_template), false)
            closeCardTemplatePreviewer()
            return
        }
        displayCardQuestion()
        if (mShowingAnswer) {
            displayCardAnswer()
        }
        showBackIcon()
    }

    protected fun getCard(col: Collection, cardListIndex: Long): Card {
        return PreviewerCard(col, cardListIndex)
    }

    private fun setCurrentCardFromNoteEditorBundle(col: Collection): Card? {
        assert(mNoteEditorBundle != null)
        currentCard = getDummyCard(mEditedModel, templateIndex, getBundleEditFields(mNoteEditorBundle))
        // example: a basic card with no fields provided
        if (mCurrentCard == null) {
            return null
        }
        val newDid = mNoteEditorBundle!!.getLong("did")
        if (col.decks.isDyn(newDid)) {
            mCurrentCard!!.oDid = mCurrentCard!!.did
        }
        mCurrentCard!!.did = newDid
        val currentNote = mCurrentCard!!.note()
        val tagsList = mNoteEditorBundle!!.getStringArrayList("tags")
        NoteUtils.setTags(currentNote, tagsList)
        return mCurrentCard
    }

    private fun getLabels(fieldValues: MutableList<String>) {
        if (mCardType != null && mCardType == getString(R.string.cloze_model_name)) {
            fieldValues[0] = getString(R.string.cloze_sample_text, "c1")
        }
        if (mCardType != null && mCardType == getString(R.string.basic_typing_model_name)) {
            fieldValues[1] = getString(R.string.basic_answer_sample_text)
        }
    }

    private fun getBundleEditFields(noteEditorBundle: Bundle?): MutableList<String> {
        val noteFields = noteEditorBundle!!.getBundle("editFields")
            ?: return ArrayList()
        // we map from "int" -> field, but the order isn't guaranteed, and there may be skips.
        // so convert this to a list of strings, with null in place of the invalid fields
        val elementCount = noteFields.keySet().stream().map { s: String -> s.toInt() }.max { obj: Int, anotherInteger: Int? -> obj.compareTo(anotherInteger!!) }.orElse(-1) + 1
        val ret = arrayOfNulls<String>(elementCount)
        Arrays.fill(ret, "") // init array, nulls cause a crash
        for (fieldOrd in noteFields.keySet()) {
            ret[fieldOrd.toInt()] = noteFields.getString(fieldOrd)
        }
        return ArrayList(listOf(*ret))
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param index The index in the templates for the model. NOT `ord`
     */
    fun getDummyCard(model: Model, index: Int): Card? {
        return getDummyCard(model, index, model.fieldsNames)
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param index The index in the templates for the model. NOT `ord`
     */
    private fun getDummyCard(model: Model?, index: Int, fieldValues: MutableList<String>): Card? {
        Timber.d("getDummyCard() Creating dummy note for index %s", index)
        if (model == null) {
            return null
        }
        if (mAllFieldsNull) {
            getLabels(fieldValues)
        }
        val n = col.newNote(model)
        var i = 0
        while (i < fieldValues.size && i < n.fields.size) {
            if (mAllFieldsNull) {
                if (mCardType != null && mCardType == getString(R.string.cloze_model_name) && i == 0 ||
                    mCardType == getString(R.string.basic_typing_model_name) && i == 1
                ) {
                    n.setField(i, fieldValues[i])
                } else {
                    n.setField(i, "(" + fieldValues[i] + ")")
                }
            } else {
                n.setField(i, fieldValues[i])
            }
            i++
        }
        try {
            // TODO: Inefficient, we discard all but one of the elements.
            val template = col.findTemplates(n)[index]
            return col.getNewLinkedCard(PreviewerCard(col, n), n, template, 1, 0L, false)
        } catch (e: Exception) {
            Timber.e(e, "getDummyCard() unable to create card")
        }
        return null
    }

    /** Override certain aspects of Card behavior so we may display unsaved data  */
    inner class PreviewerCard : Card {
        private val mNote: Note?

        constructor(col: Collection, note: Note) : super(col) {
            mNote = note
        }

        constructor(col: Collection, id: Long) : super(col, id) {
            mNote = null
        }

        /* if we have an unsaved note saved, use it instead of a collection lookup */ override fun note(reload: Boolean): Note {
            return mNote ?: super.note(reload)
        }

        /** if we have an unsaved note saved, use it instead of a collection lookup  */
        override fun note(): Note {
            return mNote ?: super.note()
        }

        /** if we have an unsaved note, never return empty  */
        override val isEmpty: Boolean
            get() = if (mNote != null) {
                false
            } else super.isEmpty

        /** Override the method that fetches the model so we can render unsaved models  */
        override fun model(): Model {
            return mEditedModel ?: super.model()
        }

        @RustCleanup("determine how Anki Desktop does this")
        override fun render_output(reload: Boolean, browser: Boolean): TemplateRenderOutput {
            if (render_output == null || reload) {
                render_output = col.render_output_legacy(this, reload, browser)
            }
            return render_output!!
        }
    }
}
