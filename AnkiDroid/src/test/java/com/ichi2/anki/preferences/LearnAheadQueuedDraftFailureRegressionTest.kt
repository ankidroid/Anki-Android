// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadQueuedDraftFailureRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `failure of an earlier save preserves a newer draft for retry`() =
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
            val input = composeRule.onNode(hasSetTextAction())
            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).performClick()
                    input.performClick()
                    input.performTextReplacement("45")

                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            val suffix = targetContext.resources.getQuantityString(CommonPlurals.timebox_minutes_suffix, 45)
            input.assertTextEquals("45", suffix)
            input.performImeAction()
            advanceRobolectricLooper()
            assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
        }

    @Test
    fun `focusing an unchanged field does not retry a rejected stepper change`() =
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

            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).performClick()
                    val suffix = targetContext.resources.getQuantityString(CommonPlurals.timebox_minutes_suffix, 21)
                    val input = composeRule.onNode(hasSetTextAction())
                    input.assertTextEquals("21", suffix)
                    input.performClick()

                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(20, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
            assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())

            composeRule
                .onNodeWithText(targetContext.getString(CommonString.pref_learn_ahead_help_summary))
                .performTouchInput { click() }
            advanceRobolectricLooper()

            assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
