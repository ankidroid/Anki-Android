
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.webkit.WebSettings;

/** Implementation of {@link Compat} for SDK level 17 */
@TargetApi(17)
public class CompatV17 extends CompatV16 implements Compat {

    @Override
    public void setHTML5MediaAutoPlay(WebSettings webSettings, Boolean allow) {
        webSettings.setMediaPlaybackRequiresUserGesture(!allow);
    }
}
