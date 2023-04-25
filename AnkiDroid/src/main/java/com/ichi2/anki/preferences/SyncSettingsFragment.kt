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

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.setFragmentResultListener
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.show

/**
 * Fragment with preferences related to syncing
 */
class SyncSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_sync
    override val analyticsScreenNameConstant: String
        get() = "prefs.sync"

    override fun initSubscreen() {
        requirePreference<Preference>(R.string.sync_account_key).setSummaryProvider {
            preferenceManager.sharedPreferences!!.getString("username", null)
                ?: getString(R.string.sync_account_summ_logged_out)
        }

        // Enable/disable force full sync if the user is logged in or not
        setFragmentResultListener(LOGIN_STATUS_CHANGED_REQUEST_KEY) { _, _ ->
            requirePreference<Preference>(R.string.force_full_sync_key).isEnabled = isLoggedIn()
        }

        // Configure force full sync option
        requirePreference<Preference>(R.string.force_full_sync_key).apply {
            isEnabled = isLoggedIn()
            setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext()).show {
                    setTitle(R.string.force_full_sync_title)
                    setMessage(R.string.force_full_sync_summary)
                    setPositiveButton(R.string.dialog_ok) { _, _ ->
                        launchCatchingTask {
                            withCol { modSchemaNoCheck() }
                            showSnackbar(R.string.force_full_sync_confirmation, Snackbar.LENGTH_SHORT)
                        }
                    }
                    setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                }
                true
            }
        }
        // Custom sync server
        requirePreference<Preference>(R.string.custom_sync_server_key).setSummaryProvider {
            val preferences = AnkiDroidApp.getSharedPrefs(requireContext())
            val url = customSyncBase(preferences)

            if (url == null) {
                getString(R.string.custom_sync_server_summary_none_of_the_two_servers_used)
            } else {
                url
            }
        }
    }

    companion object {
        const val LOGIN_STATUS_CHANGED_REQUEST_KEY = "login_status_changed"
    }
}
