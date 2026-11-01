// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.content.Context
import android.view.MenuItem
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Set the icon from a resource ID and handle LTR/RTL layout direction,
 * since ActionMenuView drawables don't handle layout direction by default.
 */
fun MenuItem.setIconRes(
    context: Context,
    resId: Int,
) {
    // Let MenuItem handle the standard icon-setting process, including theme-based tinting.
    setIcon(resId)
    // Set LTR/RTL layout direction on the mutated drawable of the icon.
    icon?.mutate()?.let { drawable ->
        DrawableCompat.setLayoutDirection(drawable, context.resources.configuration.layoutDirection)
    }
}
