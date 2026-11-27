// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
class LearnAheadAboveMaximumRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `decrementing a stored limit above the maximum uses the nearest valid bound`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(1_000.minutes)
            assertEquals(1_000.minutes, CollectionPreferences.getLearnAheadLimit())
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                AnkiDroidTheme {
                    Box(Modifier.fillMaxWidth().height(285.dp)) {
                        LearnAheadSettingsScreen(
                            state = state,
                            onToggleEnabled = viewModel::toggleEnabled,
                            onSetMinutes = viewModel::setLearnAheadLimit,
                            onNavigateUp = {},
                        )
                    }
                }
            }

            composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.minus_sign)).performClick()
            advanceRobolectricLooper()

            assertEquals(998.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
