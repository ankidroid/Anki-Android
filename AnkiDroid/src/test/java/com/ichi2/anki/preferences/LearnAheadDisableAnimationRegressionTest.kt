// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadDisableAnimationRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a failed disable keeps the displayed limit in sync with the collection`() =
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
                    composeRule.mainClock.autoAdvance = false
                    composeRule.onNode(isToggleable()).performClick()
                    composeRule.mainClock.advanceTimeByFrame()
                    composeRule.mainClock.advanceTimeByFrame()
                    val increment = composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign))
                    increment.assertExists().performClick()

                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
                composeRule.mainClock.autoAdvance = true
            }

            val state = viewModel.uiState.value as LearnAheadUiState.Loaded
            assertTrue(state.isEnabled)
            assertEquals(CollectionPreferences.getLearnAheadLimit(), state.limit.toDuration())
        }
}
