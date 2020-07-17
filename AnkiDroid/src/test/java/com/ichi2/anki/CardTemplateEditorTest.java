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

package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.afollestad.materialdialogs.DialogAction;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowIntent;

import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONObject;

import timber.log.Timber;

import static org.robolectric.Shadows.shadowOf;


@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CardTemplateEditorTest extends RobolectricTest {

    @Test
    @SuppressWarnings("PMD.NPathComplexity")
    public void testEditTemplateContents() throws Exception {
        String modelName = "Basic";

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController<CardTemplateEditor> templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());

        // Change the model and make sure it registers as changed, but the database is unchanged
        EditText templateFront = testEditor.findViewById(R.id.front_edit);
        String TEST_MODEL_QFMT_EDIT = "!@#$%^&*TEST*&^%$#@!";
        templateFront.getText().append(TEST_MODEL_QFMT_EDIT);
        advanceRobolectricLooper();
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Kill and restart the Activity, make sure model edit is preserved
        Bundle outBundle = new Bundle();
        templateEditorController.saveInstanceState(outBundle);
        templateEditorController.pause().stop().destroy();
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class).create(outBundle).start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        testEditor = (CardTemplateEditor)templateEditorController.get();
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        Assert.assertTrue("model change not preserved across activity lifecycle?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Make sure we get a confirmation dialog if we hit the back button
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home));
        advanceRobolectricLooper();
        Assert.assertEquals("Wrong dialog shown?", getDialogText(true), getResourceString(R.string.discard_unsaved_changes));
        clickDialogButton(DialogAction.NEGATIVE, true);
        advanceRobolectricLooper();
        Assert.assertTrue("model change not preserved despite canceling back button?", testEditor.modelHasChanged());

        // Make sure we things are cleared out after a cancel
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home));
        Assert.assertEquals("Wrong dialog shown?", getDialogText(true), getResourceString(R.string.discard_unsaved_changes));
        clickDialogButton(DialogAction.POSITIVE, true);
        advanceRobolectricLooper();
        Assert.assertFalse("model change not cleared despite discarding changes?", testEditor.modelHasChanged());

        // Get going for content edit assertions again...
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        testEditor = (CardTemplateEditor)templateEditorController.get();
        shadowTestEditor = shadowOf(testEditor);
        templateFront = testEditor.findViewById(R.id.front_edit);
        templateFront.getText().append(TEST_MODEL_QFMT_EDIT);
        advanceRobolectricLooper();
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());

        // Make sure we pass the edit to the Previewer
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview));
        advanceRobolectricLooper();
        Intent startedIntent = shadowTestEditor.getNextStartedActivity();
        ShadowIntent shadowIntent = shadowOf(startedIntent);
        advanceRobolectricLooper();
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer.class.getName(), shadowIntent.getIntentClass().getName());
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME));
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.getTempModel().getModel(), TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, new Intent());

        // Save the template then fetch it from the collection to see if it was saved correctly
        JSONObject testEditorModelEdited = testEditor.getTempModel().getModel();
        advanceRobolectricLooper();
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooper();
        JSONObject collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName);
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited);
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim(), collectionBasicModelCopyEdited.toString().trim());
        Assert.assertTrue("model does not have our change?", collectionBasicModelCopyEdited.toString().contains(TEST_MODEL_QFMT_EDIT));
    }


    @Test
    public void testDeleteTemplate() {

        String modelName = "Basic (and reversed card)";

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController<CardTemplateEditor> templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.getTempModel().getTemplateCount());

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getDialogText(true));
        clickDialogButton(DialogAction.POSITIVE, true);
        advanceRobolectricLooper();
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());
        Assert.assertEquals("Model should have 1 template now", 1, testEditor.getTempModel().getTemplateCount());

        // Try to delete the template again, but there's only one
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Did not show dialog about deleting only card?",
                getResourceString(R.string.card_template_editor_cant_delete),
                getDialogText(true));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        
        // Save the change to the database and make sure there's only one template after
        JSONObject testEditorModelEdited = testEditor.getTempModel().getModel();
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooper();
        JSONObject collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName);
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited);
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim(), collectionBasicModelCopyEdited.toString().trim());
    }

    @Test
    public void testTemplateAdd() throws Exception {

        // Make sure we test previewing a new card template - not working for real yet
        String modelName = "Basic";
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController<CardTemplateEditor> templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_add));
        advanceRobolectricLooper();
        // if AnkiDroid moves to match AnkiDesktop it will pop a dialog to confirm card create
        //Assert.assertEquals("Wrong dialog shown?", "This will create NN cards. Proceed?", getDialogText());
        //clickDialogButton(DialogAction.POSITIVE);
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());
        Assert.assertEquals("Change not pending add?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.getTempModel().getTemplateCount());

        // Make sure we pass the new template to the Previewer
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview));
        Intent startedIntent = shadowTestEditor.getNextStartedActivity();
        ShadowIntent shadowIntent = shadowOf(startedIntent);
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer.class.getName(), shadowIntent.getIntentClass().getName());
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME));
        Assert.assertEquals("intent did not have ordinal?", 1, startedIntent.getIntExtra("ordinal", -1));
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.getTempModel().getModel(), TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Save the change to the database and make sure there are two templates after
        JSONObject testEditorModelEdited = testEditor.getTempModel().getModel();
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooper();
        JSONObject collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName);
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited);
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim(), collectionBasicModelCopyEdited.toString().trim());
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
    public void testDeleteTemplateWithSelectivelyGeneratedCards() {

        String modelName = "Basic (optional reversed card)";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController<CardTemplateEditor> templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.getTempModel().getTemplateCount());
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));

        // Try to delete Card 1 template - click delete, check confirm for card delete popup indicating it was possible, then dismiss it
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getDialogText(true));
        clickDialogButton(DialogAction.NEGATIVE, true);
        advanceRobolectricLooper();
        Assert.assertFalse("Model should not have changed", testEditor.modelHasChanged());

        // Create note with forward and back info, Add Reverse is empty, so should only be one card
        Note selectiveGeneratedNote = getCol().newNote(collectionBasicModelOriginal);
        selectiveGeneratedNote.setField(0, "TestFront");
        selectiveGeneratedNote.setField(1, "TestBack");
        String[] fields = selectiveGeneratedNote.getFields();
        for (String field : fields) {
            Timber.d("Got a field: %s", field);
        }
        getCol().addNote(selectiveGeneratedNote);
        Assert.assertEquals("selective generation should result in one card", 1, getModelCardCount(collectionBasicModelOriginal));

        // Try to delete the template again, but there's selective generation means it would orphan the note
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Did not show dialog about deleting only card?",
                getResourceString(R.string.card_template_editor_would_delete_note),
                getDialogText(true));
        clickDialogButton(DialogAction.POSITIVE, true);
        advanceRobolectricLooper();
        Assert.assertNull("Can delete used template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertEquals("Change incorrectly added to list?", 0, testEditor.getTempModel().getTemplateChanges().size());

        // Assert can delete 'Card 2'
        Assert.assertNotNull("Cannot delete unused template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));

        // Edit note to have Add Reverse set to 'y' so we get a second card
        selectiveGeneratedNote.setField(2, "y");
        selectiveGeneratedNote.flush();

        // - assert two cards
        Assert.assertEquals("should be two cards now", 2, getModelCardCount(collectionBasicModelOriginal));

        // - assert can delete either Card template but not both
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));
        Assert.assertNull("Can delete both templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 1}));

        // A couple more notes to make sure things are okay
        Note secondNote = getCol().newNote(collectionBasicModelOriginal);
        secondNote.setField(0, "TestFront2");
        secondNote.setField(1, "TestBack2");
        secondNote.setField(2, "y");
        getCol().addNote(secondNote);

        // - assert can delete either Card template but not both
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));
        Assert.assertNull("Can delete both templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 1}));
    }


    /**
     * Normal template deletion - with no selective generation should of course work
     */
    @SuppressWarnings("PMD.ExcessiveMethodLength")
    @Test
    public void testDeleteTemplateWithGeneratedCards() {

        String modelName = "Basic (and reversed card)";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController<CardTemplateEditor> templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.getTempModel().getTemplateCount());
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));

        // Create note with forward and back info
        Note selectiveGeneratedNote = getCol().newNote(collectionBasicModelOriginal);
        selectiveGeneratedNote.setField(0, "TestFront");
        selectiveGeneratedNote.setField(1, "TestBack");
        getCol().addNote(selectiveGeneratedNote);
        Assert.assertEquals("card generation should result in two cards", 2, getModelCardCount(collectionBasicModelOriginal));

        // Test if we can delete the template - should be possible - but cancel the delete
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
                getDialogText(true));
        clickDialogButton(DialogAction.NEGATIVE, true);
        advanceRobolectricLooper();
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));
        Assert.assertNull("Can delete both templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 1}));
        Assert.assertEquals("Change in database despite no change?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        Assert.assertEquals("Model should have 2 templates still", 2, testEditor.getTempModel().getTemplateCount());

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        shadowTestEditor.clickMenuItem(R.id.action_add);
        advanceRobolectricLooper();
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());
        Assert.assertEquals("Change added but not adjusted correctly?", 2, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 2));
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooper();
        Assert.assertFalse("Model should now be unchanged", testEditor.modelHasChanged());
        Assert.assertEquals("card generation should result in three cards", 3, getModelCardCount(collectionBasicModelOriginal));
        collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName); // reload the model for future comparison after saving the edit

        // Start the CardTemplateEditor back up after saving (which closes the thing...)
        intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        testEditor = (CardTemplateEditor)templateEditorController.get();
        shadowTestEditor = shadowOf(testEditor);
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 2));
        Assert.assertEquals("Model should have 3 templates now", 3, testEditor.getTempModel().getTemplateCount());

        // Add another template - but we work in memory for a while before saving
        shadowTestEditor.clickMenuItem(R.id.action_add);
        advanceRobolectricLooper();
        Assert.assertEquals("Change added but not adjusted correctly?", 3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 0));
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());
        Assert.assertEquals("Model should have 4 templates now", 4, testEditor.getTempModel().getTemplateCount());
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 2));
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 3));
        Assert.assertEquals("Change added but not adjusted correctly?", 3, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 0));

        // Delete two pre-existing templates for real now - but still without saving it out, should work fine
        advanceRobolectricLooper();
        testEditor.mViewPager.setCurrentItem(0);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
                getDialogText(true));
        clickDialogButton(DialogAction.POSITIVE, true);
        advanceRobolectricLooper();

        advanceRobolectricLooper();
        testEditor.mViewPager.setCurrentItem(0);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
                getDialogText(true));
        clickDialogButton(DialogAction.POSITIVE, true);
        advanceRobolectricLooper();

        // - assert can delete any 1 or 2 Card templates but not all
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {2}));
        Assert.assertNotNull("Cannot delete two templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 1}));
        Assert.assertNotNull("Cannot delete two templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 2}));
        Assert.assertNotNull("Cannot delete two templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1, 2}));
        Assert.assertNull("Can delete all templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 1, 2}));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        Assert.assertEquals("Change added but not adjusted correctly?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 0));
        Assert.assertEquals("Change incorrectly pending add?", -1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 1));
        Assert.assertEquals("Change incorrectly pending add?", -1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 2));

        // Now confirm everything to persist it to the database
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooper();
        advanceRobolectricLooper();
        Assert.assertNotEquals("Change not in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        Assert.assertEquals("Model should have 2 templates now", 2, getCurrentDatabaseModelCopy(modelName).getJSONArray("tmpls").length());
        Assert.assertEquals("should be two cards", 2, getModelCardCount(collectionBasicModelOriginal));
    }

    /**
     * Deleting a template you just added - but in the same ordinal as a previous pending delete - should get it's card count correct
     */
    @SuppressWarnings("PMD.ExcessiveMethodLength")
    @Test
    public void testDeletePendingAddExistingCardCount() {

        String modelName = "Basic (optional reversed card)";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController<CardTemplateEditor> templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup(templateEditorController);
        CardTemplateEditor testEditor = (CardTemplateEditor) templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());
        Assert.assertEquals("Model should have 2 templates now", 2, testEditor.getTempModel().getTemplateCount());
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));

        // Create note with forward and back info
        Note selectiveGeneratedNote = getCol().newNote(collectionBasicModelOriginal);
        selectiveGeneratedNote.setField(0, "TestFront");
        selectiveGeneratedNote.setField(1, "TestBack");
        selectiveGeneratedNote.setField(2, "y");
        getCol().addNote(selectiveGeneratedNote);
        Assert.assertEquals("card generation should result in two cards", 2, getModelCardCount(collectionBasicModelOriginal));

        // Delete ord 1 / 'Card 2' and check the message
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        testEditor.mViewPager.setCurrentItem(1);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
                getDialogText(true));
        clickDialogButton(DialogAction.POSITIVE, true);
        advanceRobolectricLooper();
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));
        Assert.assertNull("Can delete both templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 1}));
        Assert.assertEquals("Change in database despite no save?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        Assert.assertEquals("Model should have 1 template", 1, testEditor.getTempModel().getTemplateCount());

        // Add a template - click add, click confirm for card add, click confirm again for full sync
        shadowTestEditor.clickMenuItem(R.id.action_add);
        advanceRobolectricLooper();
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());
        Assert.assertEquals("Change added but not adjusted correctly?", 1, TemporaryModel.getAdjustedAddOrdinalAtChangeIndex(testEditor.getTempModel(), 1));
        Assert.assertFalse("Ordinal pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 0));
        Assert.assertTrue("Ordinal not pending add?", TemporaryModel.isOrdinalPendingAdd(testEditor.getTempModel(), 1));
        Assert.assertEquals("Model should have 2 templates", 2, testEditor.getTempModel().getTemplateCount());

        // Delete ord 1 / 'Card 2' again and check the message - it's in the same spot as the pre-existing template but there are no cards actually associated
        testEditor.mViewPager.setCurrentItem(1);
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete));
        advanceRobolectricLooper();
        Assert.assertEquals("Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 0, 0, "Card 2"),
                getDialogText(true));
        clickDialogButton(DialogAction.POSITIVE, true);
        advanceRobolectricLooper();
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertNotNull("Cannot delete template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));
        Assert.assertNull("Can delete both templates?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0, 1}));
        Assert.assertEquals("Change in database despite no save?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        Assert.assertEquals("Model should have 1 template", 1, testEditor.getTempModel().getTemplateCount());

        // Save it out and make some assertions
        Assert.assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooper();
        Assert.assertFalse("Model should now be unchanged", testEditor.modelHasChanged());
        Assert.assertEquals("card generation should result in 1 card", 1, getModelCardCount(collectionBasicModelOriginal));
    }

    private int getModelCardCount(Model model) {
        int cardCount = 0;
        for (Long noteId : getCol().getModels().nids(model)) {
            cardCount += getCol().getNote(noteId).numberOfCards();
        }
        return cardCount;
    }
}
