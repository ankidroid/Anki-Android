// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildSrcTest {
    @Test
    fun `runs buildSrc tests`() {
        assertEquals(2, 1 + 1)
    }
}
