/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONObject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.compat.Compat.EXTRA_PROCESS_TEXT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.robolectric.Shadows.shadowOf;

@SuppressWarnings("SameParameterValue")
@RunWith(AndroidJUnit4.class)
public class NoteEditorTest extends RobolectricTest {

    @Test
    @Config(qualifiers = "en")
    public void verifyCardsList() {
        NoteEditor n = getNoteEditorEditingExistingBasicNote("Test", "Note", FromScreen.DECK_LIST);
        assertThat("Cards list is correct", ((TextView) n.findViewById(R.id.CardEditorCardsText)).getText().toString(), is("Cards: Card 1"));
    }

    @Test
    public void verifyPreviewAddingNote() {
        NoteEditor n = getNoteEditorAdding(NoteType.BASIC).withFirstField("Preview Test").build();
        ActionMenuItemView previewButton = n.findViewById(R.id.action_preview);
        previewButton.performClick();
        ShadowActivity.IntentForResult intent = shadowOf(n).getNextStartedActivityForResult();
        Bundle noteEditorBundle = intent.intent.getBundleExtra("noteEditorBundle");
        assertThat("Bundle set to add note style", noteEditorBundle.getBoolean("addNote"), is(true));
        Bundle fieldsBundle = noteEditorBundle.getBundle("editFields");
        assertThat("Bundle has fields", fieldsBundle, notNullValue());
        assertThat("Bundle has fields edited value", fieldsBundle.getString("0"), is("Preview Test"));
        assertThat("Bundle has empty tag list", noteEditorBundle.getStringArrayList("tags"), is(new ArrayList<>()));
        assertThat("Bundle has ordinal 0 for ephemeral preview", intent.intent.getIntExtra("ordinal", -1), is(0));
        assertThat("Bundle has a temporary model saved", intent.intent.hasExtra(TemporaryModel.INTENT_MODEL_FILENAME), is(true));
    }

    @Test
    public void whenEditingMultimediaEditUsesCurrentValueOfFields() {
        //Arrange
        int fieldIndex = 0;
        NoteEditor n = getNoteEditorEditingExistingBasicNote("Hello", "World", FromScreen.REVIEWER);
        enterTextIntoField(n, fieldIndex, "Good Afternoon");

        //Act
        openAdvancedTextEditor(n, fieldIndex);

        //Assert
        ShadowActivity.IntentForResult intent = shadowOf(n).getNextStartedActivityForResult();
        IField actualField = MultimediaEditFieldActivity.getFieldFromIntent(intent.intent);
        assertThat("Provided value should be the updated value", actualField.getFormattedValue(), is("Good Afternoon"));
    }

    @Test
    public void errorSavingNoteWithNoFirstFieldDisplaysNoFirstField() {
        NoteEditor noteEditor = getNoteEditorAdding(NoteType.BASIC)
                .withNoFirstField()
                .build();

        int actualResourceId = noteEditor.getAddNoteErrorResource();

        assertThat(actualResourceId, is(R.string.note_editor_no_first_field));
    }

    @Test
    public void errorSavingInvalidNoteWithAllFieldsDisplaysInvalidTemplate() {
        NoteEditor noteEditor = getNoteEditorAdding(NoteType.THREE_FIELD_INVALID_TEMPLATE)
                .withFirstField("A")
                .withSecondField("B")
                .withThirdField("C")
                .build();

        int actualResourceId = noteEditor.getAddNoteErrorResource();

        assertThat(actualResourceId, is(R.string.note_editor_no_cards_created_all_fields));
    }

    @Test
    public void errorSavingInvalidNoteWitSomeFieldsDisplaysEnterMore() {
        NoteEditor noteEditor = getNoteEditorAdding(NoteType.THREE_FIELD_INVALID_TEMPLATE)
                .withFirstField("A")
                .withThirdField("C")
                .build();

        int actualResourceId = noteEditor.getAddNoteErrorResource();

        assertThat(actualResourceId, is(R.string.note_editor_no_cards_created));
    }

    @Test
    public void errorSavingClozeNoteWithNoFirstFieldDisplaysClozeError() {
        NoteEditor noteEditor = getNoteEditorAdding(NoteType.CLOZE)
                .withNoFirstField()
                .build();

        int actualResourceId = noteEditor.getAddNoteErrorResource();

        assertThat(actualResourceId, is(R.string.note_editor_no_cloze_delations));
    }

    @Test
    public void errorSavingClozeNoteWithNoClozeDeletionsDisplaysClozeError() {
        NoteEditor noteEditor = getNoteEditorAdding(NoteType.CLOZE)
                .withFirstField("NoCloze")
                .build();

        int actualResourceId = noteEditor.getAddNoteErrorResource();

        assertThat(actualResourceId, is(R.string.note_editor_no_cloze_delations));
    }

