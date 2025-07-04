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

import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.preferences.HtmlHelpPreference

class ReviewerOptionsFragment :
    SettingsFragment(),
    PreferenceXmlSource {
    override val preferenceResource: Int = R.xml.preferences_reviewer
    override val analyticsScreenNameConstant: String = "prefs.studyScreen"

    override fun initSubscreen() {
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
        newReviewerPref.setOnPreferenceChangeListener { _, newValue ->
            val boolValue = (newValue as? Boolean) ?: return@setOnPreferenceChangeListener false
            setPrefsEnableState(boolValue)
            true
        }

        // Show play buttons on cards with audio
        // Note: Stored inverted in the collection as HIDE_AUDIO_PLAY_BUTTONS
        requirePreference<SwitchPreferenceCompat>(R.string.show_audio_play_buttons_key).apply {
            title = CollectionManager.TR.preferencesShowPlayButtonsOnCardsWith()
            launchCatchingTask { isChecked = !CollectionPreferences.getHidePlayAudioButtons() }
            setOnPreferenceChangeListener { _, newValue ->
                val newValueBool = newValue as? Boolean ?: return@setOnPreferenceChangeListener false
                launchCatchingTask { CollectionPreferences.setHideAudioPlayButtons(!newValueBool) }
                true
            }
        }

        // Show remaining card count
        requirePreference<SwitchPreferenceCompat>(R.string.show_progress_preference).apply {
            title = CollectionManager.TR.preferencesShowRemainingCardCount()
            launchCatchingTask { isChecked = CollectionPreferences.getShowRemainingDueCounts() }
            setOnPreferenceChangeListener { _, newDueCountsValue ->
                val newDueCountsValueBool = newDueCountsValue as? Boolean ?: return@setOnPreferenceChangeListener false
                launchCatchingTask { CollectionPreferences.setShowRemainingDueCounts(newDueCountsValueBool) }
                true
            }
        }
    }
}
