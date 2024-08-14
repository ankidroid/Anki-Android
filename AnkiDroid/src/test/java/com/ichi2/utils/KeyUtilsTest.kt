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

package com.ichi2.utils

import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_0
import android.view.KeyEvent.KEYCODE_9
import android.view.KeyEvent.KEYCODE_ENDCALL
import android.view.KeyEvent.KEYCODE_STAR
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.KeyUtils.getDigit
import com.ichi2.utils.KeyUtils.isDigit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class KeyUtilsTest {
    @Test
    fun testIsDigit() {
        assertTrue(isDigit(KeyEvent(ACTION_UP, KEYCODE_0)))
        assertTrue(isDigit(KeyEvent(ACTION_UP, KEYCODE_9)))
        assertFalse(isDigit(KeyEvent(ACTION_UP, KEYCODE_STAR)))
        assertFalse(isDigit(KeyEvent(ACTION_UP, KEYCODE_ENDCALL)))
    }

    @Test
    fun testGetDigit() {
        assertEquals(0, getDigit(KeyEvent(ACTION_UP, KEYCODE_0)))
        assertEquals(9, getDigit(KeyEvent(ACTION_UP, KEYCODE_9)))
        assertEquals(null, getDigit(KeyEvent(ACTION_UP, KEYCODE_STAR)))
        assertEquals(null, getDigit(KeyEvent(ACTION_UP, KEYCODE_ENDCALL)))
    }
}
