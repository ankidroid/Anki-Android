// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
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
        assertEquals(BindingTest.KEY_PREFIX + "87", Binding.keyCode(KeyEvent.KEYCODE_MEDIA_NEXT).toString())
        assertEquals(BindingTest.KEY_PREFIX + "Ctrl+88", Binding.keyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS, ctrl()).toString())
        assertEquals(BindingTest.KEY_PREFIX + "Shift+25", Binding.keyCode(KeyEvent.KEYCODE_VOLUME_DOWN, shift()).toString())
        assertEquals(BindingTest.KEY_PREFIX + "Alt+24", Binding.keyCode(KeyEvent.KEYCODE_VOLUME_UP, alt()).toString())
    }

    @Test
    fun testFromString() {
        assertEquals(Binding.unicode('Ä'), Binding.fromString(BindingTest.UNICODE_PREFIX + "Ä"))
        assertEquals(Binding.unicode('Ä', ctrl()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Ctrl+Ä"))
        assertEquals(Binding.unicode('Ä', shift()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Shift+Ä"))
        assertEquals(Binding.unicode('Ä', alt()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Alt+Ä"))
        assertEquals(
            Binding.keyCode(KeyEvent.KEYCODE_MEDIA_NEXT),
            Binding.fromString(BindingTest.KEY_PREFIX + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_NEXT)),
        )
        assertEquals(
            Binding.keyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS, ctrl()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Ctrl+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_PREVIOUS)),
        )
        assertEquals(
            Binding.keyCode(KeyEvent.KEYCODE_VOLUME_DOWN, shift()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Shift+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_DOWN)),
        )
        assertEquals(
            Binding.keyCode(KeyEvent.KEYCODE_VOLUME_UP, alt()),
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
