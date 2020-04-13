package com.ichi2.compat;

import android.annotation.TargetApi;
import android.text.Html;
import android.text.Spanned;

@TargetApi(24)
public class CompatV24 extends CompatV23 implements Compat {

    public Spanned fromHtml(String htmlString) {
        return Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY);
    }
}
