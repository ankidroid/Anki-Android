// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import org.json.JSONException
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RawGestureTest {
    @Test
    fun `multi touch is parsed`() {
        val json = """{"touchCount": 3}"""
        assertEquals(RawGesture.MultiTouch(touchCount = 3), RawGesture.MultiTouch(json.toByteArray()))
    }

    @Test
    fun `tap or swipe is parsed`() {
        val json = """{"x": 10, "y": 20, "deltaX": -30, "deltaY": 40, "time": 1700000000000, "scrollDirection": "hv"}"""
        val expected =
            RawGesture.TapOrSwipe(x = 10, y = 20, deltaX = -30, deltaY = 40, time = 1700000000000, scrollDirection = "hv")
        assertEquals(expected, RawGesture.TapOrSwipe(json.toByteArray()))
    }

    @Test
    fun `null scroll direction is parsed`() {
        val json = """{"x": 0, "y": 0, "deltaX": 0, "deltaY": 0, "time": 0, "scrollDirection": null}"""
        assertNull(RawGesture.TapOrSwipe(JSONObject(json)).scrollDirection)
    }

    @Test
    fun `missing required parameters throw`() {
        val json = """{"x": 100, "time": 0}"""
        assertFailsWith<JSONException> { RawGesture.TapOrSwipe(JSONObject(json)) }
    }

    @Test
    fun `missing touch count throws`() {
        assertFailsWith<JSONException> { RawGesture.MultiTouch(JSONObject("{}")) }
    }
}
