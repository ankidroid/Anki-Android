/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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

package com.ichi2.anki.cardviewer

import com.ichi2.anki.cardviewer.CardAppearance.Companion.hasUserDefinedNightMode
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.testutils.assertFalse
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import kotlin.test.junit5.JUnit5Asserter.assertTrue

class CardAppearanceTest {

    @Test
    fun hasUserDefinedNightModeTest() {
        val mockCard = Mockito.mock(Card::class.java)
        val col: Collection = Mockito.mock()
        doReturn(".night_mode {}").whenever(mockCard).css(col)
        assertTrue("CSS should have a night mode class", hasUserDefinedNightMode(col, mockCard))

        doReturn(".nightMode{}").whenever(mockCard).css(col)
        assertTrue("CSS should have a night mode class", hasUserDefinedNightMode(col, mockCard))

        doReturn(".night_mode_old {}").whenever(mockCard).css(col)
        assertFalse("CSS should not have a night mode class", hasUserDefinedNightMode(col, mockCard))
    }
}
