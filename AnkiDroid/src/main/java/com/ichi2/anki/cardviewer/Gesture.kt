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

import com.ichi2.anki.R

enum class Gesture(@get:JvmName("getResourceId") val mResourceId: Int) {
    SWIPE_UP(R.string.gestures_swipe_up),
    SWIPE_DOWN(R.string.gestures_swipe_down),
    SWIPE_LEFT(R.string.gestures_swipe_left),
    SWIPE_RIGHT(R.string.gestures_swipe_right),
    TAP_TOP_LEFT(R.string.gestures_corner_tap_top_left),
    TAP_TOP(R.string.gestures_tap_top),
    TAP_TOP_RIGHT(R.string.gestures_corner_tap_top_right),
    TAP_LEFT(R.string.gestures_tap_left),
    TAP_CENTER(R.string.gestures_corner_tap_middle_center),
    TAP_RIGHT(R.string.gestures_tap_right),
    TAP_BOTTOM_LEFT(R.string.gestures_corner_tap_bottom_left),
    TAP_BOTTOM(R.string.gestures_tap_bottom),
    TAP_BOTTOM_RIGHT(R.string.gestures_corner_tap_bottom_right);
}