    @Test
    public void errorSavingNoteWithNoTemplatesShowsNoCardsCreated() {
        NoteEditor noteEditor = getNoteEditorAdding(NoteType.BACKTOFRONT)
                .withFirstField("front is not enough")
                .build();

        int actualResourceId = noteEditor.getAddNoteErrorResource();

        assertThat(actualResourceId, is(R.string.note_editor_no_cards_created));
    }

    @Test
    public void clozeNoteWithNoClozeDeletionsDoesNotSave() {
        int initialCards = getCardCount();
        NoteEditor editor = getNoteEditorAdding(NoteType.CLOZE)
                .withFirstField("no cloze deletions")
                .build();

        editor.saveNote();

        assertThat(getCardCount(), is(initialCards));
    }

    @Test
    public void clozeNoteWithClozeDeletionsDoesSave() {
        int initialCards = getCardCount();
        NoteEditor editor = getNoteEditorAdding(NoteType.CLOZE)
                .withFirstField("{{c1::AnkiDroid}} is fantastic")
                .build();

        editor.saveNote();

        assertThat(getCardCount(), is(initialCards + 1));
    }

    @Test
    @Ignore("Not yet implemented")
    public void clozeNoteWithClozeInWrongFieldDoesNotSave() {
        //Anki Desktop blocks with "Continue?", we should just block to match the above test
        int initialCards = getCardCount();
        NoteEditor editor = getNoteEditorAdding(NoteType.CLOZE)
                .withSecondField("{{c1::AnkiDroid}} is fantastic")
                .build();

        editor.saveNote();

        assertThat(getCardCount(), is(initialCards));
    }

    @Test
    public void verifyStartupAndCloseWithNoCollectionDoesNotCrash() {
        enableNullCollection();
        try (ActivityScenario<NoteEditor> scenario = ActivityScenario.launch(NoteEditor.class)) {
            scenario.onActivity(noteEditor -> {
                noteEditor.onBackPressed();
                assertThat("Pressing back should finish the activity", noteEditor.isFinishing());
            });

            Instrumentation.ActivityResult result = scenario.getResult();
            assertThat("Activity should be cancelled as no changes were made", result.getResultCode(), is(Activity.RESULT_CANCELED));
        }
    }

    @Test
    public void copyNoteCopiesDeckId() {
        // value returned if deck not found
        final int DECK_ID_NOT_FOUND = -404;
        long currentDid = addDeck("Basic::Test");
        getCol().getConf().put("curDeck", currentDid);
        Note n = super.addNoteUsingBasicModel("Test", "Note");
        n.model().put("did", currentDid);
        NoteEditor editor = getNoteEditorEditingExistingBasicNote("Test", "Note", FromScreen.DECK_LIST);

        getCol().getConf().put("curDeck", Consts.DEFAULT_DECK_ID); // Change DID if going through default path
        Intent copyNoteIntent = getCopyNoteIntent(editor);
        NoteEditor newNoteEditor = super.startActivityNormallyOpenCollectionWithIntent(NoteEditor.class, copyNoteIntent);

        assertThat("Selected deck ID should be the current deck id", editor.getDeckId(), is(currentDid));
        assertThat("Deck ID in the intent should be the selected deck id", copyNoteIntent.getLongExtra(NoteEditor.EXTRA_DID, DECK_ID_NOT_FOUND), is(currentDid));
        assertThat("Deck ID in the new note should be the ID provided in the intent", newNoteEditor.getDeckId(), is(currentDid));
    }


    @Test
    public void stickyFieldsAreUnchangedAfterAdd() {
        // #6795 - newlines were converted to <br>
        Model basic = makeNoteForType(NoteType.BASIC);

        // Enable sticky "Front" field
        basic.getJSONArray("flds").getJSONObject(0).put("sticky", true);

        String initFirstField = "Hello";
        String initSecondField = "unused";
        String newFirstField = "Hello" + FieldEditText.NEW_LINE + "World"; // /r/n on Windows under Robolectric

        NoteEditor editor = getNoteEditorAdding(NoteType.BASIC)
                .withFirstField(initFirstField)
                .withSecondField(initSecondField)
                .build();

        assertThat(Arrays.asList(editor.getCurrentFieldStrings()), contains(initFirstField, initSecondField));

        editor.setFieldValueFromUi(0, newFirstField);
        assertThat(Arrays.asList(editor.getCurrentFieldStrings()), contains(newFirstField, initSecondField));

        editor.saveNote();
        this.waitForAsyncTasksToComplete();

        List<String> actual = Arrays.asList(editor.getCurrentFieldStrings());
        assertThat("newlines should be preserved, second field should be blanked", actual, contains(newFirstField, ""));
    }

