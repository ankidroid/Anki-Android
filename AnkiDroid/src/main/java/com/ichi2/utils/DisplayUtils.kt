// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.content.Context
import android.graphics.Insets
import android.graphics.Point
import android.os.Build
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager

object DisplayUtils {
    @Suppress("DEPRECATION") // #9333: defaultDisplay & getSize
    fun getDisplayDimensions(wm: WindowManager): Point {
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            val insets: Insets =
                wm.currentWindowMetrics
                    .getWindowInsets()
                    .getInsetsIgnoringVisibility(
                        WindowInsets.Type.navigationBars()
                            or WindowInsets.Type.displayCutout(),
                    )
            point.x = bounds.width() - (insets.right + insets.left)
            point.y = bounds.height() - (insets.top + insets.bottom)
        } else {
            val display = wm.defaultDisplay
            display.getSize(point)
        }
        return point
    }

    fun getDisplayDimensions(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return getDisplayDimensions(wm)
    }

    /** Allow the window to be resized when an input method is shown,
     * so that its contents are not covered by the input method */
    @Suppress("DEPRECATION") // 7110: SOFT_INPUT_ADJUST_RESIZE
    fun resizeWhenSoftInputShown(window: Window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
