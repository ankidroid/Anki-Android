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

class GestureProcessor(private val mProcessor: ViewerCommand.CommandProcessor) {
    private var mGestureDoubleTap: ViewerCommand? = null
    private var mGestureLongclick: ViewerCommand? = null
    private var mGestureSwipeUp: ViewerCommand? = null
    private var mGestureSwipeDown: ViewerCommand? = null
    private var mGestureSwipeLeft: ViewerCommand? = null
    private var mGestureSwipeRight: ViewerCommand? = null
    private var mGestureTapLeft: ViewerCommand? = null
    private var mGestureTapRight: ViewerCommand? = null
    private var mGestureTapTop: ViewerCommand? = null
    private var mGestureTapBottom: ViewerCommand? = null
    private var mGestureTapTopLeft: ViewerCommand? = null
    private var mGestureTapTopRight: ViewerCommand? = null
    private var mGestureTapCenter: ViewerCommand? = null
    private var mGestureTapBottomLeft: ViewerCommand? = null
    private var mGestureTapBottomRight: ViewerCommand? = null
    private val mGestureMapper = GestureMapper()

    /**
     * Whether the class has been enabled.
     * This requires the "gestures" preference is enabled,
     * and [GestureProcessor.init] has been called
     */
    var isEnabled = false
        private set

    fun init(preferences: SharedPreferences) {
        isEnabled = preferences.getBoolean("gestures", false)
        mGestureDoubleTap = Gesture.DOUBLE_TAP.fromPreference(preferences)
        mGestureLongclick = Gesture.LONG_TAP.fromPreference(preferences)
        mGestureSwipeUp = Gesture.SWIPE_UP.fromPreference(preferences)
        mGestureSwipeDown = Gesture.SWIPE_DOWN.fromPreference(preferences)
        mGestureSwipeLeft = Gesture.SWIPE_LEFT.fromPreference(preferences)
        mGestureSwipeRight = Gesture.SWIPE_RIGHT.fromPreference(preferences)
        mGestureMapper.init(preferences)
        mGestureTapLeft = Gesture.TAP_LEFT.fromPreference(preferences)
        mGestureTapRight = Gesture.TAP_RIGHT.fromPreference(preferences)
        mGestureTapTop = Gesture.TAP_TOP.fromPreference(preferences)
        mGestureTapBottom = Gesture.TAP_BOTTOM.fromPreference(preferences)
        val useCornerTouch = preferences.getBoolean("gestureCornerTouch", false)
        if (useCornerTouch) {
            mGestureTapTopLeft = Gesture.TAP_TOP_LEFT.fromPreference(preferences)
            mGestureTapTopRight = Gesture.TAP_TOP_RIGHT.fromPreference(preferences)
            mGestureTapCenter = Gesture.TAP_CENTER.fromPreference(preferences)
            mGestureTapBottomLeft = Gesture.TAP_BOTTOM_LEFT.fromPreference(preferences)
            mGestureTapBottomRight = Gesture.TAP_BOTTOM_RIGHT.fromPreference(preferences)
        }
    }

    fun onTap(height: Int, width: Int, posX: Float, posY: Float): Boolean {
        val gesture = mGestureMapper.gesture(height, width, posX, posY) ?: return false
        return execute(gesture)
    }

    fun onDoubleTap(): Boolean {
        return execute(Gesture.DOUBLE_TAP)
    }

    fun onLongTap(): Boolean {
        return execute(Gesture.LONG_TAP)
    }

    fun onFling(dx: Float, dy: Float, velocityX: Float, velocityY: Float, isSelecting: Boolean, isXScrolling: Boolean, isYScrolling: Boolean): Boolean {
        val gesture = mGestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling)
        return execute(gesture)
    }

    private fun execute(gesture: Gesture): Boolean {
        val command = mapGestureToCommand(gesture)
        return if (command != null) {
            mProcessor.executeCommand(command)
        } else {
            false
        }
    }

    private fun mapGestureToCommand(gesture: Gesture): ViewerCommand? {
        return when (gesture) {
            Gesture.SWIPE_UP -> mGestureSwipeUp
            Gesture.SWIPE_DOWN -> mGestureSwipeDown
            Gesture.SWIPE_LEFT -> mGestureSwipeLeft
            Gesture.SWIPE_RIGHT -> mGestureSwipeRight
            Gesture.TAP_TOP -> mGestureTapTop
            Gesture.TAP_TOP_LEFT -> mGestureTapTopLeft
            Gesture.TAP_TOP_RIGHT -> mGestureTapTopRight
            Gesture.TAP_LEFT -> mGestureTapLeft
            Gesture.TAP_CENTER -> mGestureTapCenter
            Gesture.TAP_RIGHT -> mGestureTapRight
            Gesture.TAP_BOTTOM -> mGestureTapBottom
            Gesture.TAP_BOTTOM_LEFT -> mGestureTapBottomLeft
            Gesture.TAP_BOTTOM_RIGHT -> mGestureTapBottomRight
            Gesture.DOUBLE_TAP -> mGestureDoubleTap
            Gesture.LONG_TAP -> mGestureLongclick
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