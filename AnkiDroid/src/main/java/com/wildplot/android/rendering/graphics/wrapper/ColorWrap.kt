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

import android.graphics.Color

class ColorWrap {
    // android.graphics.Color
    val colorValue: Int
    val red: Int
        get() = Color.red(colorValue)
    val green: Int
        get() = Color.green(colorValue)
    val blue: Int
        get() = Color.blue(colorValue)

    constructor(colorValue: Int) : super() {
        this.colorValue = colorValue
    }

    constructor(colorValue: Int, af: Float) : super() {
        val a = Math.round(af * 255)
        val r = Color.red(colorValue)
        val g = Color.green(colorValue)
        val b = Color.blue(colorValue)
        this.colorValue = Color.argb(a, r, g, b)
    }

    constructor(r: Int, g: Int, b: Int) {
        colorValue = Color.rgb(r, g, b)
    }

    constructor(r: Int, g: Int, b: Int, a: Int) {
        colorValue = Color.argb(a, r, g, b)
    }

    constructor(r: Float, g: Float, b: Float, a: Float) {
        colorValue = Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    constructor(r: Float, g: Float, b: Float) {
        colorValue = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    companion object {
        val red = ColorWrap(Color.RED)
        val RED = ColorWrap(Color.RED)
        val BLACK = ColorWrap(Color.BLACK)
        val black = ColorWrap(Color.BLACK)
        val GREEN = ColorWrap(Color.GREEN)
        val green = ColorWrap(Color.GREEN)
        val LIGHT_GRAY = ColorWrap(Color.LTGRAY)
        val WHITE = ColorWrap(Color.WHITE)
        val white = ColorWrap(Color.WHITE)
    }
}
