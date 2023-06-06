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
import androidx.core.content.edit
import androidx.preference.Preference
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

/**
 * Fragment with preferences related to notifications
 */
class AccessibilitySettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_accessibility
    override val analyticsScreenNameConstant: String
        get() = "prefs.accessibility"

    override fun initSubscreen() {
        // Reset accessibility button to default
        val resetAccessibilityButtons = requirePreference<Preference>("reset_accessibility_buttons")
        resetAccessibilityButtons.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).show {
                title(R.string.reset_settings_to_default)
                positiveButton(R.string.reset) {
                    // Reset the settings to default
                    AnkiDroidApp.getSharedPrefs(requireContext()).edit {
                        remove("cardZoom")
                        remove("imageZoom")
                        remove("answerButtonSize")
                        remove("showLargeAnswerButtons")
                        remove("relativeCardBrowserFontSize")
                    }
                    // Refresh the screen to display the changes
                    refreshScreen()
                }
                negativeButton(R.string.dialog_cancel, null)
            }
            true
        }
    }
}
