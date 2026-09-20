// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode
import com.ichi2.anki.common.utils.ext.addingDefaultsMode
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.getNoteEditorFragment
import com.ichi2.anki.noteeditor.openNoteEditorWithArgs
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteEditorDeckSelectionTest : RobolectricTest() {
    private fun openAdd(deckId: DeckId? = null) =
        startRegularActivity<NoteEditorActivity>(NoteEditorDestination.AddNote(deckId).toIntent()).getNoteEditorFragment()

    private fun useAddingDefaults(mode: AddingDefaultsMode) {
        ensureCollectionLoadIsSynchronous()
        col.config.addingDefaultsMode = mode
    }

    private fun share(text: String) =
        openNoteEditorWithArgs(
            Bundle().apply { putString(Intent.EXTRA_TEXT, text) },
            Intent.ACTION_SEND,
        )

    private fun NoteEditorFragment.select(did: DeckId) {
        onDeckSelected(SelectableDeck.Deck(did, col.decks.name(did)))
    }

    @Test
    @Ignore("Issue 22065: save task starts while activity is finishing")
    fun `successive shares use the study deck in current-deck mode`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            val a = addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val first = share("first share")
            val activity = first.requireActivity()
            first.select(b)
            first.saveNote()
            advanceRobolectricLooper()
            assertTrue(activity.isFinishing)
            assertEquals(listOf(b), col.findCards("").map { col.getCard(it).did })
            assertEquals(a, col.decks.selected())
            assertEquals(a, share("second share").deckId)
        }

    @Test
    @Ignore("Issue 22065: save task starts while activity is finishing")
    fun `successive shares remember the destination in note-type mode`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            val a = addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val first = share("first share")
            val ntid = first.editorNote!!.noteTypeId
            val activity = first.requireActivity()
            first.select(b)
            first.saveNote()
            advanceRobolectricLooper()
            assertTrue(activity.isFinishing)
            assertEquals(listOf(b), col.findCards("").map { col.getCard(it).did })
            assertEquals(a, col.decks.selected())
            val second = share("second share")
            assertEquals(ntid, second.editorNote!!.noteTypeId)
            assertEquals(b, second.deckId)
        }

    @Test
    fun `explicit Default deck takes precedence over the remembered deck`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            val b = addDeck("Remembered B", setAsSelected = true)
            col.addNote(col.newNote(col.notetypes.basic).apply { fields[0] = "saved" }, b)
            assertEquals(b, col.defaultsForAdding().deckId)
            assertEquals(1L, openAdd(1L).deckId)
        }

    // Preserve Android's existing launch validation. Anki's legacy desktop chooser instead uses
    // Default for an invalid explicit destination; this is not an upstream parity assertion.
    @Test
    fun `invalid explicit destination falls back to the remembered deck`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            addDeck("Study", setAsSelected = true)
            val rememberedDeck = addDeck("Remembered")
            col.addNote(col.newNote(col.notetypes.basic).apply { fields[0] = "existing" }, rememberedDeck)
            val filteredDeck = addDynamicDeck("Filtered")
            val missingDeck = addDeck("Deleted")
            col.decks.remove(listOf(missingDeck))

            for (invalidDeck in listOf(filteredDeck, missingDeck)) {
                val editor = openAdd(invalidDeck)
                assertEquals(rememberedDeck, editor.deckId)
                editor.requireActivity().finish()
            }
        }

    @Test
    fun `switching to a note type without history preserves the chosen deck`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val reversed = col.notetypes.basicAndReversed
            assertNull(col.defaultDeckForNoteType(reversed.id))
            val editor = openAdd()
            editor.select(b)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()
            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            assertEquals(b, editor.deckId)
        }

    @Test
    fun `deleted remembered deck does not replace the editor destination on a note-type change`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            addDeck("Study", setAsSelected = true)
            val destination = addDeck("Destination")
            val deletedDeck = addDeck("Deleted")
            val reversed = col.notetypes.basicAndReversed
            col.addNote(col.newNote(reversed).apply { fields[0] = "existing" }, deletedDeck)
            col.decks.remove(listOf(deletedDeck))
            col.notetypes.setCurrent(col.notetypes.basic)
            assertNull(col.defaultDeckForNoteType(reversed.id))

            val editor = openAdd()
            editor.select(destination)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()

            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            assertEquals(destination, editor.deckId)
        }

    @Test
    fun `filtered remembered deck does not replace the editor destination on a note-type change`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            val filteredDeck = addDynamicDeck("Filtered")
            val studyDeck = addDeck("Study", setAsSelected = true)
            val destination = addDeck("Destination")
            val reversed = col.notetypes.basicAndReversed
            // addNote records the requested destination even when card generation uses a fallback.
            col.addNote(col.newNote(reversed).apply { fields[0] = "existing" }, filteredDeck)
            col.notetypes.setCurrent(col.notetypes.basic)
            assertNull(col.defaultDeckForNoteType(reversed.id))

            val editor = openAdd()
            editor.select(destination)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()

            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            assertEquals(destination, editor.deckId)
            assertEquals(studyDeck, col.decks.selected())
        }

    @Test
    fun `switching note types in current-deck mode preserves the chosen deck despite remembered history`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            val studyDeck = addDeck("Study", setAsSelected = true)
            val destination = addDeck("Destination")
            val rememberedDeck = addDeck("Remembered")
            val reversed = col.notetypes.basicAndReversed
            col.addNote(col.newNote(reversed).apply { fields[0] = "existing" }, rememberedDeck)
            assertEquals(rememberedDeck, col.defaultDeckForNoteType(reversed.id))
            col.config.addingDefaultsMode = AddingDefaultsMode.USE_CURRENT_DECK
            col.notetypes.setCurrent(col.notetypes.basic)

            val editor = openAdd()
            editor.select(destination)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()

            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            assertEquals(destination, editor.deckId)
            assertEquals(studyDeck, col.decks.selected())
        }

    @Test
    fun `canceling a note-type change preserves the saved deck default`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            val a = addDeck("Study A", setAsSelected = true)
            val basic = col.notetypes.basic
            col.addNote(col.newNote(basic).apply { fields[0] = "saved basic" }, a)
            val editor = openAdd()
            editor.setCurrentlySelectedNoteType(col.notetypes.basicAndReversed.id)
            advanceRobolectricLooper()
            editor.requireActivity().finish()
            assertEquals(basic.id, col.defaultsForAdding().notetypeId)
            assertEquals(1, col.noteCount())
            assertEquals(basic.id, openAdd().editorNote!!.noteTypeId)
        }

    @Test
    fun `initial deck and note type match backend defaults after a collection add`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            val a = addDeck("Study A", setAsSelected = true)
            val b = addDeck("Add B")
            val editor = openAdd()
            editor.setCurrentlySelectedNoteType(col.notetypes.basicAndReversed.id)
            advanceRobolectricLooper()
            editor.setFieldValueFromUi(0, "reversed in A")
            editor.saveNote()
            advanceRobolectricLooper()
            editor.requireActivity().finish()
            val basic = col.notetypes.basic
            col.addNote(col.newNote(basic).apply { fields[0] = "basic in B via backend" }, b)
            assertEquals(a, col.decks.selected())
            val expected = col.defaultsForAdding()
            assertEquals(b, expected.deckId)
            assertEquals(basic.id, expected.notetypeId)
            val next = openAdd()
            assertEquals(expected.deckId, next.deckId)
            assertEquals(expected.notetypeId, next.editorNote!!.noteTypeId)
        }

    @Test
    fun `canceling a note-type change preserves the last added note type`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE)
            val studyDeck = addDeck("Study", setAsSelected = true)
            val rememberedDeck = addDeck("Remembered")
            val basic = col.notetypes.basic
            col.addNote(col.newNote(basic).apply { fields[0] = "saved" }, rememberedDeck)

            val editor = openAdd()
            editor.setCurrentlySelectedNoteType(col.notetypes.basicAndReversed.id)
            advanceRobolectricLooper()
            editor.requireActivity().finish()

            val next = openAdd()
            assertEquals(basic.id, next.editorNote!!.noteTypeId)
            assertEquals(rememberedDeck, next.deckId)
            assertEquals(studyDeck, col.decks.selected())
            assertEquals(1, col.noteCount())
        }

    @Test
    fun `saving retains the open editor selections when the study deck has another note type`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            val studyDeck = addDeck("Study", setAsSelected = true)
            val destination = addDeck("Destination")
            val basic = col.notetypes.basic
            val reversed = col.notetypes.basicAndReversed
            col.addNote(col.newNote(basic).apply { fields[0] = "existing" }, studyDeck)

            val editor = openAdd()
            editor.select(destination)
            editor.setCurrentlySelectedNoteType(reversed.id)
            advanceRobolectricLooper()
            assertEquals(destination, editor.deckId)
            editor.setFieldValueFromUi(0, "new note")
            editor.saveNote()
            advanceRobolectricLooper()

            assertFalse(editor.requireActivity().isFinishing)
            assertEquals(destination, editor.deckId)
            assertEquals(reversed.id, editor.editorNote!!.noteTypeId)
            val defaults = col.defaultsForAdding()
            assertEquals(studyDeck, defaults.deckId)
            assertEquals(basic.id, defaults.notetypeId)
            assertEquals(basic.id, openAdd().editorNote!!.noteTypeId)
        }

    @Test
    fun `recreation preserves an unsaved note type and its fields`() =
        runTest {
            useAddingDefaults(AddingDefaultsMode.USE_CURRENT_DECK)
            val studyDeck = addDeck("Study", setAsSelected = true)
            val basic = col.notetypes.basic
            val cloze = col.notetypes.cloze
            col.addNote(col.newNote(basic).apply { fields[0] = "existing" }, studyDeck)
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    NoteEditorActivity::class.java,
                    NoteEditorDestination.AddNote().toIntent(),
                )
            val editor = controller.get().getNoteEditorFragment()
            editor.setCurrentlySelectedNoteType(cloze.id)
            advanceRobolectricLooper()
            editor.setFieldValueFromUi(0, "{{c1::unsaved cloze}}")

            controller.recreate()
            advanceRobolectricLooper()

            val restored = controller.get().getNoteEditorFragment()
            assertEquals(cloze.id, restored.editorNote!!.noteTypeId)
            assertEquals("{{c1::unsaved cloze}}", restored.currentFieldStrings[0])
            assertEquals(basic.id, col.defaultsForAdding().notetypeId)
            assertEquals(1, col.noteCount())
        }
}
