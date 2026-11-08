// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntTest {
    @Test
    fun `single bit matches among other set bits`() {
        assertTrue(0b1010.hasBitFlag(0b0010))
        assertFalse(0b1000.hasBitFlag(0b0010))
        assertFalse(0.hasBitFlag(0b0010))
    }

    @Test
    fun `multi bit mask requires every bit`() {
        assertTrue(0b1111.hasBitFlag(0b0101))
        assertFalse(0b1100.hasBitFlag(0b0101))
        assertFalse(0b1010.hasBitFlag(0b0101))
    }

    @Test
    fun `zero mask matches every value`() {
        assertTrue(0.hasBitFlag(0))
        assertTrue(0b1010.hasBitFlag(0))
        assertTrue((-1).hasBitFlag(0))
    }

    @Test
    fun `sign bit can be tested like other bits`() {
        assertTrue(Int.MIN_VALUE.hasBitFlag(Int.MIN_VALUE))
        assertTrue((-1).hasBitFlag(Int.MIN_VALUE))
        assertFalse(Int.MAX_VALUE.hasBitFlag(Int.MIN_VALUE))
    }
}
