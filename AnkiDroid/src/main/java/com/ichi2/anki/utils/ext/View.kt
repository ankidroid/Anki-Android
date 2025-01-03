/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

import android.content.Context
import android.view.View
import com.ichi2.anki.scheduling.dpToPx

/**
 * Sets the relative padding for all dimensions of the view
 *
 * The view may add on the space required to display the scrollbars,
 * depending on the style and visibility of the scrollbars.
 * So the values returned from `getPadding` calls
 * may be different from the values set in this call.
 */
fun View.setPaddingRelative(px: Int) = setPaddingRelative(px, px, px, px)

/**
 * Sets the relative padding for all dimensions of the view
 *
 * The view may add on the space required to display the scrollbars,
 * depending on the style and visibility of the scrollbars.
 * So the values returned from `getPadding` calls
 * may be different from the values set in this call.
 */
fun View.setPaddingRelative(dp: DisplayPixels) = setPaddingRelative(dp.toPx(context))

/**
 * Sets the relative padding
 *
 * The view may add on the space required to display the scrollbars,
 * depending on the style and visibility of the scrollbars.
 * So the values returned from `getPadding` calls
 * may be different from the values set in this call.
 */
// Since we're in Kotlin, this now allows named arguments!
fun View.setPaddingRelative(
    start: DisplayPixels,
    top: DisplayPixels,
    end: DisplayPixels,
    bottom: DisplayPixels,
) = setPaddingRelative(start.toPx(context), top.toPx(context), end.toPx(context), bottom.toPx(context))

/** Returns a [DisplayPixels] instance equal to this [Int] number of display pixels. */
val Int.dp
    get() = DisplayPixels(dp = this)

/**
 * Helper for 'display pixels' to 'pixels' conversions
 */
@JvmInline
value class DisplayPixels(
    val dp: Int,
) {
    // TODO: improve once we have context parameters
    fun toPx(context: Context) = dp.dpToPx(context)
}
