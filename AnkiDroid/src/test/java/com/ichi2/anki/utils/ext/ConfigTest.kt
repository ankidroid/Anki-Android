// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode
import com.ichi2.anki.common.utils.ext.addingDefaultsMode
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Tests for extensions to [com.ichi2.anki.libanki.Config]
 */
@RunWith(AndroidJUnit4::class)
class ConfigTest : RobolectricTest() {
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
