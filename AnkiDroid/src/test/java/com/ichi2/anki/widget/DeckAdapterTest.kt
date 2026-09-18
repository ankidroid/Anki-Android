/*
 *  Copyright (c) 2026 Vedant Kakade <vedantkakade05@gmail.com>
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
package com.ichi2.anki.widget

import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.deckpicker.DeckFilters
import com.ichi2.anki.deckpicker.filterAndFlattenDisplay
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.anki.withDeckPicker
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckAdapterTest : RobolectricTest() {
    @Test
    fun ensureDeckSelectionUpdatesCorrectly() {
        val deck1Id = addDeck("Deck 1")
        val deck2Id = addDeck("Deck 2")
        val deck3Id = addDeck("Deck 3")

        val node =
            col.sched.deckDueTree().filterAndFlattenDisplay(
                DeckFilters.create(""),
                deck1Id,
            )

        assertTrue(node.first { it.did == deck1Id }.isSelected)

        val afterDeck2 = node.map { it.withUpdatedDeckId(deck2Id) }
        assertFalse(actual = afterDeck2.first { it.did == deck1Id }.isSelected)
        assertTrue(actual = afterDeck2.first { it.did == deck2Id }.isSelected)

        val afterDeck3 = afterDeck2.map { it.withUpdatedDeckId(deck3Id) }
        assertFalse(actual = afterDeck3.first { it.did == deck2Id }.isSelected)
        assertTrue(actual = afterDeck3.first { it.did == deck3Id }.isSelected)
    }

    @Test
    fun `selecting a pressed deck does not start a new ripple`() {
        val initiallySelected = addDeck("Initially selected")
        val pressedDeck = addDeck("Pressed")
        col.decks.select(initiallySelected)

        withDeckPicker(deckCount = 0) { deckPicker ->
            val adapter = deckPicker.deckPickerBinding.decks.adapter as DeckAdapter
            val pressedRow = deckPicker.deckHolder(pressedDeck).itemView
            pressedRow.isPressed = true
            val originalRipple = pressedRow.background

            adapter.updateSelectedDeck(pressedDeck)
            advanceRobolectricLooperUntil { adapter.currentList.single { it.did == pressedDeck }.isSelected }

            val selectedBackground = deckPicker.deckHolder(pressedDeck).itemView.background
            // A replacement drawable must not inherit the press and start a second ripple.
            assertTrue(
                selectedBackground === originalRipple || android.R.attr.state_pressed !in selectedBackground.state,
                "Selecting a pressed deck restarted its ripple",
            )
        }
    }

    @Test
    fun `toggling subdecks preserves the arrow view and updates its icon`() {
        val parentDeck = addDeck("Parent")
        addDeck("Parent::Child")
        col.decks.select(parentDeck)

        withDeckPicker(deckCount = 0) { deckPicker ->
            val adapter = deckPicker.deckPickerBinding.decks.adapter as DeckAdapter
            val parent = deckPicker.deckHolder(parentDeck)
            val changePayloads = mutableListOf<Any?>()
            adapter.observeItemRangeChanges { _, _, payload -> changePayloads.add(payload) }

            repeat(2) {
                val wasCollapsed = adapter.currentList.single { it.did == parentDeck }.collapsed
                parent.binding.deckExpander.performClick()
                advanceRobolectricLooperUntil {
                    adapter.currentList.single { it.did == parentDeck }.collapsed != wasCollapsed
                }

                assertTrue(changePayloads.isNotEmpty())
                assertTrue(changePayloads.all { it != null }, "A full row update interrupts the arrow ripple")
                assertSame(parent, deckPicker.deckHolder(parentDeck))
                assertEquals(
                    deckPicker.getString(if (wasCollapsed) R.string.collapse else R.string.expand),
                    parent.binding.deckExpander.contentDescription,
                )
                changePayloads.clear()
            }
        }
    }

    private fun DeckPicker.deckHolder(deckId: DeckId): DeckAdapter.ViewHolder {
        val decks = deckPickerBinding.decks
        val adapter = decks.adapter as DeckAdapter
        val position = adapter.currentList.indexOfFirst { it.did == deckId }
        return decks.findViewHolderForAdapterPosition(position) as DeckAdapter.ViewHolder
    }

    private fun RecyclerView.Adapter<*>.observeItemRangeChanges(listener: (Int, Int, Any?) -> Unit) {
        registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeChanged(
                    positionStart: Int,
                    itemCount: Int,
                    payload: Any?,
                ) {
                    listener(positionStart, itemCount, payload)
                }
            },
        )
    }
}
