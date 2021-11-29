/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.web;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

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

    public static void handleSyncServerPreferenceChange(Context context) {
        Timber.i("Sync Server Preferences updated.");
        // #4921 - if any of the preferences change, we should reset the HostNum.
        // This is because different servers use different HostNums for data mappings.
        HostNumFactory.getInstance(context).reset();
    }
}
