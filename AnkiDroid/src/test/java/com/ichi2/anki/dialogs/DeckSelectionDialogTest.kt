/*
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.SelectableDeck
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckSelectionDialogTest : RobolectricTest() {
    @Test
    fun verifyDeckDisplayName() {
        val input = "deck::sub-deck::sub-deck2::sub-deck3"
        val expected = "sub-deck3"

        val deck = SelectableDeck.Deck(1234, input)
        val actual: String = deck.getDisplayName(targetContext)

        assertThat(actual, Matchers.equalTo(expected))
    }

    @Test
    fun testDialogCreation() {
        val decks: List<SelectableDeck> = listOf(SelectableDeck.Deck(5L, "deck"))
        val dialogTitle = "Select Deck"
        val summaryMessage = "Choose a deck from the list"
        val keepRestoreDefaultButton = true

        val dialog = DeckSelectionDialog.newInstance(dialogTitle, summaryMessage, keepRestoreDefaultButton, decks)
        assertNotNull(dialog)
        assertEquals(dialogTitle, dialog.arguments?.getString("title"))
        assertEquals(summaryMessage, dialog.arguments?.getString("summaryMessage"))
    }

    @Test
    fun `decks are sorted alphabetically in main list`() {
        val unsorted =
            listOf(
                SelectableDeck.Deck(3L, "Zebra"),
                SelectableDeck.Deck(1L, "Apple"),
                SelectableDeck.Deck(2L, "Mango"),
            )
        val sorted = unsorted.sortedWith(compareBy { it.name.lowercase() })

        assertEquals("Apple", sorted[0].name)
        assertEquals("Mango", sorted[1].name)
        assertEquals("Zebra", sorted[2].name)
    }

    @Test
    fun `deck sorting is case insensitive`() {
        val unsorted =
            listOf(
                SelectableDeck.Deck(1L, "zebra"),
                SelectableDeck.Deck(2L, "Apple"),
                SelectableDeck.Deck(3L, "mango"),
            )
        val sorted = unsorted.sortedWith(compareBy { it.name.lowercase() })

        assertEquals("Apple", sorted[0].name)
        assertEquals("mango", sorted[1].name)
        assertEquals("zebra", sorted[2].name)
    }
}
