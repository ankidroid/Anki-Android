package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.libanki.Note;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowActivity;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.robolectric.Shadows.shadowOf;

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


    private void openAdvancedTextEditor(NoteEditor n, int fieldIndex) {
        n.startAdvancedTextEditor(fieldIndex);
    }


    private void enterTextIntoField(NoteEditor n, int i, String newText) {
        n.setFieldValueFromUi(i, newText);
    }


    private NoteEditor getNoteEditorEditingExistingBasicNote(String front, String back, FromScreen reviewer) {
        Note n = super.addNoteUsingBasicModel(front, back);

        Intent i = new Intent();
        if (reviewer == FromScreen.REVIEWER) {
            i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER);
            AbstractFlashcardViewer.setEditorCard(n.cards().get(0));
        } else {
            throw new IllegalStateException(reviewer.toString() + " unhandled");
        }

        return super.startActivityNormallyOpenCollectionWithIntent(NoteEditor.class, i);
    }

    private enum FromScreen {
        REVIEWER
    }
}
