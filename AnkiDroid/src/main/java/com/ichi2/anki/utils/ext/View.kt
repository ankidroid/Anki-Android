// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Danilo Mendes <danilodanicomendes@gmail.com>

package com.ichi2.anki.utils.ext

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors

fun View.isTouchWithinBounds(event: MotionEvent): Boolean {
    val rect = Rect().apply { getDrawingRect(this) }
    return rect.contains((left + event.x).toInt(), (top + event.y).toInt())
}

/** Tints the background to [color] at the given opacity (0f..1f) */
fun View.setBackgroundTint(
    color: Int,
    alpha: Float,
) {
    backgroundTintList =
        ColorStateList.valueOf(
            MaterialColors.compositeARGBWithAlpha(color, (alpha * 255).toInt()),
        )
}
