package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Intent;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 23 */
@TargetApi(23)
public class CompatV23 extends CompatV21 implements Compat {

    @Override
    public CharSequence onCardBrowserActionProcessTextIntent(Intent intent) {
        if (intent.getAction() == Intent.ACTION_PROCESS_TEXT) {
            CharSequence search = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            assert search != null;
            return search;
        }
        else {
            return "";
        }
    }
}
