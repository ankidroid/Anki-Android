// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadPendingEditDisplayRegressionTest : RobolectricTest() {
    @Test
    fun `an earlier successful save cannot replace a newer pending limit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            val dispatcher = StandardTestDispatcher(testScheduler)
            var nextWrite: Runnable? = null
            var dispatches = 0
            val holdSecondWrite =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        if (++dispatches == 2) {
                            nextWrite = Runnable { dispatcher.dispatch(context, block) }
                        } else {
                            dispatcher.dispatch(context, block)
                        }
                    }
                }
            val displayedWhileSecondSaveWasPending =
                withCollectionAccessOn(holdSecondWrite) {
                    viewModel.setLearnAheadLimit(21)
                    viewModel.setLearnAheadLimit(22)
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                    (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes.also {
                        assertNotNull(nextWrite).run()
                        advanceUntilIdle()
                        advanceRobolectricLooper()
                    }
                }

            assertEquals(22.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(22, displayedWhileSecondSaveWasPending)
        }
}
