// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.model.CardsOrNotes
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for extensions to [com.ichi2.anki.libanki.Config]
 */
class ConfigTest : InMemoryAnkiTest() {
    @Test
    fun `legacy settings retain their missing and invalid value defaults`() {
        val keys =
            listOf(
                "curDeck",
            )

        fun assertDefaults() {
            assertEquals(1L, col.config.currentDeckId)
        }

        keys.forEach { col.config.remove(it) }
        assertDefaults()
        keys.forEach { col.config.set(it, listOf("invalid")) }
        assertDefaults()
    }

    @Test
    fun `legacy settings observe values changed outside the accessors`() {
        col.config.set("curDeck", 123L)

        assertEquals(123L, col.config.currentDeckId)
    }

    @Test
    fun `browser mode helpers use the mapped config property`() {
        CardsOrNotes.NOTES.saveToCollection(col)
        assertEquals(CardsOrNotes.NOTES, col.config.cardsOrNotes)
        assertEquals(CardsOrNotes.NOTES, CardsOrNotes.fromCollection(col))

        col.config.cardsOrNotes = CardsOrNotes.CARDS
        assertEquals(CardsOrNotes.CARDS, CardsOrNotes.fromCollection(col))
    }

    @Test
    fun `adding defaults mode uses the backend default`() {
        col.config.remove("addToCur")

        assertEquals(AddingDefaultsMode.USE_CURRENT_DECK, col.config.addingDefaultsMode)
    }

    @Test
    fun `adding defaults mode changes the backend destination`() {
        val studyDeck = addDeck("Study", setAsSelected = true)
        val destination = addDeck("Destination")
        col.addNote(col.newNote(col.notetypes.basic).apply { fields[0] = "saved" }, destination)

        col.config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
        assertEquals(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE, col.config.addingDefaultsMode)
        assertEquals(destination, col.defaultsForAdding().deckId)

        col.config.addingDefaultsMode = AddingDefaultsMode.USE_CURRENT_DECK
        assertEquals(AddingDefaultsMode.USE_CURRENT_DECK, col.config.addingDefaultsMode)
        assertEquals(studyDeck, col.defaultsForAdding().deckId)
        assertEquals(studyDeck, col.decks.selected())
    }

    @Test
    fun `test non-diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("uber").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("uber").size)
    }

    @Test
    fun `test diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("über").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("über").size)
    }

    @Test
    fun `test Japanese input`() {
        addBasicNote("は")
        addBasicNote("ば")
        addBasicNote("ぱ")

        assertEquals(1, col.findCards("は").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("は").size)
    }
}
