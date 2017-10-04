package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Intent;

/** Implementation of {@link Compat} for SDK level 23 */
@TargetApi(23)
public class CompatV23 extends CompatV21 implements Compat {

    public static final String ACTION_PROCESS_TEXT = Intent.ACTION_PROCESS_TEXT;
    public static final String EXTRA_PROCESS_TEXT = Intent.EXTRA_PROCESS_TEXT;

}
