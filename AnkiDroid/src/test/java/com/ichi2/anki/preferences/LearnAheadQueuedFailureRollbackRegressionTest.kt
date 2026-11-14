// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadQueuedFailureRollbackRegressionTest : RobolectricTest() {
    @Test
    fun `failed queued edits restore the persisted limit instead of another failed edit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    viewModel.setLearnAheadLimit(21)
                    viewModel.setLearnAheadLimit(22)
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(20, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
            assertEquals(20, Prefs.customLearnAheadLimit)
        }

    @Test
    fun `an earlier failed edit does not roll back a later successful edit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            val dispatcher = StandardTestDispatcher(testScheduler)
            var firstOperation = true
            val failFirstOperation =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        dispatcher.dispatch(context) {
                            val previousFailure = CollectionManager.emulatedOpenFailure
                            CollectionManager.emulatedOpenFailure =
                                if (firstOperation) CollectionManager.CollectionOpenFailure.LOCKED else null
                            firstOperation = false
                            try {
                                block.run()
                            } finally {
                                CollectionManager.emulatedOpenFailure = previousFailure
                            }
                        }
                    }
                }
            withCollectionAccessOn(failFirstOperation) {
                viewModel.setLearnAheadLimit(21)
                viewModel.setLearnAheadLimit(22)
                advanceUntilIdle()
                advanceRobolectricLooper()
            }
            assertEquals(22.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(22, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
            assertEquals(22, Prefs.customLearnAheadLimit)
        }

    @Test
    fun `failed queued toggles restore the persisted enabled state`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    viewModel.toggleEnabled(false)
                    viewModel.toggleEnabled(true)
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(20.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(true, (viewModel.uiState.value as LearnAheadUiState.Loaded).isEnabled)
        }
}
