package com.ichi2.compat;

import android.annotation.TargetApi;
import android.app.Activity;

/** Implementation of {@link Compat} for SDK level 9 */
@TargetApi(11)
public class CompatV11 extends CompatV5 implements Compat {
    @Override
    public void invalidateOptionsMenu(Activity activity) {
        activity.invalidateOptionsMenu();
    }
}
