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
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreference
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.*
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.provider.CardContentProvider
import com.ichi2.compat.CompatHelper
import timber.log.Timber

class AdvancedSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_advanced
    override val analyticsScreenNameConstant: String
        get() = "prefs.advanced"

    @Suppress("Deprecation") // Material dialog neutral button deprecation
    override fun initSubscreen() {
        val screen = preferenceScreen
        // Check that input is valid before committing change in the collection path
        requirePreference<EditTextPreference>(CollectionHelper.PREF_COLLECTION_PATH).apply {
            setOnPreferenceChangeListener { _, newValue: Any? ->
                val newPath = newValue as String?
                try {
                    CollectionHelper.initializeAnkiDroidDirectory(newPath)
                    true
                } catch (e: StorageAccessException) {
                    Timber.e(e, "Could not initialize directory: %s", newPath)
                    MaterialDialog(requireContext()).show {
                        title(R.string.dialog_collection_path_not_dir)
                        positiveButton(R.string.dialog_ok) {
                            dismiss()
                        }
                        negativeButton(R.string.reset_custom_buttons) {
                            text = CollectionHelper.getDefaultAnkiDroidDirectory(requireContext())
                        }
                    }
                    false
                }
            }
        }
        if (col != null && col!!.schedVer() == 1) {
            Timber.i("Displaying V1-to-V2 scheduler preference")
            val schedVerPreference = SwitchPreference(requireContext())
            schedVerPreference.setTitle(R.string.sched_v2)
            schedVerPreference.setSummary(R.string.sched_v2_summ)
            schedVerPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                MaterialDialog(requireContext()).show {
                    // Going to V2
                    title(R.string.sched_ver_toggle_title)
                        .message(R.string.sched_ver_1to2)
                        .positiveButton(R.string.dialog_ok) {
                            col!!.modSchemaNoCheck()
                            try {
                                col!!.changeSchedulerVer(2)
                                screen.removePreference(schedVerPreference)
                            } catch (e2: ConfirmModSchemaException) {
                                // This should never be reached as we explicitly called modSchemaNoCheck()
                                throw RuntimeException(e2)
                            }
                        }
                        .neutralButton(R.string.help) {
                            // call v2 scheduler documentation website
                            val uri = Uri.parse(getString(R.string.link_anki_2_scheduler))
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            startActivity(intent)
                        }
                        .negativeButton(R.string.dialog_cancel) {
                            schedVerPreference.isChecked = false
                        }
                }
                false
            }
            // meaning of order here is the position of Preference in xml layout.
            schedVerPreference.order = 5
            screen.addPreference(schedVerPreference)
        }
        // Adding change logs in both debug and release builds
        Timber.i("Adding open changelog")
        val changelogPreference = Preference(requireContext())
        changelogPreference.setTitle(R.string.open_changelog)
        val infoIntent = Intent(requireContext(), Info::class.java)
        infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
        changelogPreference.intent = infoIntent
        screen.addPreference(changelogPreference)
        // Workaround preferences
        removeUnnecessaryAdvancedPrefs()
        addThirdPartyAppsListener()

        // Configure "Reset languages" preference
        requirePreference<Preference>(R.string.pref_reset_languages_key).setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_languages)
                .setIcon(R.drawable.ic_warning_black)
                .setMessage(R.string.reset_languages_question)
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    if (MetaDB.resetLanguages(requireContext())) {
                        UIUtils.showThemedToast(requireContext(), R.string.reset_confirmation, true)
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            true
        }
        // Advanced statistics
        requirePreference<Preference>(R.string.pref_advanced_statistics_key).setSummaryProvider {
            if (AnkiDroidApp.getSharedPrefs(requireContext()).getBoolean("advanced_statistics_enabled", false)) {
                getString(R.string.enabled)
            } else {
                getString(R.string.disabled)
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
    }

    private fun removeUnnecessaryAdvancedPrefs() {
        val plugins = findPreference<PreferenceCategory>("category_plugins")
        // Disable the emoji/kana buttons to scroll preference if those keys don't exist
        if (!CompatHelper.hasKanaAndEmojiKeys()) {
            val emojiScrolling = findPreference<SwitchPreference>("scrolling_buttons")
            if (emojiScrolling != null && plugins != null) {
                plugins.removePreference(emojiScrolling)
            }
        }
        // Disable the double scroll preference if no scrolling keys
        if (!CompatHelper.hasScrollKeys() && !CompatHelper.hasKanaAndEmojiKeys()) {
            val doubleScrolling = findPreference<SwitchPreference>("double_scrolling")
            if (doubleScrolling != null && plugins != null) {
                plugins.removePreference(doubleScrolling)
            }
        }
    }

    private fun addThirdPartyAppsListener() {
        // #5864 - some people don't have a browser so we can't use <intent>
        // and need to handle the keypress ourself.
        val showThirdParty = requirePreference<Preference>("thirdpartyapps_link")
        val githubThirdPartyAppsUrl = "https://github.com/ankidroid/Anki-Android/wiki/Third-Party-Apps"
        showThirdParty.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            try {
                val openThirdPartyAppsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(githubThirdPartyAppsUrl))
                super.startActivity(openThirdPartyAppsIntent)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e)
                // We use a different message here. We have limited space in the snackbar
                val error = getString(R.string.activity_start_failed_load_url, githubThirdPartyAppsUrl)
                UIUtils.showSimpleSnackbar(requireActivity(), error, false)
            }
            true
        }
    }

    companion object {
        @JvmStatic
        fun getSubscreenIntent(context: Context?): Intent {
            return getSubscreenIntent(context, AdvancedSettingsFragment::class.java.name)
        }
    }
}
