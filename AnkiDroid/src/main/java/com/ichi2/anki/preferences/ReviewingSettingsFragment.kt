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

import androidx.preference.ListPreference
import androidx.preference.SwitchPreference
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.launchWithCol
import com.ichi2.anki.preferences.Preferences.Companion.getDayOffset
import com.ichi2.anki.preferences.Preferences.Companion.setDayOffset
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.preferences.NumberRangePreferenceCompat
import com.ichi2.preferences.SeekBarPreferenceCompat

class ReviewingSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_reviewing
    override val analyticsScreenNameConstant: String
        get() = "prefs.reviewing"

    override fun initSubscreen() {
        // New cards position
        // Represents the collections pref "newSpread": i.e.
        // whether the new cards are added at the end of the queue or randomly in it.
        requirePreference<ListPreference>(R.string.new_spread_preference).apply {
            launchCatchingTask { setValueIndex(withCol { get_config_int("newSpread") }) }
            setOnPreferenceChangeListener { newValue ->
                launchWithCol { set_config("newSpread", (newValue as String).toInt()) }
            }
        }

        // Learn ahead limit
        // Represents the collections pref "collapseTime": i.e.
        // if there are no card to review now, but there are learning cards remaining for today, we show those learning cards if they are due before LEARN_CUTOFF minutes
        // Note that "collapseTime" is in second while LEARN_CUTOFF is in minute.
        requirePreference<NumberRangePreferenceCompat>(R.string.learn_cutoff_preference).apply {
            launchCatchingTask { setValue(withCol { get_config_int("collapseTime") / 60 }) }
            setOnPreferenceChangeListener { newValue ->
                launchWithCol { set_config("collapseTime", (newValue as Int * 60)) }
            }
        }
        // Timebox time limit
        // Represents in Android preferences the collections configuration "timeLim": i.e.
        // the duration of a review timebox in minute. Each TIME_LIMIT minutes, a message appear suggesting to halt and giving the number of card reviewed
        // Note that "timeLim" is in seconds while TIME_LIMIT is in minutes.
        requirePreference<NumberRangePreferenceCompat>(R.string.time_limit_preference).apply {
            launchCatchingTask { setValue(withCol { get_config_int("timeLim") / 60 }) }
            setOnPreferenceChangeListener { newValue ->
                launchWithCol { set_config("timeLim", (newValue as Int * 60)) }
            }
        }
        // Start of next day
        // Represents the collection pref "rollover"
        // in sched v2, and crt in sched v1. I.e. at which time of the day does the scheduler reset
        requirePreference<SeekBarPreferenceCompat>(R.string.day_offset_preference).apply {
            launchCatchingTask { value = getDayOffset() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { setDayOffset(requireContext(), newValue as Int) }
            }
        }
        // Automatic display answer
        requirePreference<SwitchPreference>(R.string.timeout_answer_preference).setOnPreferenceChangeListener { newValue ->
            // Enable `Keep screen on` along with the automatic display answer preference
            if (newValue == true) {
                requirePreference<SwitchPreference>(R.string.keep_screen_on_preference).isChecked = true
            }
        }

        /**
         * Timeout answer
         * An integer representing the action when "Automatic Answer" flips a card from answer to question
         * 0 represents "bury", 1-4 represents the named buttons
         * @see com.ichi2.anki.reviewer.AutomaticAnswerAction
         * We use the same key in the collection config
         * @see com.ichi2.anki.reviewer.AutomaticAnswerAction.CONFIG_KEY
         * */
        requirePreference<ListPreference>(R.string.automatic_answer_action_preference).apply {
            launchCatchingTask { setValueIndex(withCol { get_config(AutomaticAnswerAction.CONFIG_KEY, 0.toInt())!! }) }
            setOnPreferenceChangeListener { newValue ->
                launchWithCol { set_config(AutomaticAnswerAction.CONFIG_KEY, (newValue as String).toInt()) }
            }
        }
        // New timezone handling
        requirePreference<SwitchPreference>(R.string.new_timezone_handling_preference).apply {
            launchCatchingTask {
                isChecked = withCol { sched._new_timezone_enabled() }
                isEnabled = withCol { schedVer() > 1 }
            }
            setOnPreferenceChangeListener { newValue ->
                if (newValue == true) {
                    launchWithCol { sched.set_creation_offset() }
                } else {
                    launchWithCol { sched.clear_creation_offset() }
                }
            }
        }
    }
}
