/***************************************************************************************
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 30 */
@TargetApi(30)
public class CompatV30 extends CompatV28 implements Compat {

    @Override
    public Rect getWindowBounds() {
        WindowManager wm = (WindowManager) AnkiDroidApp.getInstance().getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        return wm.getCurrentWindowMetrics().getBounds();
    }

    @Override
    public void hideStatusBars(Window w) {
        WindowInsetsController insets = w.getInsetsController();
        if (insets != null) {
            insets.hide(WindowInsets.Type.statusBars());
        }
    }

    @Override
    public void setFullScreen(final AbstractFlashcardViewer a) {
        WindowInsetsController insetsController = a.getWindow().getInsetsController();
        if (insetsController == null) {
            Timber.w("Unable to set full screen, WindowInsertsController is null");
            return;
        }
        insetsController.hide(
                WindowInsets.Type.statusBars()
                | WindowInsets.Type.navigationBars() // Test for regression https://github.com/ankidroid/Anki-Android/issues/5245
                | WindowInsets.Type.systemBars()
        );
        insetsController.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_SWIPE
        );

        // Show / hide the Action bar together with the status bar
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(a);
        final int fullscreenMode = Integer.parseInt(prefs.getString("fullscreenMode", "0"));
        CompatHelper.getCompat().setStatusBarColor(a.getWindow(), Themes.getColorFromAttr(a, R.attr.colorPrimaryDark));
        View decorView = a.getWindow().getDecorView();
        decorView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                WindowInsets defaultInsets = view.onApplyWindowInsets(windowInsets);
                final View toolbar = a.findViewById(R.id.toolbar);
                final View answerButtons = a.findViewById(R.id.answer_options_layout);
                final View topbar = a.findViewById(R.id.top_bar);
                if (toolbar == null || topbar == null || answerButtons == null) {
                    return defaultInsets;
                }

                // I think the forward-port here is to do a WindowsInsets.Builder with WindowInsets.getInsetsIgnoringVisibility
                // Construct the result and return it?

                // Note that system bars will only be "visible" if none of the
                // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
//                boolean visible = (flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
//                Timber.d("System UI visibility change. Visible: %b", visible);
//                if (visible) {
//                    showViewWithAnimation(toolbar);
//                    if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
//                        showViewWithAnimation(topbar);
//                        showViewWithAnimation(answerButtons);
//                    }
//                } else {
//                    hideViewWithAnimation(toolbar);
//                    if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
//                        hideViewWithAnimation(topbar);
//                        hideViewWithAnimation(answerButtons);
//                    }
//                }
                return defaultInsets;
            }
        });
    }

    @Override
    public boolean isImmersiveSystemUiVisible(AnkiActivity activity) {
        // We will consider the UI immersive if system bars are set to swipe
        WindowInsetsController insetsController = activity.getWindow().getDecorView().getWindowInsetsController();
        if (insetsController == null) {
            Timber.w("Unable to determine if system UI is immersive. WindowInsetsController was null");
            return false;
        }
         return insetsController.getSystemBarsBehavior() == WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_SWIPE;
    }
}
