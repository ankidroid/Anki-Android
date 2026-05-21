/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownloadSpeedCalculatorTest {
    private lateinit var calculator: DownloadSpeedCalculator

    @Before
    fun setUp() {
        calculator = DownloadSpeedCalculator(alpha = 0.5)
    }

    @Test
    fun `initial update returns 0 speed`() {
        val speed = calculator.update(1000, 1000)
        assertEquals(0.0, speed, 0.001)
    }

    @Test
    fun `second update returns instant speed if first was 0`() {
        calculator.update(0, 1000)
        val speed = calculator.update(1000, 2000) // 1000 bytes in 1s = 1000 B/s
        assertEquals(1000.0, speed, 0.001)
    }

    @Test
    fun `smoothing logic correctly applies EMA with asymmetric alpha`() {
        calculator = DownloadSpeedCalculator(alpha = 0.25)

        calculator.update(0, 0)
        calculator.update(
            1000,
            1000,
        ) // First non-zero speed initializes the smoothed speed to 1000 B/s.

        // Next instant speed: 3000 B/s (diff: 3000 bytes in 1s)
        val speed = calculator.update(4000, 2000)

        // EMA: (1000 * 0.75) + (3000 * 0.25) = 1500
        assertEquals(1500.0, speed, 0.001)
    }

    @Test
    fun `speed drops gradually towards 0 when download stalls`() {
        calculator.update(0, 0)
        calculator.update(1000, 1000) // Baseline
        calculator.update(2000, 2000) // 1000 B/s. lastSmoothedSpeed = 1000.

        // Stall: 0 bytes in 1s
        val speed1 = calculator.update(2000, 3000)
        // (1000 * 0.5) + (0 * 0.5) = 500
        assertEquals(500.0, speed1, 0.001)

        // Continue stall
        val speed2 = calculator.update(2000, 4000)
        // (500 * 0.5) + (0 * 0.5) = 250
        assertEquals(250.0, speed2, 0.001)
    }

    @Test
    fun `reset clears internal state`() {
        calculator.update(0, 0)
        calculator.update(1000, 1000)
        calculator.reset()

        val firstSpeedAfterReset = calculator.update(1000, 1000)
        assertEquals(0.0, firstSpeedAfterReset, 0.001)

        val secondSpeedAfterReset = calculator.update(2000, 2000)
        assertEquals(1000.0, secondSpeedAfterReset, 0.001)
    }

    @Test
    fun `handles non-uniform time steps`() {
        calculator.update(0, 0)
        calculator.update(1000, 1000) // 1000 B/s

        // 2000 bytes in 2s = 1000 B/s
        val speed = calculator.update(3000, 3000)
        assertEquals(1000.0, speed, 0.001)
    }

    @Test
    fun `duplicate timestamp keeps previous speed and rebases bytes for next sample`() {
        calculator.update(0, 0)
        calculator.update(1000, 1000)

        val duplicateTimestampSpeed = calculator.update(1500, 1000)
        assertEquals(1000.0, duplicateTimestampSpeed, 0.001)

        val nextSpeed = calculator.update(2500, 2000)
        assertEquals(1000.0, nextSpeed, 0.001)
    }

    @Test
    fun `downloaded byte regression keeps previous speed but resets baseline`() {
        calculator.update(0, 0)
        calculator.update(1000, 1000)

        val regressedProgressSpeed = calculator.update(900, 2000)
        assertEquals(1000.0, regressedProgressSpeed, 0.001)

        val nextSpeed = calculator.update(1900, 3000)
        assertEquals(1000.0, nextSpeed, 0.001)
    }
}
