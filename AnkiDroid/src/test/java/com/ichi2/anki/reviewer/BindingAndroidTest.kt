/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewer.Binding.ModifierKeys.alt
import com.ichi2.anki.reviewer.Binding.ModifierKeys.ctrl
import com.ichi2.anki.reviewer.Binding.ModifierKeys.shift
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BindingAndroidTest {

    @Test
    fun testKeycodeToString() {
        // These use native functions. We may need KeyEvent.keyCodeToString
        assertEquals(BindingTest.keyPrefix + "87", Binding.keyCode(KeyEvent.KEYCODE_MEDIA_NEXT).toString())
        assertEquals(BindingTest.keyPrefix + "Ctrl+88", Binding.keyCode(ctrl(), KeyEvent.KEYCODE_MEDIA_PREVIOUS).toString())
        assertEquals(BindingTest.keyPrefix + "Shift+25", Binding.keyCode(shift(), KeyEvent.KEYCODE_VOLUME_DOWN).toString())
        assertEquals(BindingTest.keyPrefix + "Alt+24", Binding.keyCode(alt(), KeyEvent.KEYCODE_VOLUME_UP).toString())
    }
}
