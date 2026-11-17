// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadQueuedAccessibilityDraftRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `rollback of a queued step preserves a newer accessibility edit`() =
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
                    val plus = composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign))
                    plus.performClick()
                    val stepSuffix = targetContext.resources.getQuantityString(CommonPlurals.timebox_minutes_suffix, 21)
                    input.assertTextEquals("21", stepSuffix)
                    input.assertIsNotFocused()
                    input.performSemanticsAction(SemanticsActions.SetText) { setText ->
                        assertTrue(setText(AnnotatedString("45")))
                    }
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }

            val draftSuffix = targetContext.resources.getQuantityString(CommonPlurals.timebox_minutes_suffix, 45)
            input.assertTextEquals("45", draftSuffix)
            input.performImeAction()
            advanceRobolectricLooper()
            assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
