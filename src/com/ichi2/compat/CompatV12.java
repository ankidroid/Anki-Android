package com.ichi2.compat;

import android.annotation.TargetApi;
import android.webkit.CookieManager;

/** Implementation of {@link Compat} for SDK level 12 */
@TargetApi(12)
public class CompatV12 extends CompatV9 implements Compat {

    @Override
    public void enableFileSchemeCookies() {
        CookieManager.setAcceptFileSchemeCookies(true);
    }

}