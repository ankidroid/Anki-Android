// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Unparsed gesture data sent by the JavaScript code.
 *
 * @see GestureParser
 *
 * @throws [kotlinx.serialization.SerializationException] if the data is invalid
 */
sealed class RawGesture {
    /** A tap with more than one finger */
    @Serializable
    data class MultiTouch(
        val touchCount: Int,
    ) : RawGesture() {
        companion object {
            fun fromJson(bytes: ByteArray): MultiTouch = decodeJson(bytes)
        }
    }

    @Serializable
    data class TapOrSwipe(
        val x: Int,
        val y: Int,
        val deltaX: Int,
        val deltaY: Int,
        val time: Long,
        /** @see GestureParser.determineSwipeGesture */
        val scrollDirection: String? = null,
    ) : RawGesture() {
        companion object {
            fun fromJson(bytes: ByteArray): TapOrSwipe = decodeJson(bytes)
        }
    }
}

// Keep the default, strict Json: card JavaScript can send any body, and e.g. `ignoreUnknownKeys`
// skips unknown values without a nesting limit
private inline fun <reified T> decodeJson(bytes: ByteArray): T = Json.decodeFromString(bytes.decodeToString())
