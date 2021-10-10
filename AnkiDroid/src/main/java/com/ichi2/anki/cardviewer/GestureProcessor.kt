/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import android.content.SharedPreferences
import com.ichi2.anki.reviewer.GestureMapper

class GestureProcessor(private val processor: ViewerCommand.CommandProcessor?) {
    private var gestureDoubleTap: ViewerCommand? = null
    private var gestureLongclick: ViewerCommand? = null
    private var gestureSwipeUp: ViewerCommand? = null
    private var gestureSwipeDown: ViewerCommand? = null
    private var gestureSwipeLeft: ViewerCommand? = null
    private var gestureSwipeRight: ViewerCommand? = null
    private var gestureTapLeft: ViewerCommand? = null
    private var gestureTapRight: ViewerCommand? = null
    private var gestureTapTop: ViewerCommand? = null
    private var gestureTapBottom: ViewerCommand? = null
    private var gestureTapTopLeft: ViewerCommand? = null
    private var gestureTapTopRight: ViewerCommand? = null
    private var gestureTapCenter: ViewerCommand? = null
    private var gestureTapBottomLeft: ViewerCommand? = null
    private var gestureTapBottomRight: ViewerCommand? = null
    private val gestureMapper = GestureMapper()

    /**
     * Whether the class has been enabled.
     * This requires the "gestures" preference is enabled,
     * and [GestureProcessor.init] has been called
     */
    var isEnabled = false
        private set

    fun init(preferences: SharedPreferences) {
        isEnabled = preferences.getBoolean("gestures", false)
        gestureDoubleTap = Gesture.DOUBLE_TAP.fromPreference(preferences)
        gestureLongclick = Gesture.LONG_TAP.fromPreference(preferences)
        gestureSwipeUp = Gesture.SWIPE_UP.fromPreference(preferences)
        gestureSwipeDown = Gesture.SWIPE_DOWN.fromPreference(preferences)
        gestureSwipeLeft = Gesture.SWIPE_LEFT.fromPreference(preferences)
        gestureSwipeRight = Gesture.SWIPE_RIGHT.fromPreference(preferences)
        gestureMapper.init(preferences)
        gestureTapLeft = Gesture.TAP_LEFT.fromPreference(preferences)
        gestureTapRight = Gesture.TAP_RIGHT.fromPreference(preferences)
        gestureTapTop = Gesture.TAP_TOP.fromPreference(preferences)
        gestureTapBottom = Gesture.TAP_BOTTOM.fromPreference(preferences)
        val useCornerTouch = preferences.getBoolean("gestureCornerTouch", false)
        if (useCornerTouch) {
            gestureTapTopLeft = Gesture.TAP_TOP_LEFT.fromPreference(preferences)
            gestureTapTopRight = Gesture.TAP_TOP_RIGHT.fromPreference(preferences)
            gestureTapCenter = Gesture.TAP_CENTER.fromPreference(preferences)
            gestureTapBottomLeft = Gesture.TAP_BOTTOM_LEFT.fromPreference(preferences)
            gestureTapBottomRight = Gesture.TAP_BOTTOM_RIGHT.fromPreference(preferences)
        }
    }

    fun onTap(height: Int, width: Int, posX: Float, posY: Float): Boolean? {
        val gesture = gestureMapper.gesture(height, width, posX, posY) ?: return false
        return execute(gesture)
    }

    fun onDoubleTap(): Boolean? {
        return execute(Gesture.DOUBLE_TAP)
    }

    fun onLongTap(): Boolean? {
        return execute(Gesture.LONG_TAP)
    }

    fun onFling(dx: Float, dy: Float, velocityX: Float, velocityY: Float, isSelecting: Boolean, isXScrolling: Boolean, isYScrolling: Boolean): Boolean? {
        val gesture = gestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling)
        return execute(gesture)
    }

    private fun execute(gesture: Gesture): Boolean? {
        val command = mapGestureToCommand(gesture)
        return if (command != null) {
            processor?.executeCommand(command)
        } else {
            false
        }
    }

    private fun mapGestureToCommand(gesture: Gesture): ViewerCommand? {
        return when (gesture) {
            Gesture.SWIPE_UP -> gestureSwipeUp
            Gesture.SWIPE_DOWN -> gestureSwipeDown
            Gesture.SWIPE_LEFT -> gestureSwipeLeft
            Gesture.SWIPE_RIGHT -> gestureSwipeRight
            Gesture.TAP_TOP -> gestureTapTop
            Gesture.TAP_TOP_LEFT -> gestureTapTopLeft
            Gesture.TAP_TOP_RIGHT -> gestureTapTopRight
            Gesture.TAP_LEFT -> gestureTapLeft
            Gesture.TAP_CENTER -> gestureTapCenter
            Gesture.TAP_RIGHT -> gestureTapRight
            Gesture.TAP_BOTTOM -> gestureTapBottom
            Gesture.TAP_BOTTOM_LEFT -> gestureTapBottomLeft
            Gesture.TAP_BOTTOM_RIGHT -> gestureTapBottomRight
            Gesture.DOUBLE_TAP -> gestureDoubleTap
            Gesture.LONG_TAP -> gestureLongclick
        }
    }

    /**
     * Whether one of the provided gestures is bound
     * @param gestures the gestures to check
     * @return `false` if none of the gestures are bound. `true` otherwise
     */
    fun isBound(vararg gestures: Gesture): Boolean {
        if (!isEnabled) {
            return false
        }
        for (gesture in gestures) {
            if (mapGestureToCommand(gesture) != ViewerCommand.COMMAND_NOTHING) {
                return true
            }
        }
        return false
    }
}
