package com.ichi2.utils;

import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.WebView;

public class WebViewDebugging {

    public static void initializeDebugging(SharedPreferences sharedPrefs) {
        // DEFECT: We might be able to cache this value: check what happens on WebView Renderer crash
        // On your desktop use chrome://inspect to connect to emulator WebViews
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            boolean enableDebugging = sharedPrefs.getBoolean("html_javascript_debugging", false);
            WebView.setWebContentsDebuggingEnabled(enableDebugging);
        }
    }
}
