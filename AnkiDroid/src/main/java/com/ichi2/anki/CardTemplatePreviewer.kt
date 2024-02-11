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
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.PreviewLayout
import com.ichi2.anki.cardviewer.PreviewLayout.Companion.createAndDisplay
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
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
    private var editedModelFileName: String? = null
    private var editedNotetype: NotetypeJson? = null
    private var ordinal = 0

    /** The index of the card in cardList to show  */
    private var cardListIndex = 0

    /** The list (currently singular) of cards to be previewed
     * A single template was selected, and there was an associated card which exists
     */
    private var cardList: LongArray? = null
    private var noteEditorBundle: Bundle? = null
    private var showingAnswer = false

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
    private var allFieldsNull = true
    private var cardType: String? = null
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
            noteEditorBundle = parameters.getBundle("noteEditorBundle")
            editedModelFileName = parameters.getString(CardTemplateNotetype.INTENT_MODEL_FILENAME)
            cardList = parameters.getLongArray("cardList")
            ordinal = parameters.getInt("ordinal")
            cardListIndex = parameters.getInt("cardListIndex")
            showingAnswer = parameters.getBoolean("showingAnswer", showingAnswer)
        }
        if (editedModelFileName != null) {
            Timber.d("onCreate() loading edited model from %s", editedModelFileName)
            try {
                editedNotetype = CardTemplateNotetype.getTempModel(editedModelFileName!!)
                cardType = editedNotetype!!.optString("name")
            } catch (e: IOException) {
                Timber.w(e, "Unable to load temp model from file %s", editedModelFileName)
                closeCardTemplatePreviewer()
            }
        }
        setNavigationBarColor(R.attr.showAnswerColor)
        showBackIcon()
        // Ensure navigation drawer can't be opened. Various actions in the drawer cause crashes.
        disableDrawerSwipe()
        startLoadingCollection()
    }

    override fun onResume() {
        super.onResume()
        if (currentCard == null || ordinal < 0) {
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
        previewLayout = createAndDisplay(this, toggleAnswerHandler)
        previewLayout!!.setOnPreviousCard { onPreviousCard() }
        previewLayout!!.setOnNextCard { onNextCard() }
        previewLayout!!.hideNavigationButtons()
        previewLayout!!.setPrevButtonEnabled(false)
    }

    override fun displayCardQuestion() {
        super.displayCardQuestion()
        showingAnswer = false
        previewLayout!!.setShowingAnswer(false)
    }

    override fun displayCardAnswer() {
        if (allFieldsNull && cardType != null && cardType == getString(R.string.basic_typing_model_name)) {
            answerField!!.setText(getString(R.string.basic_answer_sample_text_user))
        }
        super.displayCardAnswer()
        showingAnswer = true
        previewLayout!!.setShowingAnswer(true)
    }

    override fun hideEaseButtons() {
        /* do nothing */
    }

    override fun displayAnswerBottomBar() {
        /* do nothing */
    }

    private val toggleAnswerHandler = View.OnClickListener {
        if (showingAnswer) {
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
        outState.putString(CardTemplateNotetype.INTENT_MODEL_FILENAME, editedModelFileName)
        outState.putLongArray("cardList", cardList)
        outState.putInt("ordinal", ordinal)
        outState.putInt("cardListIndex", cardListIndex)
        outState.putBundle("noteEditorBundle", noteEditorBundle)
        outState.putBoolean("showingAnswer", showingAnswer)
        super.onSaveInstanceState(outState)
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        if (noteEditorBundle != null) {
            allFieldsNull = false
            cardIndex = indexFromOrdinal(col, noteEditorBundle!!, ordinal)
            Timber.d("ord %d => idx %d", ordinal, cardIndex)
            // loading from the note editor
            val toPreview = setCurrentCardFromNoteEditorBundle(col)
            if (toPreview != null) {
                cardCount = toPreview.note(col).numberOfCardsEphemeral(col)
                if (cardCount >= 2) {
                    previewLayout!!.showNavigationButtons()
                }
            }
        } else {
            // loading from the card template editor
            allFieldsNull = true
            // card template with associated card due to opening from note editor
            if (cardList != null && cardListIndex >= 0 && cardListIndex < cardList!!.size) {
                currentCard = PreviewerCard(col, cardList!![cardListIndex])
            } else if (editedNotetype != null) { // bare note type (not coming from note editor), or new card template
                Timber.d("onCreate() CardTemplatePreviewer started with edited model and template index, displaying blank to preview formatting")
                currentCard = getDummyCard(editedNotetype!!, ordinal)
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
        if (showingAnswer) {
            displayCardAnswer()
        }
        showBackIcon()
    }

    override fun executeCommand(which: ViewerCommand, fromGesture: Gesture?): Boolean {
        if (which == ViewerCommand.SHOW_ANSWER) {
            return super.executeCommand(which, fromGesture)
        }
        return false
    }

    protected fun getCard(col: Collection, cardListIndex: Long): Card {
        return PreviewerCard(col, cardListIndex)
    }

    private fun setCurrentCardFromNoteEditorBundle(col: Collection): Card? {
        assert(noteEditorBundle != null)
        currentCard = getDummyCard(editedNotetype, cardIndex, getBundleEditFields(noteEditorBundle))
        // example: a basic card with no fields provided
        if (currentCard == null) {
            return null
        }
        val newDid = noteEditorBundle!!.getLong("did")
        if (col.decks.isDyn(newDid)) {
            currentCard!!.oDid = currentCard!!.did
        }
        currentCard!!.did = newDid
        val currentNote = currentCard!!.note(col)
        val tagsList = noteEditorBundle!!.getStringArrayList("tags")
        setTags(currentNote, tagsList)
        return currentCard
    }

    /**
     * Set the set tags of currentNote to tagsList.  We make no
     * assumption on the content of tagsList, except that its strings
     * are valid tags (i.e. no spaces in it).
     */
    private fun setTags(currentNote: Note, tagsList: List<String>?) {
        val currentTags = currentNote.tags.toTypedArray()
        for (tag in currentTags) {
            currentNote.delTag(tag)
        }
        if (tagsList != null) {
            val tagsSet = getColUnsafe.tags.canonify(tagsList)
            currentNote.addTags(tagsSet)
        }
    }

    private fun getLabels(fieldValues: MutableList<String>) {
        if (cardType != null && cardType == getString(R.string.cloze_model_name)) {
            fieldValues[0] = getString(R.string.cloze_sample_text, "c1")
        }
        if (cardType != null && cardType == getString(R.string.basic_typing_model_name)) {
            fieldValues[1] = getString(R.string.basic_answer_sample_text)
        }
    }

    private fun getBundleEditFields(noteEditorBundle: Bundle?): MutableList<String> {
        val noteFields = noteEditorBundle!!.getBundle("editFields")
            ?: return mutableListOf()
        // we map from "int" -> field, but the order isn't guaranteed, and there may be skips.
        // so convert this to a list of strings, with null in place of the invalid fields
        val elementCount = noteFields.keySet().stream().map { s: String -> s.toInt() }.max { obj: Int, anotherInteger: Int? -> obj.compareTo(anotherInteger!!) }.orElse(-1) + 1
        val ret = Array(elementCount) { "" } // init array, nulls cause a crash
        for (fieldOrd in noteFields.keySet()) {
            ret[fieldOrd.toInt()] = noteFields.getString(fieldOrd)!!
        }
        return mutableListOf(*ret)
    }

    private fun indexFromOrdinal(col: Collection, fieldsBundle: Bundle, ordinal: Int): Int {
        return when (editedNotetype?.isCloze) {
            true -> {
                val note = col.newNote(editedNotetype!!).apply {
                    for ((index, field) in getBundleEditFields(fieldsBundle).withIndex()) {
                        this.setField(index, field)
                    }
                }
                val clozeNumber = this.ordinal + 1
                col.clozeNumbersInNote(note).indexOf(clozeNumber)
            }
            else -> ordinal
        }
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param index The index in the templates for the model. NOT `ord`
     */
    fun getDummyCard(notetype: NotetypeJson, index: Int): Card? {
        return getDummyCard(notetype, index, notetype.fieldsNames.toMutableList())
    }

    /**
     * This method generates a note from a sample model, or fails if invalid
     * @param cardIndex The index in the templates for the model. NOT `ord`
     */
    private fun getDummyCard(notetype: NotetypeJson?, cardIndex: Int, fieldValues: MutableList<String>): Card? {
        Timber.d("getDummyCard() Creating dummy note for index %s", cardIndex)
        if (notetype == null) {
            return null
        }
        if (allFieldsNull) {
            getLabels(fieldValues)
        }
        val n = getColUnsafe.newNote(notetype)
        var i = 0
        while (i < fieldValues.size && i < n.fields.size) {
            if (allFieldsNull) {
                if (cardType != null && cardType == getString(R.string.cloze_model_name) && i == 0 ||
                    cardType == getString(R.string.basic_typing_model_name) && i == 1
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
            val ord = n.cardIndexToOrd(getColUnsafe, cardIndex)
            return n.ephemeralCard(getColUnsafe, ord = ord, customNoteType = notetype)
        } catch (e: Exception) {
            // Calling code handles null return, so we can log this for developer's interest but move on
            Timber.d(e, "getDummyCard() unable to create card")
        }
        return null
    }

    /** Override certain aspects of Card behavior so we may display unsaved data  */
    inner class PreviewerCard(col: Collection, id: Long) : Card(col, id) {
        private val _note: Note? = null

        /* if we have an unsaved note saved, use it instead of a collection lookup */
        override fun note(
            col: Collection,
            reload: Boolean
        ): Note {
            return _note ?: super.note(col, reload)
        }

        /** if we have an unsaved note saved, use it instead of a collection lookup  */
        override fun note(col: Collection): Note {
            return _note ?: super.note(col)
        }

        /** if we have an unsaved note, never return empty  */
        val isEmpty: Boolean
            get() = _note != null

        /** Override the method that fetches the model so we can render unsaved models  */
        override fun model(col: Collection): NotetypeJson {
            return editedNotetype ?: super.model(col)
        }

        override fun renderOutput(col: Collection, reload: Boolean, browser: Boolean): TemplateRenderOutput {
            if (renderOutput == null || reload) {
                val index = if (model(col).isCloze) {
                    0
                } else {
                    ord
                }
                val context = TemplateManager.TemplateRenderContext.fromCardLayout(
                    col,
                    note(col),
                    this,
                    model(col),
                    model(col).getJSONArray("tmpls")[index] as JSONObject,
                    fillEmpty = false
                )
                renderOutput =
                    context.render()
            }
            return renderOutput!!
        }
    }
}

/** returns the number of cards from a note which has not had data saved in the database */
private fun Note.numberOfCardsEphemeral(col: Collection): Int {
    // We can't use note.numberOfCards() as this uses the database value
    return when {
        this.notetype.isCloze -> col.clozeNumbersInNote(this).size
        else -> notetype.templatesNames.size
    }
}

/**
 * Given a card index, returns the 'ord' of the card
 */
private fun Note.cardIndexToOrd(col: Collection, index: Int): Int {
    // We can't use note.numberOfCards() as this uses the database value
    return when {
        this.notetype.isCloze -> col.clozeNumbersInNote(this)[index] - 1
        else -> index
    }
}
