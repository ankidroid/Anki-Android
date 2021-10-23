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
import android.view.View;

import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
public class CardTemplatePreviewerTest extends RobolectricTest {

    @Test
    public void testPreviewUnsavedTemplate() {

        String modelName = "Basic";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        JSONObject template = collectionBasicModelOriginal.getJSONArray("tmpls").getJSONObject(0);
        template.put("qfmt", template.getString("qfmt") + "PREVIEWER_TEST");
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), collectionBasicModelOriginal);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath);
        intent.putExtra("index", 0);

        ActivityController<TestCardTemplatePreviewer> previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer.class, intent).create().start().resume().visible();
        saveControllerForCleanup((previewerController));
        TestCardTemplatePreviewer testCardTemplatePreviewer = previewerController.get();
        Assert.assertTrue("model change did not show up?",
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).q().contains("PREVIEWER_TEST") &&
                        testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).a().contains("PREVIEWER_TEST"));

        // Take it through a destroy/re-create lifecycle in order to test instance state persistence
        Bundle outBundle = new Bundle();
        previewerController.saveInstanceState(outBundle);
        previewerController.pause().stop().destroy();
        previewerController = Robolectric.buildActivity(TestCardTemplatePreviewer.class).create(outBundle).start().resume().visible();
        saveControllerForCleanup(previewerController);
        testCardTemplatePreviewer = previewerController.get();
        Assert.assertTrue("model change not preserved in lifecycle??",
                testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).q().contains("PREVIEWER_TEST") &&
                        testCardTemplatePreviewer.getDummyCard(collectionBasicModelOriginal, 0).a().contains("PREVIEWER_TEST"));


        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testCardTemplatePreviewer.getShowingAnswer());
        testCardTemplatePreviewer.disableDoubleClickPrevention();
        View showAnswerButton = testCardTemplatePreviewer.findViewById(R.id.preview_buttons_layout);
        showAnswerButton.performClick();
        Assert.assertTrue("Not showing the answer?", testCardTemplatePreviewer.getShowingAnswer());
    }

    @Test
    public void testPreviewNormal() {

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
        TestCardTemplatePreviewer testCardTemplatePreviewer = previewerController.get();

        // Make sure we can click
        Assert.assertFalse("Showing the answer already?", testCardTemplatePreviewer.getShowingAnswer());
        testCardTemplatePreviewer.disableDoubleClickPrevention();
        View showAnswerButton = testCardTemplatePreviewer.findViewById(R.id.preview_buttons_layout);
        showAnswerButton.performClick();
        Assert.assertTrue("Not showing the answer?", testCardTemplatePreviewer.getShowingAnswer());
    }

    @Test
    public void testPreviewNoteEditorFieldData() {
        String cloze = "Cloze";
        String basicAndReverseOptional = "Basic (optional reversed card)";
        Model collectionBasicModelOriginal = getCurrentDatabaseModelCopy(cloze);
        Model basicAndReverseOptionalModel = getCurrentDatabaseModelCopy(basicAndReverseOptional);
        ArrayList<String> cardsList = new ArrayList<>();
        JSONArray tmpls = collectionBasicModelOriginal.getJSONArray("tmpls");
        for(String name : tmpls.stringIterable()) {
            cardsList.add(name);
        }
        Assert.assertTrue(cardsList.size() == 1);
        cardsList.clear();
        JSONArray tmplsBR = basicAndReverseOptionalModel.getJSONArray("tmpls");
        for(String name : tmplsBR.stringIterable()) {
            cardsList.add(name);
        }
        Assert.assertTrue(cardsList.size() == 2);
    }

    @Test
    public void singleTemplateFromNoteEditorHasNoNavigation() {
        List<NoteService.NoteField> fields = new ArrayList<>();
        fields.add(new Field(0, "Hello"));
        fields.add(new Field(1, "World"));

        Model basicModel = getCurrentDatabaseModelCopy("Basic");
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), basicModel);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath);


        Bundle noteEditorBundle = new Bundle();
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields));
        noteEditorBundle.putInt("ordinal", 0);
        noteEditorBundle.putLong("did", 1);
        intent.putExtra("noteEditorBundle", noteEditorBundle);

        TestCardTemplatePreviewer testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer.class, intent);

        assertThat("prev should not be visible", testCardTemplatePreviewer.previousButtonVisible(), is(false));
        assertThat("next should not be visible", testCardTemplatePreviewer.nextButtonVisible(), is(false));
    }


    @Test
    public void nonClozeFromNoteEditorHasMultipleCards() {
        List<NoteService.NoteField> fields = new ArrayList<>();
        fields.add(new Field(0, "Hello"));
        fields.add(new Field(1, "World"));

        Model basicModel = getCurrentDatabaseModelCopy("Basic (and reversed card)");
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), basicModel);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath);


        Bundle noteEditorBundle = new Bundle();
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields));
        noteEditorBundle.putInt("ordinal", 0);
        noteEditorBundle.putLong("did", 1);
        intent.putExtra("noteEditorBundle", noteEditorBundle);

        TestCardTemplatePreviewer testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer.class, intent);

        assertTwoCards(testCardTemplatePreviewer);
    }


    @Test
    public void clozeFromEditorHasMultipleCards() {
        List<NoteService.NoteField> fields = new ArrayList<>();
        fields.add(new Field(0, "{{c1::Hello}} {{c3::World}}"));
        fields.add(new Field(1, "World"));

        Model basicModel = getCurrentDatabaseModelCopy("Cloze");
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), basicModel);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath);


        Bundle noteEditorBundle = new Bundle();
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields));
        noteEditorBundle.putInt("ordinal", 0);
        noteEditorBundle.putLong("did", 1);
        intent.putExtra("noteEditorBundle", noteEditorBundle);

        TestCardTemplatePreviewer testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer.class, intent);

        assertTwoCards(testCardTemplatePreviewer);
    }

    @Test
    public void cardTemplatePreviewerSecondOrd_issue8001() {
        long cid = addNoteUsingBasicAndReversedModel("hello", "world").cards().get(1).getId();

        Model model = getCurrentDatabaseModelCopy("Basic (and reversed card)");
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), model);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath);
        intent.putExtra("ordinal", 1);
        intent.putExtra("cardListIndex", 0);
        intent.putExtra("cardList", new long[] { cid });

        TestCardTemplatePreviewer testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer.class, intent);

        assertThat(testCardTemplatePreviewer.getCardContent(), containsString("world"));
        assertThat(testCardTemplatePreviewer.getCardContent(), not(containsString("hello")));
        assertThat(testCardTemplatePreviewer.getCardContent(), not(containsString("Front")));
    }

    @Test
    public void cardTemplatePreviewerNoCards_issue9687() {
        List<NoteService.NoteField> fields = new ArrayList<>();
        fields.add(new Field(0, ""));
        fields.add(new Field(1, ""));

        Model basicModel = getCurrentDatabaseModelCopy("Basic");
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), basicModel);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModelPath);


        Bundle noteEditorBundle = new Bundle();
        noteEditorBundle.putBundle("editFields", getFieldsAsBundleForPreview(fields));
        noteEditorBundle.putInt("ordinal", 0);
        noteEditorBundle.putLong("did", 1);
        intent.putExtra("noteEditorBundle", noteEditorBundle);

        TestCardTemplatePreviewer testCardTemplatePreviewer = super.startActivityNormallyOpenCollectionWithIntent(TestCardTemplatePreviewer.class, intent);

        assertThat("Activity should be finishing - no cards to show", testCardTemplatePreviewer.isFinishing(), is(true));
    }


    @NonNull
    protected Bundle getFieldsAsBundleForPreview(List<NoteService.NoteField> fields) {
        return NoteService.getFieldsAsBundleForPreview(fields, false);
    }


    protected void assertTwoCards(TestCardTemplatePreviewer testCardTemplatePreviewer) {
        assertThat("prev should not be enabled", testCardTemplatePreviewer.previousButtonEnabled(), is(false));
        assertThat("next should be enabled", testCardTemplatePreviewer.nextButtonEnabled(), is(true));

        testCardTemplatePreviewer.onNextTemplate();

        assertThat("index is changed", testCardTemplatePreviewer.getTemplateIndex(), is(1));
        assertThat("prev should be enabled", testCardTemplatePreviewer.previousButtonEnabled(), is(true));
        assertThat("next should not be enabled", testCardTemplatePreviewer.nextButtonEnabled(), is(false));

        testCardTemplatePreviewer.onNextTemplate();

        // no effect
        assertThat("index is changed", testCardTemplatePreviewer.getTemplateIndex(), is(1));
        assertThat("prev should be enabled", testCardTemplatePreviewer.previousButtonEnabled(), is(true));
        assertThat("next should not be enabled", testCardTemplatePreviewer.nextButtonEnabled(), is(false));

        testCardTemplatePreviewer.onPreviousTemplate();

        // previous
        assertThat("index is changed", testCardTemplatePreviewer.getTemplateIndex(), is(0));
        assertThat("prev should be enabled", testCardTemplatePreviewer.previousButtonEnabled(), is(false));
        assertThat("next should not be enabled", testCardTemplatePreviewer.nextButtonEnabled(), is(true));
    }

    private Card getSavedCard(Model model, int ordinal) {
        @NonNull Note n = getCol().newNote(model);
        List<String> fieldNames = model.getFieldsNames();
        for (int i = 0; i < fieldNames.size(); i++) {
            n.setField(i, fieldNames.get(i));
        }
        n.flush();
        return getCol().getNewLinkedCard(new Card(getCol()), n, model.getJSONArray("tmpls").getJSONObject(ordinal), 1, 1, true);
    }

    private class Field implements NoteService.NoteField {

        private final int mOrd;
        private final String mText;


        public Field(int ord, String text) {
            this.mOrd = ord;
            this.mText = text;
        }

        @Override
        public int getOrd() {
            return mOrd;
        }


        @Nullable
        @Override
        public String getFieldText() {
            return mText;
        }
    }
}