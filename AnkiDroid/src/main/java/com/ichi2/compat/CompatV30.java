

package com.ichi2.compat;



import android.annotation.TargetApi;

import android.view.Window;
import android.view.WindowInsets;


/** Implementation of {@link Compat} for SDK level 30 and higher. Check  {@link Compat}'s for more detail. */
@TargetApi(30)
public class CompatV30 extends CompatV26 implements Compat {
    @Override
    public void setFullscreen(Window window) {
        window.getDecorView().getWindowInsetsController().hide(WindowInsets.Type.statusBars());
    }
}