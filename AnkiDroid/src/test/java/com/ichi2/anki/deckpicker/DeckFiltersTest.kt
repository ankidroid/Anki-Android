/*
 *  Copyright (c) 2024 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.deckpicker

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.deckpicker.DeckFilters.DeckFilter.Companion.containsAtPosition
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class) // This is necessary, android and JVM differ on JSONObject.NULL
@Config(application = EmptyApplication::class)
class DeckFiltersTest : AndroidTest {
    val oneDecksFilterText = "u"
    val twoDecksFilterText = "o::ba"
    val partialFilterTextOne = "u:"
    val partialFilterTextTwo = "u::"
    val twoDecksFilter = DeckFilters.DeckFilter(twoDecksFilterText)
    val oneDecksFilter = DeckFilters.DeckFilter(oneDecksFilterText)
    val partialFilterOne = DeckFilters.DeckFilter(partialFilterTextOne)
    val partialFilterTwo = DeckFilters.DeckFilter(partialFilterTextTwo)
    val filters = DeckFilters(listOf(oneDecksFilter, twoDecksFilter))
    val noSearches = DeckFilters(listOf())
    val partialFiltersOne = DeckFilters(listOf(partialFilterOne, twoDecksFilter))
    val partialFiltersTwo = DeckFilters(listOf(partialFilterTwo, twoDecksFilter))

    @Test
    fun testSearching() {
        assertTrue(filters.searching())
        assertFalse(noSearches.searching())
    }

    @Test
    fun testContainsAtPosition() {
        assertTrue(containsAtPosition(twoDecksFilterText, 1, "foo::bar", 3))
        assertTrue(containsAtPosition(twoDecksFilterText, 1, "foo::ba", 3))
        assertTrue(containsAtPosition(twoDecksFilterText, 1, "o::bar", 1))
        assertTrue(containsAtPosition(twoDecksFilterText, 1, "o::ba", 1))

        // Wrong position
        assertFalse(containsAtPosition(twoDecksFilterText, 1, "foo::bar", 1))
        assertFalse(containsAtPosition(twoDecksFilterText, 1, "foo::bar", 5))
        assertFalse(containsAtPosition(twoDecksFilterText, 1, "foo::bar", 5))

        // Not enough characters in containing
        assertFalse(containsAtPosition(twoDecksFilterText, 1, "foo::", 3))
        assertFalse(containsAtPosition(twoDecksFilterText, 1, "::bar", 0))
    }

    @Test
    fun testDeckLastNameMatchesFilter() {
        assertTrue(twoDecksFilter.deckLastNameMatchesFilter("foo::bar"))
        assertFalse(twoDecksFilter.deckLastNameMatchesFilter("foo::bar::buz"))
        assertFalse(twoDecksFilter.deckLastNameMatchesFilter("foo::buz::bar"))
        assertFalse(twoDecksFilter.deckLastNameMatchesFilter("foo::b"))

        assertFalse(oneDecksFilter.deckLastNameMatchesFilter("foo::bar"))
        assertTrue(oneDecksFilter.deckLastNameMatchesFilter("foo::bar::buz"))
        assertFalse(oneDecksFilter.deckLastNameMatchesFilter("foo::bu::bar"))
        assertFalse(oneDecksFilter.deckLastNameMatchesFilter("foo::b"))

        assertFalse(partialFilterOne.deckLastNameMatchesFilter("foo::bar"))
        assertTrue(partialFilterOne.deckLastNameMatchesFilter("foo::bar::bu"))
        assertFalse(partialFilterOne.deckLastNameMatchesFilter("foo::bu::bar"))
        assertFalse(partialFilterOne.deckLastNameMatchesFilter("foo::b"))

        assertFalse(partialFilterTwo.deckLastNameMatchesFilter("foo::bar"))
        assertFalse(partialFilterTwo.deckLastNameMatchesFilter("foo::bar::bu"))
        assertTrue(partialFilterTwo.deckLastNameMatchesFilter("foo::bar::bu::plop"))
    }

    @Test
    fun testDeckLastNameMatchesAFilter() {
        assertTrue(filters.deckLastNameMatchesAFilter("foo::bar"))
        assertFalse(filters.deckLastNameMatchesAFilter("foo::buz::bar"))
        assertTrue(filters.deckLastNameMatchesAFilter("foo::bar::buz"))
        assertTrue(filters.deckLastNameMatchesAFilter("buz::foo::bar"))
        assertFalse(filters.deckLastNameMatchesAFilter("buz::foo::b"))
    }

    @Test
    fun testDeckNameMatchesFilter() {
        assertTrue(oneDecksFilter.deckNameMatchesFilter("foo::buz::bar"))
        assertTrue(oneDecksFilter.deckNameMatchesFilter("auie"))
        assertFalse(oneDecksFilter.deckNameMatchesFilter("aie"))

        assertTrue(partialFilterOne.deckNameMatchesFilter("foo::bu::plop"))
        assertTrue(partialFilterOne.deckNameMatchesFilter("foo::bu"))
    }

    @Test
    fun testDeckNamesMatchFilter() {
        assertFalse(filters.deckNamesMatchFilters("foo::buz::bar"))
        assertTrue(filters.deckNamesMatchFilters("foo::bar::buz"))
        assertTrue(filters.deckNamesMatchFilters("buz::foo::bar"))
        assertFalse(filters.deckNamesMatchFilters("buz::foo::b"))
        assertFalse(filters.deckNamesMatchFilters("foo::bar::bz"))
    }

    @Test
    fun testAccept() {
        // Accepts exactly decks that either:
        // * ends with a deck containing "u", and another position contains "o::ba"
        // * ends with a deck starting with "ba", and second to last decks ends with "o", and some position contains a u
        assertTrue(noSearches.accept("foo"))
        assertTrue(filters.accept("foo::bar::buz"))
        assertFalse(filters.accept("foo::bar::buz::plop"))
        assertTrue(filters.accept("buz::foo::bar"))
        assertTrue(filters.accept("buz::plop::foo::bar"))
        assertFalse(filters.accept("buz::plop::foo::plop::bar"))
        assertFalse(filters.accept("buz"))

        // Accepts exactly decks that either:
        // * ends with a deck ending in "u", and another position contains "o::ba"
        // * ends with a deck starting with "ba", and second to last decks ends with "o", and some parent deck name ends with a "u"
        assertTrue(partialFiltersOne.accept("bu::foo::bar"))
        assertFalse(partialFiltersOne.accept("buz::foo::bar"))
        assertTrue(partialFiltersOne.accept("foo::bar::bu"))
        assertFalse(partialFiltersOne.accept("foo::bar::buz"))

        // Accepts exactly decks that either:
        // * second to last deck ends with a "u", and another position contains "o::ba"
        // * ends with a deck starting with "ba", and second to last decks ends with "o", and some parent deck name ends with a "u"
        assertFalse(partialFiltersTwo.accept("foo::bar::bu"))
        assertFalse(partialFiltersOne.accept("foo::bar::buz"))
        assertTrue(partialFiltersTwo.accept("foo::bar::bu::plop"))
        assertTrue(partialFiltersTwo.accept("bu::foo::bar"))
    }
}
