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
package com.ichi2.anki.widgets;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;


import java.util.List;

/**
 * Originally created by Paul Woitaschek (http://www.paul-woitaschek.de, woitaschek@posteo.de)
 * Defines the behavior for the floating action button. If the dependency is a Snackbar, move the
 * fab up.
 */
public class FabBehavior extends CoordinatorLayout.Behavior<View> {

    private float mTranslationY;

    public FabBehavior() {
        super();
    }

    public FabBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private static float getFabTranslationYForSnackbar(CoordinatorLayout parent, View fab) {
        float minOffset = 0.0F;
        List<View> dependencies = parent.getDependencies(fab);
        int i = 0;

        for (int z = dependencies.size(); i < z; ++i) {
            View view = dependencies.get(i);
            if (view instanceof Snackbar.SnackbarLayout && parent.doViewsOverlap(fab, view)) {
                minOffset = Math.min(minOffset, view.getTranslationY() - (float) view.getHeight());
            }
        }

        return minOffset;
    }

    @Override
    public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull View fab, @NonNull View dependency) {
        if (dependency instanceof Snackbar.SnackbarLayout && fab.getVisibility() == View.VISIBLE) {
            float translationY = getFabTranslationYForSnackbar(parent, fab);
            if (translationY != this.mTranslationY) {
                ViewCompat.animate(fab).cancel();
                fab.setTranslationY(translationY);
                this.mTranslationY = translationY;
            }
        }
        return false;
    }

    @Override
    public void onDependentViewRemoved(@NonNull CoordinatorLayout parent, @NonNull View fab, @NonNull View dependency) {
        super.onDependentViewRemoved(parent, fab, dependency);
        if (dependency instanceof Snackbar.SnackbarLayout && fab.getVisibility() == View.VISIBLE) {
            float translationY = getFabTranslationYForSnackbar(parent, fab);
            if (translationY == this.mTranslationY) {
                ViewCompat.animate(fab).cancel();
                fab.setTranslationY(0);
                this.mTranslationY = 0;
            }
        }
    }
}