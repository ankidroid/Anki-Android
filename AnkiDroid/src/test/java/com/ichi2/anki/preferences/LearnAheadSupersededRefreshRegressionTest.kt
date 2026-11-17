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
class LearnAheadSupersededRefreshRegressionTest : RobolectricTest() {
    @Test
    fun `a second refresh preserves edits entered while the first refresh was pending`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                viewModel.refresh()
                viewModel.setLearnAheadLimit(45)
                viewModel.refresh()
                advanceUntilIdle()
                advanceRobolectricLooper()
                advanceUntilIdle()
            }

            assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
