/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.cardviewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.TapGestureMode.FOUR_POINT
import com.ichi2.anki.cardviewer.TapGestureMode.NINE_POINT

/**
 * https://www.fileformat.info/info/unicode/char/235d/index.htm (similar to a finger)
 * Supported on API 23
 */
const val GESTURE_PREFIX = "\u235D"

/** Supported on API 21: https://emojipedia.org/google/android-5.0/backhand-index-pointing-up/ */
const val LEGACY_GESTURE_PREFIX = "\uD83D\uDC46"

fun interface GestureListener {
    fun onGesture(gesture: Gesture)
}

// TODO: Code and preference defaults are inconsistent: #8066
enum class Gesture(
    @get:JvmName("getResourceId") val resourceId: Int,
    private val preferenceKey: String,
    private val preferenceDefault: ViewerCommand
) {
    SWIPE_UP(R.string.gestures_swipe_up, "gestureSwipeUp", ViewerCommand.COMMAND_EDIT),
    SWIPE_DOWN(R.string.gestures_swipe_down, "gestureSwipeDown", ViewerCommand.COMMAND_NOTHING),
    SWIPE_LEFT(R.string.gestures_swipe_left, "gestureSwipeLeft", ViewerCommand.COMMAND_UNDO),
    SWIPE_RIGHT(R.string.gestures_swipe_right, "gestureSwipeRight", ViewerCommand.COMMAND_EXIT),
    LONG_TAP(R.string.gestures_long_tap, "gestureLongclick", ViewerCommand.COMMAND_LOOKUP),
    DOUBLE_TAP(R.string.gestures_double_tap, "gestureDoubleTap", ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED),
    TAP_TOP_LEFT(R.string.gestures_corner_tap_top_left, "gestureTapTopLeft", ViewerCommand.COMMAND_NOTHING),
    TAP_TOP(R.string.gestures_tap_top, "gestureTapTop", ViewerCommand.COMMAND_BURY_CARD),
    TAP_TOP_RIGHT(R.string.gestures_corner_tap_top_right, "gestureTapTopRight", ViewerCommand.COMMAND_NOTHING),
    TAP_LEFT(R.string.gestures_tap_left, "gestureTapLeft", ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE2),
    TAP_CENTER(R.string.gestures_corner_tap_middle_center, "gestureTapCenter", ViewerCommand.COMMAND_NOTHING),
    TAP_RIGHT(R.string.gestures_tap_right, "gestureTapRight", ViewerCommand.COMMAND_FLIP_OR_ANSWER_RECOMMENDED),
    TAP_BOTTOM_LEFT(R.string.gestures_corner_tap_bottom_left, "gestureTapBottomLeft", ViewerCommand.COMMAND_NOTHING),
    TAP_BOTTOM(R.string.gestures_tap_bottom, "gestureTapBottom", ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE1),
    TAP_BOTTOM_RIGHT(R.string.gestures_corner_tap_bottom_right, "gestureTapBottomRight", ViewerCommand.COMMAND_NOTHING);

    fun fromPreference(prefs: SharedPreferences): ViewerCommand {
        val value = prefs.getString(preferenceKey, null) ?: return preferenceDefault

        val valueAsInt = Integer.parseInt(value)

        return ViewerCommand.fromInt(valueAsInt) ?: preferenceDefault
    }

    fun toDisplayString(context: Context): String =
        getDisplayPrefix() + ' ' + context.getString(resourceId)

    private fun getDisplayPrefix(): String =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) LEGACY_GESTURE_PREFIX else GESTURE_PREFIX
}

/**
 * How the screen is segmented for tap gestures.
 * The modes are incompatible ([NINE_POINT] defines points which are ambiguous in [FOUR_POINT]).
 * @see FOUR_POINT
 */
enum class TapGestureMode {
    /**
     * The cardinal directions: up, down, left & right.
     * Draw a line from corner to corner diagonally, each touch target fully handles the
     * edge which it is associated with
     * four-point and nine-point are thus incompatible because the four-point center and corners
     * are ambiguous in a nine-point system and thus not interchangeable
     */
    FOUR_POINT,
    /**
     * Divide the screen into 9 equally sized squares for touch targets.
     * Better for tablets
     * See: #7537
     */
    NINE_POINT;

    companion object {
        @JvmStatic
        fun fromPreference(preferences: SharedPreferences): TapGestureMode =
            when (preferences.getBoolean("gestureCornerTouch", false)) {
                true -> NINE_POINT
                false -> FOUR_POINT
            }
    }
}
