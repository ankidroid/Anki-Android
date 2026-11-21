// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadRefreshRaceRegressionTest : RobolectricTest() {
    @Test
    fun `a refresh cannot replace a limit edited while the collection read is pending`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            assertEquals(LearnAheadLimit.DEFAULT, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit)

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                viewModel.refresh()
                viewModel.setLearnAheadLimit(45)
                advanceUntilIdle()
                advanceRobolectricLooper()
                advanceUntilIdle()
            }

            assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(45, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
            assertEquals(45, Prefs.customLearnAheadLimit)
        }
}
