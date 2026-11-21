// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NumberPickerStateTest {
    @Test
    fun `stepping at integer boundaries clamps without wrapping`() {
        val maximum = NumberPickerState(Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE.toString())
        maximum.step(true)
        assertEquals(Int.MAX_VALUE, maximum.value)
        assertEquals(TextRange(maximum.input.text.length), maximum.input.selection)

        val minimum = NumberPickerState(Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE.toString())
        minimum.step(false)
        assertEquals(Int.MIN_VALUE, minimum.value)
        assertEquals(TextRange(minimum.input.text.length), minimum.input.selection)
    }

    @Test
    fun `stepping after clearing text uses the last stepped value and places the cursor at the end`() {
        val state = NumberPickerState(5, 99999, "30", TextRange(0, 2))
        var valid: Boolean? = null
        state.onValidityChanged = { valid = it }
        state.step(true)
        assertEquals(31, state.value)
        assertEquals(TextRange(2), state.input.selection)
        assertEquals(true, valid)

        state.input.edit { replace(0, length, "") }
        state.step(false)
        assertEquals(30, state.value)
        assertEquals(TextRange(2), state.input.selection)
    }
}
