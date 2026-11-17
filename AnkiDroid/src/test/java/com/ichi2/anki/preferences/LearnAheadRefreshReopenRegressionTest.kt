// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadRefreshReopenRegressionTest : RobolectricTest() {
    @Test
    fun `a new settings screen waits for an edit accepted during the previous screen refresh`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val firstScreen = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            val secondScreen =
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    firstScreen.refresh()
                    firstScreen.setLearnAheadLimit(45)
                    // Opening a search result can leave the first screen on the back stack.
                    LearnAheadSettingsViewModel().also {
                        advanceUntilIdle()
                        advanceRobolectricLooper()
                        advanceUntilIdle()
                        advanceRobolectricLooper()
                        advanceUntilIdle()
                        advanceRobolectricLooper()
                    }
                }

            assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(45, (secondScreen.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
        }
}
