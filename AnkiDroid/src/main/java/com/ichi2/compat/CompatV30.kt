/*
 * Copyright (c) 2022 Nishant Bhandari <nishantbhandari0019@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.compat

import android.annotation.TargetApi
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/** Implementation of [Compat] for SDK level 30 and higher. Check [Compat]'s for more detail.  */
@TargetApi(30)
class CompatV30 : CompatV29(), Compat {
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
