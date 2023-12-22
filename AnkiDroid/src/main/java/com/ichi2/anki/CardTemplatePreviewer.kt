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
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.cardviewer.PreviewLayout
import com.ichi2.anki.cardviewer.PreviewLayout.Companion.createAndDisplay
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.TemplateManager
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

/**
 * The card template previewer intent must supply one or more cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */
@NeedsTest("after switch to new schema as default, add test to confirm audio tags rendered")
open class CardTemplatePreviewer : AbstractFlashcardViewer() {
    private var mEditedModelFileName: String? = null
    private var mEditedNotetype: NotetypeJson? = null
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
     * The number of valid cards for the note
     * Only used if mNoteEditorBundle != null
     *
     * If launched from the Template Editor, only one the selected card template is selectable
     */
    private var cardCount = 0

    /**
     * The index of the selected card in the previewer. Used for forward/back
     * For cloze note types, this may not be the same as `ord`: `{{c1::A}} {{c3::B}}`
     */
    var cardIndex = 0
        private set
    private var mAllFieldsNull = true
    private var mCardType: String? = null
    protected var previewLayout: PreviewLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        var parameters = savedInstanceState
        if (parameters == null) {
            parameters = intent.extras
        }
        if (parameters != null) {
            mNoteEditorBundle = parameters.getBundle("noteEditorBundle")
            mEditedModelFileName = parameters.getString(CardTemplateNotetype.INTENT_MODEL_FILENAME)
            mCardList = parameters.getLongArray("cardList")
            mOrdinal = parameters.getInt("ordinal")
            mCardListIndex = parameters.getInt("cardListIndex")
            mShowingAnswer = parameters.getBoolean("showingAnswer", mShowingAnswer)
        }
        if (mEditedModelFileName != null) {
            Timber.d("onCreate() loading edited model from %s", mEditedModelFileName)
            try {
                mEditedNotetype = CardTemplateNotetype.getTempModel(mEditedModelFileName!!)
                mCardType = mEditedNotetype!!.optString("name")
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
        if (currentCard == null || mOrdinal < 0) {
            Timber.e("CardTemplatePreviewer started with empty card list or invalid index")
            closeCardTemplatePreviewer()
        }
    }

    private fun closeCardTemplatePreviewer() {
        Timber.d("CardTemplatePreviewer:: closeCardTemplatePreviewer()")
        setResult(RESULT_OK)
        CardTemplateNotetype.clearTempModelFiles()
        finish()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("Deprecated in API34+dependencies for predictive back feature")
    override fun onBackPressed() {
        Timber.i("CardTemplatePreviewer:: onBackPressed()")
        super.onBackPressed()
        closeCardTemplatePreviewer()
    }

    override fun performReload() {
        // This should not happen.
        finish()
    }

    override fun onNavigationPressed() {
        Timber.i("CardTemplatePreviewer:: Navigation button pressed")
        closeCardTemplatePreviewer()
    }

    override fun initLayout() {
        super.initLayout()
        topBarLayout!!.visibility = View.GONE
        findViewById<View>(R.id.answer_options_layout).visibility = View.GONE
        findViewById<View>(R.id.bottom_area_layout).visibility = View.VISIBLE
        previewLayout = createAndDisplay(this, mToggleAnswerHandler)
        previewLayout!!.setOnPreviousCard { onPreviousCard() }
        previewLayout!!.setOnNextCard { onNextCard() }
        previewLayout!!.hideNavigationButtons()
        previewLayout!!.setPrevButtonEnabled(false)
    }

    override fun displayCardQuestion() {
        super.displayCardQuestion()
        mShowingAnswer = false
        previewLayout!!.setShowingAnswer(false)
    }

    override fun displayCardAnswer() {
        if (mAllFieldsNull && mCardType != null && mCardType == getString(R.string.basic_typing_model_name)) {
            answerField!!.setText(getString(R.string.basic_answer_sample_text_user))
        }
        super.displayCardAnswer()
        mShowingAnswer = true
        previewLayout!!.setShowingAnswer(true)
    }

    override fun hideEaseButtons() {
        // do nothing
    }

    override fun displayAnswerBottomBar() {
        // do nothing
    }

    private val mToggleAnswerHandler =
        View.OnClickListener {
            if (mShowingAnswer) {
                displayCardQuestion()
            } else {
                displayCardAnswer()
            }
        }

    /** When the next template is requested  */
    fun onNextCard() {
        var index = cardIndex
        if (!isNextBtnEnabled(index)) {
            return
        }
        cardIndex = ++index
        onCardIndexChanged()
    }

    /** When the previous template is requested  */
    fun onPreviousCard() {
        var index = cardIndex
        if (!isPrevBtnEnabled(index)) {
            return
        }
        cardIndex = --index
        onCardIndexChanged()
    }

    /**
     * Loads the next card after the current card index has been changed
     */
    private fun onCardIndexChanged() {
        val prevBtnEnabled = isPrevBtnEnabled(cardIndex)
        val nextBtnEnabled = isNextBtnEnabled(cardIndex)
        previewLayout!!.setPrevButtonEnabled(prevBtnEnabled)
        previewLayout!!.setNextButtonEnabled(nextBtnEnabled)
        setCurrentCardFromNoteEditorBundle(getColUnsafe)
        displayCardQuestion()
    }

    private fun isPrevBtnEnabled(cardIndex: Int): Boolean {
        return cardIndex > 0
    }

    private fun isNextBtnEnabled(cardIndex: Int): Boolean {
        return cardIndex < cardCount - 1
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(CardTemplateNotetype.INTENT_MODEL_FILENAME, mEditedModelFileName)
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
            cardIndex = indexFromOrdinal(col, mNoteEditorBundle!!, mOrdinal)
            Timber.d("ord %d => idx %d", mOrdinal, cardIndex)
            // loading from the note editor
            val toPreview = setCurrentCardFromNoteEditorBundle(col)
            if (toPreview != null) {
                cardCount = toPreview.note().numberOfCardsEphemeral()
                if (cardCount >= 2) {
                    previewLayout!!.showNavigationButtons()
                }
            }
        } else {
            // loading from the card template editor
            mAllFieldsNull = true
            // card template with associated card due to opening from note editor
            if (mCardList != null && mCardListIndex >= 0 && mCardListIndex < mCardList!!.size) {
                currentCard = PreviewerCard(col, mCardList!![mCardListIndex])
            } else if (mEditedNotetype != null) { // bare note type (not coming from note editor), or new card template
                Timber.d(
                    "onCreate() CardTemplatePreviewer started with edited model and template index, displaying blank to preview formatting",
                )
                currentCard = getDummyCard(mEditedNotetype!!, mOrdinal)
                if (currentCard == null) {
                    showThemedToast(applicationContext, getString(R.string.invalid_template), false)
                    closeCardTemplatePreviewer()
                }
            }
        }
        if (currentCard == null) {
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

    protected fun getCard(
        col: Collection,
        cardListIndex: Long,
    ): Card {
        return PreviewerCard(col, cardListIndex)
    }

    private fun setCurrentCardFromNoteEditorBundle(col: Collection): Card? {
        assert(mNoteEditorBundle != null)
        currentCard = getDummyCard(mEditedNotetype, cardIndex, getBundleEditFields(mNoteEditorBundle))
        // example: a basic card with no fields provided
        if (currentCard == null) {
            return null
        }
        val newDid = mNoteEditorBundle!!.getLong("did")
        if (col.decks.isDyn(newDid)) {
            currentCard!!.oDid = currentCard!!.did
        }
        currentCard!!.did = newDid
        val currentNote = currentCard!!.note()
        val tagsList = mNoteEditorBundle!!.getStringArrayList("tags")
        setTags(currentNote, tagsList)
        return currentCard
    }

    /**
     * Set the set tags of currentNote to tagsList.  We make no
     * assumption on the content of tagsList, except that its strings
     * are valid tags (i.e. no spaces in it).
     */
    private fun setTags(
        currentNote: Note,
        tagsList: List<String>?,
    ) {
        val currentTags = currentNote.tags.toTypedArray()
        for (tag in currentTags) {
            currentNote.delTag(tag)
        }
        if (tagsList != null) {
            val tagsSet = currentNote.col.tags.canonify(tagsList)
            currentNote.addTags(tagsSet)
        }
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
        val noteFields =
            noteEditorBundle!!.getBundle("editFields")
                ?: return mutableListOf()
        // we map from "int" -> field, but the order isn't guaranteed, and there may be skips.
        // so convert this to a list of strings, with null in place of the invalid fields
        val elementCount =
            noteFields.keySet().stream().map {
                    s: String ->
                s.toInt()
            }.max { obj: Int, anotherInteger: Int? -> obj.compareTo(anotherInteger!!) }.orElse(-1) + 1
        val ret = Array(elementCount) { "" } // init array, nulls cause a crash
        for (fieldOrd in noteFields.keySet()) {
            ret[fieldOrd.toInt()] = noteFields.getString(fieldOrd)!!
        }
        return mutableListOf(*ret)
    }

    private fun indexFromOrdinal(
        col: Collection,
        fieldsBundle: Bundle,
        ordinal: Int,
    ): Int {
        return when (mEditedNotetype?.isCloze) {
            true -> {
                val note =
                    col.newNote(mEditedNotetype!!).apply {
                        for ((index, field) in getBundleEditFields(fieldsBundle).withIndex()) {
                            this.setField(index, field)
                        }
                    }
                val clozeNumber = mOrdinal + 1
                col.clozeNumbersInNote(note).indexOf(clozeNumber)
            }
            else -> ordinal
        }
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param index The index in the templates for the model. NOT `ord`
     */
    fun getDummyCard(
        notetype: NotetypeJson,
        index: Int,
    ): Card? {
        return getDummyCard(notetype, index, notetype.fieldsNames.toMutableList())
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param cardIndex The index in the templates for the model. NOT `ord`
     */
    private fun getDummyCard(
        notetype: NotetypeJson?,
        cardIndex: Int,
        fieldValues: MutableList<String>,
    ): Card? {
        Timber.d("getDummyCard() Creating dummy note for index %s", cardIndex)
        if (notetype == null) {
            return null
        }
        if (mAllFieldsNull) {
            getLabels(fieldValues)
        }
        val n = getColUnsafe.newNote(notetype)
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
            return EphemeralCard.fromNote(n, getColUnsafe, cardIndex)
        } catch (e: Exception) {
            // Calling code handles null return, so we can log this for developer's interest but move on
            Timber.d(e, "getDummyCard() unable to create card")
        }
        return null
    }

    /** Override certain aspects of Card behavior so we may display unsaved data  */
    inner class PreviewerCard(col: Collection, id: Long) : Card(col, id) {
        private val mNote: Note? = null

        // if we have an unsaved note saved, use it instead of a collection lookup
        override fun note(reload: Boolean): Note {
            return mNote ?: super.note(reload)
        }

        /** if we have an unsaved note saved, use it instead of a collection lookup  */
        override fun note(): Note {
            return mNote ?: super.note()
        }

        /** if we have an unsaved note, never return empty  */
        val isEmpty: Boolean
            get() = mNote != null

        /** Override the method that fetches the model so we can render unsaved models  */
        override fun model(): NotetypeJson {
            return mEditedNotetype ?: super.model()
        }

        override fun renderOutput(
            reload: Boolean,
            browser: Boolean,
        ): TemplateRenderOutput {
            if (renderOutput == null || reload) {
                val index =
                    if (model().isCloze) {
                        0
                    } else {
                        ord
                    }
                val context =
                    TemplateManager.TemplateRenderContext.fromCardLayout(
                        note(),
                        this,
                        model(),
                        model().getJSONArray("tmpls")[index] as JSONObject,
                        fillEmpty = false,
                    )
                renderOutput =
                    context.render()
            }
            return renderOutput!!
        }
    }
}

private class EphemeralCard(col: Collection, id: Long?) : Card(col, id) {
    override fun renderOutput(
        reload: Boolean,
        browser: Boolean,
    ): TemplateRenderOutput {
        return this.renderOutput!!
    }

    companion object {
        fun fromNote(
            n: Note,
            col: Collection,
            cardIndex: Int = 0,
        ): EphemeralCard {
            val card = EphemeralCard(col, null)
            card.did = 1
            card.ord = n.cardIndexToOrd(cardIndex)
            Timber.v("Generating ephemeral note, idx %d ord %d", cardIndex, card.ord)

            val nt = n.notetype
            val templateIdx =
                if (nt.type == Consts.MODEL_CLOZE) {
                    0
                } else {
                    cardIndex
                }
            val template = nt.tmpls[templateIdx] as JSONObject
            template.put("ord", card.ord)

            val output =
                TemplateManager.TemplateRenderContext.fromCardLayout(
                    n,
                    card,
                    notetype = nt,
                    template = template,
                    fillEmpty = false,
                ).render()
            card.renderOutput = output
            card.setNote(n)
            return card
        }
    }
}

/** returns the number of cards from a note which has not had data saved in the database */
private fun Note.numberOfCardsEphemeral(): Int {
    // We can't use note.numberOfCards() as this uses the database value
    return when {
        this.notetype.isCloze -> col.clozeNumbersInNote(this).size
        else -> notetype.templatesNames.size
    }
}

/**
 * Given a card index, returns the 'ord' of the card
 */
private fun Note.cardIndexToOrd(index: Int): Int {
    // We can't use note.numberOfCards() as this uses the database value
    return when {
        this.notetype.isCloze -> col.clozeNumbersInNote(this)[index] - 1
        else -> index
    }
}
