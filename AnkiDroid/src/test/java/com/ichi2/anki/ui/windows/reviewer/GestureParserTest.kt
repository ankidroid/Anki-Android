/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
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

import android.net.Uri
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.TapGestureMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GestureParserTest {
    // Avoids `java.lang.RuntimeException: Method scheme in android.net.Uri$Builder not mocked.`
    // The other option is using Robolectric, but that runs much slower
    private fun createMockUri(
        host: String = "tapOrSwipe",
        x: Int? = 100,
        y: Int? = 100,
        deltaX: Int? = 0,
        deltaY: Int? = 0,
        scrollDirection: String? = null,
    ): Uri =
        mockk {
            every { this@mockk.host } returns host
            every { getQueryParameter("x") } returns x?.toString()
            every { getQueryParameter("y") } returns y?.toString()
            every { getQueryParameter("deltaX") } returns deltaX?.toString()
            every { getQueryParameter("deltaY") } returns deltaY?.toString()
            every { getQueryParameter("scrollDirection") } returns scrollDirection
        }

    private fun parseGesture(
        uri: Uri,
        isScrolling: Boolean = false,
        scale: Float = 1.0f,
        scrollX: Int = 0,
        scrollY: Int = 0,
        measuredWidth: Int = 900,
        measuredHeight: Int = 1500,
        gestureMode: TapGestureMode = TapGestureMode.NINE_POINT,
    ): Gesture? =
        GestureParser.parse(
            uri,
            isScrolling,
            scale,
            scrollX,
            scrollY,
            measuredWidth,
            measuredHeight,
            gestureMode,
        )

    @Test
    fun `parse returns null when isScrolling is true`() {
        val uri = createMockUri()
        val gesture = parseGesture(uri = uri, isScrolling = true)
        assertNull(gesture, "Gesture should be null if scrolling")
    }

    @Test
    fun `parse returns DOUBLE_TAP for doubleTap host`() {
        val uri = createMockUri(host = "doubleTap")
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.DOUBLE_TAP, gesture)
    }

    @Test
    fun `parse returns null if required parameters are missing`() {
        val malformedUri = createMockUri(x = 100, y = null, deltaX = null)
        val gesture = parseGesture(uri = malformedUri)
        assertNull(gesture, "Gesture should be null if parameters are missing")
    }

    // Swipe tests

    @Test
    fun `parse detects SWIPE_RIGHT`() {
        val uri = createMockUri(deltaX = 20, deltaY = 5)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.SWIPE_RIGHT, gesture)
    }

    @Test
    fun `parse detects SWIPE_LEFT`() {
        val uri = createMockUri(deltaX = -25, deltaY = 10)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.SWIPE_LEFT, gesture)
    }

    @Test
    fun `parse detects SWIPE_DOWN`() {
        val uri = createMockUri(deltaX = 5, deltaY = 20)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.SWIPE_DOWN, gesture)
    }

    @Test
    fun `parse detects SWIPE_UP`() {
        val uri = createMockUri(deltaX = 10, deltaY = -25)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.SWIPE_UP, gesture)
    }

    @Test
    fun `parse ignores horizontal swipe if content can scroll horizontally`() {
        val uri = createMockUri(deltaX = 25, scrollDirection = "h")
        val gesture = parseGesture(uri = uri)
        assertNull(gesture, "Horizontal swipe should be ignored")
    }

    @Test
    fun `parse ignores vertical swipe if content can scroll vertically`() {
        val uri = createMockUri(deltaY = 25, scrollDirection = "v")
        val gesture = parseGesture(uri = uri)
        assertNull(gesture, "Vertical swipe should be ignored")
    }

    @Test
    fun `parse swipe threshold is adjusted by scale`() {
        val uri = createMockUri(x = 50, y = 50, deltaX = 10)
        val gesture = parseGesture(uri = uri, scale = 2.0f)
        assertEquals(Gesture.SWIPE_RIGHT, gesture)
    }

    // Nine points tests
    @Test
    fun `parse detects nine points TAP_TOP_LEFT`() {
        val uri = createMockUri(x = 150, y = 250)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_TOP_LEFT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_TOP`() {
        val uri = createMockUri(x = 450, y = 250)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_TOP, gesture)
    }

    @Test
    fun `parse detects nine points TAP_TOP_RIGHT`() {
        val uri = createMockUri(x = 750, y = 250)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_TOP_RIGHT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_LEFT`() {
        val uri = createMockUri(x = 150, y = 750)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_LEFT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_CENTER`() {
        val uri = createMockUri(x = 450, y = 750)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_CENTER, gesture)
    }

    @Test
    fun `parse detects nine points TAP_RIGHT`() {
        val uri = createMockUri(x = 750, y = 750)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_RIGHT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_BOTTOM_LEFT`() {
        val uri = createMockUri(x = 150, y = 1250)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_BOTTOM_LEFT, gesture)
    }

    @Test
    fun `parse detects nine points TAP_BOTTOM`() {
        val uri = createMockUri(x = 450, y = 1250)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_BOTTOM, gesture)
    }

    @Test
    fun `parse detects nine points TAP_BOTTOM_RIGHT`() {
        val uri = createMockUri(x = 750, y = 1250)
        val gesture = parseGesture(uri = uri)
        assertEquals(Gesture.TAP_BOTTOM_RIGHT, gesture)
    }

    // Four points tests

    @Test
    fun `parse detects four points TAP_TOP`() {
        val uri = createMockUri(x = 750, y = 100)
        val gesture =
            parseGesture(
                uri = uri,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_TOP, gesture)
    }

    @Test
    fun `parse detects four points TAP_RIGHT`() {
        val uri = createMockUri(x = 900, y = 750)
        val gesture =
            parseGesture(
                uri = uri,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_RIGHT, gesture)
    }

    @Test
    fun `parse detects four points TAP_BOTTOM`() {
        val uri = createMockUri(x = 250, y = 900)
        val gesture =
            parseGesture(
                uri = uri,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_BOTTOM, gesture)
    }

    @Test
    fun `parse detects four points TAP_LEFT`() {
        val uri = createMockUri(x = 100, y = 250)
        val gesture =
            parseGesture(
                uri = uri,
                measuredWidth = 1000,
                measuredHeight = 1000,
                gestureMode = TapGestureMode.FOUR_POINT,
            )
        assertEquals(Gesture.TAP_LEFT, gesture)
    }

    // Tap with scroll & scale

    @Test
    fun `parse detects tap correctly with scrolling`() {
        val uri = createMockUri(x = 550, y = 950)
        val gesture = parseGesture(uri = uri, scrollX = 100, scrollY = 200)
        assertEquals(Gesture.TAP_CENTER, gesture)
    }

    @Test
    fun `parse detects tap correctly with scaling`() {
        val uri = createMockUri(x = 225, y = 375)
        val gesture = parseGesture(uri = uri, scale = 2.0f)
        assertEquals(Gesture.TAP_CENTER, gesture)
    }
}
