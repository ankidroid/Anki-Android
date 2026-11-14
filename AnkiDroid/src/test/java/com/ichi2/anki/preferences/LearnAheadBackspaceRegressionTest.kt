// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class LearnAheadBackspaceRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `backspacing all digits restores the committed limit on blur`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(111.minutes)
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
            // Each backspace delivers a separate edit, unlike replacing the entire field at once.
            for (remaining in listOf("11", "1", "")) {
                field.performTextReplacement(remaining)
                advanceRobolectricLooper()
            }
            composeRule.onNodeWithText(targetContext.getString(CommonString.pref_learn_ahead_help_summary)).performClick()
            advanceRobolectricLooper()
            assertEquals(111.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
