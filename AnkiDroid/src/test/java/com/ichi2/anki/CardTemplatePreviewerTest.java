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

package com.ichi2.anki;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONObject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public class CardTemplatePreviewerTest extends RobolectricTest {

    @Test
    public void testPreviewUnsavedTemplate() throws Exception {

        String modelName = "Basic";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        JSONObject template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0);
        template.put("qfmt", template.getString("qfmt").concat("PREVIEWER_TEST"));
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), collectionBasicModelOriginal);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath);
        intent.putExtra("index", 0);

        ActivityController<TestCardTemplatePreviewer> previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer.class, intent).create().start().resume().visible();
        saveControllerForCleanup((previewerController));
        TestCardTemplatePreviewer testCardTemplatePreviewer = (TestCardTemplatePreviewer) previewerController.get();
        Assert.assertTrue("model change did not show up?",
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).q().contains("PREVIEWER_TEST") &&
                        testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).a().contains("PREVIEWER_TEST"));

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        Bundle outBundle = new Bundle();
        previewerController.saveInstanceState(outBundle);
        previewerController.pause().stop().destroy();
        previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer.class).create(outBundle).start().resume().visible();
        saveControllerForCleanup(previewerController);
        testCardTemplatePreviewer = (TestCardTemplatePreviewer) previewerController.get();
        Assert.assertTrue("model change not preserved in lifecycle??",
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).q().contains("PREVIEWER_TEST") &&
                        testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).a().contains("PREVIEWER_TEST"));


        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testCardTemplatePreviewer.getShowingAnswer());
        testCardTemplatePreviewer.disableDoubleClickPrevention();
        LinearLayout showAnswerButton = testCardTemplatePreviewer.findViewById(R.id.flashcard_layout_flip);
        showAnswerButton.performClick();
        Assert.assertTrue("Not showing the answer?", testCardTemplatePreviewer.getShowingAnswer());
    }

    @Test
    public void testPreviewNormal() throws Exception {

        // Make sure we test previewing a new card template
        String modelName = "Basic (and reversed card)";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Card testCard1 = getSavedCard(collectionBasicModelOriginal, 0);
        Card testCard2 = getSavedCard(collectionBasicModelOriginal, 1);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("cardList", new long[] { testCard1.getId(), testCard2.getId() } );
        intent.putExtra("index", 0);

        ActivityController<TestCardTemplatePreviewer> previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer.class, intent).create().start().resume().visible();
        saveControllerForCleanup(previewerController);

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        Bundle outBundle = new Bundle();
        previewerController.saveInstanceState(outBundle);
        previewerController.pause().stop().destroy();
        previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer.class).create(outBundle).start().resume().visible();
        saveControllerForCleanup((previewerController));
        TestCardTemplatePreviewer testCardTemplatePreviewer = (TestCardTemplatePreviewer) previewerController.get();

        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testCardTemplatePreviewer.getShowingAnswer());
        testCardTemplatePreviewer.disableDoubleClickPrevention();
        LinearLayout showAnswerButton = testCardTemplatePreviewer.findViewById(R.id.flashcard_layout_flip);
        showAnswerButton.performClick();
        Assert.assertTrue("Not showing the answer?", testCardTemplatePreviewer.getShowingAnswer());
    }

    private Card getSavedCard(Model model, int ordinal) throws Exception {
        Note n = getCol().newNote(model);
        ArrayList<String> fieldNames = Models.fieldNames(model);
        for (int i = 0; i < fieldNames.size(); i++) {
            n.setField(i, fieldNames.get(i));
        }
        n.flush();
        return getCol().getNewLinkedCard(new Card(getCol()), n, model.getJSONArray("tmpls").getJSONObject(ordinal), 1, 1, true);
    }
}