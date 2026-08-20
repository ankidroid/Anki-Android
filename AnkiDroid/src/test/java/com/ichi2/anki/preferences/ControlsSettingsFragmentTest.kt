/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import android.content.DialogInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.preferences.ReviewerControlPreference
import com.ichi2.testutils.HamcrestUtils
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlsSettingsFragmentTest : RobolectricTest() {
    @Test
    fun `XML keys match the Enum keys`() {
        for (screen in ControlPreferenceScreen.entries) {
            val xmlKeys =
                PreferenceTestUtils.getKeysFromXml(targetContext, screen.xmlRes, excludeCategories = true).toMutableList().apply {
                    remove("binding_BROWSE")
                    remove("binding_STATISTICS")
                    remove("binding_whiteboard_UNDO")
                    remove("binding_whiteboard_REDO")
                    remove("binding_whiteboard_CLEAR")
                    remove("binding_whiteboard_TOGGLE_ERASER")
                }
            val enumKeys = screen.getActions().map { it.preferenceKey }

            assertThat(xmlKeys, HamcrestUtils.containsInAnyOrder(enumKeys))
        }
    }

    /**
     * Tests for alert dialog behavior in [ControlsSettingsFragment] using 'swipe up' gesture as the input binding.
     */
    @Test
    fun `skip button binding only to answer`() {
        withControlsSettings {
            val binding = Binding.GestureInput(Gesture.SWIPE_UP)
            answerPref.setBinding(binding)
            clickAlertDialogButton(DialogInterface.BUTTON_NEGATIVE, checkDismissed = true)

            val showAnswerBindings = showAnswerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            val answerBindings = answerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            assertThat("binding is assigned to Show answer", showAnswerBindings, not(hasItem(equalTo(Pair(binding, CardSide.QUESTION)))))
            assertThat("binding not assigned to answer", answerBindings, hasItem(equalTo(Pair(binding, CardSide.ANSWER))))
        }
    }

    @Test
    fun `assign buttns binding to both answer and show answer`() {
        withControlsSettings {
            val binding = Binding.GestureInput(Gesture.SWIPE_UP)
            answerPref.setBinding(binding)
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, checkDismissed = true)

            val showAnswerBindings = showAnswerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            val answerBindings = answerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            assertThat("binding not assigned to Show answer", showAnswerBindings, hasItem(equalTo(Pair(binding, CardSide.QUESTION))))
            assertThat("binding not assigned to answer", answerBindings, hasItem(equalTo(Pair(binding, CardSide.ANSWER))))
        }
    }
}

private val ControlsSettingsFragment.showAnswerPref
    get() = requirePreference<ReviewerControlPreference>(getString(R.string.show_answer_command_key))

private val ControlsSettingsFragment.answerPref
    get() = requirePreference<ReviewerControlPreference>(getString(R.string.answer_good_command_key))

/**
 * launches the [ControlsSettingsFragment] and runs [block] on it.
 */
context(_: DeferredNavigation)
private fun withControlsSettings(block: ControlsSettingsFragment.() -> Unit) {
    val intent =
        PreferencesActivity.getIntent(
            ApplicationProvider.getApplicationContext(),
            ControlsSettingsFragment::class,
        )
    ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
        scenario.onActivity { activity ->
            val preferencesFragment = activity.fragment as PreferencesFragment
            val controlsSettingsFragment =
                preferencesFragment.childFragmentManager.findFragmentByTag(
                    ControlsSettingsFragment::class.java.name,
                ) as ControlsSettingsFragment
            controlsSettingsFragment.block()
        }
    }
}
