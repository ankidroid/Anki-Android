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
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public class PreviewerTest extends RobolectricTest {

    @Test
    public void testPreviewUnsavedTemplate() throws Exception {

        String modelName = "Basic";
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        JSONObject template = (JSONObject)collectionBasicModelOriginal.getJSONArray("tmpls").get(0);
        template.put("qfmt", template.getString("qfmt").concat("PREVIEWER_TEST"));
        String tempModelPath = CardTemplateEditor.saveTempModel(getTargetContext(), collectionBasicModelOriginal);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(CardTemplateEditor.INTENT_MODEL_FILENAME, tempModelPath);
        intent.putExtra("index", 0);

        ActivityController previewerController = Robolectric.buildActivity(TestPreviewer.class, intent).create().start().resume().visible();
        TestPreviewer testPreviewer = (TestPreviewer) previewerController.get();
        Assert.assertTrue("model change did not show up?",
                testPreviewer.getDummyCard(collectionBasicModelOriginal, 0).q().contains("PREVIEWER_TEST") &&
                        testPreviewer.getDummyCard(collectionBasicModelOriginal, 0).a().contains("PREVIEWER_TEST"));

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        Bundle outBundle = new Bundle();
        previewerController.saveInstanceState(outBundle);
        previewerController.pause().stop().destroy();
        previewerController = Robolectric.buildActivity(TestPreviewer.class).create(outBundle).start().resume().visible();
        testPreviewer = (TestPreviewer) previewerController.get();
        Assert.assertTrue("model change not preserved in lifecycle??",
                testPreviewer.getDummyCard(collectionBasicModelOriginal, 0).q().contains("PREVIEWER_TEST") &&
                        testPreviewer.getDummyCard(collectionBasicModelOriginal, 0).a().contains("PREVIEWER_TEST"));


        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testPreviewer.getShowingAnswer());
        testPreviewer.disableDoubleClickPrevention();
        LinearLayout showAnswerButton = testPreviewer.findViewById(R.id.flashcard_layout_flip);
        showAnswerButton.performClick();
        Assert.assertTrue("Not showing the answer?", testPreviewer.getShowingAnswer());
    }

    @Test
    public void testPreviewNormal() throws Exception {

        // Make sure we test previewing a new card template
        String modelName = "Basic (and reversed card)";
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Card testCard1 = getSavedCard(collectionBasicModelOriginal, 0);
        Card testCard2 = getSavedCard(collectionBasicModelOriginal, 1);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("cardList", new long[] { testCard1.getId(), testCard2.getId() } );
        intent.putExtra("index", 0);

        ActivityController previewerController = Robolectric.buildActivity(TestPreviewer.class, intent).create().start().resume().visible();

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        Bundle outBundle = new Bundle();
        previewerController.saveInstanceState(outBundle);
        previewerController.pause().stop().destroy();
        previewerController = Robolectric.buildActivity(TestPreviewer.class).create(outBundle).start().resume().visible();
        TestPreviewer testPreviewer = (TestPreviewer) previewerController.get();

        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testPreviewer.getShowingAnswer());
        testPreviewer.disableDoubleClickPrevention();
        LinearLayout showAnswerButton = testPreviewer.findViewById(R.id.flashcard_layout_flip);
        showAnswerButton.performClick();
        Assert.assertTrue("Not showing the answer?", testPreviewer.getShowingAnswer());
    }

    private Card getSavedCard(JSONObject model, int ordinal) throws Exception {
        Note n = getCol().newNote(model);
        ArrayList<String> fieldNames = Models.fieldNames(model);
        for (int i = 0; i < fieldNames.size(); i++) {
            n.setField(i, fieldNames.get(i));
        }
        n.flush();
        return getCol()._newCard(new Card(getCol()), n, (JSONObject)model.getJSONArray("tmpls").get(ordinal), 1, true);
    }
}

class TestPreviewer extends Previewer {
    public boolean getShowingAnswer() { return mShowingAnswer; }
    public void disableDoubleClickPrevention() { mLastClickTime = (AbstractFlashcardViewer.DOUBLE_TAP_IGNORE_THRESHOLD * -2); }
}