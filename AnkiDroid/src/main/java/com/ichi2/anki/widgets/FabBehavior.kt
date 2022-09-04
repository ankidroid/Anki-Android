/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.UIUtils

/**
 * Originally created by Paul Woitaschek (http://www.paul-woitaschek.de, woitaschek@posteo.de)
 * Defines the behavior for the floating action button. If the dependency is a Snackbar, move the
 * fab up.
 */
@Suppress("UNUSED")
class FabBehavior constructor(context: Context, attrs: AttributeSet? = null) :
    CoordinatorLayout.Behavior<View>(context, attrs) {

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency is SnackbarLayout
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, fab: View, snackbar: View): Boolean {
        val fabTranslationY = calculateFabTranslationYForSnackbar(fab, snackbar)
        translateFabToYWithAnimation(fab, fabTranslationY, SNACKBAR_FADE_IN_DURATION)
        return false
    }

    override fun onDependentViewRemoved(parent: CoordinatorLayout, fab: View, snackbar: View) {
        translateFabToYWithAnimation(fab, 0f, SNACKBAR_FADE_OUT_DURATION)
    }

    private var lastFabTranslationY = 0f

    private fun translateFabToYWithAnimation(fab: View, fabTranslationY: Float, duration: Long) {
        if (lastFabTranslationY != fabTranslationY) {
            lastFabTranslationY = fabTranslationY
            fab.animate()
                .setInterpolator(DecelerateInterpolator(2f))
                .translationY(fabTranslationY)
                .setDuration(duration)
                .start()
        }
    }
}

/**
 * These are the same as:
 *   * [BaseTransientBottomBar.ANIMATION_FADE_IN_DURATION]
 *   * [BaseTransientBottomBar.ANIMATION_FADE_OUT_DURATION]
 */
private const val SNACKBAR_FADE_IN_DURATION = 150L
private const val SNACKBAR_FADE_OUT_DURATION = 75L

/**
 * Here, fab is the entire layout containing the button itself, related menu and the margin.
 * It extends to the very bottom of the screen.
 * We allow the fab and the snackbar to overlap a bit, so that the fab doesn't appear too high.
 */
private val MAX_OVERLAP = UIUtils.convertDpToPixel(10f, AnkiDroidApp.instance.applicationContext)

private fun calculateFabTranslationYForSnackbar(fab: View, snackbar: View): Float {
    val untranslatedFabBottom = fab.bottom
    val effectiveSnackbarTop = snackbar.top + snackbar.translationY
    val overlap = untranslatedFabBottom - effectiveSnackbarTop

    return if (overlap > MAX_OVERLAP) -(overlap - MAX_OVERLAP) else 0f
}
