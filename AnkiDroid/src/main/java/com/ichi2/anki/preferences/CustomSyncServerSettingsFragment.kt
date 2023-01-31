/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.SyncPreferences
import com.ichi2.anki.web.CustomSyncServer
import com.ichi2.preferences.VersatileTextPreference
import okhttp3.HttpUrl.Companion.toHttpUrl

class CustomSyncServerSettingsFragment : SettingsFragment() {
    override val preferenceResource = R.xml.preferences_custom_sync_server
    override val analyticsScreenNameConstant = "prefs.custom_sync_server"

    override fun initSubscreen() {
        listOf(
            R.string.custom_sync_server_collection_url_key,
        ).forEach {
            requirePreference<VersatileTextPreference>(it).continuousValidator =
                VersatileTextPreference.Validator { value ->
                    if (value.isNotEmpty()) value.toHttpUrl()
                }
        }
    }

    // See discussion at https://github.com/ankidroid/Anki-Android/pull/12367#discussion_r967681337
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (
            key == SyncPreferences.CUSTOM_SYNC_URI ||
            key == SyncPreferences.CUSTOM_SYNC_ENABLED
        ) {
            CustomSyncServer.handleSyncServerPreferenceChange(AnkiDroidApp.instance)
            prefs.edit {
                remove(SyncPreferences.CURRENT_SYNC_URI)
            }
        }
    }
}
