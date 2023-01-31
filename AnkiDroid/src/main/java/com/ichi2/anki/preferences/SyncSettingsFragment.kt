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

import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.customSyncBase
import com.ichi2.anki.snackbar.showSnackbar

/**
 * Fragment with preferences related to syncing
 */
class SyncSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_sync
    override val analyticsScreenNameConstant: String
        get() = "prefs.sync"

    override fun initSubscreen() {
        // AnkiWeb Account
        updateSyncAccountSummary()

        // Configure force full sync option
        requirePreference<Preference>(R.string.force_full_sync_key).setOnPreferenceClickListener {
            MaterialDialog(requireContext()).show {
                title(R.string.force_full_sync_title)
                message(R.string.force_full_sync_summary)
                positiveButton(R.string.dialog_ok) {
                    if (col == null) {
                        showThemedToast(requireContext(), R.string.directory_inaccessible, false)
                        return@positiveButton
                    }
                    col!!.modSchemaNoCheck()
                    showSnackbar(R.string.force_full_sync_confirmation, Snackbar.LENGTH_SHORT)
                }
                negativeButton(R.string.dialog_cancel)
            }
            true
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

    private fun updateSyncAccountSummary() {
        requirePreference<Preference>(R.string.sync_account_key)
            .summary = preferenceManager.sharedPreferences!!.getString("username", "")!!
            .ifEmpty { getString(R.string.sync_account_summ_logged_out) }
    }

    // TODO trigger the summary change from MyAccount.kt once it is migrated to a fragment
    override fun onResume() {
        // Trigger a summary update in case the user logged in/out on MyAccount activity
        updateSyncAccountSummary()
        super.onResume()
    }
}
