/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.instanteditor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.instantnoteeditor.InstantEditorViewModel
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.instantnoteeditor.SaveNoteResult
import com.ichi2.testutils.TestClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstantEditorViewModelTest : RobolectricTest() {

    @Test
    fun testSetUpNoteType_with_Cloze_NoteType() = runViewModelTest {
        assertEquals(InstantNoteEditorActivity.DialogType.SHOW_EDITOR_DIALOG, dialogType.value)
    }

    @Test
    fun testSetUpNoteType_with_NoCloze_NoteType() = runViewModelTest {
        val noteTypes = col.notetypes.all().filter { it.isCloze }

        for (note in noteTypes) {
            col.backend.removeNotetype(note.id)
        }

        waitForAsyncTasksToComplete()

        // Reinitialize the viewModel
        runViewModelTest({ InstantEditorViewModel() }) {
            assertEquals(
                InstantNoteEditorActivity.DialogType.NO_CLOZE_NOTE_TYPES_DIALOG,
                dialogType.value
            )
        }
    }

    @Test
    fun `test cloze number reset to 1`() = runViewModelTest {
        val sentenceArray = mutableListOf("Hello", "world")

        toggleAllClozeDeletions(sentenceArray)

        assertEquals("all clozes are detected", clozeDeletionCount, 2)

        toggleAllClozeDeletions(sentenceArray)

        assertEquals("all cloze deletions are removed", clozeDeletionCount, 0)
        assertEquals("cloze number is reset if there are no clozes", currentClozeNumber, 1)
    }

    @Test
    fun `test cloze number is reset to max value from cloze list`() = runViewModelTest {
        val sentenceArray = mutableListOf("Hello", "world", "this", "is", "test", "sentence")

        // cloze on the first 3 words
        toggleClozeDeletions(sentenceArray, 0, 1, 2)

        // disable cloze on the first 2, leaving "this" as {{c3::
        toggleClozeDeletions(sentenceArray, 0, 1)

        assertEquals("cloze number is 'Current Cloze Number + 1'", currentClozeNumber, 4)

        // remove the remaining cloze all clozes
        toggleClozeDeletion(sentenceArray, 2)
        assertEquals("cloze number is reset if all clozes are removed", currentClozeNumber, 1)
    }

    @Test
    fun testSavingNoteWithNoCloze() = runViewModelTest {
        editorNote.setField(0, "Hello")
        val result = checkAndSaveNote()

        assertEquals(CollectionManager.TR.addingYouHaveAClozeDeletionNote(), saveNoteResult(result))
    }

    @Test
    fun testSavingNoteWithEmptyFields() = runViewModelTest {
        editorNote.setField(0, "{{c1::Hello}}")

        val result = checkAndSaveNote()

        assertEquals("Success", saveNoteResult(result))
    }

    @Test
    fun testSavingNoteWithClozeFields() = runViewModelTest {
        val result = checkAndSaveNote()

        assertEquals(CollectionManager.TR.addingTheFirstFieldIsEmpty(), saveNoteResult(result))
    }

    @Test
    fun testCheckAndSaveNote_NullEditorNote_ReturnsFailure() = runViewModelTest {
        val result = checkAndSaveNote()

        assertTrue(result is SaveNoteResult.Warning)
    }

    @Test
    fun buildClozeTextTest() = runViewModelTest {
        val text = "test"
        val result = buildClozeText(text)

        assertEquals("{{c1::test}}", result)
    }

    @Test
    fun `buildClozeText handles undo word`() = runViewModelTest {
        val text = "{{c1::Word}}"
        val result = buildClozeText(text)

        assertEquals("Word", result)
    }

    @Test
    fun testExtractWordsIncludingClozes() = runViewModelTest {
        val sentence = "This is a {{c1::test}} sentence with {{c2::multiple}} clozes."
        setClozeFieldText(sentence)
        val expectedWords = listOf("This", "is", "a", "{{c1::test}}", "sentence", "with", "{{c2::multiple}}", "clozes.")
        val extractedWords = getWordsFromFieldText()
        assertEquals(expectedWords, extractedWords)
    }

    @Test
    fun testExtractWordsIncludingPunctuations() = runViewModelTest {
        val sentence = "This is a {{c1::test}}!! sentence with {{c2::multiple}} clozes?"
        setClozeFieldText(sentence)
        val expectedWords = listOf("This", "is", "a", "{{c1::test}}!!", "sentence", "with", "{{c2::multiple}}", "clozes?")
        val extractedWords = getWordsFromFieldText()
        assertEquals(expectedWords, extractedWords)
    }

    @Test
    fun testGetCleanClozeWords() = runViewModelTest {
        val testCases = listOf(
            "{{c1::word}}" to "word",
            "{{c2::another}}" to "another",
            "{{c4::help}}!!" to "help!!",
            "no cloze" to "no cloze",
            "[{{c6::word}}]" to "[word]"
        )

        testCases.forEach { (input, expected) ->
            val cleanedWord = getCleanClozeWords(input)
            assertEquals(expected, cleanedWord)
        }
    }

    @Test
    fun `test words with internal punctuation`() = runViewModelTest {
        val text = "hello-world"
        val result = buildClozeText(text)

        assertEquals("{{c1::hello-world}}", result)
    }

    @Test
    fun `test words with internal underscore punctuation`() = runViewModelTest {
        val text = "hello_world"
        val result = buildClozeText(text)
        assertEquals("{{c1::hello_world}}", result)
    }

    @Test
    fun testSwitchingBetweenEditModes() = runViewModelTest {
        val word = "Word!"
        val expectedCloze = "{{c1::Word}}!"

        val result = buildClozeText(word)

        assertEquals(expectedCloze, result)

        val cleanWord = getCleanClozeWords(expectedCloze)

        assertEquals(word, cleanWord)
    }

    private fun runViewModelTest(
        initViewModel: () -> InstantEditorViewModel = { InstantEditorViewModel() },
        testBody: suspend InstantEditorViewModel.() -> Unit
    ) = runInstantEditorViewModelTest(initViewModel, testBody)

    private fun saveNoteResult(result: SaveNoteResult): String? {
        return when (result) {
            is SaveNoteResult.Failure -> result.message

            SaveNoteResult.Success -> {
                // It doesn't return a string in case of success hence we mimic that that the check was successful
                "Success"
            }

            is SaveNoteResult.Warning -> result.message
        }
    }

    companion object {
        fun TestClass.runInstantEditorViewModelTest(
            initViewModel: () -> InstantEditorViewModel = { InstantEditorViewModel() },
            testBody: suspend InstantEditorViewModel.() -> Unit
        ) = runTest {
            val viewModel = initViewModel()
            testBody(viewModel)
        }
    }
}

private fun InstantEditorViewModel.toggleAllClozeDeletions(words: MutableList<String>) {
    for (index in words.indices) {
        words[index] = buildClozeText(words[index])
    }
}

@Suppress("SameParameterValue")
private fun InstantEditorViewModel.toggleClozeDeletions(words: MutableList<String>, vararg indices: Int) {
    for (index in indices) {
        words[index] = buildClozeText(words[index])
    }
}

@Suppress("SameParameterValue")
private fun InstantEditorViewModel.toggleClozeDeletion(words: MutableList<String>, index: Int) {
    words[index] = buildClozeText(words[index])
}
