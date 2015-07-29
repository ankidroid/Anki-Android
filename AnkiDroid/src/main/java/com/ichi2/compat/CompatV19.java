
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.R;

/** Implementation of {@link Compat} for SDK level 19 */
@TargetApi(19)
public class CompatV19 extends CompatV16 implements Compat {

    @Override
    public void setFullScreen(NavigationDrawerActivity activity) {
        // This setting is enabled on Navigation Drawer activities in order to correctly
        // display the status bar. Having it enabled prevents the window from consuming
        // all the space made available when hiding the system UI, so we turn it off here.
        // Since we are hiding the status bar, there is no problem with turning this off.
        activity.getDrawerLayout().setFitsSystemWindows(false);

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