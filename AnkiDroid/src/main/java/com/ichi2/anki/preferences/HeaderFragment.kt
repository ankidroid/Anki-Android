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
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.compat.CompatHelper
import com.ichi2.preferences.HeaderPreference
import com.ichi2.utils.AdaptionUtil

class HeaderFragment : PreferenceFragmentCompat() {
    private var selectedHeaderPreference: HeaderPreference? = null
    private var selectedHeaderPreferenceKey: String = DEFAULT_SELECTED_HEADER
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers, rootKey)

        selectedHeaderPreferenceKey = savedInstanceState?.getString(KEY_SELECTED_HEADER_PREF) ?: DEFAULT_SELECTED_HEADER

        highlightHeaderPreference(requirePreference<HeaderPreference>(selectedHeaderPreferenceKey))

        requirePreference<HeaderPreference>(R.string.pref_backup_limits_screen_key)
            .title = TR.preferencesBackups()

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

    private fun highlightHeaderPreference(headerPreference: HeaderPreference) {
        if (!(activity as Preferences).hasLateralNavigation()) {
            return
        }
        selectedHeaderPreference?.setHighlighted(false)
        // highlight the newly selected header
        selectedHeaderPreference = headerPreference.apply {
            setHighlighted(true)
            selectedHeaderPreferenceKey = this.key
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        highlightHeaderPreference(preference as HeaderPreference)
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_HEADER_PREF, selectedHeaderPreferenceKey)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        highlightHeaderPreference(requirePreference<HeaderPreference>(selectedHeaderPreferenceKey))
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.settings)
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

    fun handelHighlightHeaderPreferenceOnBack(key: String) {
        highlightHeaderPreference(requirePreference<HeaderPreference>(key))
    }

    companion object {
        private const val KEY_SELECTED_HEADER_PREF = "selected_header_pref"
        private const val DEFAULT_SELECTED_HEADER = "generalScreen"

        fun configureSearchBar(activity: AppCompatActivity, searchConfiguration: SearchConfiguration) {
            val setDuePreferenceTitle = TR.actionsSetDueDate().toSentenceCase(activity, R.string.sentence_set_due_date)
            with(searchConfiguration) {
                setActivity(activity)
                setBreadcrumbsEnabled(true)
                setFuzzySearchEnabled(false)
                setHistoryEnabled(true)

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
                index(R.xml.preferences_backup_limits)
                ignorePreference(activity.getString(R.string.pref_backups_help_key))
                indexItem()
                    .withKey(activity.getString(R.string.reschedule_command_key))
                    .withTitle(setDuePreferenceTitle)
                    .withResId(R.xml.preferences_controls)
                    .addBreadcrumb(activity.getString(R.string.pref_cat_controls))
                    .addBreadcrumb(setDuePreferenceTitle)
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

            searchConfiguration.ignorePreference(activity.getString(R.string.user_actions_controls_category_key))
        }
    }
}
