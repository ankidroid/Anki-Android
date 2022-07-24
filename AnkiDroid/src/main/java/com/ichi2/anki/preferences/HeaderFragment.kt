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

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.Preferences
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import com.ichi2.utils.AdaptionUtil

class HeaderFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers, rootKey)

        // Reviewing preferences summary
        findPreference<Preference>(getString(R.string.pref_reviewing_screen_key))!!
            .summary = Preferences.buildCategorySummary(
            getString(R.string.pref_cat_scheduling),
            getString(R.string.timeout_answer_text)
        )

        // Sync preferences summary
        findPreference<Preference>(getString(R.string.pref_sync_screen_key))!!
            .summary = Preferences.buildCategorySummary(
            getString(R.string.sync_account),
            getString(R.string.automatic_sync_choice)
        )

        // Notifications preferences summary
        findPreference<Preference>(getString(R.string.pref_notifications_screen_key))!!
            .summary = Preferences.buildCategorySummary(
            getString(R.string.notification_pref_title),
            getString(R.string.notification_minimum_cards_due_vibrate),
            getString(R.string.notification_minimum_cards_due_blink),
        )

        // Accessibility preferences summary
        findPreference<Preference>(getString(R.string.pref_accessibility_screen_key))!!
            .summary = Preferences.buildCategorySummary(
            getString(R.string.card_zoom),
            getString(R.string.button_size),
        )

        // Controls preferences summary
        findPreference<Preference>(getString(R.string.pref_controls_screen_key))!!
            .summary = Preferences.buildCategorySummary(
            getString(R.string.pref_cat_gestures),
            getString(R.string.keyboard),
            getString(R.string.bluetooth)
        )

        if (AdaptionUtil.isRestrictedLearningDevice) {
            findPreference<Preference>("pref_screen_advanced")!!.isVisible = false
        }

        if (DevOptionsFragment.isEnabled(requireContext())) {
            setDevOptionsVisibility(true)
        }

        // Set icons colors
        for (index in 0 until preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(index)
            preference.icon?.setTint(Themes.getColorFromAttr(requireContext(), R.attr.iconColor))
        }
    }

    fun setDevOptionsVisibility(isVisible: Boolean) {
        findPreference<Preference>(getString(R.string.pref_dev_options_screen_key))!!.isVisible = isVisible
    }
}
