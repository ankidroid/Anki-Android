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
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.withProgress
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
        val enableDevOptionsPref = requirePreference<SwitchPreferenceCompat>(R.string.dev_options_enabled_by_user_key)
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
            // If we don't delete the limiter data, our test crash may not go through,
            // but we are triggering it very much on purpose, we want to see the crash in ACRA
            this.context?.let { c -> CrashReportService.deleteACRALimiterData(c) }

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
            false
        }
        // Lock database
        requirePreference<Preference>(R.string.pref_lock_database_key).setOnPreferenceClickListener {
            Timber.w("Toggling database lock")
            launchCatchingTask { withCol { Thread.sleep(1000 * 86400) } }
            false
        }
        // Make it possible to test crash reporting
        requirePreference<Preference>(R.string.pref_set_database_path_debug_key).setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).show {
                setTitle("Warning!")
                setMessage(
                    "This will most likely make it so that you cannot access your collection. " +
                        "It will be very difficult to recover your data.",
                )
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    Timber.w("Setting collection path to /storage/emulated/0/AnkiDroid")
                    AnkiDroidApp.sharedPrefs().edit {
                        putString(
                            CollectionHelper.PREF_COLLECTION_PATH,
                            "/storage/emulated/0/AnkiDroid",
                        )
                    }
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            }
            false
        }

        val numberOfNotesPreference =
            requirePreference<IncrementerNumberRangePreferenceCompat>(getString(R.string.pref_fill_default_deck_number_key))

        /*
         * Create fake media section
         */
        requirePreference<Preference>(R.string.pref_fill_default_deck_key).setOnPreferenceClickListener {
            val numberOfNotes = numberOfNotesPreference.getValue()
            AlertDialog.Builder(requireContext()).show {
                setTitle("Warning!")
                setMessage(
                    "You'll add $numberOfNotes notes with no meaningful content in $numberOfNotes new decks. " +
                        "Do not do it on a collection you care about.",
                )
                setPositiveButton("OK") { _, _ ->
                    generateNotes(numberOfNotes)
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            }
            true
        }

        val sizePreference =
            requirePreference<IncrementerNumberRangePreferenceCompat>(getString(R.string.pref_fill_collection_size_file_key))
        val numberOfFilePreference =
            requirePreference<IncrementerNumberRangePreferenceCompat>(getString(R.string.pref_fill_collection_number_file_key))

        /*
         * Create fake media section
         */
        requirePreference<Preference>(R.string.pref_fill_collection_key).setOnPreferenceClickListener {
            val sizeOfFiles = sizePreference.getValue()
            val numberOfFiles = numberOfFilePreference.getValue()
            AlertDialog.Builder(requireContext()).show {
                setTitle("Warning!")
                setMessage(
                    "You'll add $numberOfFiles files with no meaningful content, " +
                        "potentially overriding existing files. " +
                        "Do not do it on a collection you care about.",
                )
                setPositiveButton("OK") { _, _ ->
                    generateFiles(sizeOfFiles, numberOfFiles)
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            }
            true
        }

        /**
         * The new review reminders system replaces the "Notifications" button in the main settings screen
         * with a "Review reminders" button, so we need to immediately reload the settings activity
         * to make this change show up.
         */
        requirePreference<Preference>(R.string.pref_new_review_reminders).setOnPreferenceChangeListener { _, _ ->
            ActivityCompat.recreate(requireActivity())
            true
        }

        requirePreference<Preference>(R.string.new_reviewer_pref_key).setOnPreferenceChangeListener { pref, newValue ->
            val boolValue = newValue as? Boolean ?: return@setOnPreferenceChangeListener false
            pref.sharedPreferences?.edit { putBoolean("newReviewerOptions", boolValue) }
            ActivityCompat.recreate(requireActivity())
            true
        }
    }

    /**
     * Generate [numberOfNotes]. Note n is in deck n, with :: between each digit.
     */
    private fun generateNotes(numberOfNotes: Int) {
        Timber.d("numberOf notes: $numberOfNotes")
        launchCatchingTask {
            withProgress("Generating $numberOfNotes notes") {
                for (i in 1..numberOfNotes) {
                    fun deckName(noteNumber: Int): String {
                        if (noteNumber < 10) {
                            return "$noteNumber"
                        }
                        return "${deckName(noteNumber / 10)}::${noteNumber % 10}"
                    }
                    withCol {
                        val deck = decks.addNormalDeckWithName(deckName(i))
                        addNote(
                            newNote().apply { setField(0, "$i") },
                            deck.id,
                        )
                    }
                    if (i % 1000 == 0) {
                        showThemedToast(requireContext(), "$i notes added.", true)
                    }
                }
                showThemedToast(requireContext(), "$numberOfNotes notes added successfully", false)
            }
        }
    }

    private fun generateFiles(
        size: Int,
        numberOfFiles: Int,
    ) {
        Timber.d("numberOf files: $numberOfFiles, size: $size")
        launchCatchingTask {
            withProgress("Generating $numberOfFiles files of size $size bytes") {
                val suffix = ".$size"
                for (i in 1..numberOfFiles) {
                    val f =
                        withContext(Dispatchers.IO) {
                            File.createTempFile("00$i", suffix)
                        }
                    f.appendBytes(ByteArray(size))

                    withCol {
                        media.addFile(f)
                    }
                    if (i % 1000 == 0) {
                        showThemedToast(requireContext(), "$i files added.", true)
                    }
                }
                showThemedToast(requireContext(), "$numberOfFiles files added successfully", false)
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
                Prefs.isDevOptionsEnabled = false
                parentFragmentManager.popBackStack()
                ActivityCompat.recreate(requireActivity())
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        }
    }
}
