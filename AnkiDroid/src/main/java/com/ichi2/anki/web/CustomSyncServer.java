package com.ichi2.anki.web;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CustomSyncServer {
    public static final String PREFERENCE_CUSTOM_SYNC_BASE = "syncBaseUrl";
    public static final String PREFERENCE_CUSTOM_MEDIA_SYNC_URL = "syncMediaUrl";
    public static final String PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER = "useCustomSyncServer";

    @Nullable
    public static String getMediaSyncUrl(@NonNull SharedPreferences preferences) {
        return preferences.getString(PREFERENCE_CUSTOM_MEDIA_SYNC_URL, null);
    }

    @Nullable
    public static String getSyncBaseUrl(@NonNull SharedPreferences preferences) {
        return getSyncBaseUrlOrDefault( preferences, null);
    }

    public static String getSyncBaseUrlOrDefault(@NonNull SharedPreferences userPreferences, String defaultValue) {
        return userPreferences.getString(PREFERENCE_CUSTOM_SYNC_BASE, defaultValue);
    }

    public static boolean isEnabled(@NonNull SharedPreferences userPreferences) {
        return userPreferences.getBoolean(PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER, false);
    }
}
