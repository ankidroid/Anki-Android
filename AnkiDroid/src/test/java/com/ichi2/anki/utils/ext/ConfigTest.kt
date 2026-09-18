// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Tests for extensions to [com.ichi2.anki.libanki.Config]
 */
@RunWith(AndroidJUnit4::class)
class ConfigTest : RobolectricTest() {
    @Test
    fun `test non-diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("uber").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("uber").size)
    }

    @Test
    fun `test diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("über").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("über").size)
    }

    @Test
    fun `test Japanese input`() {
        addBasicNote("は")
        addBasicNote("ば")
        addBasicNote("ぱ")

        assertEquals(1, col.findCards("は").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("は").size)
    }
}
