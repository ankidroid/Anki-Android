
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.webkit.WebView;

/**
 * Implementation of {@link Compat} for SDK level 7 for Nooks, e.g. Simple Touch.
 * <p>
 * This device does not support scrollbar fading.
 **/
@TargetApi(7)
public class CompatV7Nook extends CompatV7 implements Compat {

    @Override
    public void setScrollbarFadingEnabled(WebView webview, boolean enable) {
    }

}
