/*
 *  Copyright (c) 2024 Abd-Elrahman Esam <abdelrahmanesam20000@gmail.com>
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
package com.ichi2.anki.utils

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.KeyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class KeyUtilsTest {

    @Test
    fun testIsDigitWithValidDigitShouldReturnTrue() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_5)
        assertTrue(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testIsDigitWithUnValidDigitShouldReturnFalse() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        assertFalse(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testIsDigitWithNonLanguageKeyShouldReturnFalse() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        assertFalse(KeyUtils.isDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithValidDigitShouldReturnThisDigit() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_7)
        assertEquals(7, KeyUtils.getDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithNonDigitShouldReturnNull() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        assertNull(KeyUtils.getDigit(keyEvent))
    }

    @Test
    fun testGetDigitWithNonLanguageKeyShouldReturnNull() {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        assertNull(KeyUtils.getDigit(keyEvent))
    }
}
