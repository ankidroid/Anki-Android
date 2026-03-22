/*
 *  Copyright (c) 2026 Mandeep Thakkar
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 */

package com.ichi2.widget.deckpicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/* This Tests are for [DeckWidgetData] to ensure deck data
 * is correctly stored and retrieved for widget display.
 *
 * Here it Covers three different cases:
 * 1)Standard deck with realistic card count values
 * 2)Checking it can identify empty deck
 * 3)Checking it can identify non empty deck
 */

class DeckPickerWidgetTest {
    @Test
    fun `deck widget data stores correct values`() {
        val data =
            DeckWidgetData(
                deckId = 1L,
                name = "Chemistry",
                reviewCount = 10,
                learnCount = 5,
                newCount = 20,
            )
        assertEquals("Chemistry", data.name)
        assertEquals(10, data.reviewCount)
        assertEquals(5, data.learnCount)
        assertEquals(20, data.newCount)
        assertEquals(1L, data.deckId)
    }

    @Test
    fun `empty deck is correctly identified`() {
        val data =
            DeckWidgetData(
                deckId = 1L,
                name = "Empty Deck",
                reviewCount = 0,
                learnCount = 0,
                newCount = 0,
            )
        val isEmpty =
            data.newCount == 0 &&
                data.reviewCount == 0 &&
                data.learnCount == 0
        assertTrue(isEmpty)
    }

    @Test
    fun `non empty deck is correctly identified`() {
        val data =
            DeckWidgetData(
                deckId = 2L,
                name = "Active Deck",
                reviewCount = 5,
                learnCount = 3,
                newCount = 10,
            )
        val isEmpty =
            data.newCount == 0 &&
                data.reviewCount == 0 &&
                data.learnCount == 0
        assertFalse(isEmpty)
    }
}
