// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class LearnAheadReturningStepperRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a stale stepper cannot re-enable Learn Ahead while the screen refreshes`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val returningScreen = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            val secondScreen = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            secondScreen.toggleEnabled(false)
            advanceRobolectricLooper()
            assertEquals(0.minutes, CollectionPreferences.getLearnAheadLimit())

            composeRule.setContent {
                val state by returningScreen.uiState.collectAsState()
                AnkiDroidTheme {
                    LearnAheadSettingsScreen(
                        state = state,
                        onToggleEnabled = returningScreen::toggleEnabled,
                        onSetMinutes = returningScreen::setLearnAheadLimit,
                        onNavigateUp = {},
                    )
                }
            }

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                returningScreen.refresh()
                composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).performClick()
                advanceUntilIdle()
                advanceRobolectricLooper()
            }

            assertEquals(0.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
