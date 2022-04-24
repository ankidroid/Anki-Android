package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONObject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;


@RunWith(RobolectricTestRunner.class)
public class CardTemplateFormatTest extends RobolectricTest {
    /***
     * Test the scenario that replace the template format with the supported field name listed in mFieldNames.
     * The temporary change should be performed in the database.
     */
    @Test
    public void testValidTemplateFormat() {
        String modelName = "Basic";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));

        ActivityController<CardTemplateEditor> templateController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup((templateController));
        CardTemplateEditor testEditor = templateController.get();

        JSONObject template = testEditor.getTempModel().getModel().getJSONArray("tmpls").getJSONObject(0);
        template.put("qfmt",  "{{Back}}");
        Assert.assertEquals("{{Back}}", template.getString("qfmt"));
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        ShadowActivity shadowEditor = Shadows.shadowOf(testEditor);
        Assert.assertTrue("Unable to click?", shadowEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooperWithSleep();
        Assert.assertNotEquals("No change in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
    }

    /***
     * Test the scenario that replace the template format with the unsupported field name
     * The temporary change should not be performed in the database.
     */
    @Test
    public void testInvalidTemplateFormat() {
        String modelName = "Basic";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));

        ActivityController<CardTemplateEditor> templateController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        saveControllerForCleanup((templateController));
        CardTemplateEditor testEditor = templateController.get();

        JSONObject template = testEditor.getTempModel().getModel().getJSONArray("tmpls").getJSONObject(0);
        template.put("qfmt",  "{{Frot}}");
        Assert.assertEquals("{{Frot}}", template.getString("qfmt"));
        Assert.assertTrue("Model did not change after edit?", testEditor.modelHasChanged());
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());

        ShadowActivity shadowEditor = Shadows.shadowOf(testEditor);
        Assert.assertTrue("Unable to click?", shadowEditor.clickMenuItem(R.id.action_confirm));
        advanceRobolectricLooperWithSleep();
        Assert.assertEquals("Change already in database?", collectionBasicModelOriginal.toString().trim(), getCurrentDatabaseModelCopy(modelName).toString().trim());
    }
}
