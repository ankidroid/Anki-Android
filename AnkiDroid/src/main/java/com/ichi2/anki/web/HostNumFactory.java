package com.ichi2.anki.web;

import android.content.Context;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.sync.HostNum;

public class HostNumFactory {
    public static HostNum getInstance(Context context) {
        return PreferenceBackedHostNum.fromPreferences(AnkiDroidApp.getSharedPrefs(context));
    }
}
