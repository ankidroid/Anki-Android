// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.net.Uri

/**
 * Unparsed gesture data sent by the JavaScript code.
 *
 * @see GestureParser
 */
sealed class RawGesture {
    /** A tap with more than one finger */
    data class MultiTouch(
        val touchCount: Int,
    ) : RawGesture()

    data class TapOrSwipe(
        val x: Int,
        val y: Int,
        val deltaX: Int,
        val deltaY: Int,
        val time: Long,
        /** @see GestureParser.determineSwipeGesture */
        val scrollDirection: String? = null,
    ) : RawGesture()

    companion object {
        fun fromUri(uri: Uri): RawGesture? {
            if (uri.host == MULTI_FINGER_HOST) {
                return MultiTouch(touchCount = uri.getIntQuery(PARAM_TOUCH_COUNT) ?: return null)
            }
            return TapOrSwipe(
                x = uri.getIntQuery(PARAM_X) ?: return null,
                y = uri.getIntQuery(PARAM_Y) ?: return null,
                deltaX = uri.getIntQuery(PARAM_DELTA_X) ?: return null,
                deltaY = uri.getIntQuery(PARAM_DELTA_Y) ?: return null,
                time = uri.getQueryParameter(PARAM_TIME)?.toLongOrNull() ?: return null,
                scrollDirection = uri.getQueryParameter(PARAM_SCROLL_DIRECTION),
            )
        }

        private fun Uri.getIntQuery(key: String) = getQueryParameter(key)?.toIntOrNull()

        private const val PARAM_X = "x"
        private const val PARAM_Y = "y"
        private const val PARAM_DELTA_X = "deltaX"
        private const val PARAM_DELTA_Y = "deltaY"
        private const val PARAM_TIME = "time"
        private const val PARAM_SCROLL_DIRECTION = "scrollDirection"
        private const val PARAM_TOUCH_COUNT = "touchCount"
        private const val MULTI_FINGER_HOST = "multiFingerTap"
    }
}
