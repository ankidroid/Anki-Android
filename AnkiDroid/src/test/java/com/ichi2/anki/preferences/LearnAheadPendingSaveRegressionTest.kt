// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadPendingSaveRegressionTest : RobolectricTest() {
    @Test
    fun `leaving the screen preserves a queued limit change`() =
        runTest {
            checkPendingSave(25) { it.setLearnAheadLimit(25) }
        }

    @Test
    fun `leaving the screen preserves a queued toggle change`() =
        runTest {
            checkPendingSave(0) { it.toggleEnabled(false) }
        }

    private suspend fun TestScope.checkPendingSave(
        expectedMinutes: Int,
        change: (LearnAheadSettingsViewModel) -> Unit,
    ) {
        CollectionPreferences.setLearnAheadLimit(20.minutes)
        val viewModel = LearnAheadSettingsViewModel()
        val store = ViewModelStore().apply { put("learnAhead", viewModel) }
        advanceRobolectricLooper()
        try {
            // Queue collection access as if another collection operation were still running.
            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                change(viewModel)
                // Popping LearnAheadSettingsFragment clears its ViewModelStore.
                store.clear()
            }
            advanceUntilIdle()
            advanceRobolectricLooper()
            assertEquals(expectedMinutes.minutes, CollectionPreferences.getLearnAheadLimit())
        } finally {
            store.clear()
        }
    }
}
