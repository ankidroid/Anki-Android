// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.view.View
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The radius, in pixels, of the larger of the two bottom corner radii.
 */
val WindowInsetsCompat.bottomRoundedCornerRadius: Int
    get() =
        maxOf(
            getRoundedCorner(RoundedCornerCompat.POSITION_BOTTOM_LEFT)?.radius ?: 0,
            getRoundedCorner(RoundedCornerCompat.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
        )

/**
 * The vertical clearance, in pixels, which keeps end-aligned content clear of the
 *  bottom rounded display corners.
 *
 * This takes insets into account: for example in landscape, the 3-button nav bar means very little
 *  if no clearance from the corner is needed, as all content is shifted left.
 *
 * @param view supplies the layout direction: the end-side inset is the left system-bar inset
 * in RTL, otherwise the right
 */
fun WindowInsetsCompat.bottomCornerClearance(view: View): Int {
    val bars = getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    val endInset = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) bars.left else bars.right
    // The subtraction over-approximates the arc: content `d` inboard only needs
    // `r - sqrt(r² - (r - d)²)` of vertical clearance, and `r - d` is never less (a chord vs the
    // arc), so the content never dips into the corner.
    return (bottomRoundedCornerRadius - endInset).coerceAtLeast(0)
}
