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
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.compat.CompatHelper
import com.ichi2.preferences.HeaderPreference
import com.ichi2.utils.AdaptionUtil

class HeaderFragment : PreferenceFragmentCompat(), TitleProvider {
    override val title: CharSequence
        get() = getString(R.string.settings)

    private var highlightedPreferenceKey: String = ""

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers, rootKey)

        requirePreference<HeaderPreference>(R.string.pref_backup_limits_screen_key)
            .title = CollectionManager.TR.preferencesBackups()

        requirePreference<Preference>(R.string.pref_advanced_screen_key).apply {
            if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                isVisible = false
            }
        }

        requirePreference<Preference>(R.string.pref_dev_options_screen_key)
            .isVisible = DevOptionsFragment.isEnabled(requireContext())

        configureSearchBar(
            requireActivity() as AppCompatActivity,
            requirePreference<SearchPreference>(R.string.search_preference_key).searchConfiguration
        )

        if (!resources.isWindowCompact()) {
            parentFragmentManager.findFragmentById(R.id.settings_container)?.let {
                val key = getHeaderKeyForFragment(it) ?: return@let
                highlightPreference(key)
            }

            parentFragmentManager.addOnBackStackChangedListener {
                val fragment = parentFragmentManager.findFragmentById(R.id.settings_container)
                    ?: return@addOnBackStackChangedListener

                val key = getHeaderKeyForFragment(fragment) ?: return@addOnBackStackChangedListener
                highlightPreference(key)
            }
        }
    }

    private fun highlightPreference(@StringRes keyRes: Int) {
        val key = getString(keyRes)
        findPreference<HeaderPreference>(highlightedPreferenceKey)?.setHighlighted(false)
        findPreference<HeaderPreference>(key)?.setHighlighted(true)
        highlightedPreferenceKey = key
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // use the same fragment container to search in case there is a navigation container
        requirePreference<SearchPreference>(R.string.search_preference_key)
            .searchConfiguration
            .setFragmentContainerViewId((view.parent as? ViewGroup)?.id ?: R.id.settings_container)
    }

    companion object {
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

        /**
         * @return the key for the [HeaderPreference] that corresponds to the given [fragment]
         * in the Preference tree.
         *
         * e.g. Sync > Custom sync server settings -> returns the key for the Sync header
         */
        @StringRes
        fun getHeaderKeyForFragment(fragment: Fragment): Int? {
            return when (fragment) {
                is GeneralSettingsFragment -> R.string.pref_general_screen_key
                is ReviewingSettingsFragment -> R.string.pref_reviewing_screen_key
                is SyncSettingsFragment, is CustomSyncServerSettingsFragment -> R.string.pref_sync_screen_key
                is NotificationsSettingsFragment -> R.string.pref_notifications_screen_key
                is AppearanceSettingsFragment, is CustomButtonsSettingsFragment -> R.string.pref_appearance_screen_key
                is ControlsSettingsFragment -> R.string.pref_controls_screen_key
                is AccessibilitySettingsFragment -> R.string.pref_accessibility_screen_key
                is BackupLimitsSettingsFragment -> R.string.pref_backup_limits_screen_key
                is AdvancedSettingsFragment -> R.string.pref_advanced_screen_key
                is DevOptionsFragment, is ReviewerOptionsFragment -> R.string.pref_dev_options_screen_key
                is AboutFragment -> R.string.about_screen_key
                else -> null
            }
        }
    }
}
