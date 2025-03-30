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
package com.ichi2.libanki

import com.ichi2.libanki.Deck.Companion.DYN
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.assertFalse
import org.json.JSONArray
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckTest : JvmTest() {
    val d = Deck.factory("""{$DYN: 0}""")

    @Test
    fun testFiltered() {
        // `dyn` can't be set by the front-end anymore.
        val d = Deck.factory("""{$DYN :1}""")
        assertTrue(d.isFiltered)
        assertFalse("This deck should not be normal", d.isRegular)
    }

    @Test
    fun testNormal() {
        val d = Deck.factory("""{$DYN :0}""")
        assertTrue(d.isRegular)
        assertFalse("this deck should not be filtered", d.isFiltered)
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
        assertFalse("browser should be collapsed", d.browserCollapsed)
    }

    @Test
    fun testCollapsed() {
        d.collapsed = true
        assertTrue(d.collapsed)
        d.collapsed = false
        assertFalse("deck should be collapsed", d.collapsed)
    }

    @Test
    fun testId() {
        val id = 42L
        d.id = id
        assertEquals(id, d.id)
    }

    @Test
    fun testDescription() {
        val description = "foo"
        d.description = description
        assertEquals(description, d.description)
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
}
