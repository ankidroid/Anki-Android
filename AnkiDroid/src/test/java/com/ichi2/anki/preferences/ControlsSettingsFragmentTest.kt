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
import androidx.fragment.app.commitNow
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.launchActivity
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.preferences.ReviewerControlPreference
import com.ichi2.testutils.HamcrestUtils
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

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

    @Test
    fun `No button removes input binding already assigned to Show answer`() {
        launchActivity<PreferencesActivity>(PreferencesDestination.Root).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = ControlsSettingsFragment()
                activity.supportFragmentManager.commitNow {
                    add(R.id.settings_container, fragment, "test_fragment")
                }
                val showAnswerPref =
                    requireNotNull(
                        fragment.findPreference<ReviewerControlPreference>(
                            fragment.getString(R.string.show_answer_command_key),
                        ),
                    )
                val answerPref =
                    requireNotNull(
                        fragment.findPreference<ReviewerControlPreference>(
                            fragment.getString(R.string.answer_good_command_key),
                        ),
                    )
                showAnswerPref.addBinding(Binding.GestureInput(Gesture.SWIPE_UP), CardSide.QUESTION)

                val binding = Binding.GestureInput(Gesture.SWIPE_UP)
                answerPref.setBinding(binding)
                clickAlertDialogButton(DialogInterface.BUTTON_NEGATIVE, checkDismissed = false)

                assertTrue(
                    showAnswerPref.getMappableBindings().none { it.binding == binding },
                    "binding not removed from Show answer",
                )
                assertTrue(
                    answerPref.getMappableBindings().any { it.binding == binding },
                    "binding not assigned to the answer",
                )
            }
        }
    }
}
