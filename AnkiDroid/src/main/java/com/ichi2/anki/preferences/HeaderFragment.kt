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
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.Preferences
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import com.ichi2.utils.AdaptionUtil

class HeaderFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers, rootKey)

        // General category
        requirePreference<Preference>(R.string.pref_general_screen_key)
            .summary = buildCategorySummary(
            R.string.language,
            R.string.pref_cat_studying,
            R.string.pref_cat_system_wide
        )

        // Reviewing category
        requirePreference<Preference>(R.string.pref_reviewing_screen_key)
            .summary = buildCategorySummary(
            R.string.pref_cat_scheduling,
            R.string.timeout_answer_text
        )

        // Sync category
        requirePreference<Preference>(R.string.pref_sync_screen_key)
            .summary = buildCategorySummary(
            R.string.sync_account,
            R.string.automatic_sync_choice
        )

        // Notifications category
        requirePreference<Preference>(R.string.pref_notifications_screen_key)
            .summary = buildCategorySummary(
            R.string.notification_pref_title,
            R.string.notification_minimum_cards_due_vibrate,
            R.string.notification_minimum_cards_due_blink,
        )

        // Appearance category
        requirePreference<Preference>(R.string.pref_appearance_screen_key)
            .summary = buildCategorySummary(
            R.string.pref_cat_themes,
            R.string.pref_cat_fonts,
            R.string.pref_cat_reviewer
        )

        // Accessibility category
        requirePreference<Preference>(R.string.pref_accessibility_screen_key)
            .summary = buildCategorySummary(
            R.string.card_zoom,
            R.string.button_size,
        )

        // Controls category
        requirePreference<Preference>(R.string.pref_controls_screen_key)
            .summary = buildCategorySummary(
            R.string.pref_cat_gestures,
            R.string.keyboard,
            R.string.bluetooth
        )

        // Advanced category
        requirePreference<Preference>(R.string.pref_advanced_screen_key).apply {
            summary = buildCategorySummary(
                R.string.statistics,
                R.string.pref_cat_workarounds,
                R.string.pref_cat_plugins
            )
            if (AdaptionUtil.isRestrictedLearningDevice) {
                isVisible = false
            }
        }

        // Developer options category
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
        requirePreference<Preference>(R.string.pref_dev_options_screen_key).isVisible = isVisible
    }

    /**
     * Join the strings defined by [resIds]
     * with ` • ` as separator to build a summary string for preferences categories.
     * e.g. if `R.string.appName` and `R.string.msg` are given as arguments,
     * and they correspond respectively to the strings `AnkiDroid` and `Message`,
     * those strings are joined and return `AnkiDroid • Message`
     */
    private fun buildCategorySummary(@StringRes vararg resIds: Int): String {
        val strings = resIds.map { getString(it) }
        return Preferences.buildCategorySummary(*strings.toTypedArray())
    }
}
