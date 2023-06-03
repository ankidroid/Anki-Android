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
import android.content.ClipData
import android.content.Intent
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AbstractFlashcardViewer.Companion.editorCard
import com.ichi2.anki.NoteEditorTest.FromScreen.DECK_LIST
import com.ichi2.anki.NoteEditorTest.FromScreen.REVIEWER
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity
import com.ichi2.compat.Compat.Companion.ACTION_PROCESS_TEXT
import com.ichi2.compat.Compat.Companion.EXTRA_PROCESS_TEXT
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.libanki.Model
import com.ichi2.libanki.Note
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.utils.KotlinCleanup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@KotlinCleanup("IDE lint")
class NoteEditorTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "en")
    fun verifyCardsList() {
        val n = getNoteEditorEditingExistingBasicNote("Test", "Note", DECK_LIST)
        assertThat("Cards list is correct", (n.findViewById<TextView>(R.id.CardEditorCardsButton)).text.toString(), equalTo("Cards: Card 1"))
    }

    @Test
    fun verifyPreviewAddingNote() {
        val n = getNoteEditorAdding(NoteType.BASIC).withFirstField("Preview Test").build()
        n.performPreview()
        val intent = shadowOf(n).nextStartedActivityForResult
        val noteEditorBundle = intent.intent.getBundleExtra("noteEditorBundle")
        assertThat("Bundle set to add note style", noteEditorBundle!!.getBoolean("addNote"), equalTo(true))
        val fieldsBundle = noteEditorBundle.getBundle("editFields")
        assertThat("Bundle has fields", fieldsBundle, notNullValue())
        assertThat("Bundle has fields edited value", fieldsBundle!!.getString("0"), equalTo("Preview Test"))
        assertThat("Bundle has empty tag list", noteEditorBundle.getStringArrayList("tags"), equalTo(ArrayList<Any>()))
        assertThat("Bundle has no ordinal for ephemeral preview", intent.intent.hasExtra("ordinal"), equalTo(false))
        assertThat("Bundle has a temporary model saved", intent.intent.hasExtra(TemporaryModel.INTENT_MODEL_FILENAME), equalTo(true))
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
        val actualField = MultimediaEditFieldActivity.getFieldFromIntent(intent.intent)!!
        assertThat("Provided value should be the updated value", actualField.second.formattedValue, equalTo("Good Afternoon"))
    }

    @Test
    fun errorSavingNoteWithNoFirstFieldDisplaysNoFirstField() {
        val noteEditor = getNoteEditorAdding(NoteType.BASIC)
            .withNoFirstField()
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, equalTo(R.string.note_editor_no_first_field))
    }

    @Test
    @RustCleanup("needs update for new backend")
    fun errorSavingInvalidNoteWithAllFieldsDisplaysInvalidTemplate() {
        if (!BackendFactory.defaultLegacySchema) {
            return
        }
        val noteEditor = getNoteEditorAdding(NoteType.THREE_FIELD_INVALID_TEMPLATE)
            .withFirstField("A")
            .withSecondField("B")
            .withThirdField("C")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, equalTo(R.string.note_editor_no_cards_created_all_fields))
    }

    @Test
    @RustCleanup("needs update for new backend")
    fun errorSavingInvalidNoteWitSomeFieldsDisplaysEnterMore() {
        if (!BackendFactory.defaultLegacySchema) {
            return
        }
        val noteEditor = getNoteEditorAdding(NoteType.THREE_FIELD_INVALID_TEMPLATE)
            .withFirstField("A")
            .withThirdField("C")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, equalTo(R.string.note_editor_no_cards_created))
    }

    @Test
    fun errorSavingClozeNoteWithNoFirstFieldDisplaysClozeError() {
        val noteEditor = getNoteEditorAdding(NoteType.CLOZE)
            .withNoFirstField()
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, equalTo(R.string.note_editor_no_cloze_delations))
    }

    @Test
    fun errorSavingClozeNoteWithNoClozeDeletionsDisplaysClozeError() {
        val noteEditor = getNoteEditorAdding(NoteType.CLOZE)
            .withFirstField("NoCloze")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, equalTo(R.string.note_editor_no_cloze_delations))
    }

    @Test
    fun errorSavingNoteWithNoTemplatesShowsNoCardsCreated() {
        val noteEditor = getNoteEditorAdding(NoteType.BACK_TO_FRONT)
            .withFirstField("front is not enough")
            .build()
        val actualResourceId = noteEditor.addNoteErrorResource
        assertThat(actualResourceId, equalTo(R.string.note_editor_no_cards_created))
    }

    @Test
    fun clozeNoteWithNoClozeDeletionsDoesNotSave() = runTest {
        val initialCards = cardCount
        val editor = getNoteEditorAdding(NoteType.CLOZE)
            .withFirstField("no cloze deletions")
            .build()
        editor.saveNote()
        assertThat(cardCount, equalTo(initialCards))
    }

    @Test
    fun clozeNoteWithClozeDeletionsDoesSave() = runTest {
        val initialCards = cardCount
        val editor = getNoteEditorAdding(NoteType.CLOZE)
            .withFirstField("{{c1::AnkiDroid}} is fantastic")
            .build()
        editor.saveNote()
        assertThat(cardCount, equalTo(initialCards + 1))
    }

    @Test
    @Ignore("Not yet implemented")
    fun clozeNoteWithClozeInWrongFieldDoesNotSave() = runTest {
        // Anki Desktop blocks with "Continue?", we should just block to match the above test
        val initialCards = cardCount
        val editor = getNoteEditorAdding(NoteType.CLOZE)
            .withSecondField("{{c1::AnkiDroid}} is fantastic")
            .build()
        editor.saveNote()
        assertThat(cardCount, equalTo(initialCards))
    }

    @Test
    fun verifyStartupAndCloseWithNoCollectionDoesNotCrash() {
        enableNullCollection()
        ActivityScenario.launchActivityForResult(NoteEditor::class.java).use { scenario ->
            scenario.onActivity { noteEditor: NoteEditor ->
                noteEditor.onBackPressed()
                assertThat("Pressing back should finish the activity", noteEditor.isFinishing)
            }
            val result = scenario.result
            assertThat("Activity should be cancelled as no changes were made", result.resultCode, equalTo(Activity.RESULT_CANCELED))
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
        assertThat("Selected deck ID should be the current deck id", editor.deckId, equalTo(currentDid))
        assertThat("Deck ID in the intent should be the selected deck id", copyNoteIntent.getLongExtra(NoteEditor.EXTRA_DID, DECK_ID_NOT_FOUND.toLong()), equalTo(currentDid))
        assertThat("Deck ID in the new note should be the ID provided in the intent", newNoteEditor.deckId, equalTo(currentDid))
    }

    @Test
    fun stickyFieldsAreUnchangedAfterAdd() = runTest {
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
        assertThat(editor.currentFieldStrings.toList(), contains(initFirstField, initSecondField))
        editor.setFieldValueFromUi(0, newFirstField)
        assertThat(editor.currentFieldStrings.toList(), contains(newFirstField, initSecondField))

        editor.saveNote()
        waitForAsyncTasksToComplete()
        val actual = editor.currentFieldStrings.toList()

        assertThat("newlines should be preserved, second field should be blanked", actual, contains(newFirstField, ""))
    }

    @Test
    fun processTextIntentShouldCopyFirstField() {
        ensureCollectionLoadIsSynchronous()

        val i = Intent(ACTION_PROCESS_TEXT)
        i.putExtra(EXTRA_PROCESS_TEXT, "hello\nworld")
        val editor = startActivityNormallyOpenCollectionWithIntent(NoteEditor::class.java, i)
        val actual = editor.currentFieldStrings.toList()

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
        assertThat(editor.currentFieldStrings[1], equalTo("Hello"))
        editor.clearField(1)
        assertThat(editor.currentFieldStrings[1], equalTo(""))
    }

    @Test
    fun insertIntoFocusedFieldStartsAtSelection() {
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        val field: EditText = editor.getFieldForTest(0)
        editor.insertStringInField(field, "Hello")
        field.setSelection(3)
        editor.insertStringInField(field, "World")
        assertThat(editor.getFieldForTest(0).text.toString(), equalTo("HelWorldlo"))
    }

    @Test
    fun insertIntoFocusedFieldReplacesSelection() {
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        val field: EditText = editor.getFieldForTest(0)
        editor.insertStringInField(field, "12345")
        field.setSelection(2, 3) // select "3"
        editor.insertStringInField(field, "World")
        assertThat(editor.getFieldForTest(0).text.toString(), equalTo("12World45"))
    }

    @Test
    fun insertIntoFocusedFieldReplacesSelectionIfBackwards() {
        // selections can be backwards if the user uses keyboards
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        val field: EditText = editor.getFieldForTest(0)
        editor.insertStringInField(field, "12345")
        field.setSelection(3, 2) // select "3" (right to left)
        editor.insertStringInField(field, "World")
        assertThat(editor.getFieldForTest(0).text.toString(), equalTo("12World45"))
    }

    @Test
    fun defaultsToCapitalized() {
        // Requested in #3758, this seems like a sensible default
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        assertThat("Fields should have their first word capitalized by default", editor.getFieldForTest(0).isCapitalized, equalTo(true))
    }

    @Test
    @Config(qualifiers = "en")
    fun addToCurrentWithNoDeckSelectsDefault_issue_9616() {
        assumeThat(col.backend.legacySchema, not(false))
        col.conf.put("addToCur", false)
        val cloze = assertNotNull(col.models.byName(col, "Cloze"))
        cloze.remove("did")
        col.models.save(col, cloze)
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        editor.setCurrentlySelectedModel(cloze.getLong("id"))
        assertThat(editor.deckId, equalTo(Consts.DEFAULT_DECK_ID))
    }

    @Test
    fun pasteHtmlAsPlainTextTest() {
        val editor = getNoteEditorAddingNote(DECK_LIST, NoteEditor::class.java)
        editor.setCurrentlySelectedModel(col.models.byName(col, "Basic")!!.getLong("id"))
        val field = editor.getFieldForTest(0)
        field.clipboard!!.setPrimaryClip(ClipData.newHtmlText("text", "text", """<span style="color: red">text</span>"""))
        assertTrue(field.clipboard!!.hasPrimaryClip())
        assertNotNull(field.clipboard!!.primaryClip)

        // test pasting in the middle (cursor mode: selecting)
        editor.setField(0, "012345")
        field.setSelection(1, 2) // selecting "1"
        assertTrue(field.pastePlainText())
        assertEquals("0text2345", field.fieldText)
        assertEquals(5, field.selectionStart)
        assertEquals(5, field.selectionEnd)

        // test pasting in the middle (cursor mode: selecting backwards)
        editor.setField(0, "012345")
        field.setSelection(2, 1) // selecting "1"
        assertTrue(field.pastePlainText())
        assertEquals("0text2345", field.fieldText)
        assertEquals(5, field.selectionStart)
        assertEquals(5, field.selectionEnd)

        // test pasting in the middle (cursor mode: normal)
        editor.setField(0, "012345")
        field.setSelection(4) // after "3"
        assertTrue(field.pastePlainText())
        assertEquals("0123text45", field.fieldText)
        assertEquals(8, field.selectionStart)
        assertEquals(8, field.selectionEnd)

        // test pasting at the start
        editor.setField(0, "012345")
        field.setSelection(0) // before "0"
        assertTrue(field.pastePlainText())
        assertEquals("text012345", field.fieldText)
        assertEquals(4, field.selectionStart)
        assertEquals(4, field.selectionEnd)

        // test pasting at the end
        editor.setField(0, "012345")
        field.setSelection(6) // after "5"
        assertTrue(field.pastePlainText())
        assertEquals("012345text", field.fieldText)
        assertEquals(10, field.selectionStart)
        assertEquals(10, field.selectionEnd)
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
            NoteType.BASIC -> col.models.byName(col, "Basic")
            NoteType.CLOZE -> col.models.byName(col, "Cloze")
            NoteType.BACK_TO_FRONT -> {
                val name = super.addNonClozeModel("Reversed", arrayOf("Front", "Back"), "{{Back}}", "{{Front}}")
                col.models.byName(col, name)
            }
            NoteType.THREE_FIELD_INVALID_TEMPLATE -> {
                val name = super.addNonClozeModel("Invalid", arrayOf("Front", "Back", "Side"), "", "")
                col.models.byName(col, name)
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
                i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_EDIT)
                editorCard = n.firstCard(col)
            }
            DECK_LIST -> i.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
        }
        return super.startActivityNormallyOpenCollectionWithIntent(clazz, i)
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
            col.models.setCurrent(col, mModel)
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
            assertNotNull(model) { "model was null" }
            mModel = model
        }
    }
}
