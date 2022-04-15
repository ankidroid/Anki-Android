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
package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AbstractFlashcardViewer.Companion.editorCard
import com.ichi2.anki.NoteEditorTest.FromScreen.DECK_LIST
import com.ichi2.anki.NoteEditorTest.FromScreen.REVIEWER
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity
import com.ichi2.compat.Compat.ACTION_PROCESS_TEXT
import com.ichi2.compat.Compat.EXTRA_PROCESS_TEXT
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Decks.CURRENT_DECK
import com.ichi2.libanki.Model
import com.ichi2.libanki.Note
import com.ichi2.libanki.backend.DroidBackendFactory.getInstance
import com.ichi2.libanki.backend.RustDroidV16Backend
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.*
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("IDE lint")
@KotlinCleanup("is -> equalTo")
@KotlinCleanup("n.findViewById<View>(R.id.X) as TextView -> findViewById<TextView>")
@KotlinCleanup("Objects.requireNonNull -> assertNotNull - removes the !! after")
class NoteEditorTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "en")
    fun verifyCardsList() {
        val n = getNoteEditorEditingExistingBasicNote("Test", "Note", DECK_LIST)
        assertThat("Cards list is correct", (n.findViewById<View>(R.id.CardEditorCardsButton) as TextView).text.toString(), `is`("Cards: Card 1"))
    }

    @Test
    fun verifyPreviewAddingNote() {
        val n = getNoteEditorAdding(NoteType.BASIC).withFirstField("Preview Test").build()
        n.performPreview()
        val intent = shadowOf(n).nextStartedActivityForResult
        val noteEditorBundle = intent.intent.getBundleExtra("noteEditorBundle")
        assertThat("Bundle set to add note style", noteEditorBundle!!.getBoolean("addNote"), `is`(true))
        val fieldsBundle = noteEditorBundle.getBundle("editFields")
        assertThat("Bundle has fields", fieldsBundle, notNullValue())
        assertThat("Bundle has fields edited value", fieldsBundle!!.getString("0"), `is`("Preview Test"))
        assertThat("Bundle has empty tag list", noteEditorBundle.getStringArrayList("tags"), `is`(ArrayList<Any>()))
        assertThat("Bundle has no ordinal for ephemeral preview", intent.intent.hasExtra("ordinal"), `is`(false))
        assertThat("Bundle has a temporary model saved", intent.intent.hasExtra(TemporaryModel.INTENT_MODEL_FILENAME), `is`(true))
    }

    @Test
    fun whenEditingMultimediaEditUsesCurrentValueOfFields() {
        // Arrange
        val fieldIndex = 0
        val n = getNoteEditorEditingExistingBasicNote("Hello", "World", REVIEWER)
        enterTextIntoField(n, fieldIndex, "Good Afternoon")

        // Act
        openAdvancedTextEditor(n, fieldIndex)

        // Assert
        val intent = shadowOf(n).nextStartedActivityForResult
        val actualField = MultimediaEditFieldActivity.getFieldFromIntent(intent.intent)
        assertThat("Provided value should be the updated value", actualField.formattedValue, `is`("Good Afternoon"))
    }

    @Test
    fun errorSavingNoteWithNoFirstFieldDisplaysNoFirstField() {
        val noteEditor = getNoteEditorAdding(NoteType.BASIC)
            .withNoFirstField()
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, `is`(R.string.note_editor_no_first_field))
    }

    @Test
    fun errorSavingInvalidNoteWithAllFieldsDisplaysInvalidTemplate() {
        val noteEditor = getNoteEditorAdding(NoteType.THREE_FIELD_INVALID_TEMPLATE)
            .withFirstField("A")
            .withSecondField("B")
            .withThirdField("C")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, `is`(R.string.note_editor_no_cards_created_all_fields))
    }

    @Test
    fun errorSavingInvalidNoteWitSomeFieldsDisplaysEnterMore() {
        val noteEditor = getNoteEditorAdding(NoteType.THREE_FIELD_INVALID_TEMPLATE)
            .withFirstField("A")
            .withThirdField("C")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, `is`(R.string.note_editor_no_cards_created))
    }

    @Test
    fun errorSavingClozeNoteWithNoFirstFieldDisplaysClozeError() {
        val noteEditor = getNoteEditorAdding(NoteType.CLOZE)
            .withNoFirstField()
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, `is`(R.string.note_editor_no_cloze_delations))
    }

    @Test
    fun errorSavingClozeNoteWithNoClozeDeletionsDisplaysClozeError() {
        val noteEditor = getNoteEditorAdding(NoteType.CLOZE)
            .withFirstField("NoCloze")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, `is`(R.string.note_editor_no_cloze_delations))
    }

    @Test
    fun errorSavingNoteWithNoTemplatesShowsNoCardsCreated() {
        val noteEditor = getNoteEditorAdding(NoteType.BACK_TO_FRONT)
            .withFirstField("front is not enough")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, `is`(R.string.note_editor_no_cards_created))
    }

    @Test
    fun clozeNoteWithNoClozeDeletionsDoesNotSave() {
        val initialCards = cardCount
        val editor = getNoteEditorAdding(NoteType.CLOZE)
            .withFirstField("no cloze deletions")
            .build()
        saveNote(editor)
        assertThat(cardCount, `is`(initialCards))
    }

    @Test
    fun clozeNoteWithClozeDeletionsDoesSave() {
        val initialCards = cardCount
        val editor = getNoteEditorAdding(NoteType.CLOZE)
            .withFirstField("{{c1::AnkiDroid}} is fantastic")
            .build()
        saveNote(editor)
        assertThat(cardCount, `is`(initialCards + 1))
    }

    @Test
    @Ignore("Not yet implemented")
    fun clozeNoteWithClozeInWrongFieldDoesNotSave() {
        // Anki Desktop blocks with "Continue?", we should just block to match the above test
        val initialCards = cardCount
        val editor = getNoteEditorAdding(NoteType.CLOZE)
            .withSecondField("{{c1::AnkiDroid}} is fantastic")
            .build()
        saveNote(editor)
        assertThat(cardCount, `is`(initialCards))
    }

    @Test
    fun verifyStartupAndCloseWithNoCollectionDoesNotCrash() {
        enableNullCollection()
        ActivityScenario.launch(NoteEditor::class.java).use { scenario ->
            scenario.onActivity { noteEditor: NoteEditor ->
                noteEditor.onBackPressed()
                assertThat("Pressing back should finish the activity", noteEditor.isFinishing)
            }
            val result = scenario.result
            assertThat("Activity should be cancelled as no changes were made", result.resultCode, `is`(Activity.RESULT_CANCELED))
        }
    }

    @Test
    fun copyNoteCopiesDeckId() {
        // value returned if deck not found
        val DECK_ID_NOT_FOUND = -404
        val currentDid = addDeck("Basic::Test")
        col.set_config(CURRENT_DECK, currentDid)
        val n = super.addNoteUsingBasicModel("Test", "Note")
        n.model().put("did", currentDid)
        val editor = getNoteEditorEditingExistingBasicNote("Test", "Note", DECK_LIST)
        col.set_config(CURRENT_DECK, Consts.DEFAULT_DECK_ID) // Change DID if going through default path
        val copyNoteIntent = getCopyNoteIntent(editor)
        val newNoteEditor = super.startActivityNormallyOpenCollectionWithIntent(NoteEditor::class.java, copyNoteIntent)
        assertThat("Selected deck ID should be the current deck id", editor.deckId, `is`(currentDid))
        assertThat("Deck ID in the intent should be the selected deck id", copyNoteIntent.getLongExtra(NoteEditor.EXTRA_DID, DECK_ID_NOT_FOUND.toLong()), `is`(currentDid))
        assertThat("Deck ID in the new note should be the ID provided in the intent", newNoteEditor.deckId, `is`(currentDid))
    }

    @Test
    fun stickyFieldsAreUnchangedAfterAdd() {
        // #6795 - newlines were converted to <br>
        val basic = makeNoteForType(NoteType.BASIC)

        // Enable sticky "Front" field
        basic!!.getJSONArray("flds").getJSONObject(0).put("sticky", true)
        val initFirstField = "Hello"
        val initSecondField = "unused"
        val newFirstField = "Hello" + FieldEditText.NEW_LINE + "World" // /r/n on Windows under Robolectric
        val editor = getNoteEditorAdding(NoteType.BASIC)
            .withFirstField(initFirstField)
            .withSecondField(initSecondField)
            .build()
        assertThat(Arrays.asList(*editor.currentFieldStrings), contains(initFirstField, initSecondField))
        editor.setFieldValueFromUi(0, newFirstField)
        assertThat(Arrays.asList(*editor.currentFieldStrings), contains(newFirstField, initSecondField))

        saveNote(editor)
        waitForAsyncTasksToComplete()
        val actual = Arrays.asList(*editor.currentFieldStrings)

        assertThat("newlines should be preserved, second field should be blanked", actual, contains(newFirstField, ""))
    }

    @Test
    fun processTextIntentShouldCopyFirstField() {
        ensureCollectionLoadIsSynchronous()

        val i = Intent(ACTION_PROCESS_TEXT)
        i.putExtra(EXTRA_PROCESS_TEXT, "hello\nworld")
        val editor = startActivityNormallyOpenCollectionWithIntent(NoteEditor::class.java, i)
        val actual = Arrays.asList(*editor.currentFieldStrings)

        assertThat(actual, contains("hello\nworld", ""))
    }

    @Test
    fun previewWorksWithNoError() {
        // #6923 regression test - Low value - Could not make this fail as onSaveInstanceState did not crash under Robolectric.
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        assertDoesNotThrow { editor.performPreview() }
    }

    @Test
    fun clearFieldWorks() {
        // #7522
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        editor.setFieldValueFromUi(1, "Hello")
        assertThat(editor.currentFieldStrings[1], `is`("Hello"))
        editor.clearField(1)
        assertThat(editor.currentFieldStrings[1], `is`(""))
    }

    @Test
    fun insertIntoFocusedFieldStartsAtSelection() {
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        val field: EditText = editor.getFieldForTest(0)
        editor.insertStringInField(field, "Hello")
        field.setSelection(3)
        editor.insertStringInField(field, "World")
        assertThat(editor.getFieldForTest(0).text.toString(), `is`("HelWorldlo"))
    }

    @Test
    fun insertIntoFocusedFieldReplacesSelection() {
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        val field: EditText = editor.getFieldForTest(0)
        editor.insertStringInField(field, "12345")
        field.setSelection(2, 3) // select "3"
        editor.insertStringInField(field, "World")
        assertThat(editor.getFieldForTest(0).text.toString(), `is`("12World45"))
    }

    @Test
    fun insertIntoFocusedFieldReplacesSelectionIfBackwards() {
        // selections can be backwards if the user uses keyboards
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        val field: EditText = editor.getFieldForTest(0)
        editor.insertStringInField(field, "12345")
        field.setSelection(3, 2) // select "3" (right to left)
        editor.insertStringInField(field, "World")
        assertThat(editor.getFieldForTest(0).text.toString(), `is`("12World45"))
    }

    @Test
    fun defaultsToCapitalized() {
        // Requested in #3758, this seems like a sensible default
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        assertThat("Fields should have their first word capitalized by default", editor.getFieldForTest(0).isCapitalized, `is`(true))
    }

    @Test
    @Config(qualifiers = "en")
    fun addToCurrentWithNoDeckSelectsDefault_issue_9616() {
        assumeThat(getInstance(true), not(instanceOf(RustDroidV16Backend::class.java)))
        col.conf.put("addToCur", false)
        val cloze = assertNotNull(col.models.byName("Cloze"))
        cloze.remove("did")
        col.models.save(cloze)
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        editor.setCurrentlySelectedModel(cloze.getLong("id"))
        assertThat(editor.deckId, `is`(Consts.DEFAULT_DECK_ID))
    }

    private fun getCopyNoteIntent(editor: NoteEditor): Intent {
        val editorShadow = shadowOf(editor)
        editor.copyNote()
        return editorShadow.peekNextStartedActivityForResult().intent
    }

    private val cardCount: Int
        get() = col.cardCount()

    private fun getNoteEditorAdding(noteType: NoteType): NoteEditorTestBuilder {
        val n = makeNoteForType(noteType)
        return NoteEditorTestBuilder(n)
    }

    private fun makeNoteForType(noteType: NoteType): Model? {
        return when (noteType) {
            NoteType.BASIC -> col.models.byName("Basic")
            NoteType.CLOZE -> col.models.byName("Cloze")
            NoteType.BACK_TO_FRONT -> {
                val name = super.addNonClozeModel("Reversed", arrayOf("Front", "Back"), "{{Back}}", "{{Front}}")
                col.models.byName(name)
            }
            NoteType.THREE_FIELD_INVALID_TEMPLATE -> {
                val name = super.addNonClozeModel("Invalid", arrayOf("Front", "Back", "Side"), "", "")
                col.models.byName(name)
            }
        }
    }

    private fun openAdvancedTextEditor(n: NoteEditor, fieldIndex: Int) {
        n.startAdvancedTextEditor(fieldIndex)
    }

    private fun enterTextIntoField(n: NoteEditor, i: Int, newText: String) {
        n.setFieldValueFromUi(i, newText)
    }

    private fun <T : NoteEditor?> getNoteEditorAddingNote(from: FromScreen, clazz: Class<T>): T {
        ensureCollectionLoadIsSynchronous()
        val i = Intent()
        when (from) {
            REVIEWER -> i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD)
            DECK_LIST -> i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
        }
        return super.startActivityNormallyOpenCollectionWithIntent(clazz, i)
    }

    private fun getNoteEditorEditingExistingBasicNote(front: String, back: String, from: FromScreen): NoteEditor {
        val n = super.addNoteUsingBasicModel(front, back)
        return getNoteEditorEditingExistingBasicNote(n, from, NoteEditor::class.java)
    }

    private fun <T : NoteEditor?> getNoteEditorEditingExistingBasicNote(n: Note, from: FromScreen, clazz: Class<T>): T {
        val i = Intent()
        when (from) {
            REVIEWER -> {
                i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER)
                editorCard = n.firstCard()
            }
            DECK_LIST -> i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
        }
        return super.startActivityNormallyOpenCollectionWithIntent(clazz, i)
    }

    private fun saveNote(editor: NoteEditor) {
        editor.saveNote()
        advanceRobolectricLooperWithSleep()
    }

    private enum class FromScreen {
        DECK_LIST, REVIEWER
    }

    /** We don't use constants here to allow for additional note types to be defined  */
    private enum class NoteType {
        BASIC, CLOZE,

        /**Basic, but Back is on the front  */
        BACK_TO_FRONT, THREE_FIELD_INVALID_TEMPLATE
    }

    inner class NoteEditorTestBuilder(model: Model?) {
        private val mModel: Model
        private var mFirstField: String? = null
        private var mSecondField: String? = null
        private var mThirdField: String? = null
        fun build(): NoteEditor {
            val editor = build(NoteEditor::class.java)
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            // 4 is insufficient
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            return editor
        }

        fun <T : NoteEditor?> build(clazz: Class<T>): T {
            col.models.setCurrent(mModel)
            val noteEditor = getNoteEditorAddingNote(REVIEWER, clazz)
            advanceRobolectricLooper()
            noteEditor!!.setFieldValueFromUi(0, mFirstField)
            if (mSecondField != null) {
                noteEditor.setFieldValueFromUi(1, mSecondField)
            }
            if (mThirdField != null) {
                noteEditor.setFieldValueFromUi(2, mThirdField)
            }
            return noteEditor
        }

        fun withNoFirstField(): NoteEditorTestBuilder {
            return this
        }

        fun withFirstField(text: String?): NoteEditorTestBuilder {
            mFirstField = text
            return this
        }

        fun withSecondField(text: String?): NoteEditorTestBuilder {
            mSecondField = text
            return this
        }

        fun withThirdField(text: String?): NoteEditorTestBuilder {
            mThirdField = text
            return this
        }

        init {
            requireNotNull(model) { "model was null" }
            mModel = model
        }
    }
}
