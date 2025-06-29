/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.preferences.HtmlHelpPreference
import timber.log.Timber

/**
 * Developer options to test some of the new reviewer settings and features
 *
 * Not a `SettingsFragment` to avoid boilerplate and sending analytics reports,
 * since this is just a temporary screen while the new reviewer is being developed.
 */
class ReviewerOptionsFragment :
    PreferenceFragmentCompat(),
    PreferenceXmlSource,
    TitleProvider {
    override val preferenceResource: Int = R.xml.preferences_reviewer

    override val title
        get() = preferenceManager?.preferenceScreen?.title ?: ""

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(preferenceResource)

        // TODO launch the fragment inside PreferencesFragment instead of using a new activity.
        // An activity is being currently used because the preferences screens are shown below the
        // collapsible toolbar, and the menu screen has a non collapsible one. Putting it in
        // `settings_container` would lead to two toolbars, which isn't desirable. Putting its menu
        // into the collapsible toolbar would ruin the preview, which also isn't desirable.
        // An activity partially solves that, because the screen looks alright in phones, but in
        // tablets/big screens, the preferences navigation lateral bar isn't shown.
        requirePreference<Preference>(R.string.reviewer_menu_settings_key).setOnPreferenceClickListener {
            Timber.i("launching study screen settings menu")
            val intent = SingleFragmentActivity.getIntent(requireContext(), ReviewerMenuSettingsFragment::class)
            startActivity(intent)
            true
        }

        val ignoreDisplayCutout =
            requirePreference<SwitchPreferenceCompat>(R.string.ignore_display_cutout_key).apply {
                isEnabled = Prefs.hideSystemBars != HideSystemBars.NONE
            }
        val hideSystemBars =
            requirePreference<ListPreference>(R.string.hide_system_bars_key).apply {
                setOnPreferenceChangeListener { value ->
                    ignoreDisplayCutout.isEnabled = value != HideSystemBars.NONE.entryValue
                }
            }
        val newReviewerPref = requirePreference<SwitchPreferenceCompat>(R.string.new_reviewer_options_key)

        fun setPrefsEnableState(newValue: Boolean) {
            val prefs = preferenceScreen.allPreferences() - newReviewerPref
            for (pref in prefs) {
                if (pref is HtmlHelpPreference) continue
                if (pref.key == ignoreDisplayCutout.key && newValue) {
                    ignoreDisplayCutout.isEnabled = hideSystemBars.value != HideSystemBars.NONE.entryValue
                    continue
                }
                pref.isEnabled = newValue
            }
        }

        setPrefsEnableState(newReviewerPref.isChecked)
        newReviewerPref.setOnPreferenceChangeListener { newValue ->
            setPrefsEnableState(newValue)
        }

        // Show play buttons on cards with audio
        // Note: Stored inverted in the collection as HIDE_AUDIO_PLAY_BUTTONS
        requirePreference<SwitchPreferenceCompat>(R.string.show_audio_play_buttons_key).apply {
            title = CollectionManager.TR.preferencesShowPlayButtonsOnCardsWith()
            launchCatchingTask { isChecked = !CollectionPreferences.getHidePlayAudioButtons() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setHideAudioPlayButtons(!newValue) }
            }
        }

        // Show remaining card count
        requirePreference<SwitchPreferenceCompat>(R.string.show_progress_preference).apply {
            title = CollectionManager.TR.preferencesShowRemainingCardCount()
            launchCatchingTask { isChecked = CollectionPreferences.getShowRemainingDueCounts() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setShowRemainingDueCounts(newValue) }
            }
        }

        // Show estimate time
        // Represents the collection pref "estTime": i.e.
        // whether the buttons should indicate the duration of the interval if we click on them.
        requirePreference<SwitchPreferenceCompat>(R.string.show_estimates_preference).apply {
            launchCatchingTask { isChecked = CollectionPreferences.getShowIntervalOnButtons() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setShowIntervalsOnButtons(newValue) }
            }
        }
    }
}
