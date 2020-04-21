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
import org.robolectric.shadows.ShadowToast;

import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;

import timber.log.Timber;

import static android.os.Looper.getMainLooper;
import static org.robolectric.Shadows.shadowOf;


@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CardTemplateEditorTest extends RobolectricTest {

    private static int robolectricQuiesceMillis = 300;

    @Test
    @SuppressWarnings("PMD.NPathComplexity")
    public void testEditTemplateContents() throws Exception {
        String modelName = "Basic";

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());

        // Change the model and make sure it registers as changed, but the database is unchanged
        EditText templateFront = testEditor.findViewById(R.id.front_edit);
        String TEST_MODEL_QFMT_EDIT = "!@#$%^&*TEST*&^%$#@!";
        templateFront.getText().append(TEST_MODEL_QFMT_EDIT);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Kill and restart the Activity, make sure model edit is preserved
        Bundle outBundle = new Bundle();
        templateEditorController.saveInstanceState(outBundle);
        templateEditorController.pause().stop().destroy();
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class).create(outBundle).start().resume().visible();
        testEditor = (CardTemplateEditor)templateEditorController.get();
        shadowTestEditor = shadowOf(testEditor);
        Assert.assertTrue("model change not preserved across activity lifecycle?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Make sure we get a confirmation dialog if we hit the back button
        shadowTestEditor.clickMenuItem(android.R.id.home);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertEquals("Wrong dialog shown?", getDialogText(), getResourceString(R.string.discard_unsaved_changes));
        clickDialogButton(DialogAction.NEGATIVE);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertTrue("model change not preserved despite canceling back button?", testEditor.modelHasChanged());

        // Make sure we things are cleared out after a cancel
        shadowTestEditor.clickMenuItem(android.R.id.home);
        Assert.assertEquals("Wrong dialog shown?", getDialogText(), getResourceString(R.string.discard_unsaved_changes));
        clickDialogButton(DialogAction.POSITIVE);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertFalse("model change not cleared despite discarding changes?", testEditor.modelHasChanged());

        // Get going for content edit assertions again...
        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        testEditor = (CardTemplateEditor)templateEditorController.get();
        shadowTestEditor = shadowOf(testEditor);
        templateFront = testEditor.findViewById(R.id.front_edit);
        templateFront.getText().append(TEST_MODEL_QFMT_EDIT);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());

        // Make sure we pass the edit to the Previewer
        shadowTestEditor.clickMenuItem(R.id.action_preview);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Intent startedIntent = shadowTestEditor.getNextStartedActivity();
        ShadowIntent shadowIntent = shadowOf(startedIntent);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer.class.getName(), shadowIntent.getIntentClass().getName());
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME));
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.getTempModel().getModel(), TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, new Intent());

        // Save the template then fetch it from the collection to see if it was saved correctly
        JSONObject testEditorModelEdited = testEditor.getTempModel().getModel();
        shadowTestEditor.clickMenuItem(R.id.action_confirm);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        JSONObject collectionBasicModelCopyEdited = getCurrentDatabaseModelCopy(modelName);
        Assert.assertNotEquals("model is unchanged?", collectionBasicModelOriginal, collectionBasicModelCopyEdited);
        Assert.assertEquals("model did not save?", testEditorModelEdited.toString().trim(), collectionBasicModelCopyEdited.toString().trim());
        Assert.assertTrue("model does not have our change?", collectionBasicModelCopyEdited.toString().contains(TEST_MODEL_QFMT_EDIT));
    }


    @Test
    public void testDeleteTemplate() throws Exception {

        String modelName = "Basic (and reversed card)";

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController templateEditorController = Robolectric.buildActivity(NonPagingCardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        shadowTestEditor.clickMenuItem(R.id.action_delete);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getDialogText());
        clickDialogButton(DialogAction.POSITIVE);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());

        // Try to delete the template again, but there's only one so we should toast
        shadowTestEditor.clickMenuItem(R.id.action_delete);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertEquals("Did not show toast about deleting only card?",
                getResourceString(R.string.card_template_editor_cant_delete),
                ShadowToast.getTextOfLatestToast());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Related to Robolectric ViewPager support, see below
        NonPagingCardTemplateEditor.pagerCount = 1;

        // Kill and restart the Activity, make sure model edit is preserved
        // The saveInstanceState test would be useful but we can't run it without Robolectric ViewPager support
