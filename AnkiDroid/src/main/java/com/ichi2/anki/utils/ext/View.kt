/*
 * Copyright (c) 2025 Danilo Mendes <danilodanicomendes@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils.ext

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

fun View.isTouchWithinBounds(event: MotionEvent): Boolean {
    val rect = Rect().apply { getDrawingRect(this) }
    return rect.contains((left + event.x).toInt(), (top + event.y).toInt())
}