    @Test
    public void processTextIntentShouldCopyFirstField() {
        Intent i = new Intent(Intent.ACTION_PROCESS_TEXT);
        i.putExtra(EXTRA_PROCESS_TEXT, "hello\nworld");
        NoteEditor editor = startActivityNormallyOpenCollectionWithIntent(NoteEditor.class, i);
        assertThat(Arrays.asList(editor.getCurrentFieldStrings()), contains("hello\nworld", ""));
    }

    private Intent getCopyNoteIntent(NoteEditor editor) {
        ShadowActivity editorShadow = Shadows.shadowOf(editor);
        editor.copyNote();
        return editorShadow.peekNextStartedActivityForResult().intent;
    }


    private int getCardCount() {
        return getCol().cardCount();
    }

    private NoteEditorTestBuilder getNoteEditorAdding(NoteType noteType) {
        Model n = makeNoteForType(noteType);
        return new NoteEditorTestBuilder(n);
    }


    private Model makeNoteForType(NoteType noteType) {
        switch (noteType) {
            case BASIC: return getCol().getModels().byName("Basic");
            case CLOZE: return getCol().getModels().byName("Cloze");
            case BACKTOFRONT: {
                String name = super.addNonClozeModel("Reversed", new String[] {"Front", "Back"}, "{{Back}}", "{{Front}}");
                return getCol().getModels().byName(name);
            }
            case THREE_FIELD_INVALID_TEMPLATE: {
                String name = super.addNonClozeModel("Invalid", new String[] {"Front", "Back", "Side"}, "", "");
                return getCol().getModels().byName(name);
            }
            default: throw new IllegalStateException(String.format("unexpected value: %s", noteType));
        }
    }


    private void openAdvancedTextEditor(NoteEditor n, int fieldIndex) {
        n.startAdvancedTextEditor(fieldIndex);
    }


    private void enterTextIntoField(NoteEditor n, int i, String newText) {
        n.setFieldValueFromUi(i, newText);
    }

    private <T extends NoteEditor> T getNoteEditorAddingNote(FromScreen from, Class<T> clazz) {
        Intent i = new Intent();
        switch (from) {
            case REVIEWER:
                i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD);
                break;
            case DECK_LIST:
                i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
                break;
            default:
                throw new IllegalStateException(" unhandled");
        }

        return super.startActivityNormallyOpenCollectionWithIntent(clazz, i);
    }

    private NoteEditor getNoteEditorEditingExistingBasicNote(String front, String back, FromScreen from) {
        Note n = super.addNoteUsingBasicModel(front, back);
        return getNoteEditorEditingExistingBasicNote(n, from, NoteEditor.class);
    }

    private <T extends NoteEditor> T getNoteEditorEditingExistingBasicNote(Note n, FromScreen from, Class<T> clazz) {

        Intent i = new Intent();
        switch (from) {
            case REVIEWER:
                i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER);
                AbstractFlashcardViewer.setEditorCard(n.firstCard());
                break;
            case DECK_LIST:
                i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
                break;
            default:
                throw new IllegalStateException(from.toString() + " unhandled");
        }

        return super.startActivityNormallyOpenCollectionWithIntent(clazz, i);
    }

    private enum FromScreen {
        DECK_LIST,
        REVIEWER
    }

    /** We don't use constants here to allow for additional note types to be defined */
    private enum NoteType {
        BASIC,
        CLOZE,
        /**Basic, but Back is on the front */
        BACKTOFRONT,
        THREE_FIELD_INVALID_TEMPLATE
    }

    @SuppressWarnings("WeakerAccess")
    public class NoteEditorTestBuilder {

        private final Model mModel;
        private String mFirstField;
        private String mSecondField;
        private String mThirdField;


        public NoteEditorTestBuilder(Model model) {
            if (model == null) {
                throw new IllegalArgumentException("model was null");
            }
            this.mModel = model;
        }

        public NoteEditor build() {
            return build(NoteEditor.class);
        }

        public <T extends NoteEditor> T build(Class<T> clazz) {
            getCol().getModels().setCurrent(mModel);
            T noteEditor = getNoteEditorAddingNote(FromScreen.REVIEWER, clazz);
            noteEditor.setFieldValueFromUi(0, mFirstField);
            if (mSecondField != null) {
                noteEditor.setFieldValueFromUi(1, mSecondField);
            }
            if (mThirdField != null) {
                noteEditor.setFieldValueFromUi(2, mThirdField);
            }
            return noteEditor;
        }

        public NoteEditorTestBuilder withNoFirstField() {
            return this;
        }


        public NoteEditorTestBuilder withFirstField(String text) {
            this.mFirstField = text;
            return this;
        }


        public NoteEditorTestBuilder withSecondField(String text) {
            this.mSecondField = text;
            return this;
        }


        public NoteEditorTestBuilder withThirdField(String text) {
            this.mThirdField = text;
            return this;
        }
    }
}
