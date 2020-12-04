/***************************************************************************************
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.SharedPreferences;

import timber.log.Timber;

import android.view.View;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

/** Implementation of {@link Compat} for SDK level 19 */
@TargetApi(19)
public class CompatV19 extends CompatV18 implements Compat {
    private static final int ANIMATION_DURATION = 200;
    private static final float TRANSPARENCY = 0.90f;

    @Override
    public void setFullScreen(final AbstractFlashcardViewer a) {
        // Set appropriate flags to enable Sticky Immersive mode.
        a.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        //| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // temporarily disabled due to #5245
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
        );
        // Show / hide the Action bar together with the status bar
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(a);
        final int fullscreenMode = Integer.parseInt(prefs.getString("fullscreenMode", "0"));
        CompatHelper.getCompat().setStatusBarColor(a.getWindow(), Themes.getColorFromAttr(a, R.attr.colorPrimaryDark));
        View decorView = a.getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (flags -> {
                    final View toolbar = a.findViewById(R.id.toolbar);
                    final View answerButtons = a.findViewById(R.id.answer_options_layout);
                    final View topbar = a.findViewById(R.id.top_bar);
                    if (toolbar == null || topbar == null || answerButtons == null) {
                        return;
                    }
                    // Note that system bars will only be "visible" if none of the
                    // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
                    boolean visible = (flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                    Timber.d("System UI visibility change. Visible: %b", visible);
                    if (visible) {
                        showViewWithAnimation(toolbar);
                        if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
                            showViewWithAnimation(topbar);
                            showViewWithAnimation(answerButtons);
                        }
                    } else {
                        hideViewWithAnimation(toolbar);
                        if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
                            hideViewWithAnimation(topbar);
                            hideViewWithAnimation(answerButtons);
                        }
                    }
                });
    }

    private void showViewWithAnimation(final View view) {
        view.setAlpha(0.0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(TRANSPARENCY).setDuration(ANIMATION_DURATION).setListener(null);
    }

    private void hideViewWithAnimation(final View view) {
        view.animate()
                .alpha(0f)
                .setDuration(ANIMATION_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    public boolean isImmersiveSystemUiVisible(AnkiActivity activity) {
        return (activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    }
}