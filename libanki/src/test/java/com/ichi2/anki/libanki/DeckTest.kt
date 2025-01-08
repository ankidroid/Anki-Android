// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeckTest : InMemoryAnkiTest() {
    val d = Deck("{}")

    @Test
    fun `optional properties retain legacy defaults`() {
        assertFalse(d.browserCollapsed)
        assertFalse(d.descriptionAsMarkdown)
        assertEquals("", d.description)
        assertEquals(1L, d.conf)
        for (value in listOf(JSONObject.NULL, 0L, -1L, "invalid")) {
            d.jsonObject.put("conf", value)
            assertEquals(1L, d.conf)
        }
    }

    @Test
    fun `wrapping JSON preserves shared mutations`() {
        val json = JSONObject("""{"name":"before"}""")
        val deck = Deck(json)
        assertSame(json, deck.jsonObject)
        deck.name = "after"
        assertEquals("after", json.getString("name"))
        json.put("name", "updated")
        assertEquals("updated", deck.name)
    }

    @Test
    fun `deck serialization preserves unwrapped legacy fields`() {
        // Legacy learning steps are numbers in minutes, not strings such as "1h".
        val deck = Deck("""{"delays":[60,1],"previewAgainSecs":60,"previewHardSecs":600,"previewGoodSecs":0}""")
        deck.name = "Filtered"
        deck.resched = false
        val serialized = JSONObject(BackendUtils.toJsonBytes(deck).toStringUtf8())
        assertEquals(deck.jsonObject.toString(), deck.toString())
        assertEquals("Filtered", serialized.get("name"))
        assertEquals(false, serialized.get("resched"))
        assertEquals(60, serialized.get("previewAgainSecs"))
        assertEquals(600, serialized.get("previewHardSecs"))
        assertEquals(0, serialized.get("previewGoodSecs"))
        assertEquals("[60,1]", serialized.getJSONArray("delays").toString())
    }

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
}
