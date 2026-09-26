/****************************************************************************************
 * Copyright (c) 2025 Arthur Milchior <arthur@milchior.fr>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.json.JSONArray
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckTest : InMemoryAnkiTest() {
    val d = Deck("{}")

    @Test
    fun testFiltered() {
        // `dyn` can't be set by the front-end anymore.
        val d = Deck("""{"dyn" :1}""")
        assertTrue(d.isFiltered)
        assertFalse(d.isNormal, "This deck should not be normal")
    }

    @Test
    fun testNormal() {
        val d = Deck("""{"dyn" :0}""")
        assertTrue(d.isNormal)
        assertFalse(d.isFiltered, "this deck should not be filtered")
    }

    @Test
    fun testName() {
        val name = "foo"
        d.name = name
        assertEquals(name, d.name)
    }

    @Test
    fun testBrowserCollapsed() {
        d.browserCollapsed = true
        assertTrue(d.browserCollapsed)
        d.browserCollapsed = false
        assertFalse(d.browserCollapsed, "browser should be collapsed")
    }

    @Test
    fun testCollapsed() {
        d.collapsed = true
        assertTrue(d.collapsed)
        d.collapsed = false
        assertFalse(d.collapsed, "deck should be collapsed")
    }

    @Test
    fun testId() {
        val id = 42L
        d.id = id
        assertEquals(id, d.id)
    }

    @Test
    fun testConfId() {
        val confId = 42L
        d.conf = confId
        assertEquals(confId, d.conf)
    }

    @Test
    fun testDescription() {
        val description = "foo"
        d.description = description
        assertEquals(description, d.description)
    }

    @Test
    fun testNoteTypeId() {
        val noteTypeId = 42L
        d.noteTypeId = noteTypeId
        assertEquals(noteTypeId, d.noteTypeId)
    }

    @Test
    fun testResched() {
        d.resched = true
        assertTrue(d.resched)
        d.resched = false
        assertTrue(!d.resched)
    }

    @Test
    fun testPreview() {
        val again = 1
        val hard = 2
        val good = 3
        d.previewAgainSecs = again
        d.previewHardSecs = hard
        d.previewGoodSecs = good
        assertEquals(d.previewAgainSecs, again)
        assertEquals(d.previewHardSecs, hard)
        assertEquals(d.previewGoodSecs, good)
    }

    @Test
    fun testDelays() {
        val delays = JSONArray()
        delays.put("1h")
        delays.put("1m")
        assertEquals(d.delays, null)
        d.delays = delays
        assertEquals(d.delays, delays)
        d.delays = null
        assertEquals(d.delays, null)
    }

    @Test
    fun testEmpty() {
        val d = Deck("{empty: 4}")
        d.removeEmpty()
        // The property empty can be edited but never read in the frontend.
        assertFalse(d.jsonObject.has("empty"), "Empty should be removed")
    }

    val search = "search"
    val limit = 7
    val order = 42
    val t = Deck.Term(search, limit, order)

    val search2 = "search2"
    val limit2 = 7
    val order2 = 44
    val t2 = Deck.Term(search2, limit2, order2)

    @Test
    fun testSearch() {
        val expectedSearch = "expectedSearch"
        t.search = expectedSearch
        assertEquals(expectedSearch, t.search)
    }

    @Test
    fun testLimit() {
        val expectedLimit = 7
        t.limit = expectedLimit
        assertEquals(expectedLimit, t.limit)
    }

    @Test
    fun testOrder() {
        val expectedOrder = 7
        t.order = expectedOrder
        assertEquals(expectedOrder, t.order)
    }

    @Test
    fun testFirstFilter() {
        // All decks are expected to have at least one term.
        val d = Deck("""{"terms": [$t]}""")
        val firstFilter = d.firstFilter
        assertEquals(firstFilter.search, search)
        assertEquals(firstFilter.limit, limit)
        assertEquals(firstFilter.order, order)
    }

    @Test
    fun testSecondFilter() {
        val d = Deck("""{"terms": [$t]}""")
        assertEquals(null, d.secondFilter)
        d.secondFilter = t2
        assertEquals(t2, d.secondFilter)
        d.secondFilter = null
        assertEquals(null, d.secondFilter)
    }
}
