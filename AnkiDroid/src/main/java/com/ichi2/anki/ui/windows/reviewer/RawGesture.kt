// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.common.utils.ext.getStringOrNull
import org.json.JSONObject

/**
 * Unparsed gesture data sent by the JavaScript code.
 *
 * @see GestureParser
 *
 * @throws [org.json.JSONException] if the data is invalid
 */
sealed class RawGesture {
    /** A tap with more than one finger */
    data class MultiTouch(
        val touchCount: Int,
    ) : RawGesture() {
        constructor(json: JSONObject) : this(touchCount = json.getInt("touchCount"))

        constructor(bytes: ByteArray) : this(JSONObject(bytes.decodeToString()))
    }

    data class TapOrSwipe(
        val x: Int,
        val y: Int,
        val deltaX: Int,
        val deltaY: Int,
        val time: Long,
        /** @see GestureParser.determineSwipeGesture */
        val scrollDirection: String?,
    ) : RawGesture() {
        constructor(json: JSONObject) : this(
            x = json.getInt("x"),
            y = json.getInt("y"),
            deltaX = json.getInt("deltaX"),
            deltaY = json.getInt("deltaY"),
            time = json.getLong("time"),
            scrollDirection = json.getStringOrNull("scrollDirection"),
        )

        constructor(bytes: ByteArray) : this(JSONObject(bytes.decodeToString()))
    }
}
