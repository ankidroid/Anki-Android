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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.*
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.provider.CardContentProvider
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.CompatHelper
import com.ichi2.utils.show
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber

class AdvancedSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_advanced
    override val analyticsScreenNameConstant: String
        get() = "prefs.advanced"

    @RustCleanup(
        "Remove 'Default deck for statistics' and 'Advanced statistics' preferences" +
            "once the new backend is the default"
    )
    override fun initSubscreen() {
        removeUnnecessaryAdvancedPrefs()

        // Check that input is valid before committing change in the collection path
        requirePreference<EditTextPreference>(CollectionHelper.PREF_COLLECTION_PATH).apply {
            disableIfStorageMigrationInProgress()
            setOnPreferenceChangeListener { _, newValue: Any? ->
                val newPath = newValue as String
                try {
                    CollectionHelper.initializeAnkiDroidDirectory(newPath)
                    (requireActivity() as Preferences).restartWithNewDeckPicker()
                    true
                } catch (e: StorageAccessException) {
                    Timber.e(e, "Could not initialize directory: %s", newPath)
                    AlertDialog.Builder(requireContext()).show {
                        setTitle(R.string.dialog_collection_path_not_dir)
                        setPositiveButton(R.string.dialog_ok) { _, _ -> }
                        setNegativeButton(R.string.reset_custom_buttons) { _, _ ->
                            text = CollectionHelper.getDefaultAnkiDroidDirectory(requireContext())
                        }
                    }
                    false
                }
            }
        }

        // Third party apps
        requirePreference<Preference>(R.string.thirdparty_apps_key).setOnPreferenceClickListener {
            (requireActivity() as AnkiActivity).openUrl(R.string.link_third_party_api_apps)
            true
        }

        // Configure "Reset languages" preference
        requirePreference<Preference>(R.string.pref_reset_languages_key).setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).show {
                setTitle(R.string.reset_languages)
                setIcon(R.drawable.ic_warning_black)
                setMessage(R.string.reset_languages_question)
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    if (MetaDB.resetLanguages(requireContext())) {
                        showSnackbar(R.string.reset_confirmation)
                    }
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            }
            true
        }
        // Default deck for statistics
        requirePreference<Preference>(R.string.stats_default_deck_key).apply {
            // It doesn't have an effect on the new Statistics page,
            // which is enabled together with the new backend
            if (!BackendFactory.defaultLegacySchema) {
                isEnabled = false
            }
        }

        // Advanced statistics
        requirePreference<Preference>(R.string.pref_advanced_statistics_key).apply {
            // It doesn't have an effect on the new Statistics page,
            // which is enabled together with the new backend
            if (!BackendFactory.defaultLegacySchema) {
                isEnabled = false
            }
            setSummaryProvider {
                if (AnkiDroidApp.getSharedPrefs(requireContext()).getBoolean("advanced_statistics_enabled", false)) {
                    getString(R.string.enabled)
                } else {
                    getString(R.string.disabled)
                }
            }
        }
        // Enable API
        requirePreference<SwitchPreference>(R.string.enable_api_key).setOnPreferenceChangeListener { newValue ->
            val providerName = ComponentName(requireContext(), CardContentProvider::class.java.name)
            val state = if (newValue == true) {
                Timber.i("AnkiDroid ContentProvider enabled by user")
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                Timber.i("AnkiDroid ContentProvider disabled by user")
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            requireActivity().packageManager.setComponentEnabledSetting(providerName, state, PackageManager.DONT_KILL_APP)
        }

        // v3 scheduler
        @RustCleanup("move this to Reviewing > Scheduling once the new backend is the default")
        val v3schedPref = requirePreference<SwitchPreference>(R.string.enable_v3_sched_key).apply {
            launchCatchingTask { withCol { isChecked = v3Enabled } }
            // if new backend was enabled on local.properties, remove the pref dependency
            if (!BuildConfig.LEGACY_SCHEMA) {
                dependency = null
                isEnabled = true
            }
            setOnPreferenceChangeListener { newValue: Any ->
                Timber.d("v3 scheduler set to $newValue")
                launchCatchingTask { withCol { v3Enabled = newValue as Boolean } }
            }
        }

        // Use V16 backend
        requirePreference<SwitchPreference>(R.string.pref_rust_backend_key).apply {
            if (!BuildConfig.LEGACY_SCHEMA) {
                title = "New schema already enabled on local.properties"
                isEnabled = false
                isChecked = true
            }
            disableIfStorageMigrationInProgress()
            setOnPreferenceChangeListener { newValue ->
                if (newValue == true) {
                    confirmExperimentalChange(
                        R.string.use_rust_backend_title,
                        R.string.use_rust_backend_warning,
                        onCancel = { isChecked = false },
                        onConfirm = {
                            BackendFactory.defaultLegacySchema = false
                            (requireActivity() as Preferences).restartWithNewDeckPicker()
                        }
                    )
                } else {
                    BackendFactory.defaultLegacySchema = true
                    v3schedPref.isChecked = false
                    (requireActivity() as Preferences).restartWithNewDeckPicker()
                }
            }
        }
    }

    /**
     * Shows a dialog to confirm if the user wants to enable an experimental preference
     */
    private fun confirmExperimentalChange(@StringRes prefTitle: Int, @StringRes message: Int? = null, onCancel: () -> Unit, onConfirm: () -> Unit) {
        val prefTitleString = getString(prefTitle)
        val dialogTitle = getString(R.string.experimental_pref_confirmation, prefTitleString)

        AlertDialog.Builder(requireContext()).show {
            setTitle(dialogTitle)
            message?.let { setMessage(it) }
            setPositiveButton(R.string.dialog_ok) { _, _ -> onConfirm() }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> onCancel() }
            setCancelable(false) // to avoid `onCancel` not being triggered on outside cancels
        }
    }

    private fun removeUnnecessaryAdvancedPrefs() {
        /** These preferences should be searchable or not based
         * on this same condition at [Preferences.configureSearchBar] */
        // Disable the emoji/kana buttons to scroll preference if those keys don't exist
        if (!CompatHelper.hasKanaAndEmojiKeys()) {
            val emojiScrolling = findPreference<SwitchPreference>("scrolling_buttons")
            if (emojiScrolling != null) {
                preferenceScreen.removePreference(emojiScrolling)
            }
        }
        // Disable the double scroll preference if no scrolling keys
        if (!CompatHelper.hasScrollKeys() && !CompatHelper.hasKanaAndEmojiKeys()) {
            val doubleScrolling = findPreference<SwitchPreference>("double_scrolling")
            if (doubleScrolling != null) {
                preferenceScreen.removePreference(doubleScrolling)
            }
        }
    }

    private fun Preference.disableIfStorageMigrationInProgress() {
        try {
            if (ScopedStorageService.userMigrationIsInProgress(requireContext())) {
                isEnabled = false
                summaryProvider = null // needs to be disabled to set .summary
                summary = getString(R.string.functionality_disabled_during_storage_migration)
            }
        } catch (e: Exception) {
            // This screen is vital and must not crash. Trust the user knows what they're doing.
            // This exists only as a precaution.
            Timber.w(e)
        }
    }

    companion object {
        fun getSubscreenIntent(context: Context): Intent {
            return getSubscreenIntent(context, AdvancedSettingsFragment::class)
        }
    }
}
