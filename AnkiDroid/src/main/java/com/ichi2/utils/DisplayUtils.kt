// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Insets
import android.graphics.Point
import android.os.Build
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

    /**
     * Keep a floating dialog above the keyboard by letting the window manager resize it.
     *
     * The API 30 replacement (edge-to-edge with IME insets on the content view) is for
     * non-floating windows. Floating dialogs still rely on decor fitting to constrain their
     * height. Activities should handle IME insets in their own layout instead (Issue 7110).
     */
    @Suppress("DEPRECATION") // Floating dialogs still need SOFT_INPUT_ADJUST_RESIZE.
    fun resizeDialogWhenSoftInputShown(dialog: Dialog) {
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
