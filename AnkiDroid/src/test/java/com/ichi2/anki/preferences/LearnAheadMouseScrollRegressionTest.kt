// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
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
class LearnAheadMouseScrollRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `scrolling Learn Ahead with a mouse wheel commits the draft and ends editing`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
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

            val input = composeRule.onNode(hasSetTextAction())
            input.performClick()
            input.performTextReplacement("45")
            input.assertIsFocused()

            val pane = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            val scrollRange = pane.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            val previousPosition = scrollRange.value()
            pane.performMouseInput {
                moveTo(center)
                scroll(3f)
            }
            composeRule.waitUntil(2_000) { scrollRange.value() > previousPosition }
            advanceRobolectricLooper()

            assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
            input.assertIsNotFocused()
        }
}
