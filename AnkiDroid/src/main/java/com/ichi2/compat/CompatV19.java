
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.support.v4.widget.DrawerLayout;
import android.view.View;

import com.ichi2.anki.NavigationDrawerActivity;

/** Implementation of {@link Compat} for SDK level 19 */
@TargetApi(19)
public class CompatV19 extends CompatV16 implements Compat {

    @Override
    public void setFullScreen(NavigationDrawerActivity activity) {
        // This setting is enabled on Navigation Drawer activities in order to correctly
        // display the status bar. Having it enabled prevents the window from consuming
        // all the space made available when hiding the system UI, so we turn it off here.
        // Since we are hiding the status bar, there is no problem with turning this off.
        DrawerLayout drawerLayout = activity.getDrawerLayout();
        if (drawerLayout != null) {
            drawerLayout.setFitsSystemWindows(false);
        }
        // Set appropriate flags to enable Sticky Immersive mode.
        activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}