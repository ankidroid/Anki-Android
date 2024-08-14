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

import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.annotations.NeedsTest
import com.ichi2.preferences.ControlPreference

class ControlsSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_controls
    override val analyticsScreenNameConstant: String
        get() = "prefs.controls"

    @NeedsTest("Keys and titles in the XML layout are the same of the ViewerCommands")
    override fun initSubscreen() {
        val commands = ViewerCommand.entries.associateBy { it.preferenceKey }
        // set defaultValue in the prefs creation.
        // if a preference is empty, it has a value like "1/"
        allPreferences()
            .filterIsInstance<ControlPreference>()
            .filter { pref -> pref.value == null }
            .forEach { pref -> pref.value = commands[pref.key]?.defaultValue?.toPreferenceString() }

        setDynamicTitle()
    }

    private fun setDynamicTitle() {
        findPreference<ControlPreference>(getString(R.string.reschedule_command_key))?.let {
            it.title = TR.actionsSetDueDate().toSentenceCase(R.string.sentence_set_due_date)
        }
        findPreference<ControlPreference>(getString(R.string.toggle_whiteboard_command_key))?.let {
            it.title = getString(R.string.gesture_toggle_whiteboard).toSentenceCase(R.string.sentence_gesture_toggle_whiteboard)
        }
        findPreference<ControlPreference>(getString(R.string.abort_and_sync_command_key))?.let {
            it.title = getString(R.string.gesture_abort_sync).toSentenceCase(R.string.sentence_gesture_abort_sync)
        }
        findPreference<ControlPreference>(getString(R.string.flag_red_command_key))?.let {
            it.title = getString(R.string.gesture_flag_red).toSentenceCase(R.string.sentence_gesture_flag_red)
        }
        findPreference<ControlPreference>(getString(R.string.flag_orange_command_key))?.let {
            it.title = getString(R.string.gesture_flag_orange).toSentenceCase(R.string.sentence_gesture_flag_orange)
        }
        findPreference<ControlPreference>(getString(R.string.flag_green_command_key))?.let {
            it.title = getString(R.string.gesture_flag_green).toSentenceCase(R.string.sentence_gesture_flag_green)
        }
        findPreference<ControlPreference>(getString(R.string.flag_blue_command_key))?.let {
            it.title = getString(R.string.gesture_flag_blue).toSentenceCase(R.string.sentence_gesture_flag_blue)
        }
        findPreference<ControlPreference>(getString(R.string.flag_pink_command_key))?.let {
            it.title = getString(R.string.gesture_flag_pink).toSentenceCase(R.string.sentence_gesture_flag_pink)
        }
        findPreference<ControlPreference>(getString(R.string.flag_turquoise_command_key))?.let {
            it.title = getString(R.string.gesture_flag_turquoise).toSentenceCase(R.string.sentence_gesture_flag_turquoise)
        }
        findPreference<ControlPreference>(getString(R.string.flag_purple_command_key))?.let {
            it.title = getString(R.string.gesture_flag_purple).toSentenceCase(R.string.sentence_gesture_flag_purple)
        }
        findPreference<ControlPreference>(getString(R.string.remove_flag_command_key))?.let {
            it.title = getString(R.string.gesture_flag_remove).toSentenceCase(R.string.sentence_gesture_flag_remove)
        }
    }
}
