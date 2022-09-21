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

import android.content.Context
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.*
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

/**
 * Fragment exclusive to DEBUG builds which can be used
 * to add options useful for developers or WIP features.
 */
class DevOptionsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_dev_options
    override val analyticsScreenNameConstant: String
        get() = "prefs.dev_options"

    override fun initSubscreen() {
        val enableDevOptionsPref = requirePreference<SwitchPreference>(R.string.dev_options_enabled_by_user_key)
        /**
         * If it is a DEBUG build, hide the preference to disable developer options
         * If it is a RELEASE build, configure the preference to disable dev options
         * Ensure that the preference is searchable or not
         * based on the same condition at [HeaderFragment.configureSearchBar]
         */
        if (BuildConfig.DEBUG) {
            enableDevOptionsPref.isVisible = false
        } else {
            enableDevOptionsPref.setOnPreferenceChangeListener { _, _ ->
                showDisableDevOptionsDialog()
                false
            }
        }
        // Make it possible to test crash reporting
        requirePreference<Preference>(getString(R.string.pref_trigger_crash_key)).setOnPreferenceClickListener {
            Timber.w("Crash triggered on purpose from advanced preferences in debug mode")
            throw RuntimeException("This is a test crash")
        }
        // Make it possible to test analytics
        requirePreference<Preference>(getString(R.string.pref_analytics_debug_key)).setOnPreferenceClickListener {
            if (UsageAnalytics.isEnabled) {
                showSnackbar("Analytics set to dev mode")
            } else {
                showSnackbar("Done! Enable Analytics in 'General' settings to use.")
            }
            UsageAnalytics.setDevMode()
            true
        }
        // Lock database
        requirePreference<Preference>(getString(R.string.pref_lock_database_key)).setOnPreferenceClickListener {
            val c = CollectionHelper.instance.getCol(requireContext())!!
            Timber.w("Toggling database lock")
            c.db.database.beginTransaction()
            true
        }
        // Reset onboarding
        requirePreference<Preference>(getString(R.string.pref_reset_onboarding_key)).setOnPreferenceClickListener {
            OnboardingUtils.reset(requireContext())
            true
        }
        // Use scoped storage
        requirePreference<Preference>(getString(R.string.pref_scoped_storage_key)).apply {
            setDefaultValue(AnkiDroidApp.TESTING_SCOPED_STORAGE)
            setOnPreferenceClickListener {
                AnkiDroidApp.TESTING_SCOPED_STORAGE = true
                (requireActivity() as Preferences).restartWithNewDeckPicker()
                true
            }
        }
    }

    /**
     * Shows dialog to confirm if developer options should be disabled
     */
    private fun showDisableDevOptionsDialog() {
        MaterialDialog(requireContext()).show {
            title(R.string.disable_dev_options)
            positiveButton(R.string.dialog_ok) {
                disableDevOptions()
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    /**
     * Destroys the fragment and hides developer options on [HeaderFragment]
     */
    private fun disableDevOptions() {
        (requireActivity() as Preferences).setDevOptionsEnabled(false)
        parentFragmentManager.popBackStack()
    }

    companion object {
        /**
         * @return whether developer options should be shown to the user.
         * True in case [BuildConfig.DEBUG] is true
         * or if the user has enabled it with the secret on [com.ichi2.anki.preferences.AboutFragment]
         */
        fun isEnabled(context: Context): Boolean {
            return BuildConfig.DEBUG || AnkiDroidApp.getSharedPrefs(context)
                .getBoolean(context.getString(R.string.dev_options_enabled_by_user_key), false)
        }
    }
}
