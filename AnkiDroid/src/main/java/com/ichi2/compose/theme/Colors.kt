// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App specific colors that have no Material3 slot.
 *
 * FABs keep the accent the View screens use (the `fab_normal` theme attr)
 * instead of Material3's primaryContainer, which our bridged theme leaves at
 * the baseline purple.
 */
@Immutable
data class AnkiDroidColors(
    val fabContainer: Color,
    val onFab: Color,
)

/** Provided by [AnkiDroidTheme]; the default only shows up in previews and tests. */
val LocalAnkiDroidColors =
    staticCompositionLocalOf {
        AnkiDroidColors(
            fabContainer = lightColorScheme().primaryContainer,
            onFab = lightColorScheme().onPrimaryContainer,
        )
    }

/** Access like [MaterialTheme.colorScheme]: `MaterialTheme.ankiColors.fabContainer`. */
val MaterialTheme.ankiColors: AnkiDroidColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAnkiDroidColors.current
