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
import com.afollestad.materialdialogs.DialogAction
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.libanki.Model
import com.ichi2.utils.JSONObject
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import timber.log.Timber

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
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged())

        // Change the model and make sure it registers as changed, but the database is unchanged
        var templateFront = testEditor.findViewById<EditText>(R.id.editor_editText)
        val testModelQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        templateFront.text.append(testModelQfmtEdit)
        advanceRobolectricLooperWithSleep()
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged())
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Kill and restart the Activity, make sure model edit is preserved
        val outBundle = Bundle()
        templateEditorController.saveInstanceState(outBundle)
        templateEditorController.pause().stop().destroy()
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java).create(outBundle).start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        var shadowTestEditor = shadowOf(testEditor)
        Assert.assertTrue("model change not preserved across activity lifecycle?", testEditor.modelHasChanged())
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Make sure we get a confirmation dialog if we hit the back button
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals("Wrong dialog shown?", getDialogText(true), getResourceString(R.string.discard_unsaved_changes))
        clickDialogButton(DialogAction.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertTrue("model change not preserved despite canceling back button?", testEditor.modelHasChanged())

        // Make sure we things are cleared out after a cancel
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        Assert.assertEquals("Wrong dialog shown?", getDialogText(true), getResourceString(R.string.discard_unsaved_changes))
        clickDialogButton(DialogAction.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertFalse("model change not cleared despite discarding changes?", testEditor.modelHasChanged())

        // Get going for content edit assertions again...
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        templateFront = testEditor.findViewById(R.id.editor_editText)
        templateFront.text.append(testModelQfmtEdit)
        advanceRobolectricLooperWithSleep()
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged())

        // Make sure we pass the edit to the Previewer
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        advanceRobolectricLooperWithSleep()
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer::class.java.name, shadowIntent.intentClass.name)
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME))
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.tempModel.model, TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)!!))
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, Intent())

        // Save the template then fetch it from the collection to see if it was saved correctly
        val testEditorModelEdited = testEditor.tempModel.model
        advanceRobolectricLooperWithSleep()
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited)
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' })
        Assert.assertTrue("model does not have our change?", collectionBasicModelCopyEdited.toString().contains(testModelQfmtEdit))
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
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.tempModel.templateCount.toLong())

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getDialogText(true))
        clickDialogButton(DialogAction.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged())
        Assert.assertEquals("Model should have 1 template now", 1, testEditor.tempModel.templateCount.toLong())

        // Try to delete the template again, but there's only one
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals(
            "Did not show dialog about deleting only card?",
            getResourceString(R.string.card_template_editor_cant_delete),
            getDialogText(true)
        )
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Save the change to the database and make sure there's only one template after
        val testEditorModelEdited = testEditor.tempModel.model
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited)
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' })
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
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        addCardType(testEditor, shadowTestEditor)
        // if AnkiDroid moves to match AnkiDesktop it will pop a dialog to confirm card create
        // Assert.assertEquals("Wrong dialog shown?", "This will create NN cards. Proceed?", getDialogText());
        // clickDialogButton(DialogAction.POSITIVE);
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged())
        Assert.assertEquals("Change not pending add?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 0).toLong())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.tempModel.templateCount.toLong())

        // Make sure we pass the new template to the Previewer
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer::class.java.name, shadowIntent.intentClass.name)
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME))
        Assert.assertEquals("intent did not have ordinal?", 1, startedIntent.getIntExtra("ordinal", -1).toLong())
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.tempModel.model, TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)!!))
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })

        // Save the change to the database and make sure there are two templates after
        val testEditorModelEdited = testEditor.tempModel.model
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        val collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName)
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited)
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim { it <= ' ' }, collectionBasicModelCopyEdited.toString().trim { it <= ' ' })
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
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.tempModel.templateCount.toLong())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))

        // Try to delete Card 1 template - click delete, check confirm for card delete popup indicating it was possible, then dismiss it
        val shadowTestEditor = shadowOf(testEditor)
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getDialogText(true))
        clickDialogButton(DialogAction.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertFalse("Model should not have changed", testEditor.modelHasChanged())

        // Create note with forward and back info, Add Reverse is empty, so should only be one card
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        val fields = selectiveGeneratedNote.fields
        for (field in fields) {
            Timber.d("Got a field: %s", field)
        }
        col.addNote(selectiveGeneratedNote)
        Assert.assertEquals("selective generation should result in one card", 1, getModelCardCount(collectionBasicModelOriginal).toLong())

        // Try to delete the template again, but there's selective generation means it would orphan the note
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals(
            "Did not show dialog about deleting only card?",
            getResourceString(R.string.card_template_editor_would_delete_note),
            getDialogText(true)
        )
        clickDialogButton(DialogAction.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertNull("Can delete used template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertEquals("Change incorrectly added to list?", 0, testEditor.tempModel.templateChanges.size.toLong())

        // Assert can delete 'Card 2'
        Assert.assertNotNull("Cannot delete unused template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))

        // Edit note to have Add Reverse set to 'y' so we get a second card
        selectiveGeneratedNote.setField(2, "y")
        selectiveGeneratedNote.flush()

        // - assert two cards
        Assert.assertEquals("should be two cards now", 2, getModelCardCount(collectionBasicModelOriginal).toLong())

        // - assert can delete either Card template but not both
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        Assert.assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))

        // A couple more notes to make sure things are okay
        val secondNote = col.newNote(collectionBasicModelOriginal)
        secondNote.setField(0, "TestFront2")
        secondNote.setField(1, "TestBack2")
        secondNote.setField(2, "y")
        col.addNote(secondNote)

        // - assert can delete either Card template but not both
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        Assert.assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
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
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.tempModel.templateCount.toLong())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))

        // Create note with forward and back info
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        col.addNote(selectiveGeneratedNote)
        Assert.assertEquals("card generation should result in two cards", 2, getModelCardCount(collectionBasicModelOriginal).toLong())

        // Test if we can delete the template - should be possible - but cancel the delete
        var shadowTestEditor = shadowOf(testEditor)
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
            getDialogText(true)
        )
        clickDialogButton(DialogAction.NEGATIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        Assert.assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        Assert.assertEquals("Change in database despite no change?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        Assert.assertEquals("Model should have 2 templates still", 2, testEditor.tempModel.templateCount.toLong())

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        addCardType(testEditor, shadowTestEditor)
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged())
        Assert.assertEquals("Change added but not adjusted correctly?", 2, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 0).toLong())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 2))
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        Assert.assertFalse("Model should now be unchanged", testEditor.modelHasChanged())
        Assert.assertEquals("card generation should result in three cards", 3, getModelCardCount(collectionBasicModelOriginal).toLong())
        collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName) // reload the model for future comparison after saving the edit

        // Start the CardTemplateEditor back up after saving (which closes the thing...)
        intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"))
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor::class.java, intent).create().start().resume().visible()
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 2))
        Assert.assertEquals("Model should have 3 templates now", 3, testEditor.tempModel.templateCount.toLong())

        // Add another template - but we work in memory for a while before saving
        addCardType(testEditor, shadowTestEditor)
        Assert.assertEquals("Change added but not adjusted correctly?", 3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 0).toLong())
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged())
        Assert.assertEquals("Model should have 4 templates now", 4, testEditor.tempModel.templateCount.toLong())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 2))
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 3))
        Assert.assertEquals("Change added but not adjusted correctly?", 3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 0).toLong())

        // Delete two pre-existing templates for real now - but still without saving it out, should work fine
        advanceRobolectricLooperWithSleep()
        testEditor.mViewPager.currentItem = 0
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
            getDialogText(true)
        )
        clickDialogButton(DialogAction.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        testEditor.mViewPager.currentItem = 0
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
            getDialogText(true)
        )
        clickDialogButton(DialogAction.POSITIVE, true)
        advanceRobolectricLooperWithSleep()

        // - assert can delete any 1 or 2 Card templates but not all
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(2)))
        Assert.assertNotNull("Cannot delete two templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        Assert.assertNotNull("Cannot delete two templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 2)))
        Assert.assertNotNull("Cannot delete two templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1, 2)))
        Assert.assertNull("Can delete all templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1, 2)))
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        Assert.assertEquals("Change added but not adjusted correctly?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 0).toLong())
        Assert.assertEquals("Change incorrectly pending add?", -1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 1).toLong())
        Assert.assertEquals("Change incorrectly pending add?", -1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 2).toLong())

        // Now confirm everything to persist it to the database
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        advanceRobolectricLooperWithSleep()
        Assert.assertNotEquals("Change not in database?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        Assert.assertEquals("Model should have 2 templates now", 2, getCurrentDatabaseModelCopy(modelName).getJSONArray("tmpls").length().toLong())
        Assert.assertEquals("should be two cards", 2, getModelCardCount(collectionBasicModelOriginal).toLong())
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
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged())
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.tempModel.templateCount.toLong())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))

        // Create note with forward and back info
        val selectiveGeneratedNote = col.newNote(collectionBasicModelOriginal)
        selectiveGeneratedNote.setField(0, "TestFront")
        selectiveGeneratedNote.setField(1, "TestBack")
        selectiveGeneratedNote.setField(2, "y")
        col.addNote(selectiveGeneratedNote)
        Assert.assertEquals("card generation should result in two cards", 2, getModelCardCount(collectionBasicModelOriginal).toLong())

        // Delete ord 1 / 'Card 2' and check the message
        val shadowTestEditor = shadowOf(testEditor)
        testEditor.mViewPager.currentItem = 1
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
            getDialogText(true)
        )
        clickDialogButton(DialogAction.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged())
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        Assert.assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        Assert.assertEquals("Change in database despite no save?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        Assert.assertEquals("Model should have 1 template", 1, testEditor.tempModel.templateCount.toLong())

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        addCardType(testEditor, shadowTestEditor)
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged())
        Assert.assertEquals("Change added but not adjusted correctly?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempModel, 1).toLong())
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 0))
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, 1))
        Assert.assertEquals("Model should have 2 templates", 2, testEditor.tempModel.templateCount.toLong())

        // Delete ord 1 / 'Card 2' again and check the message - it's in the same spot as the pre-existing template but there are no cards actually associated
        testEditor.mViewPager.currentItem = 1
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooperWithSleep()
        Assert.assertEquals(
            "Did not show dialog about deleting template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_delete, 0, 0, "Card 2"),
            getDialogText(true)
        )
        clickDialogButton(DialogAction.POSITIVE, true)
        advanceRobolectricLooperWithSleep()
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged())
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0)))
        Assert.assertNotNull("Cannot delete template?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(1)))
        Assert.assertNull("Can delete both templates?", col.models.getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), intArrayOf(0, 1)))
        Assert.assertEquals("Change in database despite no save?", collectionBasicModelOriginal.toString().trim { it <= ' ' }, getCurrentDatabaseModelCopy(modelName).toString().trim { it <= ' ' })
        Assert.assertEquals("Model should have 1 template", 1, testEditor.tempModel.templateCount.toLong())

        // Save it out and make some assertions
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooperWithSleep()
        Assert.assertFalse("Model should now be unchanged", testEditor.modelHasChanged())
        Assert.assertEquals("card generation should result in 1 card", 1, getModelCardCount(collectionBasicModelOriginal).toLong())
    }

    @Test
    fun testDeckOverride() {
        val modelName = "Basic (optional reversed card)"
        val model = getCurrentDatabaseModelCopy(modelName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("modelId", model.getLong("id"))
        val editor = super.startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        val template = editor.tempModel.getTemplate(0)
        MatcherAssert.assertThat("Deck ID element should exist", template.has("did"), Matchers.`is`(true))
        MatcherAssert.assertThat("Deck ID element should be null", template["did"], Matchers.`is`(JSONObject.NULL))
        editor.onDeckSelected(SelectableDeck(1, "hello"))
        MatcherAssert.assertThat("Deck ID element should be changed", template["did"], Matchers.`is`(1L))
        editor.onDeckSelected(null)
        MatcherAssert.assertThat("Deck ID element should exist", template.has("did"), Matchers.`is`(true))
        MatcherAssert.assertThat("Deck ID element should be null", template["did"], Matchers.`is`(JSONObject.NULL))
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
        cardTemplateFragment!!.setCurrentEditorView(R.id.styling_edit, tempModel.css, R.string.card_template_editor_styling)

        // set Bottom Navigation View to Front
        cardTemplateFragment.setCurrentEditorView(R.id.front_edit, tempModel.getTemplate(0).getString("qfmt"), R.string.card_template_editor_front)

        // check if current content is updated or not
        assumeThat(templateEditText.text.toString(), Matchers.`is`(updatedFrontContent))
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
        assumeThat(templateEditText.text.toString(), Matchers.`is`(tempModel.getTemplate(0).getString("qfmt")))
        assumeThat(cardTemplateFragment!!.currentEditorViewId, Matchers.`is`(R.id.front_edit))

        // set Bottom Navigation View to Style
        cardTemplateFragment.setCurrentEditorView(R.id.styling_edit, tempModel.css, R.string.card_template_editor_styling)

        // check if current view is changed or not
        assumeThat(templateEditText.text.toString(), Matchers.`is`(tempModel.css))
        assumeThat(cardTemplateFragment.currentEditorViewId, Matchers.`is`(R.id.styling_edit))
    }

    private fun addCardType(testEditor: CardTemplateEditor, shadowTestEditor: ShadowActivity) {
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_add))
        advanceRobolectricLooperWithSleep()
        val ordinal = testEditor.mViewPager.currentItem
        var numAffectedCards = 0
        if (!TemporaryModel.isOrdinalPendingAdd(testEditor.tempModel, ordinal)) {
            numAffectedCards = col.models.tmplUseCount(testEditor.tempModel.model, ordinal)
        }
        Assert.assertEquals(
            "Did not show dialog about adding template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_add, numAffectedCards, numAffectedCards),
            getDialogText(true)
        )
        clickDialogButton(DialogAction.POSITIVE, true)
    }

    private fun getModelCardCount(model: Model): Int {
        var cardCount = 0
        for (noteId in col.models.nids(model)) {
            cardCount += col.getNote(noteId).numberOfCards()
        }
        return cardCount
    }
}
