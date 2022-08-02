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
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.Preferences
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.compat.CompatHelper
import com.ichi2.themes.Themes
import com.ichi2.utils.AdaptionUtil

class HeaderFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers, rootKey)
        configureSearchBar()

        requirePreference<Preference>(R.string.pref_advanced_screen_key).apply {
            if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                isVisible = false
            }
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

    /**
     * Configures the fragment's [SearchPreference]
     */
    private fun configureSearchBar() {
        val searchPreference = requirePreference<SearchPreference>(R.string.pref_search_key)
        val searchConfig = searchPreference.searchConfiguration.apply {
            setActivity(requireActivity() as Preferences)
            setFragmentContainerViewId(R.id.settings_container)
            setBreadcrumbsEnabled(true)
            setFuzzySearchEnabled(false)

            index(R.xml.preferences_general)
            index(R.xml.preferences_reviewing)
            index(R.xml.preferences_sync)
            index(R.xml.preferences_custom_sync_server)
                .addBreadcrumb(R.string.pref_cat_sync)
            index(R.xml.preferences_notifications)
            index(R.xml.preferences_appearance)
            index(R.xml.preferences_custom_buttons)
                .addBreadcrumb(R.string.pref_cat_appearance)
            index(R.xml.preferences_controls)
            index(R.xml.preferences_accessibility)
        }

        /**
         * The command bindings preferences are created programmatically
         * on [ControlsSettingsFragment.addAllControlPreferencesToCategory],
         * so they should be added programmatically to the search index as well.
         */
        for (command in ViewerCommand.values()) {
            searchConfig.indexItem()
                .withTitle(getString(command.resourceId))
                .withKey(command.preferenceKey)
                .withResId(R.xml.preferences_controls)
                .addBreadcrumb(getString(R.string.pref_cat_controls))
                .addBreadcrumb(getString(R.string.controls_main_category))
        }

        // Some preferences and categories are only shown conditionally,
        // so they should be searchable based on the same conditions

        /** From [onCreatePreferences] */
        if (DevOptionsFragment.isEnabled(requireContext())) {
            searchConfig.index(R.xml.preferences_dev_options)
            /** From [DevOptionsFragment.initSubscreen] */
            if (BuildConfig.DEBUG) {
                searchConfig.ignorePreference(getString(R.string.dev_options_enabled_by_user_key))
            }
        }

        /** From [onCreatePreferences] */
        if (!AdaptionUtil.isXiaomiRestrictedLearningDevice) {
            searchConfig.index(R.xml.preferences_advanced)
            // Advanced statistics is a subscreen of Advanced, so it should be indexed along with it
            searchConfig.index(R.xml.preferences_advanced_statistics)
                .addBreadcrumb(R.string.pref_cat_advanced)
                .addBreadcrumb(R.string.statistics)
        }

        /** From [NotificationsSettingsFragment.initSubscreen] */
        if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
            searchConfig.ignorePreference(getString(R.string.pref_notifications_vibrate_key))
            searchConfig.ignorePreference(getString(R.string.pref_notifications_blink_key))
        }

        /** From [AdvancedSettingsFragment.removeUnnecessaryAdvancedPrefs] */
        if (!CompatHelper.hasKanaAndEmojiKeys()) {
            searchConfig.ignorePreference(getString(R.string.more_scrolling_buttons_key))
        }
        /** From [AdvancedSettingsFragment.removeUnnecessaryAdvancedPrefs] */
        if (!CompatHelper.hasScrollKeys()) {
            searchConfig.ignorePreference(getString(R.string.double_scrolling_gap_key))
        }
    }

    fun setDevOptionsVisibility(isVisible: Boolean) {
        requirePreference<Preference>(R.string.pref_dev_options_screen_key).isVisible = isVisible
    }
}
