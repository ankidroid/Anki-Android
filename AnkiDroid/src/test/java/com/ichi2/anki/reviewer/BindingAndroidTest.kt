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
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding.KeyCode
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.alt
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.shift
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class BindingAndroidTest : RobolectricTest() {
    @Test
    fun testKeycodeToString() {
        // These use native functions. We may need KeyEvent.keyCodeToString
        assertEquals(BindingTest.KEY_PREFIX + "87", KeyCode(KeyEvent.KEYCODE_MEDIA_NEXT).toString())
        assertEquals(
            BindingTest.KEY_PREFIX + "Ctrl+88",
            Binding
                .KeyCode(
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    ctrl(),
                ).toString(),
        )
        assertEquals(
            BindingTest.KEY_PREFIX + "Shift+25",
            Binding
                .KeyCode(
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    shift(),
                ).toString(),
        )
        assertEquals(
            BindingTest.KEY_PREFIX + "Alt+24",
            Binding
                .KeyCode(
                    KeyEvent.KEYCODE_VOLUME_UP,
                    alt(),
                ).toString(),
        )
    }

    @Test
    fun testFromString() {
        assertEquals(Binding.unicodeUnsafe('Ä'), Binding.fromString(BindingTest.UNICODE_PREFIX + "Ä"))
        assertEquals(Binding.unicodeUnsafe('Ä', ctrl()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Ctrl+Ä"))
        assertEquals(Binding.unicodeUnsafe('Ä', shift()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Shift+Ä"))
        assertEquals(Binding.unicodeUnsafe('Ä', alt()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Alt+Ä"))
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_MEDIA_NEXT),
            Binding.fromString(BindingTest.KEY_PREFIX + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_NEXT)),
        )
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS, ctrl()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Ctrl+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_PREVIOUS)),
        )
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_VOLUME_DOWN, shift()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Shift+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_DOWN)),
        )
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_VOLUME_UP, alt()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Alt+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_UP)),
        )
        assertEquals(Binding.gesture(Gesture.TAP_TOP), Binding.fromString(BindingTest.GESTURE_PREFIX + Gesture.TAP_TOP.name))
    }

    @Test
    fun `motion event serde`() {
        assertEquals(axis(Axis.X, 1.0f), axisBindingFromString("0 1.0"))
        assertEquals(axis(Axis.Y, -1.0f), axisBindingFromString("1 -1.0"))
    }

    @Test
    @Config(qualifiers = "en")
    fun gesture_toDisplayString() {
        assertEquals("${BindingTest.GESTURE_PREFIX} Touch top", Binding.gesture(Gesture.TAP_TOP).toDisplayString())
    }

    private fun Binding.toDisplayString(): String = this.toDisplayString(targetContext)
}

private fun axis(
    axis: Axis,
    fl: Float,
) = Binding.AxisButtonBinding(axis, fl)

private fun axisBindingFromString(suffix: String) = Binding.fromString(BindingTest.JOYSTICK_PREFIX + suffix)
