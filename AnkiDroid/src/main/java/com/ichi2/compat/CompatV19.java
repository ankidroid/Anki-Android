
package com.ichi2.compat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.res.Resources;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.RelativeLayout;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.R;

/** Implementation of {@link Compat} for SDK level 19 */
@TargetApi(19)
public class CompatV19 extends CompatV16 implements Compat {
    private static final int ANIMATION_DURATION = 200;

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

        // Hack required by MaterialDrawer library to get the Toolbar to display correctly in fullscreen mode
        Resources res = a.getResources();
        a.findViewById(R.id.main_layout).setFitsSystemWindows(true);
        int topMargin = (int) res.getDimension(R.dimen.tool_bar_top_padding);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)a.findViewById(R.id.toolbar).getLayoutParams();
        lp.setMargins(0, topMargin, 0, 0);
        a.findViewById(R.id.toolbar).setLayoutParams(lp);

        // Show / hide the Action bar together with the status bar
        View decorView = a.getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int flags) {
                        final Toolbar toolbar = (Toolbar) a.findViewById(R.id.toolbar);
                        final RelativeLayout topbar = (RelativeLayout) a.findViewById(R.id.top_bar);
                        if (toolbar == null || topbar == null) {
                            return;
                        }
                        // Note that system bars will only be "visible" if none of the
                        // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
                        boolean visible = (flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                        if (visible) {
                            toolbar.setAlpha(0.0f);
                            toolbar.setVisibility(View.VISIBLE);
                            toolbar.animate().alpha(1f).setDuration(ANIMATION_DURATION).setListener(null);
                            topbar.setAlpha(0.0f);
                            topbar.setVisibility(View.VISIBLE);
                            topbar.animate().alpha(1f).setDuration(ANIMATION_DURATION).setListener(null);
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
                            topbar.animate()
                                    .alpha(0f)
                                    .setDuration(ANIMATION_DURATION)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            topbar.setVisibility(View.GONE);
                                        }
                                    });
                        }
                    }
                });
    }
}