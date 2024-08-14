/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

/** @see View.performClick */
fun View.performClickIfEnabled() {
    if (isEnabled) performClick()
}

/**
 * Performs a hit test using the raw coordinates of the provided [MotionEvent]
 */
fun View.rawHitTest(event: MotionEvent): Boolean {
    val location = IntArray(2)
    getLocationOnScreen(location)

    val rect = Rect()
    getHitRect(rect)

    rect.left += location[0]
    rect.top += location[1]
    rect.right += location[0]
    rect.bottom += location[1]

    return rect.contains(event.rawX.toInt(), event.rawY.toInt())
}
