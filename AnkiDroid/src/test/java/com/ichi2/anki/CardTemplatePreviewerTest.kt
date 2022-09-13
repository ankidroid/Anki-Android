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
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.NoteService.getFieldsAsBundleForPreview
import com.ichi2.libanki.Card
import com.ichi2.libanki.Model
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CardTemplatePreviewerTest : RobolectricTest() {

    @Test
    fun testPreviewUnsavedTemplate() {

        val modelName = "Basic"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        var previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        var testCardTemplatePreviewer = previewerController.get()
        assertTrue(
            testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.q().contains("PREVIEWER_TEST") &&
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.a().contains("PREVIEWER_TEST"),
            "model change did not show up?"
        )

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        val outBundle = Bundle()
        previewerController.saveInstanceState(outBundle)
        previewerController.pause().stop().destroy()
        previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(previewerController)
        testCardTemplatePreviewer = previewerController.get()
        assertTrue(
            testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.q().contains("PREVIEWER_TEST") &&
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.a().contains("PREVIEWER_TEST"),
            "model change not preserved in lifecycle??"
        )

        // Make sure we can click
        assertFalse(testCardTemplatePreviewer.showingAnswer, "Showing the answer already?")
        testCardTemplatePreviewer.disableDoubleClickPrevention()
        val showAnswerButton = testCardTemplatePreviewer.findViewById<View>(R.id.preview_buttons_layout)
        showAnswerButton.performClick()
        assertTrue(testCardTemplatePreviewer.showingAnswer, "Not showing the answer?")
    }

    @Test
    fun testPreviewUnsavedTemplate_Basic() {
        val modelName = "Basic"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val fields = collectionBasicModelOriginal.fieldsNames
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        val previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        val testCardTemplatePreviewer = previewerController.get()
        val arr = testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.note().fields
        assertEquals(arr[0], "(" + fields[0] + ")")
        assertEquals(arr[1], "(" + fields[1] + ")")
    }

    @Test
    @Config(qualifiers = "en")
    fun testPreviewUnsavedTemplate_Cloze() {
        val modelName = "Cloze"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val fields = collectionBasicModelOriginal.fieldsNames
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        val previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        val testCardTemplatePreviewer = previewerController.get()
        val arr = testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.note().fields
        assertEquals(arr[0], testCardTemplatePreviewer.getString(R.string.cloze_sample_text, "c1"))
        assertEquals(arr[1], "(" + fields[1] + ")")
    }

    @Test
    @Config(qualifiers = "en")
    fun testPreviewUnsavedTemplate_basic_answer() {
        val modelName = "Basic (type in the answer)"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val fields = collectionBasicModelOriginal.fieldsNames
        val template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0)
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, collectionBasicModelOriginal)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("index", 0)

        val previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(previewerController)
        val testCardTemplatePreviewer = previewerController.get()
        val arr = testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0)!!.note().fields
        assertEquals(arr[0], "(" + fields[0] + ")")
        assertEquals(arr[1], testCardTemplatePreviewer.getString(R.string.basic_answer_sample_text))
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
        assertFalse(testCardTemplatePreviewer.showingAnswer, "Showing the answer already?")
        testCardTemplatePreviewer.disableDoubleClickPrevention()
        val showAnswerButton = testCardTemplatePreviewer.findViewById<View>(R.id.preview_buttons_layout)
        showAnswerButton.performClick()
        assertTrue(testCardTemplatePreviewer.showingAnswer, "Not showing the answer?")
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
        assertTrue(cardsList.size == 1)
        cardsList.clear()
        val tmplsBR = basicAndReverseOptionalModel.getJSONArray("tmpls")
        for (name in tmplsBR.stringIterable()) {
            cardsList.add(name)
        }
        assertTrue(cardsList.size == 2)
    }

    @Test
    fun singleTemplateFromNoteEditorHasNoNavigation() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, "Hello"))
        fields.add(Field(1, "World"))

        val basicModel = getCurrentDatabaseModelCopy("Basic")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putInt("ordinal", 0)
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertFalse(testCardTemplatePreviewer.previousButtonVisible(), "prev should not be visible")
        assertFalse(testCardTemplatePreviewer.nextButtonVisible(), "next should not be visible")
    }

    @Test
    fun nonClozeFromNoteEditorHasMultipleCards() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, "Hello"))
        fields.add(Field(1, "World"))

        val basicModel = getCurrentDatabaseModelCopy("Basic (and reversed card)")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putInt("ordinal", 0)
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertTwoCards(testCardTemplatePreviewer)
    }

    @Test
    fun clozeFromEditorHasMultipleCards() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, "{{c1::Hello}} {{c3::World}}"))
        fields.add(Field(1, "World"))

        val basicModel = getCurrentDatabaseModelCopy("Cloze")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putInt("ordinal", 0)
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertTwoCards(testCardTemplatePreviewer)
    }

    @Test
    fun cardTemplatePreviewerSecondOrd_issue8001() {
        val cid = addNoteUsingBasicAndReversedModel("hello", "world").cards()[1].id

        val model = getCurrentDatabaseModelCopy("Basic (and reversed card)")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, model)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)
        intent.putExtra("ordinal", 1)
        intent.putExtra("cardListIndex", 0)
        intent.putExtra("cardList", longArrayOf(cid))

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertThat(testCardTemplatePreviewer.cardContent, containsString("world"))
        assertThat(testCardTemplatePreviewer.cardContent, not(containsString("hello")))
        assertThat(testCardTemplatePreviewer.cardContent, not(containsString("Front")))
    }

    @Test
    fun cardTemplatePreviewerNoCards_issue9687() {
        val fields: MutableList<NoteService.NoteField?> = ArrayList()
        fields.add(Field(0, ""))
        fields.add(Field(1, ""))

        val basicModel = getCurrentDatabaseModelCopy("Basic")
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, basicModel)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath)

        val noteEditorBundle = Bundle()
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields))
        noteEditorBundle.putInt("ordinal", 0)
        noteEditorBundle.putLong("did", 1)
        intent.putExtra("noteEditorBundle", noteEditorBundle)

        val testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer::class.java, intent)

        assertTrue(testCardTemplatePreviewer.isFinishing, "Activity should be finishing - no cards to show")
    }

    @KotlinCleanup("Change visibility to private")
    protected fun getFieldsAsBundleForPreview(fields: List<NoteService.NoteField?>?): Bundle {
        return getFieldsAsBundleForPreview(fields, false)
    }

    @KotlinCleanup("Change visibility to private")
    protected fun assertTwoCards(testCardTemplatePreviewer: TestCardTemplatePreviewer) {
        assertFalse(testCardTemplatePreviewer.previousButtonEnabled(), "prev should not be enabled")
        assertTrue(testCardTemplatePreviewer.nextButtonEnabled(), "next should be enabled")

        testCardTemplatePreviewer.onNextTemplate()

        assertEquals(testCardTemplatePreviewer.templateIndex, 1, "index is changed")
        assertTrue(testCardTemplatePreviewer.previousButtonEnabled(), "prev should be enabled")
        assertFalse(testCardTemplatePreviewer.nextButtonEnabled(), "next should not be enabled")

        testCardTemplatePreviewer.onNextTemplate()

        // no effect
        assertEquals(testCardTemplatePreviewer.templateIndex, 1, "index is changed")
        assertTrue(testCardTemplatePreviewer.previousButtonEnabled(), "prev should be enabled")
        assertFalse(testCardTemplatePreviewer.nextButtonEnabled(), "next should not be enabled")

        testCardTemplatePreviewer.onPreviousTemplate()

        // previous
        assertEquals(testCardTemplatePreviewer.templateIndex, 0, "index is changed")
        assertFalse(testCardTemplatePreviewer.previousButtonEnabled(), "prev should be enabled")
        assertTrue(testCardTemplatePreviewer.nextButtonEnabled(), "next should not be enabled")
    }

    private fun getSavedCard(model: Model, ordinal: Int): Card {
        val n = col.newNote(model)
        val fieldNames = model.fieldsNames
        for (i in fieldNames.indices) {
            n.setField(i, fieldNames[i])
        }
        n.flush()
        return col.getNewLinkedCard(Card(col), n, model.getJSONArray("tmpls").getJSONObject(ordinal), 1, 1, true)
    }

    @KotlinCleanup("Override fieldText in constructor and remove text")
    private inner class Field(override val ord: Int, private val text: String) : NoteService.NoteField {
        override val fieldText: String
            get() = text
    }
}
