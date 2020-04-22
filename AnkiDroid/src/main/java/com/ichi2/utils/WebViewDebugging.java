package com.ichi2.utils;

import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.WebView;

import androidx.annotation.UiThread;

public class WebViewDebugging {

    @UiThread
    public static void initializeDebugging(SharedPreferences sharedPrefs) {
        // DEFECT: We might be able to cache this value: check what happens on WebView Renderer crash
        // On your desktop use chrome://inspect to connect to emulator WebViews
        // Beware: Crash in AnkiDroidApp.onCreate() with:
        /*
        java.lang.RuntimeException: Using WebView from more than one process at once with the same data directory
        is not supported. https://crbug.com/558377 : Lock owner com.ichi2.anki:acra at
        org.chromium.android_webview.AwDataDirLock.a(PG:26)
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            boolean enableDebugging = sharedPrefs.getBoolean("html_javascript_debugging", false);
            WebView.setWebContentsDebuggingEnabled(enableDebugging);
        }
    }
}
