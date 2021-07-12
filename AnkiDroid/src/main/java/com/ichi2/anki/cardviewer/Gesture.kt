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
import com.ichi2.anki.R

/** https://www.fileformat.info/info/unicode/char/235d/index.htm (similar to a finger)  */
const val GESTURE_PREFIX = "\u235D"

// TODO: Code and preference defaults are inconsistent: #8066
enum class Gesture(
    @get:JvmName("getResourceId") val mResourceId: Int,
    private val mPreferenceKey: String,
    private val mPreferenceDefault: ViewerCommand
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
    TAP_BOTTOM_RIGHT(R.string.gestures_corner_tap_bottom_right, "gestureTapBottomRight", ViewerCommand.COMMAND_NOTHING),
    VOLUME_UP(R.string.gestures_volume_up, "gestureVolumeUp", ViewerCommand.COMMAND_NOTHING),
    VOLUME_DOWN(R.string.gestures_volume_down, "gestureVolumeDown", ViewerCommand.COMMAND_NOTHING);

    fun fromPreference(prefs: SharedPreferences): ViewerCommand {
        val value = prefs.getString(mPreferenceKey, null) ?: return mPreferenceDefault

        val valueAsInt = Integer.parseInt(value)

        return ViewerCommand.fromInt(valueAsInt) ?: mPreferenceDefault
    }

    fun toDisplayString(context: Context): String {
        return GESTURE_PREFIX + ' ' + context.getString(mResourceId)
    }
}
