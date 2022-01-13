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
package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.ViewConfiguration
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.TapGestureMode
import com.ichi2.anki.cardviewer.TapGestureMode.Companion.fromPreference
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.floor

class GestureMapper {
    var tapGestureMode = TapGestureMode.NINE_POINT
        private set
    private var mSwipeMinDistance = -1
    private var mSwipeThresholdVelocity = -1
    fun init(preferences: SharedPreferences) {
        val sensitivity = preferences.getInt("swipeSensitivity", 100)
        tapGestureMode = fromPreference(preferences)

        // ViewConfiguration can be used statically but it must be initialized during Android application lifecycle
        // Else, when Robolectric executes in the CI it accesses AnkiDroidApp.getInstance before it exists #9173
        if (VIEW_CONFIGURATION == null) {
            // Set good default values for swipe detection
            VIEW_CONFIGURATION = ViewConfiguration.get(AnkiDroidApp.getInstance())
            DEFAULT_SWIPE_MIN_DISTANCE = VIEW_CONFIGURATION!!.scaledPagingTouchSlop
            DEFAULT_SWIPE_THRESHOLD_VELOCITY = VIEW_CONFIGURATION!!.scaledMinimumFlingVelocity
        }
        if (sensitivity != 100) {
            val sens = 100.0f / sensitivity
            mSwipeMinDistance = (DEFAULT_SWIPE_MIN_DISTANCE * sens + 0.5f).toInt()
            mSwipeThresholdVelocity = (DEFAULT_SWIPE_THRESHOLD_VELOCITY * sens + 0.5f).toInt()
        } else {
            mSwipeMinDistance = DEFAULT_SWIPE_MIN_DISTANCE
            mSwipeThresholdVelocity = DEFAULT_SWIPE_THRESHOLD_VELOCITY
        }
    }

    fun gesture(
        dx: Float,
        dy: Float,
        velocityX: Float,
        velocityY: Float,
        isSelecting: Boolean,
        isXScrolling: Boolean,
        isYScrolling: Boolean
    ): Gesture? {
        try {
            if (abs(dx) > abs(dy)) {
                // horizontal swipe if moved further in x direction than y direction
                if (dx > mSwipeMinDistance && abs(velocityX) > mSwipeThresholdVelocity && !isXScrolling && !isSelecting) {
                    return Gesture.SWIPE_RIGHT
                } else if (dx < -mSwipeMinDistance && abs(velocityX) > mSwipeThresholdVelocity && !isXScrolling && !isSelecting) {
                    return Gesture.SWIPE_LEFT
                }
            } else {
                // otherwise vertical swipe
                if (dy > mSwipeMinDistance && abs(velocityY) > mSwipeThresholdVelocity && !isYScrolling) {
                    return Gesture.SWIPE_DOWN
                } else if (dy < -mSwipeMinDistance && abs(velocityY) > mSwipeThresholdVelocity && !isYScrolling) {
                    return Gesture.SWIPE_UP
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "onFling Exception")
        }
        return null
    }

    fun gesture(height: Int, width: Int, posX: Float, posY: Float): Gesture? {
        return if (width == 0 || height == 0) {
            null
        } else when (tapGestureMode) {
            TapGestureMode.FOUR_POINT -> fromTap(height, width, posX, posY)
            TapGestureMode.NINE_POINT -> fromTapCorners(height, width, posX, posY)
        }
    }

    private enum class TriState {
        LOW, MID, HIGH
    }

    companion object {
        private var VIEW_CONFIGURATION: ViewConfiguration? = null
        private var DEFAULT_SWIPE_MIN_DISTANCE = 0
        private var DEFAULT_SWIPE_THRESHOLD_VELOCITY = 0
        private fun fromTap(height: Int, width: Int, posX: Float, posY: Float): Gesture {
            val gestureIsRight = posY > height * (1 - posX / width)
            return if (posX > posY / height * width) {
                if (gestureIsRight) {
                    Gesture.TAP_RIGHT
                } else {
                    Gesture.TAP_TOP
                }
            } else {
                if (gestureIsRight) {
                    Gesture.TAP_BOTTOM
                } else {
                    Gesture.TAP_LEFT
                }
            }
        }

        private fun fromTapCorners(height: Int, width: Int, posX: Float, posY: Float): Gesture {
            val heightSegment = height / 3.0
            val widthSegment = width / 3.0
            val wSector = clamp(posX / widthSegment)
            val hSector = clamp(posY / heightSegment)
            return when (wSector) {
                TriState.LOW -> when (hSector) {
                    TriState.LOW -> Gesture.TAP_TOP_LEFT
                    TriState.MID -> Gesture.TAP_LEFT
                    TriState.HIGH -> Gesture.TAP_BOTTOM_LEFT
                }
                TriState.MID -> when (hSector) {
                    TriState.LOW -> Gesture.TAP_TOP
                    TriState.MID -> Gesture.TAP_CENTER
                    TriState.HIGH -> Gesture.TAP_BOTTOM
                }
                TriState.HIGH -> when (hSector) {
                    TriState.LOW -> Gesture.TAP_TOP_RIGHT
                    TriState.MID -> Gesture.TAP_RIGHT
                    TriState.HIGH -> Gesture.TAP_BOTTOM_RIGHT
                }
            }
        }

        @KotlinCleanup("val")
        // clamps the value from LOW-MID-HIGH
        private fun clamp(value: Double): TriState {
            val `val` = floor(value)
            if (`val` >= 2) {
                return TriState.HIGH
            }
            return if (`val` < 1) {
                TriState.LOW
            } else TriState.MID
        }
    }
}
