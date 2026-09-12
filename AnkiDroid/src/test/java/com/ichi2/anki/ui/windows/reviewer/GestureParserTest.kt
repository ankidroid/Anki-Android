/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
package com.ichi2.anki.ui.windows.reviewer

import android.view.ViewConfiguration
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.TapGestureMode
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.TestScope
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GestureParserTest {
    // Avoids `java.lang.RuntimeException: Method scheme in android.net.Uri$Builder not mocked.`
    // The other option is using Robolectric, but that runs much slower
    private fun createRawGesture(
        x: Int = 100,
        y: Int = 100,
        deltaX: Int = 0,
        deltaY: Int = 0,
        time: Long = 10000L,
        touchCount: Int = 1,
        scrollDirection: String? = null,
    ): RawGesture =
        if (touchCount > 1) {
            RawGesture.MultiTouch(touchCount)
        } else {
            RawGesture.TapOrSwipe(
                x = x,
                y = y,
                deltaX = deltaX,
                deltaY = deltaY,
                time = time,
                scrollDirection = scrollDirection,
            )
        }

    private fun parseGesture(
        rawGesture: RawGesture,
        scale: Float = 1.0f,
        scrollX: Int = 0,
        scrollY: Int = 0,
        measuredWidth: Int = 900,
        measuredHeight: Int = 1500,
        swipeSensitivity: Float = 1F,
        gestureMode: TapGestureMode = TapGestureMode.NINE_POINT,
    ): Gesture? {
        val gestureParser =
            GestureParser(
                scope = TestScope(),
                swipeSensitivity = swipeSensitivity,
                gestureMode = gestureMode,
                isDoubleTapEnabled = false,
            )
        val webViewState = GestureParser.WebViewState(scale, scrollX, scrollY, measuredWidth, measuredHeight)
        var gesture: Gesture? = null
        gestureParser.parseInternal(
            rawGesture = rawGesture,
            webViewState = webViewState,
        ) {
            gesture = it
        }
        return gesture
    }

    @Test
    fun `Two-finger tap`() {
        val rawGesture = createRawGesture(touchCount = 2)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TWO_FINGER_TAP, gesture)
    }

    @Test
    fun `Three-finger tap`() {
        val rawGesture = createRawGesture(touchCount = 3)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.THREE_FINGER_TAP, gesture)
    }

    @Test
    fun `Four-finger tap`() {
        val rawGesture = createRawGesture(touchCount = 4)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.FOUR_FINGER_TAP, gesture)
    }

    @Test
    fun `Five+ finger taps are ignored`() {
        val rawGesture = createRawGesture(touchCount = 5)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(expected = null, actual = gesture)
    }

    // Swipe tests

    @Test
    fun `parse detects SWIPE_RIGHT`() {
        val rawGesture = createRawGesture(deltaX = 150, deltaY = 5)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.SWIPE_RIGHT, gesture)
    }

    @Test
    fun `parse detects SWIPE_LEFT`() {
        val rawGesture = createRawGesture(deltaX = -150, deltaY = 10)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.SWIPE_LEFT, gesture)
    }

    @Test
    fun `parse detects SWIPE_DOWN`() {
        val rawGesture = createRawGesture(deltaX = 5, deltaY = 125)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.SWIPE_DOWN, gesture)
    }

    @Test
    fun `parse detects SWIPE_UP`() {
        val rawGesture = createRawGesture(deltaX = 10, deltaY = -150)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.SWIPE_UP, gesture)
    }

    @Test
    fun `parse ignores horizontal swipe if content can scroll horizontally`() {
        val rawGesture = createRawGesture(deltaX = 150, scrollDirection = "h")
        val gesture = parseGesture(rawGesture = rawGesture)
        assertNull(gesture, "Horizontal swipe should be ignored")
    }

    @Test
    fun `parse ignores vertical swipe if content can scroll vertically`() {
        val rawGesture = createRawGesture(deltaY = 150, scrollDirection = "v")
        val gesture = parseGesture(rawGesture = rawGesture)
        assertNull(gesture, "Vertical swipe should be ignored")
    }

    @Test
    fun `parse swipe threshold is adjusted by scale`() {
        val rawGesture = createRawGesture(x = 50, y = 50, deltaX = 100)
        val gesture = parseGesture(rawGesture = rawGesture, scale = 2.0f)
        assertEquals(Gesture.SWIPE_RIGHT, gesture)
    }

    // Nine points tests
    @Test
    fun `parse detects nine points TAP_TOP_LEFT`() {
        val rawGesture = createRawGesture(x = 150, y = 250)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_TOP_LEFT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_TOP`() {
        val rawGesture = createRawGesture(x = 450, y = 250)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_TOP, gesture)
    }

    @Test
    fun `parse detects nine points TAP_TOP_RIGHT`() {
        val rawGesture = createRawGesture(x = 750, y = 250)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_TOP_RIGHT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_LEFT`() {
        val rawGesture = createRawGesture(x = 150, y = 750)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_LEFT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_CENTER`() {
        val rawGesture = createRawGesture(x = 450, y = 750)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_CENTER, gesture)
    }

    @Test
    fun `parse detects nine points TAP_RIGHT`() {
        val rawGesture = createRawGesture(x = 750, y = 750)
        val gesture = parseGesture(rawGesture)
        assertEquals(Gesture.TAP_RIGHT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_BOTTOM_LEFT`() {
        val rawGesture = createRawGesture(x = 150, y = 1250)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_BOTTOM_LEFT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_BOTTOM`() {
        val rawGesture = createRawGesture(x = 450, y = 1250)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_BOTTOM, gesture)
    }

    @Test
    fun `parse detects nine points TAP_BOTTOM_RIGHT`() {
        val rawGesture = createRawGesture(x = 750, y = 1250)
        val gesture = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_BOTTOM_RIGHT, gesture)
    }

    // Four points tests

    @Test
    fun `parse detects four points TAP_TOP`() {
        val rawGesture = createRawGesture(x = 750, y = 100)
        val gesture =
            parseGesture(
                rawGesture = rawGesture,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_TOP, gesture)
    }

    @Test
    fun `parse detects four points TAP_RIGHT`() {
        val rawGesture = createRawGesture(x = 900, y = 750)
        val gesture =
            parseGesture(
                rawGesture = rawGesture,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_RIGHT, gesture)
    }

    @Test
    fun `parse detects four points TAP_BOTTOM`() {
        val rawGesture = createRawGesture(x = 250, y = 900)
        val gesture =
            parseGesture(
                rawGesture = rawGesture,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_BOTTOM, gesture)
    }

    @Test
    fun `parse detects four points TAP_LEFT`() {
        val rawGesture = createRawGesture(x = 100, y = 250)
        val gesture =
            parseGesture(
                rawGesture = rawGesture,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_LEFT, gesture)
    }

    // Tap with scroll & scale

    @Test
    fun `parse detects tap correctly with scrolling`() {
        val rawGesture = createRawGesture(x = 550, y = 950)
        val gesture = parseGesture(rawGesture = rawGesture, scrollX = 100, scrollY = 200)
        assertEquals(Gesture.TAP_CENTER, gesture)
    }

    @Test
    fun `parse detects tap correctly with scaling`() {
        val rawGesture = createRawGesture(x = 225, y = 375)
        val gesture = parseGesture(rawGesture = rawGesture, scale = 2.0f)
        assertEquals(Gesture.TAP_CENTER, gesture)
    }

    // region Swipe sensitivity
    @Test
    fun `reduced swipe sensitivity`() {
        val rawGesture = createRawGesture(x = 450, y = 0, deltaY = -150)
        val gesture1 = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.SWIPE_UP, gesture1)

        val gesture2 = parseGesture(rawGesture = rawGesture, swipeSensitivity = 0.2F)
        assertEquals(Gesture.TAP_TOP, gesture2)
    }

    @Test
    fun `increased swipe sensitivity`() {
        val rawGesture = createRawGesture(x = 450, y = 0, deltaY = -90)
        val gesture1 = parseGesture(rawGesture = rawGesture)
        assertEquals(Gesture.TAP_TOP, gesture1)

        val gesture2 = parseGesture(rawGesture = rawGesture, swipeSensitivity = 1.8F)
        assertEquals(Gesture.SWIPE_UP, gesture2)
    }
    //endregion

    companion object {
        @BeforeClass
        @JvmStatic // required for @BeforeClass
        fun before() {
            mockkStatic(ViewConfiguration::class)
            every { ViewConfiguration.getDoubleTapTimeout() } answers { 300 }
        }

        @JvmStatic // required for @AfterClass
        @AfterClass
        fun after() {
            unmockkStatic(ViewConfiguration::class)
        }
    }
}
