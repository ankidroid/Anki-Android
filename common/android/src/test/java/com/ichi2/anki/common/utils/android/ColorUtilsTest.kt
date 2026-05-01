/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.common.utils.android

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests for [darkenColor] and [lightenColorAbsolute] */
@RunWith(AndroidJUnit4::class)
class ColorUtilsTest {
    @Test
    fun darkenColor_withNoChange_returnsSameColor() {
        val white = Color.WHITE
        assertEquals(white, darkenColor(white, factor = 1.0f))
    }

    @Test
    fun darkenColor_withFullDarken_returnsBlack() {
        val white = Color.WHITE
        assertEquals(Color.BLACK, darkenColor(white, factor = 0.0f))
    }

    @Test
    fun lightenColorAbsolute_withNoChange_returnsSameColor() {
        val red = Color.RED
        assertEquals(red, lightenColorAbsolute(red, amount = 0.0f))
    }

    @Test
    fun `test color fallback logic with takeIf`() {
        val color = Color.BLACK
        val nightMode = true

        // attempt to lighten the color (standard behavior for night mode).
        val firstAttempt = lightenColorAbsolute(color, 0.25f)

        /*
        verify that if the first attempt fails to change the color, the fallback logic is triggered.
        this ensures we always provide a visual difference and avoid potential crashes from identical colors.
         */
        val finalPressedColor =
            firstAttempt.takeIf { it != color }
                ?: if (nightMode) darkenColor(color, 0.85f) else lightenColorAbsolute(color, 0.25f)

        if (firstAttempt == color) {
            // if the initial transformation failed, the final color must be different due to fallback logic.
            assert(finalPressedColor != color) { "Fallback failed to change the color for ${Integer.toHexString(color)}" }
        } else {
            // if the initial transformation succeeded, takeIf should retain that new color.
            assertEquals(firstAttempt, finalPressedColor)
        }
    }

    @Test
    fun `test pressedColor ensuresDifference`() {
        val colorsToTest = listOf(Color.BLACK, Color.WHITE, Color.RED)
        val modes = listOf(true, false) // nightMode on and off

        for (color in colorsToTest) {
            for (nightMode in modes) {
                // apply initial transformation based on the theme
                val initial = if (nightMode) lightenColorAbsolute(color, 0.25f) else darkenColor(color, 0.85f)

                /*
                defensive check: If "initial" is identical to "color", apply the reverse transformation.
                this prevents the "IllegalArgumentException" caused by requiring different colors for Ripple drawables.
                 */
                val finalColor =
                    initial.takeIf { it != color }
                        ?: if (nightMode) darkenColor(color, 0.85f) else lightenColorAbsolute(color, 0.25f)

                // assert that the final color is different from the original to ensure a valid state for the UI
                assert(finalColor != color) {
                    "Final color ${Integer.toHexString(
                        finalColor,
                    )} must be different from original ${Integer.toHexString(color)} in nightMode=$nightMode"
                }
            }
        }
    }
}
