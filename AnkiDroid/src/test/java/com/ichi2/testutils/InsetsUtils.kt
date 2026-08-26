// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
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

/**
 * Injects insets to simulate an edge-to-edge on a real device.
 *
 * Overlays are displayed as translucent bands so content can be drawn behind them.
 *
 * @param cutoutLeft simulates a display cutout on the left edge, as when a phone with a
 * top notch is rotated to landscape.
 */
@SuppressLint("RtlHardcoded") // insets and cutouts are physical: not layout-direction relative
fun Activity.simulateSystemBars(cutoutLeft: Dp = 0.dp) {
    val statusBarHeight = 24.dp
    val navBarHeight = 48.dp
    val insets =
        WindowInsetsCompat
            .Builder()
            .setInsets(statusBars(), insetsOf(top = statusBarHeight))
            // workaround for 'systemWindowInsets', so snackbars match a real device
            .setInsets(navigationBars(), insetsOf(left = cutoutLeft, bottom = navBarHeight))
            .setInsets(displayCutout(), insetsOf(left = cutoutLeft))
            .build()
    ViewCompat.dispatchApplyWindowInsets(findViewById(android.R.id.content), insets)

    val decor = window.decorView as ViewGroup
    val context: Context = this
    val bands =
        buildList {
            add(FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, statusBarHeight.toPx(context), Gravity.TOP))
            add(FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(context), Gravity.BOTTOM))
            if (cutoutLeft.dp > 0) {
                add(FrameLayout.LayoutParams(cutoutLeft.toPx(context), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT))
            }
        }
    bands.forEach { params ->
        decor.addView(View(this).apply { setBackgroundColor(0x80000000.toInt()) }, params)
    }
    advanceRobolectricLooper()
}
