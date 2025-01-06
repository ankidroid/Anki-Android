package com.ichi2.anki.utils.android

import androidx.annotation.ColorInt

/**
 * Darkens a color by a given factor.
 *
 * @param color The color to darken.
 * @param factor The factor by which to darken the color.
 * @return The darkened color.
 */
@ColorInt
fun darkenColor(@ColorInt color: Int, factor: Float): Int {
    val a = android.graphics.Color.alpha(color)
    val r = (android.graphics.Color.red(color) * factor).toInt()
    val g = (android.graphics.Color.green(color) * factor).toInt()
    val b = (android.graphics.Color.blue(color) * factor).toInt()

    // Ensure the darkened color is different from the input color
    return if (r == android.graphics.Color.red(color) &&
        g == android.graphics.Color.green(color) &&
        b == android.graphics.Color.blue(color)
    ) {
        android.graphics.Color.argb(a, r + 1, g + 1, b + 1)
    } else {
        android.graphics.Color.argb(a, r, g, b)
    }
}
