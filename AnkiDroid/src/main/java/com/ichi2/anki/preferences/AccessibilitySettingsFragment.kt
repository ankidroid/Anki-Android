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

import com.ichi2.anki.Preferences.SettingsFragment
import com.ichi2.anki.R
import com.ichi2.preferences.SeekBarPreferenceCompat

/**
 * Fragment with preferences related to notifications
 */
class AccessibilitySettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_acessibility
    override val analyticsScreenNameConstant: String
        get() = "prefs.accessibility"

    override fun initSubscreen() {
        // Card zoom
        requirePreference<SeekBarPreferenceCompat>(R.string.card_zoom_preference)
            .setFormattedSummary(R.string.pref_summary_percentage)
        // Image zoom
        requirePreference<SeekBarPreferenceCompat>(R.string.image_zoom_preference)
            .setFormattedSummary(R.string.pref_summary_percentage)
        // Answer button size
        requirePreference<SeekBarPreferenceCompat>(R.string.answer_button_size_preference)
            .setFormattedSummary(R.string.pref_summary_percentage)
    }
}
