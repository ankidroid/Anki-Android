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
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.WhichButton
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.libanki.Model
import com.ichi2.utils.JSONObject
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import timber.log.Timber
import kotlin.test.*

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
        assertFalse(testEditor.modelHasChanged(), "Model should not have changed yet")

        // Change the model and make sure it registers as changed, but the database is unchanged
        var templateFront = testEditor.findViewById<EditText>(R.id.editor_editText)
        val testModelQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        templateFront.text.append(testModelQfmtEdit)
        advanceRobolectricLooperWithSleep()
        assertTrue(testEditor.modelHasChanged(), "Model did not change after edit?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change already in database?")

        // Kill and restart the Activity, make sure model edit is preserved
        val outBundle = Bundle()
        templateEditorController.saveInstanceState(outBundle)
        templateEditorController.pause().stop().destroy()
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        var shadowTestEditor = shadowOf(testEditor)
        assertTrue(testEditor.modelHasChanged(), "model change not preserved across activity lifecycle?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change already in database?")

        // Make sure we get a confirmation dialog if we hit the back button
        assertTrue(shadowTestEditor.clickMenuItem(android.R.id.home), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(getDialogText(true), getResourceString(R.string.discard_unsaved_changes), "Wrong dialog shown?")
        clickDialogButton(WhichButton.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        assertTrue(testEditor.modelHasChanged(), "model change not preserved despite canceling back button?")

        // Make sure we things are cleared out after a cancel
        assertTrue(shadowTestEditor.clickMenuItem(android.R.id.home), "Unable to click?")
        assertEquals(getDialogText(true), getResourceString(R.string.discard_unsaved_changes), "Wrong dialog shown?")
        clickDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertFalse(testEditor.modelHasChanged(), "model change not cleared despite discarding changes?")

        // Get going for content edit assertions again...
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        templateFront = testEditor.findViewById(R.id.editor_editText)
        templateFront.text.append(testModelQfmtEdit)
        advanceRobolectricLooperWithSleep()
        assertTrue(testEditor.modelHasChanged(), "Model did not change after edit?")

        // Make sure we pass the edit to the Previewer
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_preview), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        advanceRobolectricLooperWithSleep()
        assertEquals(CardTemplatePreviewer::class.java.name, shadowIntent.intentClass.name, "Previewer not started?")
        assertNotNull(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME), "intent did not have model JSON filename?")
        assertNotEquals(testEditor.tempModel?.model, TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)!!), "Model sent to Previewer is unchanged?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change already in database?")
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, Intent())

        // Save the template then fetch it from the collection to see if it was saved correctly
        val testEditorModelEdited = testEditor.tempModel?.model
        advanceRobolectricLooperWithSleep()
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_confirm), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        assertNotEquals(collectionBasicModelOriginal, collectionBasicModelCopyEdited, "model is unchanged?")
        assertEquals(testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' }, "model did not save?")
        assertTrue(collectionBasicModelCopyEdited.toString().contains(testModelQfmtEdit), "model does not have our change?")
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
        assertFalse(testEditor.modelHasChanged(), "Model should not have changed yet")
        assertEquals(2, testEditor.tempModel?.templateCount, "Model should have 2 templates now")

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals("Delete the “Card 1” card type, and its 0 cards?", getDialogText(true), "Wrong dialog shown?")
        clickDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertTrue(testEditor.modelHasChanged(), "Model should have changed")
        assertEquals(1, testEditor.tempModel?.templateCount, "Model should have 1 template now")

        // Try to delete the template again, but there's only one
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(
            getResourceString(R.string.card_template_editor_cant_delete),
            getDialogText(true), "Did not show dialog about deleting only card?"
        )
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change already in database?")

        // Save the change to the database and make sure there's only one template after
        val testEditorModelEdited = testEditor.tempModel?.model
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_confirm), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        assertNotEquals(collectionBasicModelOriginal, collectionBasicModelCopyEdited, "model is unchanged?")
        assertEquals(testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' }, "model did not save?")
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
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        addCardType(testEditor, shadowTestEditor)
        // if AnkiDroid moves to match AnkiDesktop it will pop a dialog to confirm card create
        // assertEquals("This will create NN cards. Proceed?", getDialogText()), "Wrong dialog shown?";
        // clickDialogButton(WhichButton.POSITIVE);
        assertTrue(testEditor.modelHasChanged(), "Model should have changed")
        assertEquals(1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0), "Change not pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertTrue(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal not pending add?")
        assertEquals(2, testEditor.tempModel!!.templateCount, "Model should have 2 templates now")

        // Make sure we pass the new template to the Previewer
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_preview), "Unable to click?")
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        assertEquals(CardTemplatePreviewer::class.java.name, shadowIntent.intentClass.name, "Previewer not started?")
        assertNotNull(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME), "intent did not have model JSON filename?")
        assertEquals(1, startedIntent.getIntExtra("ordinal", -1), "intent did not have ordinal?")
        assertNotEquals(testEditor.tempModel?.model, TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)!!), "Model sent to Previewer is unchanged?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change already in database?")

        // Save the change to the database and make sure there are two templates after
        val testEditorModelEdited = testEditor.tempModel?.model
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_confirm), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        assertNotEquals(collectionBasicModelOriginal, collectionBasicModelCopyEdited, "model is unchanged?")
        assertEquals(testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' }, "model did not save?")
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
        assertFalse(testEditor.modelHasChanged(), "Model should not have changed yet")
        assertEquals(2, testEditor.tempModel?.templateCount, "Model should have 2 templates now")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal pending add?")

        // Try to delete Card 1 template - click delete, check confirm for card delete popup indicating it was possible, then dismiss it
        val shadowTestEditor = shadowOf(testEditor)
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals("Delete the “Card 1” card type, and its 0 cards?", getDialogText(true), "Wrong dialog shown?")
        clickDialogButton(WhichButton.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        assertFalse(testEditor.modelHasChanged(), "Model should not have changed")

        // Create note with forward and back info, Add Reverse is empty, so should only be one card
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        val fields = selectiveGeneratedNote.fields
        for (field in fields) {
            Timber.d("Got a field: %s", field)
        }
        col.addNote(selectiveGeneratedNote)
        assertEquals(1, getModelCardCount(collectionBasicModelOriginal), "selective generation should result in one card")

        // Try to delete the template again, but there's selective generation means it would orphan the note
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(
            getResourceString(R.string.card_template_editor_would_delete_note), getDialogText(true), "Did not show dialog about deleting only card?"
        )
        clickDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)), "Can delete used template?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change already in database?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertEquals(0, testEditor.tempModel?.templateChanges?.size, "Change incorrectly added to list?")

        // Assert can delete 'Card 2'
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)), "Cannot delete unused template?")

        // Edit note to have Add Reverse set to 'y' so we get a second card
        selectiveGeneratedNote.setField(2, "y")
        selectiveGeneratedNote.flush()

        // - assert two cards
        assertEquals(2, getModelCardCount(collectionBasicModelOriginal), "should be two cards now")

        // - assert can delete either Card template but not both
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)), "Cannot delete template?")
        assertNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)), "Can delete both templates?")

        // A couple more notes to make sure things are okay
        val secondNote = col.newNote(collectionBasicModelOriginal)
        secondNote.setField(0, "TestFront2")
        secondNote.setField(1, "TestBack2")
        secondNote.setField(2, "y")
        col.addNote(secondNote)

        // - assert can delete either Card template but not both
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)), "Cannot delete template?")
        assertNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)), "Can delete both templates?")
    }

    /**
     * Normal template deletion - with no selective generation should of course work
     */
    @Test
    fun testDeleteTemplateWithGeneratedCards() {
        val modelName = "Basic (and reversed card)"
        var collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName)

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        var intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        var templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        var testEditor = templateEditorController.get()
        assertFalse(testEditor.modelHasChanged(), "Model should not have changed yet")
        assertEquals(2, testEditor.tempModel?.templateCount, "Model should have 2 templates now")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal pending add?")

        // Create note with forward and back info
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        col.addNote(selectiveGeneratedNote)
        assertEquals(2, getModelCardCount(collectionBasicModelOriginal), "card generation should result in two cards")

        // Test if we can delete the template - should be possible - but cancel the delete
        var shadowTestEditor = shadowOf(testEditor)
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"), getDialogText(true), "Did not show dialog about deleting template and it's card?"
        )
        clickDialogButton(WhichButton.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)), "Cannot delete template?")
        assertNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)), "Can delete both templates?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change in database despite no change?")
        assertEquals(2, testEditor.tempModel?.templateCount, "Model should have 2 templates still")

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        addCardType(testEditor, shadowTestEditor)
        assertTrue(testEditor.modelHasChanged(), "Model should have changed")
        assertEquals(2, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0), "Change added but not adjusted correctly?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal pending add?")
        assertTrue(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 2), "Ordinal not pending add?")
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_confirm), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertFalse(testEditor.modelHasChanged(), "Model should now be unchanged")
        assertEquals(3, getModelCardCount(collectionBasicModelOriginal), "card generation should result in three cards")
        collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName) // reload the model for future comparison after saving the edit

        // Start the CardTemplateEditor back up after saving (which closes the thing...)
        intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        assertFalse(testEditor.modelHasChanged(), "Model should not have changed yet")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 2), "Ordinal pending add?")
        assertEquals(3, testEditor.tempModel?.templateCount, "Model should have 3 templates now")

        // Add another template - but we work in memory for a while before saving
        addCardType(testEditor, shadowTestEditor)
        assertEquals(3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0), "Change added but not adjusted correctly?")
        assertTrue(testEditor.modelHasChanged(), "Model should have changed")
        assertEquals(4, testEditor.tempModel?.templateCount, "Model should have 4 templates now")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 2), "Ordinal pending add?")
        assertTrue(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 3), "Ordinal not pending add?")
        assertEquals(3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0), "Change added but not adjusted correctly?")

        // Delete two pre-existing templates for real now - but still without saving it out, should work fine
        advanceRobolectricLooperWithSleep()
        testEditor.viewPager.currentItem = 0
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"), getDialogText(true), "Did not show dialog about deleting template and it's card?"
        )
        clickDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        testEditor.viewPager.currentItem = 0
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"), getDialogText(true), "Did not show dialog about deleting template and it's card?"
        )
        clickDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()

        // - assert can delete any 1 or 2 Card templates but not all
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(2)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)), "Cannot delete two templates?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 2)), "Cannot delete two templates?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1, 2)), "Cannot delete two templates?")
        assertNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1, 2)), "Can delete all templates?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change already in database?")
        assertEquals(1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 0), "Change added but not adjusted correctly?")
        assertEquals(-1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 1), "Change incorrectly pending add?")
        assertEquals(-1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 2), "Change incorrectly pending add?")

        // Now confirm everything to persist it to the database
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_confirm), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        assertNotEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change not in database?")
        assertEquals(2, getCurrentDatabaseModelCopy(modelName).getJSONArray("tmpls").length(), "Model should have 2 templates now")
        assertEquals(2, getModelCardCount(collectionBasicModelOriginal), "should be two cards")
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
        assertFalse(testEditor.modelHasChanged(), "Model should not have changed yet")
        assertEquals(2, testEditor.tempModel?.templateCount, "Model should have 2 templates now")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal pending add?")

        // Create note with forward and back info
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        selectiveGeneratedNote.setField(2, "y")
        col.addNote(selectiveGeneratedNote)
        assertEquals(2, getModelCardCount(collectionBasicModelOriginal), "card generation should result in two cards")

        // Delete ord 1 / 'Card 2' and check the message
        val shadowTestEditor = shadowOf(testEditor)
        testEditor.viewPager.currentItem = 1
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"), getDialogText(true), "Did not show dialog about deleting template and it's card?"
        )
        clickDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertTrue(testEditor.modelHasChanged(), "Model should have changed")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)), "Cannot delete template?")
        assertNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)), "Can delete both templates?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change in database despite no save?")
        assertEquals(1, testEditor.tempModel?.templateCount, "Model should have 1 template")

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        addCardType(testEditor, shadowTestEditor)
        assertTrue(testEditor.modelHasChanged(), "Model should have changed")
        assertEquals(1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel!!, 1), "Change added but not adjusted correctly?")
        assertFalse(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 0), "Ordinal pending add?")
        assertTrue(TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, 1), "Ordinal not pending add?")
        assertEquals(2, testEditor.tempModel?.templateCount, "Model should have 2 templates")

        // Delete ord 1 / 'Card 2' again and check the message - it's in the same spot as the pre-existing template but there are no cards actually associated
        testEditor.viewPager.currentItem = 1
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_delete), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertEquals(
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 0, 0, "Card 2"), getDialogText(true), "Did not show dialog about deleting template and it's card?"
        )
        clickDialogButton(WhichButton.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        assertTrue(testEditor.modelHasChanged(), "Model should have changed")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)), "Cannot delete template?")
        assertNotNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)), "Cannot delete template?")
        assertNull(col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)), "Can delete both templates?")
        assertEquals(collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' }, "Change in database despite no save?")
        assertEquals(1, testEditor.tempModel?.templateCount, "Model should have 1 template")

        // Save it out and make some assertions
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_confirm), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        assertFalse(testEditor.modelHasChanged(), "Model should now be unchanged")
        assertEquals(1, getModelCardCount(collectionBasicModelOriginal), "card generation should result in 1 card")
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
        assertTrue(shadowTestEditor.clickMenuItem(R.id.action_add), "Unable to click?")
        advanceRobolectricLooperWithSleep()
        val ordinal = testEditor.viewPager.currentItem
        var numAffectedCards = 0
        if (!TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel!!, ordinal)) {
            numAffectedCards = col.models.tmplUseCount(testEditor.tempModel!!.model, ordinal)
        }
        assertEquals(
            getQuantityString(R.plurals.card_template_editor_confirm_add, numAffectedCards, numAffectedCards), getDialogText(true), "Did not show dialog about adding template and it's card?"
        )
        clickDialogButton(WhichButton.POSITIVE, true)
    }

    private fun getModelCardCount(model: Model): Int {
        var cardCount = 0
        for (noteId in col.models.nids(model)) {
            cardCount += col.getNote(noteId).numberOfCards()
        }
        return cardCount
    }
}
