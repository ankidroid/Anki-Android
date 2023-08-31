/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@wildplot.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.wildplot.android.rendering.graphics.wrapper

import android.graphics.Rect

class RectangleWrap(rect: Rect) {
    val x: Int
    val y: Int
    var width: Int
    var height: Int

    constructor(width: Int, height: Int) : this(Rect(0, 0, width, height)) {}

    fun width(): Int {
        return width
    }

    fun height(): Int {
        return height
    }

    init {
        x = rect.left
        y = rect.top
        height = rect.height()
        width = rect.width()
    }
}
