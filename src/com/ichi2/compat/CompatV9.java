package com.ichi2.compat;

import android.annotation.TargetApi;
import android.view.View;

import java.text.Normalizer;

/** Implementation of {@link Compat} for SDK level 9 */
@TargetApi(9)
public class CompatV9 extends CompatV8 implements Compat {
    @Override
    public String normalizeUnicode(String txt) {
        if (!Normalizer.isNormalized(txt, Normalizer.Form.NFD)) {
            return Normalizer.normalize(txt, Normalizer.Form.NFD);
        }
        return txt;
    }
    @Override
    public void setOverScrollModeNever(View v) {
        v.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }
}
