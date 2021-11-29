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

package com.ichi2.anki.web

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

object CustomSyncServer {
    const val PREFERENCE_CUSTOM_SYNC_BASE = "syncBaseUrl"
    const val PREFERENCE_CUSTOM_MEDIA_SYNC_URL = "syncMediaUrl"
    const val PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER = "useCustomSyncServer"

    @JvmStatic
    fun getMediaSyncUrl(preferences: SharedPreferences): String? {
        return preferences.getString(PREFERENCE_CUSTOM_MEDIA_SYNC_URL, null)
    }

    @JvmStatic
    fun getSyncBaseUrl(preferences: SharedPreferences): String? {
        return getSyncBaseUrlOrDefault(preferences, null)
    }

    @JvmStatic
    fun getSyncBaseUrlOrDefault(userPreferences: SharedPreferences, defaultValue: String?): String? {
        return userPreferences.getString(PREFERENCE_CUSTOM_SYNC_BASE, defaultValue)
    }

    @JvmStatic
    fun isEnabled(userPreferences: SharedPreferences): Boolean {
        return userPreferences.getBoolean(PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER, false)
    }

    @JvmStatic
    fun handleSyncServerPreferenceChange(context: Context?) {
        Timber.i("Sync Server Preferences updated.")
        // #4921 - if any of the preferences change, we should reset the HostNum.
        // This is because different servers use different HostNums for data mappings.
        HostNumFactory.getInstance(context).reset()
    }
}
