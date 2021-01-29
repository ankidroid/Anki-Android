package com.ichi2.utils;

import android.os.Build;
import android.webkit.WebView;

import com.ichi2.preferences.PreferenceKeys;
import com.ichi2.preferences.Prefs;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;

public class WebViewDebugging {

    private static boolean sHasSetDataDirectory = false;

    @UiThread
    public static void initializeDebugging(Prefs prefs) {
        // DEFECT: We might be able to cache this value: check what happens on WebView Renderer crash
        // On your desktop use chrome://inspect to connect to emulator WebViews
        // Beware: Crash in AnkiDroidApp.onCreate() with:
        /*
        java.lang.RuntimeException: Using WebView from more than one process at once with the same data directory
        is not supported. https://crbug.com/558377 : Lock owner com.ichi2.anki:acra at
        org.chromium.android_webview.AwDataDirLock.a(PG:26)
         */
        boolean enableDebugging = prefs.getBoolean(PreferenceKeys.HtmlJavascriptDebugging);
        WebView.setWebContentsDebuggingEnabled(enableDebugging);
    }

    /** Throws IllegalStateException if a WebView has been initialized */
    @RequiresApi(api = Build.VERSION_CODES.P)
    public static void setDataDirectorySuffix(@NonNull String suffix) {
        WebView.setDataDirectorySuffix(suffix);
        sHasSetDataDirectory = true;
    }

    public static boolean hasSetDataDirectory() {
        // Implicitly truth requires API >= P
        return sHasSetDataDirectory;
    }
}
