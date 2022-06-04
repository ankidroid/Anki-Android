package com.ichi2.compat

import android.annotation.TargetApi
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/** Implementation of [Compat] for SDK level 30 and higher. Check [Compat]'s for more detail.  */
@TargetApi(30)
class CompatV30 : CompatV26(), Compat {
    override fun setFullscreen(window: Window?) {
        window?.decorView?.windowInsetsController!!.hide(WindowInsets.Type.statusBars())
    }

    override fun hideSystemBars(window: Window?) {
        window?.setDecorFitsSystemWindows(false)
        val controller = window?.insetsController
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun isImmersiveSystemUiVisible(window: Window?): Boolean {
        return window?.decorView?.windowInsetsController!!.systemBarsAppearance == 0
    }
}
