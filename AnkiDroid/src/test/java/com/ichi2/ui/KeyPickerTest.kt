// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.KeySelectionDialogUtils
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.testutils.KeyEventUtils
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPickerTest : RobolectricTest() {
    private var keyPicker: KeyPicker =
        run {
            targetContext.setTheme(DayTheme.LIGHT.styleResId)
            KeyPicker.inflate(targetContext)
        }

    @Test
    fun test_normal_binding() {
        assertThat(keyPicker.getBinding(), nullValue())

        keyPicker.dispatchKeyEvent(getVKey())

        assertThat(keyPicker.getBinding(), not(nullValue()))
    }

    @Test
    fun invalid_binding_keeps_null_value() {
        assertThat(keyPicker.getBinding(), nullValue())

        keyPicker.dispatchKeyEvent(getInvalidEvent())

        assertThat(keyPicker.getBinding(), nullValue())
    }

    @Test
    fun invalid_binding_keeps_same_value() {
        keyPicker.dispatchKeyEvent(getVKey())

        val binding = keyPicker.getBinding()
        assertThat(binding, not(nullValue()))

        keyPicker.dispatchKeyEvent(getInvalidEvent())

        assertThat(keyPicker.getBinding(), sameInstance(binding))
    }

    @Test
    fun user_specified_validation() {
        // We don't want shift/alt as a single keypress - this stops them being used as modifier keys
        val leftShiftPress = KeyEventUtils.leftShift()

        keyPicker.setKeycodeValidation(KeySelectionDialogUtils.disallowModifierKeyCodes())
        keyPicker.dispatchKeyEvent(leftShiftPress)
        assertThat(keyPicker.getBinding(), nullValue())

        // now turn it off and ensure it wasn't a fluke
        keyPicker.setKeycodeValidation { true }
        keyPicker.dispatchKeyEvent(leftShiftPress)
        assertThat(keyPicker.getBinding(), notNullValue())
    }

    private fun getVKey() = KeyEventUtils.getVKey()

    private fun getInvalidEvent() = KeyEventUtils.getInvalid()
}
