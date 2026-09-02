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
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
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
 * Builds realistic window insets, for Robolectric.
 *
 * A 24dp status bar is always present, others are optional:
 *
 * ```kt
 * windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp)
 * ```
 *
 * The 'stable' insets ([WindowInsetsCompat.getInsetsIgnoringVisibility]) report the bars'
 * regions whether or not the bars are currently visible: [navBarStableBottom] stays reported
 * while immersive mode hides the bars themselves.
 *
 * @param navBarBottom a navigation bar along the bottom edge. Always appears in portrait,
 * on tablets, or if gesture navigation is enabled.
 * @param navBarRight a navigation bar along the right edge (phones in landscape mode with 3-button
 * navigation)
 * @param navBarStableBottom the bottom navigation bar's region, as still reported while it is hidden
 * @param cutoutLeft a display cutout on the left edge (landscape phone with a notch)
 * @param cutoutTop a display cutout on the top edge (portrait phone with a notch)
 * @param bottomCornerRadius the radius of both bottom rounded display corners
 * @param imeBottom the height of the on-screen keyboard
 * @param barsVisible whether the status and navigation bars are shown; `false` in immersive mode
 */
context(context: Context)
fun windowInsetsOf(
    navBarBottom: Dp = 0.dp,
    navBarRight: Dp = 0.dp,
    navBarStableBottom: Dp = navBarBottom,
    cutoutLeft: Dp = 0.dp,
    cutoutTop: Dp = 0.dp,
    bottomCornerRadius: Dp = 0.dp,
    imeBottom: Dp = 0.dp,
    barsVisible: Boolean = true,
): WindowInsetsCompat =
    WindowInsetsCompat
        .Builder()
        .setInsets(statusBars(), insetsOf(top = if (barsVisible) 24.dp else 0.dp))
        .setInsetsIgnoringVisibility(statusBars(), insetsOf(top = 24.dp))
        .setInsets(navigationBars(), insetsOf(right = navBarRight, bottom = navBarBottom))
        .setInsetsIgnoringVisibility(
            navigationBars(),
            insetsOf(right = navBarRight, bottom = navBarStableBottom),
        ).setInsets(displayCutout(), insetsOf(left = cutoutLeft, top = cutoutTop))
        .setInsetsIgnoringVisibility(displayCutout(), insetsOf(left = cutoutLeft, top = cutoutTop))
        .setInsets(ime(), insetsOf(bottom = imeBottom))
        .setVisible(statusBars() or navigationBars(), barsVisible)
        .apply {
            // set even when zero: Robolectric's WindowInsets.Builder leaks rounded corners between tests
            val radius = bottomCornerRadius.toPx(context)
            for (position in intArrayOf(RoundedCornerCompat.POSITION_BOTTOM_LEFT, RoundedCornerCompat.POSITION_BOTTOM_RIGHT)) {
                setRoundedCorner(position, RoundedCornerCompat(position, radius, radius, radius))
            }
        }.build()

/**
 * Dispatches [windowInsetsOf] at the decor, as an edge-to-edge device does.
 *
 * Robolectric otherwise reports all insets as zero.
 */
fun Activity.dispatchInsets(
    navBarBottom: Dp = 0.dp,
    navBarRight: Dp = 0.dp,
    navBarStableBottom: Dp = navBarBottom,
    cutoutLeft: Dp = 0.dp,
    cutoutTop: Dp = 0.dp,
    bottomCornerRadius: Dp = 0.dp,
    imeBottom: Dp = 0.dp,
    barsVisible: Boolean = true,
) {
    val insets =
        windowInsetsOf(
            navBarBottom = navBarBottom,
            navBarRight = navBarRight,
            navBarStableBottom = navBarStableBottom,
            cutoutLeft = cutoutLeft,
            cutoutTop = cutoutTop,
            bottomCornerRadius = bottomCornerRadius,
            imeBottom = imeBottom,
            barsVisible = barsVisible,
        )
    ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
}

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
