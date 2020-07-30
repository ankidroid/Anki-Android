package com.ichi2.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HtmlUtils {
    //#5188 - compat.fromHtml converts newlines into spaces.
    @Nullable
    public static String convertNewlinesToHtml(@Nullable String html) {
        if (html == null) {
            return null;
        }
        String withoutWindowsLineEndings = html.replace("\r\n", "<br/>");
        //replace unix line endings
        return withoutWindowsLineEndings.replace("\n", "<br/>");
    }


    @NonNull
    public static String escape(@NonNull String html) {
        return TextUtils.htmlEncode(html);
    }
}
