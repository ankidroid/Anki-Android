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

package com.ichi2.anki.utils.android

import android.graphics.Color
import androidx.annotation.ColorInt
import com.ichi2.anki.common.utils.ext.clamp

/**
 * Darkens the provided ARGB color by a provided [factor]
 *
 * @param argb The ARGB color to transform
 * @param factor Amount to darken, between 1.0f (no change) and 0.0f (black)
 * @return The darkened color in ARGB
 */
@ColorInt
fun darkenColor(@ColorInt argb: Int, factor: Float): Int {
    val hsv = argb.toHSV()
    // https://en.wikipedia.org/wiki/HSL_and_HSV
    // The third component is the 'value', or 'lightness/darkness'
    hsv[2] = (hsv[2] * factor).clamp(0f, 1f)
    return Color.HSVToColor(hsv)
}

/**
 * Converts an ARGB color to an array of its HSV components
 *
 * [0] is Hue: `[0..360[`
 * [1] is Saturation: `[0...1]`
 * [2] is Value: `[0...1]`
 */
private fun Int.toHSV(): FloatArray = FloatArray(3).also { arr ->
    Color.colorToHSV(this, arr)
}
