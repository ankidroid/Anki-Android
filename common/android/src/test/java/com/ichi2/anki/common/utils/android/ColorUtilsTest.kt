/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.common.utils.android

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests for [darkenColor] and [lightenColorAbsolute] */
@RunWith(AndroidJUnit4::class)
class ColorUtilsTest {
    @Test
    fun darkenColor_withNoChange_returnsSameColor() {
        val white = Color.WHITE
        assertEquals(white, darkenColor(white, factor = 1.0f))
    }

    @Test
    fun darkenColor_withFullDarken_returnsBlack() {
        val white = Color.WHITE
        assertEquals(Color.BLACK, darkenColor(white, factor = 0.0f))
    }

    @Test
    fun lightenColorAbsolute_withNoChange_returnsSameColor() {
        val red = Color.RED
        assertEquals(red, lightenColorAbsolute(red, amount = 0.0f))
    }
}
