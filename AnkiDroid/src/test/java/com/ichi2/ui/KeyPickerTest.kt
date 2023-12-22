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

package com.ichi2.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.KeySelectionDialogUtils
import com.ichi2.testutils.KeyEventUtils
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPickerTest : RobolectricTest() {
    private var mKeyPicker: KeyPicker = KeyPicker.inflate(targetContext)

    @Test
    fun test_normal_binding() {
        assertThat(mKeyPicker.getBinding(), nullValue())

        mKeyPicker.dispatchKeyEvent(getVKey())

        assertThat(mKeyPicker.getBinding(), not(nullValue()))
    }

    @Test
    fun invalid_binding_keeps_null_value() {
        assertThat(mKeyPicker.getBinding(), nullValue())

        mKeyPicker.dispatchKeyEvent(getInvalidEvent())

        assertThat(mKeyPicker.getBinding(), nullValue())
    }

    @Test
    fun invalid_binding_keeps_same_value() {
        mKeyPicker.dispatchKeyEvent(getVKey())

        val binding = mKeyPicker.getBinding()
        assertThat(binding, not(nullValue()))

        mKeyPicker.dispatchKeyEvent(getInvalidEvent())

        assertThat(mKeyPicker.getBinding(), sameInstance(binding))
    }

    @Test
    fun user_specified_validation() {
        // We don't want shift/alt as a single keypress - this stops them being used as modifier keys
        val leftShiftPress = KeyEventUtils.leftShift()

        mKeyPicker.setKeycodeValidation(KeySelectionDialogUtils.disallowModifierKeyCodes())
        mKeyPicker.dispatchKeyEvent(leftShiftPress)
        assertThat(mKeyPicker.getBinding(), nullValue())

        // now turn it off and ensure it wasn't a fluke
        mKeyPicker.setKeycodeValidation { true }
        mKeyPicker.dispatchKeyEvent(leftShiftPress)
        assertThat(mKeyPicker.getBinding(), notNullValue())
    }

    private fun getVKey() = KeyEventUtils.getVKey()

    private fun getInvalidEvent() = KeyEventUtils.getInvalid()
}
