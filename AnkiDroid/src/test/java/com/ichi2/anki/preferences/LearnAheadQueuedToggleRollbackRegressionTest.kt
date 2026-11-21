// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadQueuedToggleRollbackRegressionTest : RobolectricTest() {
    @Test
    fun `a failed limit change preserves an earlier successful enable`() =
        kotlinx.coroutines.test.runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                withCollectionAccessOn(dispatcher) {
                    Prefs.customLearnAheadLimit = 20
                    val initialWrite = launch { CollectionPreferences.setLearnAheadLimit(0.minutes) }
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                    advanceUntilIdle()
                    assertTrue(initialWrite.isCompleted)
                    LearnAheadSettingsViewModel().also {
                        advanceUntilIdle()
                        advanceRobolectricLooper()
                        advanceUntilIdle()
                        assertFalse((it.uiState.value as LearnAheadUiState.Loaded).isEnabled)
                    }
                }
            var dispatched = 0
            val failSecondCollectionCall =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        val fail = ++dispatched == 2
                        dispatcher.dispatch(context) {
                            val previousFailure = CollectionManager.emulatedOpenFailure
                            if (fail) CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                            try {
                                block.run()
                            } finally {
                                CollectionManager.emulatedOpenFailure = previousFailure
                            }
                        }
                    }
                }
            withCollectionAccessOn(failSecondCollectionCall) {
                viewModel.toggleEnabled(true)
                viewModel.setLearnAheadLimit(30)
                advanceUntilIdle()
                advanceRobolectricLooper()
            }

            val savedLimit = CollectionPreferences.getLearnAheadLimit()
            assertEquals(20.minutes, savedLimit)
            assertEquals(listOf(CommonString.something_wrong), viewModel.errorEvents.replayCache)
            assertTrue((viewModel.uiState.value as LearnAheadUiState.Loaded).isEnabled)
            assertEquals(savedLimit, Prefs.customLearnAheadLimit.minutes)
        }
}
