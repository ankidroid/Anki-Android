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
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.ichi2.anki.*
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat
import com.ichi2.utils.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

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
        requirePreference<Preference>(R.string.pref_trigger_crash_key).setOnPreferenceClickListener {
            Timber.w("Crash triggered on purpose from advanced preferences in debug mode")
            throw RuntimeException("This is a test crash")
        }
        // Make it possible to test analytics
        requirePreference<Preference>(R.string.pref_analytics_debug_key).setOnPreferenceClickListener {
            if (UsageAnalytics.isEnabled) {
                showSnackbar("Analytics set to dev mode")
            } else {
                showSnackbar("Done! Enable Analytics in 'General' settings to use.")
            }
            UsageAnalytics.setDevMode()
            true
        }
        // Lock database
        requirePreference<Preference>(R.string.pref_lock_database_key).setOnPreferenceClickListener {
            val c = CollectionHelper.instance.getCol(requireContext())!!
            Timber.w("Toggling database lock")
            c.db.database.beginTransaction()
            true
        }
        // Reset onboarding
        requirePreference<Preference>(R.string.pref_reset_onboarding_key).setOnPreferenceClickListener {
            OnboardingUtils.reset(requireContext())
            true
        }

        val sizePreference = requirePreference<IncrementerNumberRangePreferenceCompat>(getString(R.string.pref_fill_collection_size_file_key))
        val numberOfFilePreference = requirePreference<IncrementerNumberRangePreferenceCompat>(getString(R.string.pref_fill_collection_number_file_key))

        /*
         * Create fake media section
         */
        requirePreference<Preference>(R.string.pref_fill_collection_key).setOnPreferenceClickListener {
            val sizeOfFiles = sizePreference.getValue()
            val numberOfFiles = numberOfFilePreference.getValue()
            AlertDialog.Builder(requireContext()).show {
                setTitle("Warning!")
                setMessage("You'll add $numberOfFiles files with no meaningful content, potentially overriding existing files. Do not do it on a collection you care about.")
                setPositiveButton("OK") { _, _ ->
                    generateFiles(sizeOfFiles, numberOfFiles)
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            }
            true
        }
    }

    private fun generateFiles(size: Int, numberOfFiles: Int) {
        Timber.d("numberOf files: $numberOfFiles, size: $size")
        launchCatchingTask {
            withProgress("Generating $numberOfFiles files of size $size bytes") {
                val suffix = ".$size"
                for (i in 1..numberOfFiles) {
                    val f = withContext(Dispatchers.IO) {
                        File.createTempFile("00$i", suffix)
                    }
                    f.appendBytes(ByteArray(size))

                    CollectionManager.withCol {
                        media.addFile(f)
                    }
                    if (i % 1000 == 0) {
                        UIUtils.showThemedToast(requireContext(), "$i files added.", true)
                    }
                }
                UIUtils.showThemedToast(requireContext(), "$numberOfFiles files added successfully", false)
            }
        }
    }

    /**
     * Shows dialog to confirm if developer options should be disabled
     */
    private fun showDisableDevOptionsDialog() {
        AlertDialog.Builder(requireContext()).show {
            setTitle(R.string.disable_dev_options)
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                disableDevOptions()
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
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
