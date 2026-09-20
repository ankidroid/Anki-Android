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
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
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
}
