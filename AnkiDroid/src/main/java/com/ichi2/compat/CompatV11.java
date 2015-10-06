
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.view.WindowManager;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.NavigationDrawerActivity;

import java.text.Normalizer;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 11 (Honeycomb) */
@TargetApi(11)
public class CompatV11 extends CompatV10 implements Compat {

    /**
     * Restart the activity and discard old backstack, creating it new from the heirarchy in the manifest
     */
    public void restartActivityInvalidateBackstack(AnkiActivity activity) {
        Timber.i("AnkiActivity -- restartActivityInvalidateBackstack()");
        Intent intent = new Intent();
        intent.setClass(activity, activity.getClass());
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(activity);
        stackBuilder.addNextIntentWithParentStack(intent);
        stackBuilder.startActivities(new Bundle());
        activity.finishWithoutAnimation();
    }


    @Override
    public void setFullScreen(NavigationDrawerActivity activity) {
        super.setFullScreen(activity);
        registerHideActionBarListener(activity);
    }

    protected void registerHideActionBarListener(final NavigationDrawerActivity activity) {
        View decorView = activity.getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        // Note that system bars will only be "visible" if none of the
                        // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
                        ActionBar ab = activity.getSupportActionBar();
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            if (ab != null) {
                                ab.show();
                            }
                        } else {
                            if (ab != null) {
                                ab.hide();
                            }
                        }
                    }
                });
    }

}
