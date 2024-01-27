/****************************************************************************************
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
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.NoteService.getFieldsAsBundleForPreview
import com.ichi2.libanki.Card
import com.ichi2.libanki.NotetypeJson
import com.ichi2.utils.stringIterable
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CardTemplatePreviewerTest : RobolectricTest() {

    @Test
    fun testPreviewUnsavedTemplate() {
        val modelName = "Basic"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        var previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        var testCardTemplatePreviewer = previewerController.get()
        Assert.assertTrue(
            "model change did not show up?",
            testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.question().contains("PREVIEWER_TEST") &&
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.answer().contains("PREVIEWER_TEST")
        )

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        val outBundle = Bundle()
        previewerController.saveInstanceState(outBundle)
        previewerController.pause().stop().destroy()
        previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(previewerController)
        testCardTemplatePreviewer = previewerController.get()
        Assert.assertTrue(
            "model change not preserved in lifecycle??",
            testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.question().contains("PREVIEWER_TEST") &&
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.answer().contains("PREVIEWER_TEST")
        )

        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testCardTemplatePreviewer.showingAnswer)
        testCardTemplatePreviewer.disableDoubleClickPrevention()
        val showAnswerButton = testCardTemplatePreviewer.findViewById<View>(R.id.preview_buttons_layout)
        showAnswerButton.performClick()
        Assert.assertTrue("Not showing the answer?", testCardTemplatePreviewer.showingAnswer)
    }

    @Test
    fun testPreviewUnsavedTemplate_Basic() {
        val modelName = "Basic"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val fields = collectionBasicModelOriginal.fieldsNames
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        val previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        val testCardTemplatePreviewer = previewerController.get()
        val arr = testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.note().fields
        assertThat(arr[0], equalTo("(" + fields[0] + ")"))
        assertThat(arr[1], equalTo("(" + fields[1] + ")"))
    }

    @Test
    @Config(qualifiers = "en")
    fun testPreviewUnsavedTemplate_Cloze() {
        val modelName = "Cloze"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val fields = collectionBasicModelOriginal.fieldsNames
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        val previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        val testCardTemplatePreviewer = previewerController.get()
        val arr = testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.note().fields
        assertThat(arr[0], equalTo(testCardTemplatePreviewer.getString(R.string.cloze_sample_text, "c1")))
        assertThat(arr[1], equalTo("(" + fields[1] + ")"))
    }

    @Test
    @Config(qualifiers = "en")
    fun testPreviewUnsavedTemplate_basic_answer() {
        val modelName = "Basic (type in the answer)"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val fields = collectionBasicModelOriginal.fieldsNames
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        val previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        val testCardTemplatePreviewer = previewerController.get()
        val arr = testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.note().fields
        assertThat(arr[0], equalTo("(" + fields[0] + ")"))
        assertThat(arr[1], equalTo(testCardTemplatePreviewer.getString(R.string.basic_answer_sample_text)))
    }

    @Test
    fun testPreviewNormal() {
        // Make sure we test previewing a new card template
        val modelName = "Basic (and reversed card)"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val testCard1 = getSavedCard(collectionBasicModelOriginal, 0)
        val testCard2 = getSavedCard(collectionBasicModelOriginal, 1)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("cardList", longArrayOf(testCard1.id, testCard2.id))
        intent.putExtra("index", 0)

        var previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        val outBundle = Bundle()
        previewerController.saveInstanceState(outBundle)
        previewerController.pause().stop().destroy()
        previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(previewerController)
        val testCardTemplatePreviewer = previewerController.get()

        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testCardTemplatePreviewer.showingAnswer)
        testCardTemplatePreviewer.disableDoubleClickPrevention()
        val showAnswerButton = testCardTemplatePreviewer.findViewById<View>(R.id.preview_buttons_layout)
        showAnswerButton.performClick()
        Assert.assertTrue("Not showing the answer?", testCardTemplatePreviewer.showingAnswer)
    }

    @Test
    fun testPreviewNoteEditorFieldData() {
        val cloze = "Cloze"
        val basicAndReverseOptional = "Basic (optional reversed card)"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(cloze)
        val basicAndReverseOptionalModel = getCurrentDatabaseModelCopy(basicAndReverseOptional)
        val cardsList = ArrayList<String>()
        val tmpls = collectionBasicModelOriginal.getJSONArray("tmpls")
        for (name in tmpls.stringIterable()) {
            cardsList.add(name)
        }
        Assert.assertTrue(cardsList.size == 1)
        cardsList.clear()
        val tmplsBR = basicAndReverseOptionalModel.getJSONArray("tmpls")
        for (name in tmplsBR.stringIterable()) {
            cardsList.add(name)
        }
        Assert.assertTrue(cardsList.size == 2)
    }

    @Test
    fun singleTemplateFromNoteEditorHasNoNavigation() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, "Hello"))
        fields.add(Field(1, "World"))

        val basicModel = getCurrentDatabaseModelCopy("Basic")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertThat("prev should not be visible", testCardTemplatePreviewer.previousButtonVisible(), equalTo(false))
        assertThat("next should not be visible", testCardTemplatePreviewer.nextButtonVisible(), equalTo(false))
    }

    @Test
    fun nonClozeFromNoteEditorHasMultipleCards() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, "Hello"))
        fields.add(Field(1, "World"))

        val basicModel = getCurrentDatabaseModelCopy("Basic (and reversed card)")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertTwoCards(testCardTemplatePreviewer)
    }

    @Test
    fun `Note Editor Cloze preview displays multiple cards - Issue 14717`() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, "{{c1::Hello}} {{c3::World}}"))
        fields.add(Field(1, "World"))

        val basicModel = getCurrentDatabaseModelCopy("Cloze")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertThat("card 1 has content", testCardTemplatePreviewer.cardContent, containsString("World"))

        assertTwoCards(testCardTemplatePreviewer)

        // ensure that template 2 is valid
        testCardTemplatePreviewer.onNextCard()
        assertThat("card 2 has content", testCardTemplatePreviewer.cardContent, containsString("Hello"))
    }

    @Test
    fun cardTemplatePreviewerSecondOrd_issue8001() {
        val cid = addNoteUsingBasicAndReversedModel("hello", "world").cards()[1].id

        val model = getCurrentDatabaseModelCopy("Basic (and reversed card)")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, model)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("ordinal", 1)
        intent.putExtra("cardListIndex", 0)
        intent.putExtra("cardList", longArrayOf(cid))

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertThat(testCardTemplatePreviewer.cardContent, containsString("world"))
        assertThat(testCardTemplatePreviewer.cardContent, not(containsString("hello")))
        assertThat(testCardTemplatePreviewer.cardContent, not(containsString("Front")))
    }

    @Test
    fun `Previewing an empty note should not crash`() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, ""))
        fields.add(Field(1, ""))

        val basicModel = getCurrentDatabaseModelCopy("Basic")
        val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertThat("A blank card can be previewed", testCardTemplatePreviewer.cardContent, containsString("The front of this card is blank"))
    }

    @Test
    fun `Issue 14692 - 'Read Text' enabled, and previewing a card from the note editor`() {
        // Hack: TTS Doesn't work in Robolectric, instead we directly test the `readCardTts` method
        val fields: MutableList<NoteService.NoteField?> = arrayListOf(
            Field(0, "Hello"),
            Field(1, "World")
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val basicModel = getCurrentDatabaseModelCopy("Basic (and reversed card)")
            val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, basicModel)
            putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
            putExtra(
                "noteEditorBundle",
                bundleOf(
                    "editFields" to getFieldsAsBundleForPreview(fields),
                    "did" to 1L
                )
            )
        }

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        // TTS doesn't get initialized in Robolectric, but we've passed through rendering the card
        // so treat the UninitializedPropertyAccessException as a success
        assertThrows<UninitializedPropertyAccessException> {
            testCardTemplatePreviewer.readCardTts(SingleCardSide.FRONT)
        }
    }

    @Test
    fun `The ordinal provided is used (standard) - Issue 14694`() {
        val fields: MutableList<NoteService.NoteField?> = arrayListOf(
            Field(0, "Hello"),
            Field(1, "World")
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val basicModel = getCurrentDatabaseModelCopy("Basic (and reversed card)")
            val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, basicModel)
            putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
            putExtra(
                "noteEditorBundle",
                bundleOf(
                    "editFields" to getFieldsAsBundleForPreview(fields),
                    "did" to 1L
                )
            )
            putExtra("ordinal", 1)
        }

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertThat("Front is not displayed", testCardTemplatePreviewer.cardContent, not(containsString(">Hello<")))
        assertThat("Back is displayed", testCardTemplatePreviewer.cardContent, containsString(">World<"))
    }

    @Test
    fun `The ordinal provided is used (cloze)- Issue 14694`() {
        val fields: MutableList<NoteService.NoteField?> = arrayListOf(
            Field(0, "{{c1::Hello}} {{c3::World}}"),
            Field(1, "Extra")
        )
        val ordinalOfSecondCard = 2

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val clozeModel = getCurrentDatabaseModelCopy("Cloze")
            val tempModelPath = CardTemplateNotetype.saveTempModel(targetContext, clozeModel)
            putExtra(CardTemplateNotetype.INTENT_MODEL_FILENAME, tempModelPath)
            putExtra(
                "noteEditorBundle",
                bundleOf(
                    "editFields" to getFieldsAsBundleForPreview(fields),
                    "did" to 1L
                )
            )
            putExtra("ordinal", ordinalOfSecondCard)
        }

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        // data-cloze contains the string, even if the cloze is hidden, so search for >term<
        assertThat("ord 1 is displayed", testCardTemplatePreviewer.cardContent, containsString(">Hello<"))
        assertThat("ord 1 is hidden", testCardTemplatePreviewer.cardContent, not(containsString(">World<")))
    }

    private fun getFieldsAsBundleForPreview(fields: List<NoteService.NoteField?>?): Bundle {
        return getFieldsAsBundleForPreview(fields, false)
    }

    private fun assertTwoCards(testCardTemplatePreviewer: TestCardTemplatePreviewer) {
        assertThat("prev should not be enabled", testCardTemplatePreviewer.previousButtonEnabled(), equalTo(false))
        assertThat("next should be enabled", testCardTemplatePreviewer.nextButtonEnabled(), equalTo(true))

        testCardTemplatePreviewer.onNextCard()

        assertThat("index is changed", testCardTemplatePreviewer.cardIndex, equalTo(1))
        assertThat("prev should be enabled", testCardTemplatePreviewer.previousButtonEnabled(), equalTo(true))
        assertThat("next should not be enabled", testCardTemplatePreviewer.nextButtonEnabled(), equalTo(false))

        testCardTemplatePreviewer.onNextCard()

        // no effect
        assertThat("index is changed", testCardTemplatePreviewer.cardIndex, equalTo(1))
        assertThat("prev should be enabled", testCardTemplatePreviewer.previousButtonEnabled(), equalTo(true))
        assertThat("next should not be enabled", testCardTemplatePreviewer.nextButtonEnabled(), equalTo(false))

        testCardTemplatePreviewer.onPreviousCard()

        // previous
        assertThat("index is changed", testCardTemplatePreviewer.cardIndex, equalTo(0))
        assertThat("prev should be enabled", testCardTemplatePreviewer.previousButtonEnabled(), equalTo(false))
        assertThat("next should not be enabled", testCardTemplatePreviewer.nextButtonEnabled(), equalTo(true))
    }

    private fun getSavedCard(notetype: NotetypeJson, ordinal: Int): Card {
        val n = col.newNote(notetype)
        val fieldNames = notetype.fieldsNames
        for (i in fieldNames.indices) {
            n.setField(i, fieldNames[i])
        }
        col.addNote(n)
        print(ordinal)
        return n.cards()[0]
    }

    private inner class Field(
        override val ord: Int,
        override val fieldText: String
    ) : NoteService.NoteField
}
