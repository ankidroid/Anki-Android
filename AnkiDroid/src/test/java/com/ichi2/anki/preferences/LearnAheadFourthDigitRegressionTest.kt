// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadFourthDigitRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `typing a fourth digit before the end keeps the limit`() =
        runTest {
            showScreen(999)
            val field = composeRule.onNode(hasSetTextAction())
            field.performClick()
            field.performTextInputSelection(TextRange(0))
            field.performTextInput("1")
            field.performImeAction()
            advanceRobolectricLooper()
            assertEquals(999.minutes, CollectionPreferences.getLearnAheadLimit())
        }

    private suspend fun showScreen(minutes: Int) {
        CollectionPreferences.setLearnAheadLimit(minutes.minutes)
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
    }
}
