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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadQueuedMainRollbackProbeTest : RobolectricTest() {
    @Test
    fun `the displayed Learn Ahead limit follows whichever queued edit saves`() =
        kotlinx.coroutines.test.runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel =
                withCollectionAccessOn(dispatcher) {
                    val initialWrite = launch { CollectionPreferences.setLearnAheadLimit(20.minutes) }
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                    advanceUntilIdle()
                    assertTrue(initialWrite.isCompleted)
                    LearnAheadSettingsViewModel().also {
                        advanceUntilIdle()
                        advanceRobolectricLooper()
                        advanceUntilIdle()
                        assertEquals(20, (it.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
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
                viewModel.setLearnAheadLimit(25)
                viewModel.setLearnAheadLimit(30)
                advanceUntilIdle()
                advanceRobolectricLooper()
            }

            val savedLimit = CollectionPreferences.getLearnAheadLimit()
            assertTrue(savedLimit == 25.minutes || savedLimit == 30.minutes)
            assertEquals(listOf(CommonString.something_wrong), viewModel.errorEvents.replayCache)
            assertEquals(savedLimit, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.toDuration())
            assertEquals(savedLimit, Prefs.customLearnAheadLimit.minutes)
        }
}
