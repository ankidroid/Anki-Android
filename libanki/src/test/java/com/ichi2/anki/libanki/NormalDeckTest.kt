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

import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.FilteredDeck
import com.ichi2.anki.libanki.RegularDeck
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import kotlin.test.assertEquals

class NormalDeckTest : InMemoryAnkiTest() {
    val d = RegularDeck("""{"dyn": 1}""")

    @Test
    fun testConfId() {
        val confId = 42L
        d.conf = confId
        assertEquals(confId, d.conf)
    }

    @Test
    fun testNoteTypeId() {
        val noteTypeId = 42L
        d.noteTypeId = noteTypeId
        assertEquals(noteTypeId, d.noteTypeId)
    }

    @Test
    fun testFactory() {
        val d = Deck.factory("""{"dyn": 1}""")
        assertInstanceOf<FilteredDeck>(d)
    }
}
