// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import androidx.core.graphics.Insets
import com.ichi2.utils.Dp
import com.ichi2.utils.dp

/**
 * Helper to build [Insets] using [Dp]
 *
 * Default parameters allow for succinct code:
 *
 * ```kt
 * insetsOf(top = 24.dp)
 * ```
 *
 * @see Insets.of
 */
context(context: Context)
fun insetsOf(
    left: Dp = 0.dp,
    top: Dp = 0.dp,
    right: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Insets =
    Insets.of(
        left.toPx(context),
        top.toPx(context),
        right.toPx(context),
        bottom.toPx(context),
    )
