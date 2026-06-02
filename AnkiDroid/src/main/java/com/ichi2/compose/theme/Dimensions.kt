// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic spacing tokens for Compose screens, defined by intent rather than exact pixels.
 * * Access these via [MaterialTheme.dimensions] to keep spacing consistent with
 * Material 3 colors and typography (e.g., `MaterialTheme.dimensions.screenEdge`).
 *
 * @property screenEdge Outer padding between content and the device edge.
 * @property sectionGap Gap between independent UI sections.
 * @property itemGap Gap between related items in a row or column.
 * @property tightGap Fine-grained spacing inside compound elements.
 * @property microGap Sub-element spacing (e.g., an eyebrow text above a title).
 */
@Immutable
data class Dimensions(
    val screenEdge: Dp = 24.dp,
    val sectionGap: Dp = 16.dp,
    val itemGap: Dp = 12.dp,
    val tightGap: Dp = 8.dp,
    val microGap: Dp = 4.dp,
)

/**
 * Provides the active [Dimensions] down the Compose tree.
 * * For cleaner code, read from [MaterialTheme.dimensions] instead of using this directly.
 * You only need to use this directly when overriding tokens via `CompositionLocalProvider`.
 */
val LocalDimensions = staticCompositionLocalOf { Dimensions() }

/**
 * A handy extension to access spacing tokens the exact same way you access
 * [MaterialTheme.colorScheme] or [MaterialTheme.typography].
 */
val MaterialTheme.dimensions: Dimensions
    @Composable
    @ReadOnlyComposable
    get() = LocalDimensions.current
