package com.ichi2.anki;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONObject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowActivity;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.robolectric.Shadows.shadowOf;

@SuppressWarnings("SameParameterValue")
@RunWith(AndroidJUnit4.class)
public class NoteEditorTest extends RobolectricTest {

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
        //Anki Desktop blocks with "Continue?", we sould just block to match the above test
        int initialCards = getCardCount();
        NoteEditor editor = getNoteEditorAdding(NoteType.CLOZE)
                .withSecondField("{{c1::AnkiDroid}} is fantastic")
                .build();

        editor.saveNote();

        assertThat(getCardCount(), is(initialCards));
    }

    @Test
    public void verifyStartupAndCloseWithNoCollectionDoesNotCrash() {
        try (ActivityScenario<NullCollectionNoteEditor> scenario = ActivityScenario.launch(NullCollectionNoteEditor.class)) {
            scenario.onActivity(noteEditor -> {
                noteEditor.onBackPressed();
                assertThat("Pressing back should finish the activity", noteEditor.isFinishing());
            });

            Instrumentation.ActivityResult result = scenario.getResult();
            assertThat("Activity should be cancelled as no changes were made", result.getResultCode(), is(Activity.RESULT_CANCELED));
        }
    }

    private int getCardCount() {
        return getCol().cardCount();
    }

    private NoteEditorTestBuilder getNoteEditorAdding(NoteType noteType) {
        JSONObject n = makeNoteForType(noteType);
        return new NoteEditorTestBuilder(n);
    }


    private JSONObject makeNoteForType(NoteType noteType) {
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
        if (from == FromScreen.REVIEWER) {
            i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD);
        } else {
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
        if (from == FromScreen.REVIEWER) {
            i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER);
            AbstractFlashcardViewer.setEditorCard(n.cards().get(0));
        } else {
            throw new IllegalStateException(from.toString() + " unhandled");
        }

        return super.startActivityNormallyOpenCollectionWithIntent(clazz, i);
    }

    private enum FromScreen {
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

        private final JSONObject mModel;
        private String mFirstField;
        private String mSecondField;
        private String mThirdField;


        public NoteEditorTestBuilder(JSONObject model) {
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
