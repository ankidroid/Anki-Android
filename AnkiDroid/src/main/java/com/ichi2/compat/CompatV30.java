

package com.ichi2.compat;



import android.annotation.TargetApi;

import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.ichi2.anki.R;
import com.ichi2.anki.reviewer.FullScreenMode;


/** Implementation of {@link Compat} for SDK level 30 and higher. Check {@link Compat}'s for more detail. */
@TargetApi(30)
public class CompatV30 extends CompatV26 implements Compat {
    @Override
    public void setFullscreen(Window window) {
        window.getDecorView().getWindowInsetsController().hide(WindowInsets.Type.statusBars());
    }

    @Override
    public void hideSystembars(Window window) {
        window.setDecorFitsSystemWindows(false);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public boolean isImmersiveSystemUiVisible(Window window) {
        return (window.getDecorView().getWindowInsetsController().getSystemBarsAppearance()) == 0;
    }
}