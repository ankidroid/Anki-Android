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
import android.system.Os
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.preferences.VersatileTextWithASwitchPreference
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber

object CustomSyncServer {
    const val PREFERENCE_CUSTOM_COLLECTION_SYNC_URL = "customCollectionSyncUrl"
    const val PREFERENCE_CUSTOM_MEDIA_SYNC_URL = "syncMediaUrl"

    const val PREFERENCE_CUSTOM_COLLECTION_SYNC_SERVER_ENABLED =
        PREFERENCE_CUSTOM_COLLECTION_SYNC_URL + VersatileTextWithASwitchPreference.SWITCH_SUFFIX
    const val PREFERENCE_CUSTOM_MEDIA_SYNC_SERVER_ENABLED =
        PREFERENCE_CUSTOM_MEDIA_SYNC_URL + VersatileTextWithASwitchPreference.SWITCH_SUFFIX

    fun getCollectionSyncUrlIfSetAndEnabledOrNull(preferences: SharedPreferences): String? {
        val enabled = preferences.getBoolean(PREFERENCE_CUSTOM_COLLECTION_SYNC_SERVER_ENABLED, false)
        if (!enabled) return null
        val collectionSyncUrl = preferences.getString(PREFERENCE_CUSTOM_COLLECTION_SYNC_URL, null)
        return if (collectionSyncUrl.isNullOrEmpty()) null else collectionSyncUrl
    }

    fun getMediaSyncUrlIfSetAndEnabledOrNull(preferences: SharedPreferences): String? {
        val enabled = preferences.getBoolean(PREFERENCE_CUSTOM_MEDIA_SYNC_SERVER_ENABLED, false)
        if (!enabled) return null
        val mediaSyncUrl = preferences.getString(PREFERENCE_CUSTOM_MEDIA_SYNC_URL, null)
        return if (mediaSyncUrl.isNullOrEmpty()) null else mediaSyncUrl
    }

    fun handleSyncServerPreferenceChange(context: Context) {
        Timber.i("Sync Server Preferences updated.")
        // #4921 - if any of the preferences change, we should reset the HostNum.
        // This is because different servers use different HostNums for data mappings.
        HostNumFactory.getInstance(context).reset()

        if (!BackendFactory.defaultLegacySchema) {
            setOrUnsetEnvironmentalVariablesForBackend(context)
        }
    }

    fun setOrUnsetEnvironmentalVariablesForBackend(context: Context) {
        val preferences = AnkiDroidApp.getSharedPrefs(context)

        val customCollectionSyncUrl = getCollectionSyncUrlIfSetAndEnabledOrNull(preferences)
        val customMediaSyncUrl = getMediaSyncUrlIfSetAndEnabledOrNull(preferences)

        if (customCollectionSyncUrl != null) {
            Os.setenv("SYNC_ENDPOINT", customCollectionSyncUrl, true)
        } else {
            Os.unsetenv("SYNC_ENDPOINT")
        }

        if (customMediaSyncUrl != null) {
            Os.setenv("SYNC_ENDPOINT_MEDIA", customMediaSyncUrl, true)
        } else {
            Os.unsetenv("SYNC_ENDPOINT_MEDIA")
        }
    }
}
