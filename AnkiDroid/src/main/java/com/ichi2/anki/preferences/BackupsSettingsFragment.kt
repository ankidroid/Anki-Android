// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import com.ichi2.anki.R
import com.ichi2.anki.backup.BackupDirectoryProvider
import com.ichi2.compat.CompatHelper
import timber.log.Timber

class BackupsSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_backups
    override val analyticsScreenNameConstant: String
        get() = "prefs.backups"

    private val openFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) {
                return@registerForActivityResult
            }
            val resolved = resolveFilesystemPath(uri)
            if (resolved == null) {
                Timber.w("Unable to resolve filesystem path for %s", uri)
                return@registerForActivityResult
            }
            BackupDirectoryProvider.setCustomPath(requireContext(), resolved)
            updateSummary(findPreference<Preference>(BackupDirectoryProvider.PREF_KEY), resolved)
        }

    override fun initSubscreen() {
        val backupLocation = requirePreference<Preference>(BackupDirectoryProvider.PREF_KEY)
        backupLocation.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openFolder.launch(null)
            true
        }
        updateSummary(
            backupLocation,
            requireContext().let { ctx ->
                BackupDirectoryProvider.resolve(ctx, ctx.sharedPrefs())
                ctx.sharedPrefs().getString(BackupDirectoryProvider.PREF_KEY, null)
            },
        )
    }

    private fun updateSummary(pref: Preference?, custom: String?) {
        pref ?: return
        pref.summary = custom ?: getString(R.string.backup_location_default_summary)
    }

    /**
     * Converts a SAF tree Uri into a raw filesystem path understood by the Rust backend.
     */
    private fun resolveFilesystemPath(uri: Uri): String? = CompatHelper.getCompat().getPathFromUri(requireContext(), uri)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }
}
