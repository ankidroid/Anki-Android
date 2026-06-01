/*
 *  Copyright (c) 2026 Tim Rae <perceptualchaos2@gmail.com>
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

package com.ichi2.anki.ui.compose

import androidx.annotation.AttrRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.ichi2.themes.Themes
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Bridges the host activity's AppCompat/Material-Components theme into Compose's
 * [MaterialTheme] so Compose surfaces pick up the user-selected AnkiDroid theme.
 *
 * For each slot we read the corresponding theme attribute. Some AnkiDroid themes
 * define M3 attributes with alpha (e.g. `colorSurfaceContainer = #0F03A9F4`,
 * intended for compositing in the View hierarchy); those are composited against
 * `?android:colorBackground` so the resulting Compose color is fully opaque.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun AnkiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = Themes.isNightTheme

    fun themeColor(
        @AttrRes attr: Int,
        fallback: Color,
    ): Color {
        val fallbackArgb = fallback.toArgb()
        val resolved = MaterialColors.getColor(context, attr, fallbackArgb)
        if (resolved == fallbackArgb) return fallback
        val alpha = resolved ushr 24
        if (alpha == 0xFF) return Color(resolved)
        val background =
            MaterialColors.getColor(context, android.R.attr.colorBackground, fallbackArgb) or
                0xFF000000.toInt()
        return Color(ColorUtils.compositeColors(resolved, background))
    }

    val base = if (isDark) darkColorScheme() else lightColorScheme()
    val scheme =
        base.copy(
            primary = themeColor(AppCompatR.attr.colorPrimary, base.primary),
            onPrimary = themeColor(MaterialR.attr.colorOnPrimary, base.onPrimary),
            surface = themeColor(MaterialR.attr.colorSurface, base.surface),
            onSurface = themeColor(MaterialR.attr.colorOnSurface, base.onSurface),
            onSurfaceVariant = themeColor(MaterialR.attr.colorOnSurfaceVariant, base.onSurfaceVariant),
            background = themeColor(android.R.attr.colorBackground, base.background),
            onBackground = themeColor(MaterialR.attr.colorOnSurface, base.onBackground),
            surfaceContainer = themeColor(MaterialR.attr.colorSurfaceContainer, base.surfaceContainer),
            secondaryContainer = themeColor(MaterialR.attr.colorSecondaryContainer, base.secondaryContainer),
            onSecondaryContainer = themeColor(MaterialR.attr.colorOnSecondaryContainer, base.onSecondaryContainer),
        )

    MaterialTheme(colorScheme = scheme, content = content)
}