//        Bundle outBundle = new Bundle();
//        templateEditorController.saveInstanceState(outBundle);
//        templateEditorController.pause().stop().destroy();
//        templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class).create(outBundle).start().resume().visible();
//        testEditor = (CardTemplateEditor)templateEditorController.get();
//        Assert.assertTrue("model change not preserved across activity lifecycle?", testEditor.modelHasChanged());
//        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Save the change to the database and make sure there's only one template after
        JSONObject testEditorModelEdited = testEditor.getTempModel().getModel();
        shadowTestEditor.clickMenuItem(R.id.action_confirm);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
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
        NonPagingCardTemplateEditor.pagerCount = 1;
        ActivityController templateEditorController = Robolectric.buildActivity(NonPagingCardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        shadowTestEditor.clickMenuItem(R.id.action_add);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        // TODO never existed in AnkiDroid but to match AnkiDesktop we should pop a dialog to confirm card create
        //Assert.assertEquals("Wrong dialog shown?", "This will create NN cards. Proceed?", getDialogText());
        //clickDialogButton(DialogAction.POSITIVE);
        Assert.assertTrue("Model should have changed", testEditor.modelHasChanged());

        // Make sure we pass the new template to the Previewer
        shadowTestEditor.clickMenuItem(R.id.action_preview);
        Intent startedIntent = shadowTestEditor.getNextStartedActivity();
        ShadowIntent shadowIntent = shadowOf(startedIntent);
        Assert.assertEquals("Previewer not started?", CardTemplatePreviewer.class.getName(), shadowIntent.getIntentClass().getName());
        Assert.assertNotNull("intent did not have model JSON filename?", startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME));
        Assert.assertEquals("intent did not have ordinal?", startedIntent.getIntExtra("index", -1), 0);
        Assert.assertNotEquals("Model sent to Previewer is unchanged?", testEditor.getTempModel().getModel(), TemporaryModel.getTempModel(startedIntent.getStringExtra(TemporaryModel.INTENT_MODEL_FILENAME)));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Save the change to the database and make sure there are two templates after
        JSONObject testEditorModelEdited = testEditor.getTempModel().getModel();
        shadowTestEditor.clickMenuItem(R.id.action_confirm);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
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
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);

        // Start the CardTemplateEditor with a specific model, and make sure the model starts unchanged
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController templateEditorController = Robolectric.buildActivity(NonPagingCardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor)templateEditorController.get();
        Assert.assertFalse("Model should not have changed yet", testEditor.modelHasChanged());

        // Try to delete Card 1 template - click delete, check confirm for card delete popup indicating it was possible, then dismiss it
        ShadowActivity shadowTestEditor = shadowOf(testEditor);
        shadowTestEditor.clickMenuItem(R.id.action_delete);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getDialogText());
        clickDialogButton(DialogAction.NEGATIVE);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertFalse("Model should not have changed", testEditor.modelHasChanged());


        // Create note with forward and back info, Add Reverse set to 'n'
        Note selectiveGeneratedNote = getCol().newNote(collectionBasicModelOriginal);
        selectiveGeneratedNote.setField(0, "TestFront");
        selectiveGeneratedNote.setField(1, "TestBack");
        String[] fields = selectiveGeneratedNote.getFields();
        for (String field : fields) {
            Timber.d("Got a field: %s", field);
        }
        getCol().addNote(selectiveGeneratedNote);

        // - assert one card
        ArrayList<Long> noteIds = getCol().getModels().nids(collectionBasicModelOriginal);
        int cardCount = 0;
        for (Long noteId : noteIds) {
            cardCount += getCol().getNote(noteId).cards().size();
        }
        Assert.assertEquals("selective generation should result in one card", 1, cardCount);

        // Try to delete the template again, but there's selective generation means it would orphan the note so we should toast
        shadowTestEditor.clickMenuItem(R.id.action_delete);
        try { Thread.sleep(robolectricQuiesceMillis); } catch (Exception e) { Timber.e(e); }
        shadowOf(getMainLooper()).idle();
        Assert.assertEquals("Did not show toast about deleting only card?",
                getResourceString(R.string.card_template_editor_would_delete_note),
                ShadowToast.getTextOfLatestToast());
        Assert.assertNull("Can delete used template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {0}));
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        // Assert can delete 'Card 2'
        Assert.assertNotNull("Can delete unused template?", getCol().getModels().getCardIdsForModel(collectionBasicModelOriginal.getLong("id"), new int[] {1}));

        // Edit note to have Add Reverse set to 'y'
        selectiveGeneratedNote.setField(2, "y");
        selectiveGeneratedNote.flush();

        // - assert two cards
        noteIds = getCol().getModels().nids(collectionBasicModelOriginal);
        cardCount = 0;
        for (Long noteId : noteIds) {
            cardCount += getCol().getNote(noteId).cards().size();
        }
        Assert.assertEquals("should be two cards now", 2, cardCount);

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
} 