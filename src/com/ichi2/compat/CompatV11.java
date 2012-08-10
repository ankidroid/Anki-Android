package com.ichi2.compat;

import com.ichi2.anki.R;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;

/** Implementation of {@link Compat} for SDK level 9 */
@TargetApi(11)
public class CompatV11 extends CompatV5 implements Compat {
    @Override
    public void invalidateOptionsMenu(Activity activity) {
        activity.invalidateOptionsMenu();
    }
    @Override
    public void setActionBarBackground(Activity activity) {
    	ActionBar ab = activity.getActionBar();
        ab.setBackgroundDrawable(new ColorDrawable(activity.getResources().getColor(R.color.white_background_night)));
    }
}
