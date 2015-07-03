package com.ichi2.compat;

import android.annotation.TargetApi;
import android.webkit.CookieManager;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 12 */
@TargetApi(12)
public class CompatV12 extends CompatV11 implements Compat {

    // On API level 12 and higher, WebKit prevents file scheme pages from accessing cookies.
    // This function removes this restriction.
    @Override
    public void enableCookiesForFileSchemePages() {
        try {
            CookieManager.setAcceptFileSchemeCookies(true);
        } catch (Throwable e) {
            Timber.e(e, "Runtime exception enabling cookies");
        }
    }
}
