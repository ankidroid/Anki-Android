// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
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
class LearnAheadFailedSaveRetryRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `retrying the same limit after a transient save failure persists the edit`() =
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
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                input.performClick()
                input.performTextReplacement("25")
                input.performImeAction()
                advanceRobolectricLooper()
                assertEquals(listOf(CommonString.something_wrong), viewModel.errorEvents.replayCache)
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())
            viewModel.consumeError()
            input.performClick()
            input.performTextReplacement("25")
            input.performImeAction()
            advanceRobolectricLooper()
            assertEquals(25.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
