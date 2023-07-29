/****************************************************************************************
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

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.WhichButton
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.libanki.Model
import com.ichi2.testutils.assertFalse
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import timber.log.Timber
import kotlin.test.junit5.JUnit5Asserter.assertEquals
import kotlin.test.junit5.JUnit5Asserter.assertNotEquals
import kotlin.test.junit5.JUnit5Asserter.assertNotNull
import kotlin.test.junit5.JUnit5Asserter.assertNull
import kotlin.test.junit5.JUnit5Asserter.assertTrue

// @Ignore
@RunWith(AndroidJUnit4::class)
class CardTemplateEditorTest : RobolectricTest() {
    @Test
    @Throws(Exception::class)
    fun testEditTemplateContents() {
        val modelName = "Basic"

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        var templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        var testEditor = templateEditorController.get()
        assertFalse("Model should not have changed yet", testEditor.modelHasChanged())

        // Change the model and make sure it registers as changed, but the database is unchanged
        var templateFront = testEditor.findViewById<EditText>(R.id.editor_editText)
        val testModelQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        templateFront.text.append(testModelQfmtEdit)
        advanceRobolectricLooperWithSleep()
        assertTrue("Model did not change after edit?", testEditor.modelHasChanged())
        assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Kill and restart the Activity, make sure model edit is preserved
        val outBundle = Bundle()
        templateEditorController.saveInstanceState(outBundle)
        templateEditorController.pause().stop().destroy()
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        var shadowTestEditor = shadowOf(testEditor)
        assertTrue("model change not preserved across activity lifecycle?", testEditor.modelHasChanged())
        assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Make sure we get a confirmation dialog if we hit the back button
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        advanceRobolectricLooperWithSleep()
        assertEquals("Wrong dialog shown?", getAlertDialogText(true), getResourceString(R.string.discard_unsaved_changes))
        clickAlertDialogButton(DialogInterface.BUTTON_NEGATIVE, false)
        advanceRobolectricLooperWithSleep()
        assertTrue("model change not preserved despite canceling back button?", testEditor.modelHasChanged())

        // Make sure we things are cleared out after a cancel
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        assertEquals("Wrong dialog shown?", getAlertDialogText(true), getResourceString(R.string.discard_unsaved_changes))
        clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, false)
        advanceRobolectricLooperWithSleep()
        assertFalse("model change not cleared despite discarding changes?", testEditor.modelHasChanged())

        // Get going for content edit assertions again...
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        templateFront = testEditor.findViewById(R.id.editor_editText)
        templateFront.text.append(testModelQfmtEdit)
        advanceRobolectricLooperWithSleep()
        assertTrue("Model did not change after edit?", testEditor.modelHasChanged())

        // Make sure we pass the edit to the Previewer
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        advanceRobolectricLooperWithSleep()
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        advanceRobolectricLooperWithSleep()
        assertEquals("Previewer not started?", CardTemplatePreviewer::class.java.name, shadowIntent.intentClass.name)
        assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME))
        assertNotEquals("Model sent to Previewer is unchanged?", testEditor.tempModel?.model, TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)!!))
        assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, Intent())

        // Save the template then fetch it from the collection to see if it was saved correctly
        val testEditorModelEdited = testEditor.tempModel?.model
        advanceRobolectricLooperWithSleep()
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited)
        assertEquals("model did not save?", testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' })
        assertTrue("model does not have our change?", collectionBasicModelCopyEdited.toString().contains(testModelQfmtEdit))
    }

    @Test
    fun testDeleteTemplate() {
        val modelName = "Basic (and reversed card)"

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        val templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        assertEquals("Model should have 2 templates now", 2, testEditor.tempModel?.templateCount)

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getMaterialDialogText(true))
        clickMaterialDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertTrue("Model should have changed", testEditor.modelHasChanged())
        assertEquals("Model should have 1 template now", 1, testEditor.tempModel?.templateCount)

        // Try to delete the template again, but there's only one
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals(
            "Did not show dialog about deleting only card?",
            getResourceString(R.string.card_template_editor_cant_delete),
            getAlertDialogText(true)
        )
        assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Save the change to the database and make sure there's only one template after
        val testEditorModelEdited = testEditor.tempModel?.model
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited)
        assertEquals("model did not save?", testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' })
    }

    @Test
    @Throws(Exception::class)
    fun testTemplateAdd() {
        // Make sure we test previewing a new card template - not working for real yet
        val modelName = "Basic"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        val templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        addCardType(testEditor, shadowTestEditor)
        // if AnkiDroid moves to match AnkiDesktop it will pop a dialog to confirm card create
        // Assert.assertEquals("Wrong dialog shown?", "This will create NN cards. Proceed?", getDialogText());
        // clickDialogButton(WhichButton.POSITIVE);
        assertTrue("Model should have changed", testEditor.modelHasChanged())
        assertEquals("Change not pending add?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))
        assertEquals("Model should have 2 templates now", 2, testEditor.tempModel!!.templateCount)

        // Make sure we pass the new template to the Previewer
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        assertEquals("Previewer not started?", CardTemplatePreviewer::class.java.name, shadowIntent.intentClass.name)
        assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME))
        assertEquals("intent did not have ordinal?", 1, startedIntent.getIntExtra("ordinal", -1))
        assertNotEquals("Model sent to Previewer is unchanged?", testEditor.tempModel?.model, TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)!!))
        assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Save the change to the database and make sure there are two templates after
        val testEditorModelEdited = testEditor.tempModel?.model
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited)
        assertEquals("model did not save?", testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' })
    }

    /**
     * In a model with two card templates using different fields, some notes may only use card 1,
     * and some may only use card 2. If you delete the 2nd template,
     * it will cause the notes that only use card 2 to disappear.
     *
     * So the unit test would then be to make a model like the "basic (optional reverse card)"
     * with two fields Enable1 and Enable2, and two templates "card 1" and "card 2".
     * Both cards use selective generation, so they're empty unless the corresponding field is set.
     *
     * So then in the unit test you make the model, add the two templates, then you add two notes,
     * with Enable1 and Enable2 respectively set to "y".
     * Then you try to delete one of the templates and it should fail
     *
     * (question: but I thought deleting one should work - still one card left to maintain the note,
     * and second template delete should fail since we finally get to a place where no cards are left?
     * I am having trouble creating selectively generated cards though - I can do one optional field but not 2 ugh)
     *
     */
    @Test
    fun testDeleteTemplateWithSelectivelyGeneratedCards() {
        val modelName = "Basic (optional reversed card)"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        val templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        assertEquals("Model should have 2 templates now", 2, testEditor.tempModel?.templateCount)
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))

        // Try to delete Card 1 template - click delete, check confirm for card delete popup indicating it was possible, then dismiss it
        val shadowTestEditor = shadowOf(testEditor)
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getMaterialDialogText(true))
        clickMaterialDialogButton(WhichButton.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        assertFalse("Model should not have changed", testEditor.modelHasChanged())

        // Create note with forward and back info, Add Reverse is empty, so should only be one card
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        val fields = selectiveGeneratedNote.fields
        for (field in fields) {
            Timber.d("Got a field: %s", field)
        }
        col.addNote(selectiveGeneratedNote)
        assertEquals("selective generation should result in one card", 1, getModelCardCount(collectionBasicModelOriginal))

        // Try to delete the template again, but there's selective generation means it would orphan the note
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals(
            "Did not show dialog about deleting only card?",
            getResourceString(R.string.card_template_editor_would_delete_note),
            getAlertDialogText(true)
        )
        clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertNull("Can delete used template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertEquals("Change incorrectly added to list?", 0, testEditor.tempModel?.templateChanges?.size)

        // Assert can delete 'Card 2'
        assertNotNull("Cannot delete unused template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))

        // Edit note to have Add Reverse set to 'y' so we get a second card
        selectiveGeneratedNote.setField(2, "y")
        selectiveGeneratedNote.flush()

        // - assert two cards
        assertEquals("should be two cards now", 2, getModelCardCount(collectionBasicModelOriginal))

        // - assert can delete either Card template but not both
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))

        // A couple more notes to make sure things are okay
        val secondNote = col.newNote(collectionBasicModelOriginal)
        secondNote.setField(0, "TestFront2")
        secondNote.setField(1, "TestBack2")
        secondNote.setField(2, "y")
        col.addNote(secondNote)

        // - assert can delete either Card template but not both
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
    }

    /**
     * Normal template deletion - with no selective generation should of course work
     */
    @Test
    fun testDeleteTemplateWithGeneratedCards() = runTest {
        val modelName = "Basic (and reversed card)"
        var collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        var intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        var templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        var testEditor = templateEditorController.get()
        assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        assertEquals("Model should have 2 templates now", 2, testEditor.tempModel?.templateCount)
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))

        // Create note with forward and back info
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        col.addNote(selectiveGeneratedNote)
        assertEquals("card generation should result in two cards", 2, getModelCardCount(collectionBasicModelOriginal))

        // Test if we can delete the template - should be possible - but cancel the delete
        var shadowTestEditor = shadowOf(testEditor)
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
            getMaterialDialogText(true)
        )
        clickMaterialDialogButton(WhichButton.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        assertEquals("Change in database despite no change?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        assertEquals("Model should have 2 templates still", 2, testEditor.tempModel?.templateCount)

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        addCardType(testEditor, shadowTestEditor)
        assertTrue("Model should have changed", testEditor.modelHasChanged())
        assertEquals("Change added but not adjusted correctly?", 2, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))
        assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 2))
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        assertFalse("Model should now be unchanged", testEditor.modelHasChanged())
        assertEquals("card generation should result in three cards", 3, getModelCardCount(collectionBasicModelOriginal))
        collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName) // reload the model for future comparison after saving the edit

        // Start the CardTemplateEditor back up after saving (which closes the thing...)
        intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 2))
        assertEquals("Model should have 3 templates now", 3, testEditor.tempModel?.templateCount)

        // Add another template - but we work in memory for a while before saving
        addCardType(testEditor, shadowTestEditor)
        assertEquals("Change added but not adjusted correctly?", 3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0))
        assertTrue("Model should have changed", testEditor.modelHasChanged())
        assertEquals("Model should have 4 templates now", 4, testEditor.tempModel?.templateCount)
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 2))
        assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 3))
        assertEquals("Change added but not adjusted correctly?", 3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0))

        // Delete two pre-existing templates for real now - but still without saving it out, should work fine
        advanceRobolectricLooperWithSleep()
        testEditor.viewPager.currentItem = 0
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
            getMaterialDialogText(true)
        )
        clickMaterialDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        testEditor.viewPager.currentItem = 0
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
            getMaterialDialogText(true)
        )
        clickMaterialDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()

        // - assert can delete any 1 or 2 Card templates but not all
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(2)))
        assertNotNull("Cannot delete two templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        assertNotNull("Cannot delete two templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 2)))
        assertNotNull("Cannot delete two templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1, 2)))
        assertNull("Can delete all templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1, 2)))
        assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        assertEquals("Change added but not adjusted correctly?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0))
        assertEquals("Change incorrectly pending add?", -1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 1))
        assertEquals("Change incorrectly pending add?", -1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 2))

        // Now confirm everything to persist it to the database
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        assertNotEquals("Change not in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        assertEquals("Model should have 2 templates now", 2, getCurrentDatabaseModelCopy(modelName).getJSONArray("tmpls").length())
        assertEquals("should be two cards", 2, getModelCardCount(collectionBasicModelOriginal))
    }

    /**
     * Deleting a template you just added - but in the same ordinal as a previous pending delete - should get it's card count correct
     */
    @Test
    fun testDeletePendingAddExistingCardCount() {
        val modelName = "Basic (optional reversed card)"
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        val templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        assertEquals("Model should have 2 templates now", 2, testEditor.tempModel?.templateCount)
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))

        // Create note with forward and back info
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        selectiveGeneratedNote.setField(2, "y")
        col.addNote(selectiveGeneratedNote)
        assertEquals("card generation should result in two cards", 2, getModelCardCount(collectionBasicModelOriginal))

        // Delete ord 1 / 'Card 2' and check the message
        val shadowTestEditor = shadowOf(testEditor)
        testEditor.viewPager.currentItem = 1
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
            getMaterialDialogText(true)
        )
        clickMaterialDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertTrue("Model should have changed", testEditor.modelHasChanged())
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        assertEquals("Change in database despite no save?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        assertEquals("Model should have 1 template", 1, testEditor.tempModel?.templateCount)

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        addCardType(testEditor, shadowTestEditor)
        assertTrue("Model should have changed", testEditor.modelHasChanged())
        assertEquals("Change added but not adjusted correctly?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 1))
        assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0))
        assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1))
        assertEquals("Model should have 2 templates", 2, testEditor.tempModel?.templateCount)

        // Delete ord 1 / 'Card 2' again and check the message - it's in the same spot as the pre-existing template but there are no cards actually associated
        testEditor.viewPager.currentItem = 1
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 0, 0, "Card 2"),
            getMaterialDialogText(true)
        )
        clickMaterialDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertTrue("Model should have changed", testEditor.modelHasChanged())
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        assertEquals("Change in database despite no save?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        assertEquals("Model should have 1 template", 1, testEditor.tempModel?.templateCount)

        // Save it out and make some assertions
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        assertFalse("Model should now be unchanged", testEditor.modelHasChanged())
        assertEquals("card generation should result in 1 card", 1, getModelCardCount(collectionBasicModelOriginal))
    }

    @Test
    fun testDeckOverride() {
        val modelName = "Basic (optional reversed card)"
        val model = getCurrentDatabaseModelCopy(modelName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", model.getLong("id"))
        val editor = super.startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        val template = editor.tempModel?.getTemplate(0)
        MatcherAssert.assertThat("Deck ID element should exist", template?.has("did"), Matchers.equalTo(true))
        MatcherAssert.assertThat("Deck ID element should be null", template?.get("did"), Matchers.equalTo(JSONObject.NULL))
        editor.onDeckSelected(SelectableDeck(1, "hello"))
        MatcherAssert.assertThat("Deck ID element should be changed", template?.get("did"), Matchers.equalTo(1L))
        editor.onDeckSelected(null)
        MatcherAssert.assertThat("Deck ID element should exist", template!!.has("did"), Matchers.equalTo(true))
        MatcherAssert.assertThat("Deck ID element should be null", template["did"], Matchers.equalTo(JSONObject.NULL))
    }

    @Test
    fun testContentPreservedAfterChangingEditorView() {
        val modelName = "Basic"

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        val templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        // Change the model and make sure it registers as changed, but the database is unchanged
        val templateEditText = testEditor.findViewById<EditText>(R.id.editor_editText)
        val testModelQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        val updatedFrontContent = templateEditText.text.append(testModelQfmtEdit).toString()
        advanceRobolectricLooperWithSleep()
        val cardTemplateFragment = testEditor.currentFragment
        val tempModel = testEditor.tempModel
        // set Bottom Navigation View to Style
        cardTemplateFragment!!.setCurrentEditorView(R.id.styling_edit, tempModel!!.css, R.string.card_template_editor_styling)

        // set Bottom Navigation View to Front
        cardTemplateFragment.setCurrentEditorView(R.id.front_edit, tempModel.getTemplate(0).getString("qfmt"), R.string.card_template_editor_front)

        // check if current content is updated or not
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(updatedFrontContent))
    }

    @Test
    fun testBottomNavigationViewLayoutTransition() {
        val modelName = "Basic"

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        val collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        val templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        // Change the model and make sure it registers as changed, but the database is unchanged
        val templateEditText = testEditor.findViewById<EditText>(R.id.editor_editText)
        advanceRobolectricLooperWithSleep()
        val cardTemplateFragment = testEditor.currentFragment
        val tempModel = testEditor.tempModel

        // check if current view is front(default) view
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(tempModel!!.getTemplate(0).getString("qfmt")))
        assumeThat(cardTemplateFragment!!.currentEditorViewId, Matchers.equalTo(R.id.front_edit))

        // set Bottom Navigation View to Style
        cardTemplateFragment.setCurrentEditorView(R.id.styling_edit, tempModel.css, R.string.card_template_editor_styling)

        // check if current view is changed or not
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(tempModel.css))
        assumeThat(cardTemplateFragment.currentEditorViewId, Matchers.equalTo(R.id.styling_edit))
    }

    private fun addCardType(testEditor: CardTemplateEditor, shadowTestEditor: ShadowActivity) {
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_add))
        advanceRobolectricLooperWithSleep()
        val ordinal = testEditor.viewPager.currentItem
        val numAffectedCards = if (!TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, ordinal)) {
            col.models.tmplUseCount(testEditor.tempModel!!.model, ordinal)
        } else {
            0
        }
        assertEquals(
            "Did not show dialog about adding template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_add, numAffectedCards, numAffectedCards),
            getMaterialDialogText(true)
        )
        clickMaterialDialogButton(WhichButton.POSITIVE, true)
    }

    private fun getModelCardCount(model: Model): Int {
        var cardCount = 0
        for (noteId in col.models.nids(model)) {
            cardCount += col.getNote(noteId).numberOfCards()
        }
        return cardCount
    }
}
