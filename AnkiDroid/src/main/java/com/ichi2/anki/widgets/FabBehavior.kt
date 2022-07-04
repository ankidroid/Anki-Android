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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.UIUtils

/**
 * Originally created by Paul Woitaschek (http://www.paul-woitaschek.de, woitaschek@posteo.de)
 * Defines the behavior for the floating action button. If the dependency is a Snackbar, move the
 * fab up.
 */
class FabBehavior : CoordinatorLayout.Behavior<View> {
    private var mTranslationY = 0f

    constructor() : super() {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency is SnackbarLayout
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, fab: View, dependency: View): Boolean {
        if (dependency is SnackbarLayout && fab.visibility == View.VISIBLE) {
            val translationY = getFabTranslationYForSnackbar(parent, fab)
            if (translationY != mTranslationY) {
                ViewCompat.animate(fab).cancel()
                fab.translationY = translationY
                mTranslationY = translationY
            }
        }
        return false
    }

    override fun onDependentViewRemoved(parent: CoordinatorLayout, fab: View, dependency: View) {
        super.onDependentViewRemoved(parent, fab, dependency)
        if (dependency is SnackbarLayout && fab.visibility == View.VISIBLE) {
            val translationY = getFabTranslationYForSnackbar(parent, fab)
            if (translationY == mTranslationY) {
                ViewCompat.animate(fab).cancel()
                fab.translationY = 0f
                mTranslationY = 0f
            }
        }
    }
}

/**
 * Here, fab is the entire layout containing the button itself, related menu and the margin.
 * It extends to the very bottom of the screen.
 * We allow the fab and the snackbar to overlap a bit, so that the fab doesn't appear too high.
 */
private val MAX_OVERLAP = UIUtils.convertDpToPixel(10f, AnkiDroidApp.getInstance().applicationContext)

private fun getFabTranslationYForSnackbar(parent: CoordinatorLayout, fab: View): Float {
    return parent.getDependencies(fab)
        .filter { view -> view is SnackbarLayout && view.isVisible }
        .minOfOrNull { snackbar ->
            val untranslatedFabBottom = fab.bottom
            val effectiveSnackbarTop = snackbar.top + snackbar.translationY
            val overlap = untranslatedFabBottom - effectiveSnackbarTop

            if (overlap > MAX_OVERLAP) -(overlap - MAX_OVERLAP) else 0f
        } ?: 0f
}
