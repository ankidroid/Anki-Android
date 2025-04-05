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

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.preference.Preference
import androidx.preference.children
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.previewer.PreviewerAction
import com.ichi2.anki.reviewer.MappableAction
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.annotations.NeedsTest
import com.ichi2.preferences.ControlPreference
import timber.log.Timber

class ControlsSettingsFragment :
    SettingsFragment(),
    TabLayout.OnTabSelectedListener {
    override val preferenceResource: Int
        get() = R.xml.preferences_controls
    override val analyticsScreenNameConstant: String
        get() = "prefs.controls"

    override fun initSubscreen() {
        requirePreference<Preference>(R.string.pref_controls_tab_layout_key).setViewId(R.id.tab_layout)
        val initialScreen = ControlPreferenceScreen.entries.first()
        addPreferencesFromResource(initialScreen.xmlRes)
        // TODO replace the preference with something dismissible. This is meant only to improve
        //  the discoverability of the system shortcut for the shortcuts dialog.
        requirePreference<Preference>(R.string.pref_keyboard_shortcuts_key).apply {
            isVisible = resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY
            setOnPreferenceClickListener {
                requireActivity().requestShowKeyboardShortcuts()
                true
            }
        }
        setControlPreferencesDefaultValues(initialScreen)
        setDynamicTitle()
    }

    private fun setControlPreferencesDefaultValues(screen: ControlPreferenceScreen) {
        val commands = screen.getActions().associateBy { it.preferenceKey }
        val prefs = sharedPrefs()
        preferenceScreen
            .allPreferences()
            .filterIsInstance<ControlPreference>()
            .filter { pref -> pref.value == null }
            .forEach { pref -> commands[pref.key]?.getBindings(prefs)?.toPreferenceString()?.let { pref.value = it } }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        listView.post {
            val tabLayout = listView.findViewById<TabLayout>(R.id.tab_layout)
            for (screen in ControlPreferenceScreen.entries) {
                val tab = tabLayout.newTab().setText(screen.titleRes)
                tabLayout.addTab(tab)
            }
            tabLayout.addOnTabSelectedListener(this)
        }
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        val screen = ControlPreferenceScreen.entries[tab.position]
        Timber.v("Selected tab %d - %s", tab.position, screen.name)
        addPreferencesFromResource(screen.xmlRes)
        setControlPreferencesDefaultValues(screen)
        setDynamicTitle()
    }

    @NeedsTest("General category is kept and the other elements are removed")
    override fun onTabUnselected(tab: TabLayout.Tab?) {
        val preferences = preferenceScreen.children.toList()
        // 0 is the `General` category, which should be kept
        for (pref in preferences.subList(1, preferences.size)) {
            preferenceScreen.removePreference(pref)
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab?) = Unit

    private fun setDynamicTitle() {
        findPreference<ControlPreference>(getString(R.string.reschedule_command_key))?.let {
            val preferenceTitle = TR.actionsSetDueDate().toSentenceCase(R.string.sentence_set_due_date)
            it.title = preferenceTitle
            it.dialogTitle = preferenceTitle
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

    private fun String.toSentenceCase(
        @StringRes resId: Int,
    ): String = this.toSentenceCase(this@ControlsSettingsFragment, resId)
}

enum class ControlPreferenceScreen(
    @XmlRes val xmlRes: Int,
    @StringRes val titleRes: Int,
) {
    REVIEWER(R.xml.preferences_reviewer_controls, R.string.pref_controls_reviews_tab),
    PREVIEWER(R.xml.preferences_previewer_controls, R.string.pref_controls_previews_tab),
    ;

    fun getActions(): List<MappableAction<*>> =
        when (this) {
            REVIEWER -> ViewerCommand.entries
            PREVIEWER -> PreviewerAction.entries
        }
}
