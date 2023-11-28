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
import com.ichi2.anki.cardviewer.Gesture
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2

class BindingTest {
    @Test
    fun modifierKeys_Are_Loaded() {
        testModifierKeys("shift", KeyEvent::isShiftPressed, Binding.ModifierKeys::shiftMatches)
        testModifierKeys("ctrl", KeyEvent::isCtrlPressed, Binding.ModifierKeys::ctrlMatches)
        testModifierKeys("alt", KeyEvent::isAltPressed, Binding.ModifierKeys::altMatches)
    }

    @Test
    fun unicodeKeyIsLoaded() {
        val binding = unicodeCharacter('a')

        assertThat(binding.unicodeCharacter, equalTo('a'))
    }

    @Test
    fun keycodeIsLoaded() {
        val binding = keyCode(KeyEvent.KEYCODE_A)

        assertThat(binding.keycode, equalTo(KeyEvent.KEYCODE_A))
    }

    @Test
    fun testUnicodeToString() {
        assertEquals(unicodePrefix + "Ä", Binding.unicode('Ä').toString())
        assertEquals(unicodePrefix + "Ctrl+Ä", Binding.unicode(Binding.ModifierKeys.ctrl(), 'Ä').toString())
        assertEquals(unicodePrefix + "Shift+Ä", Binding.unicode(Binding.ModifierKeys.shift(), 'Ä').toString())
        assertEquals(unicodePrefix + "Alt+Ä", Binding.unicode(Binding.ModifierKeys.alt(), 'Ä').toString())
        assertEquals(unicodePrefix + "Ctrl+Alt+Shift+Ä", Binding.unicode(allModifierKeys(), 'Ä').toString())
    }

    @Test
    fun testGestureToString() {
        assertEquals(gesturePrefix + "TAP_TOP", Binding.gesture(Gesture.TAP_TOP).toString())
    }

    @Test
    fun testUnknownToString() {
        // This seems sensible - serialising an unknown will mean that nothing is saved.
        assertThat(Binding.unknown().toString(), equalTo(""))
    }

    private fun testModifierKeys(name: String, event: KFunction1<KeyEvent, Boolean>, getValue: KFunction2<Binding.ModifierKeys, Boolean, Boolean>) {
        fun testModifierResult(event: KFunction1<KeyEvent, Boolean>, returnedFromMock: Boolean) {
            val mock = mock<KeyEvent> {
                on(event) doReturn returnedFromMock
            }

            val bindings = Binding.key(mock)

            for (binding in bindings) {
                assertThat("Should match when '$name:$returnedFromMock': ", getValue(binding.modifierKeys!!, true), equalTo(returnedFromMock))
                assertThat("Should match when '$name:${!returnedFromMock}': ", getValue(binding.modifierKeys!!, false), equalTo(!returnedFromMock))
            }
        }

        testModifierResult(event, true)
        testModifierResult(event, false)
    }

    companion object {
        const val gesturePrefix = '\u235D'
        const val keyPrefix = '\u2328'
        const val unicodePrefix = '\u2705'

        fun allModifierKeys() = Binding.ModifierKeys(shift = true, ctrl = true, alt = true)

        fun unicodeCharacter(c: Char): Binding {
            val mock = mock<KeyEvent> {
                on { getUnicodeChar(anyInt()) } doReturn c.code
                on { unicodeChar } doReturn c.code
            }

            return Binding.key(mock).first { x -> x.unicodeCharacter != null }
        }

        fun keyCode(keyCode: Int): Binding {
            val mock = mock<KeyEvent> {
                on { getKeyCode() } doReturn keyCode
            }

            return Binding.key(mock).first { x -> x.keycode != null }
        }
    }
}
