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

import android.app.AlertDialog
import android.webkit.URLUtil
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.ichi2.anki.R
import com.ichi2.anki.web.CustomSyncServer

class CustomSyncServerSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_custom_sync_server
    override val analyticsScreenNameConstant: String
        get() = "prefs.custom_sync_server"

    override fun initSubscreen() {
        // Use custom sync server
        requirePreference<SwitchPreference>(R.string.custom_sync_server_enable_key).setOnPreferenceChangeListener { _ ->
            CustomSyncServer.handleSyncServerPreferenceChange(requireContext())
        }
        // Sync url
        requirePreference<Preference>(R.string.custom_sync_server_collection_url_key).setOnPreferenceChangeListener { _, newValue: Any ->
            val newUrl = newValue.toString()
            if (newUrl.isNotEmpty() && !URLUtil.isValidUrl(newUrl)) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.custom_sync_server_base_url_invalid)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show()
                return@setOnPreferenceChangeListener false
            }
            CustomSyncServer.handleSyncServerPreferenceChange(requireContext())
            true
        }
        // Media url
        requirePreference<Preference>(R.string.custom_sync_server_media_url_key).setOnPreferenceChangeListener { _, newValue: Any ->
            val newUrl = newValue.toString()
            if (newUrl.isNotEmpty() && !URLUtil.isValidUrl(newUrl)) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.custom_sync_server_media_url_invalid)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show()
                return@setOnPreferenceChangeListener false
            }
            CustomSyncServer.handleSyncServerPreferenceChange(requireContext())
            true
        }
    }
}
