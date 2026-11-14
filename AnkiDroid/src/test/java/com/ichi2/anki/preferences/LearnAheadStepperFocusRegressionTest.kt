// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadStepperFocusRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `incrementing the focused Learn Ahead field saves the incremented limit`() = checkButton(increment = true)

    @Test
    fun `decrementing the focused Learn Ahead field saves the decremented limit`() = checkButton(increment = false)

    @Test
    fun `clearing the field and losing focus reverts to the committed limit instead of intermediate digit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                AnkiDroidTheme {
                    LearnAheadSettingsScreen(
                        state = state,
                        onToggleEnabled = viewModel::toggleEnabled,
                        onSetMinutes = viewModel::setLearnAheadLimit,
                        onNavigateUp = {},
                    )
                }
            }

            // User enters 111 and commits
            val field = composeRule.onNode(hasSetTextAction())
            field.performClick()
            field.performTextReplacement("111")
            field.performImeAction()
            advanceRobolectricLooper()
            assertEquals(111.minutes, CollectionPreferences.getLearnAheadLimit())

            // User focuses, clears field (or types 0), then taps outside (loses focus)
            field.performClick()
            field.performTextReplacement("")
            // Clicking help card / outside to lose focus
            composeRule.onNodeWithText(targetContext.getString(CommonString.pref_learn_ahead_help_summary)).performClick()
            advanceRobolectricLooper()

            // Field must revert to 111, not 1
            assertEquals(111.minutes, CollectionPreferences.getLearnAheadLimit())
            val suffix111 = targetContext.resources.getQuantityString(com.ichi2.anki.CommonPlurals.timebox_minutes_suffix, 111)
            field.assertTextEquals("111", suffix111)
        }

    @Test
    fun `typing zero and losing focus reverts to valid limit instead of one`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                AnkiDroidTheme {
                    LearnAheadSettingsScreen(
                        state = state,
                        onToggleEnabled = viewModel::toggleEnabled,
                        onSetMinutes = viewModel::setLearnAheadLimit,
                        onNavigateUp = {},
                    )
                }
            }

            val field = composeRule.onNode(hasSetTextAction())
            field.performClick()
            field.performTextReplacement("0")
            composeRule.onNodeWithText(targetContext.getString(CommonString.pref_learn_ahead_help_summary)).performClick()
            advanceRobolectricLooper()

            // Must revert to 20, never 1
            assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())
            val suffix20 = targetContext.resources.getQuantityString(com.ichi2.anki.CommonPlurals.timebox_minutes_suffix, 20)
            field.assertTextEquals("20", suffix20)
        }

    private fun checkButton(increment: Boolean) =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                AnkiDroidTheme {
                    LearnAheadSettingsScreen(
                        state = state,
                        onToggleEnabled = viewModel::toggleEnabled,
                        onSetMinutes = viewModel::setLearnAheadLimit,
                        onNavigateUp = {},
                    )
                }
            }
            composeRule.onNode(hasSetTextAction()).performClick()
            val description = targetContext.getString(if (increment) CommonString.plus_sign else CommonString.minus_sign)
            composeRule.onNodeWithContentDescription(description).performClick()
            advanceRobolectricLooper()

            val expectedMinutes = if (increment) 21 else 19
            assertEquals(expectedMinutes.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(expectedMinutes, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
        }
}
