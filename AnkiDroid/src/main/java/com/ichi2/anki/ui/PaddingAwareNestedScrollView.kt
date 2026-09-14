// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.core.view.isEmpty
import androidx.core.widget.NestedScrollView

/**
 * A [NestedScrollView] whose content scrolls beneath its padding (`clipToPadding="false"`), which
 * keeps focused content clear of that padding.
 *
 * [NestedScrollView] brings a child on screen using its full height, so content focused by the
 * keyboard, or shown by TalkBack, could end up beneath whatever the padding is reserved for, such
 * as the bottom navigation.
 */
class PaddingAwareNestedScrollView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : NestedScrollView(context, attrs, defStyleAttr) {
        override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
            if (isEmpty()) return 0
            val visibleTop = scrollY + paddingTop
            val visibleBottom = scrollY + height - paddingBottom
            val delta =
                when {
                    // below: show its bottom, or its top if it does not fit
                    rect.bottom > visibleBottom && rect.top > visibleTop ->
                        minOf(rect.bottom - visibleBottom, rect.top - visibleTop)
                    // above: show its top, or its bottom if it does not fit
                    rect.top < visibleTop && rect.bottom < visibleBottom ->
                        maxOf(rect.top - visibleTop, rect.bottom - visibleBottom)
                    else -> 0
                }
            val maxScrollY = maxOf(0, getChildAt(0).height - (height - paddingTop - paddingBottom))
            return delta.coerceIn(-scrollY, maxScrollY - scrollY)
        }
    }
