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
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.compat.CompatHelper
import com.ichi2.utils.AdaptionUtil

class HeaderFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers, rootKey)

        requirePreference<Preference>(R.string.pref_advanced_screen_key).apply {
            if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                isVisible = false
            }
        }

        if (DevOptionsFragment.isEnabled(requireContext())) {
            setDevOptionsVisibility(true)
        }

        configureSearchBar(
            requireActivity() as AppCompatActivity,
            requirePreference<SearchPreference>(R.string.search_preference_key).searchConfiguration
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // use the same fragment container to search in case there is a navigation container
        requirePreference<SearchPreference>(R.string.search_preference_key)
            .searchConfiguration
            .setFragmentContainerViewId((view.parent as? ViewGroup)?.id ?: R.id.settings_container)
    }

    fun setDevOptionsVisibility(isVisible: Boolean) {
        requirePreference<Preference>(R.string.pref_dev_options_screen_key).isVisible = isVisible
    }

    companion object {
        fun configureSearchBar(activity: AppCompatActivity, searchConfiguration: SearchConfiguration) {
            with(searchConfiguration) {
                setActivity(activity)
                setBreadcrumbsEnabled(true)
                setFuzzySearchEnabled(false)
                setHistoryEnabled(true)
                textNoResults = activity.getString(R.string.pref_search_no_results)

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

            // Some preferences and categories are only shown conditionally,
            // so they should be searchable based on the same conditions

            /** From [HeaderFragment.onCreatePreferences] */
            if (DevOptionsFragment.isEnabled(activity)) {
                searchConfiguration.index(R.xml.preferences_dev_options)
                /** From [DevOptionsFragment.initSubscreen] */
                if (BuildConfig.DEBUG) {
                    searchConfiguration.ignorePreference(activity.getString(R.string.dev_options_enabled_by_user_key))
                }
            }

            /** From [HeaderFragment.onCreatePreferences] */
            if (!AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                searchConfiguration.index(R.xml.preferences_advanced)
            }

            /** From [NotificationsSettingsFragment.initSubscreen] */
            if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                searchConfiguration.ignorePreference(activity.getString(R.string.pref_notifications_vibrate_key))
                searchConfiguration.ignorePreference(activity.getString(R.string.pref_notifications_blink_key))
            }

            /** From [AdvancedSettingsFragment.removeUnnecessaryAdvancedPrefs] */
            if (!CompatHelper.hasScrollKeys()) {
                searchConfiguration.ignorePreference(activity.getString(R.string.double_scrolling_gap_key))
            }
        }
    }
}
