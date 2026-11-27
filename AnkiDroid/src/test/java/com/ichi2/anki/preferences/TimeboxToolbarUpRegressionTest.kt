// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import androidx.appcompat.R as AppCompatR

@RunWith(AndroidJUnit4::class)
class TimeboxToolbarUpRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `toolbar Up leaves Timebox while the limit field is focused`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, ReviewingSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    val preferences = activity.fragment as PreferencesFragment
                    preferences.childFragmentManager.apply {
                        commit {
                            replace(R.id.settings_container, TimeboxSettingsFragment())
                            addToBackStack(null)
                        }
                        executePendingTransactions()
                    }
                    advanceRobolectricLooper()
                }

                val input = composeRule.onNode(hasSetTextAction())
                input.performClick()
                input.performTextReplacement("45")
                input.assertIsFocused()
                composeRule
                    .onNodeWithContentDescription(targetContext.getString(AppCompatR.string.abc_action_bar_up_description))
                    .performClick()

                scenario.onActivity { activity ->
                    val preferences = activity.fragment as PreferencesFragment
                    preferences.childFragmentManager.executePendingTransactions()
                    assertIs<ReviewingSettingsFragment>(preferences.childFragmentManager.findFragmentById(R.id.settings_container))
                }
            }
        }
}
