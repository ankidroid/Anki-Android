// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SavedStateHandleTest {
    @Test
    fun `non-null value uses its default when no value is saved`() {
        val state = SavedStateHandle()
        var selection: Int by state.value("selection", defaultValue = -1)

        assertEquals(-1, selection)
        assertFalse(state.contains("selection"))

        selection = 2
        assertEquals(2, state.get<Int>("selection"))
        assertEquals(2, selection)

        state["selection"] = null
        assertEquals(-1, selection)
    }
}
