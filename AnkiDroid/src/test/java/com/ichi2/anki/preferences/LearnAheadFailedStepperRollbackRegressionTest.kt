// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonPlurals
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
class LearnAheadFailedStepperRollbackRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `failed stepper change restores the visible saved limit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                AnkiDroidTheme {
                    LearnAheadSettingsScreen(state, viewModel::toggleEnabled, viewModel::setLearnAheadLimit, {})
                }
            }
            try {
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).performClick()
                advanceRobolectricLooper()
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())
            val suffix = targetContext.resources.getQuantityString(CommonPlurals.timebox_minutes_suffix, 20)
            composeRule.onNode(hasSetTextAction()).assertTextEquals("20", suffix)
        }

    @Test
    fun `backgrounding does not retry a rejected stepper change`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, LearnAheadSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                scenario.onActivity { advanceRobolectricLooper() }
                composeRule.waitForIdle()
                try {
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).performClick()
                    advanceRobolectricLooper()
                } finally {
                    CollectionManager.emulatedOpenFailure = null
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                advanceRobolectricLooper()
                assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())
            }
        }
}
