
package com.ichi2.compat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

/** Implementation of {@link Compat} for SDK level 19 */
@TargetApi(19)
public class CompatV19 extends CompatV17 implements Compat {
    private static final int ANIMATION_DURATION = 200;
    private static final float TRANSPARENCY = 0.90f;

    @Override
    public void setFullScreen(final AbstractFlashcardViewer a) {
        // Set appropriate flags to enable Sticky Immersive mode.
        a.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
        // Show / hide the Action bar together with the status bar
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(a);
        final int fullscreenMode = Integer.parseInt(prefs.getString("fullscreenMode", "0"));
        View decorView = a.getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int flags) {
                        final Toolbar toolbar = (Toolbar) a.findViewById(R.id.toolbar);
                        final LinearLayout answerButtons = (LinearLayout) a.findViewById(
                                R.id.answer_options_layout);
                        final RelativeLayout topbar = (RelativeLayout) a.findViewById(R.id.top_bar);
                        //final DrawerLayout drawerLayout = (DrawerLayout) a.findViewById(R.id.drawer_layout);
                        if (toolbar == null || topbar == null || answerButtons == null) {
                            return;
                        }
                        // Note that system bars will only be "visible" if none of the
                        // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
                        boolean visible = (flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                        //drawerLayout.setFitsSystemWindows(visible);
                        if (visible) {
                            toolbar.setAlpha(0.0f);
                            toolbar.setVisibility(View.VISIBLE);
                            toolbar.animate().alpha(TRANSPARENCY).setDuration(ANIMATION_DURATION)
                                    .setListener(null);
                            if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
                                topbar.setAlpha(0.0f);
                                topbar.setVisibility(View.VISIBLE);
                                topbar.animate().alpha(TRANSPARENCY).setDuration(ANIMATION_DURATION)
                                        .setListener(null);
                                answerButtons.setAlpha(0.0f);
                                answerButtons.setVisibility(View.VISIBLE);
                                answerButtons.animate().alpha(TRANSPARENCY).setDuration(ANIMATION_DURATION)
                                        .setListener(null);
                            }
                        } else {
                            toolbar.animate()
                                    .alpha(0f)
                                    .setDuration(ANIMATION_DURATION)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            toolbar.setVisibility(View.GONE);
                                        }
                                    });
                            if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
                                topbar.animate()
                                        .alpha(0f)
                                        .setDuration(ANIMATION_DURATION)
                                        .setListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                topbar.setVisibility(View.GONE);
                                            }
                                        });
                                answerButtons.animate()
                                        .alpha(0f)
                                        .setDuration(ANIMATION_DURATION)
                                        .setListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                answerButtons.setVisibility(View.GONE);
                                            }
                                        });
                            }
                        }
                    }
                });
    }
}