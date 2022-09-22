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
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.alt
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.shift
import com.ichi2.anki.reviewer.MappableBinding.Screen.Reviewer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class BindingAndroidTest : RobolectricTest() {

    @Test
    fun testKeycodeToString() {
        // These use native functions. We may need KeyEvent.keyCodeToString
        assertEquals(BindingTest.keyPrefix + "87", Binding.keyCode(KeyEvent.KEYCODE_MEDIA_NEXT).toString())
        assertEquals(BindingTest.keyPrefix + "Ctrl+88", Binding.keyCode(ctrl(), KeyEvent.KEYCODE_MEDIA_PREVIOUS).toString())
        assertEquals(BindingTest.keyPrefix + "Shift+25", Binding.keyCode(shift(), KeyEvent.KEYCODE_VOLUME_DOWN).toString())
        assertEquals(BindingTest.keyPrefix + "Alt+24", Binding.keyCode(alt(), KeyEvent.KEYCODE_VOLUME_UP).toString())
    }

    @Test
    fun testFromString() {
        assertBindingEquals(Binding.unicode('Ä'), Binding.fromString(BindingTest.unicodePrefix + "Ä"))
        assertBindingEquals(Binding.unicode(ctrl(), 'Ä'), Binding.fromString(BindingTest.unicodePrefix + "Ctrl+Ä"))
        assertBindingEquals(Binding.unicode(shift(), 'Ä'), Binding.fromString(BindingTest.unicodePrefix + "Shift+Ä"))
        assertBindingEquals(Binding.unicode(alt(), 'Ä'), Binding.fromString(BindingTest.unicodePrefix + "Alt+Ä"))
        assertBindingEquals(Binding.keyCode(KeyEvent.KEYCODE_MEDIA_NEXT), Binding.fromString(BindingTest.keyPrefix + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_NEXT)))
        assertBindingEquals(Binding.keyCode(ctrl(), KeyEvent.KEYCODE_MEDIA_PREVIOUS), Binding.fromString(BindingTest.keyPrefix + "Ctrl+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_PREVIOUS)))
        assertBindingEquals(Binding.keyCode(shift(), KeyEvent.KEYCODE_VOLUME_DOWN), Binding.fromString(BindingTest.keyPrefix + "Shift+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertBindingEquals(Binding.keyCode(alt(), KeyEvent.KEYCODE_VOLUME_UP), Binding.fromString(BindingTest.keyPrefix + "Alt+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_UP)))
        assertBindingEquals(Binding.gesture(Gesture.TAP_TOP), Binding.fromString(BindingTest.gesturePrefix + Gesture.TAP_TOP.name))
    }

    @Test
    @Config(qualifiers = "en")
    fun gesture_toDisplayString() {
        assertEquals("${BindingTest.gesturePrefix} Touch top", Binding.gesture(Gesture.TAP_TOP).toDisplayString())
    }

    @Test
    @Config(sdk = [21, 22], qualifiers = "en")
    fun gesture_toDisplayString_legacy() {
        // Use an emoji as a prefix as the newer unicode character doesn't display
        assertEquals("\uD83D\uDC46 Touch top", Binding.gesture(Gesture.TAP_TOP).toDisplayString())
    }

    private fun Binding.toDisplayString(): String {
        return this.toDisplayString(targetContext)
    }

    private fun assertBindingEquals(fst: Binding, snd: Binding) {
        val first = MappableBinding(fst, Reviewer(CardSide.BOTH))
        val second = MappableBinding(snd, Reviewer(CardSide.BOTH))
        assertEquals(first, second)
    }
}
