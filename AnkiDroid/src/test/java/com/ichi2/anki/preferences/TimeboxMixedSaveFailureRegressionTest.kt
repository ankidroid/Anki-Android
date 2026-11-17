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
class TimeboxMixedSaveFailureRegressionTest : RobolectricTest() {
    @Test
    fun `failed edit followed by failed disable restores the persisted limit`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(20.minutes)
            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()
            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    viewModel.setMinutes(25)
                    viewModel.toggleEnabled(false)
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(20.minutes, CollectionPreferences.getTimeboxTimeLimit())
            val state = viewModel.uiState.value
            assertEquals(20, state.limit.minutes)
            assertEquals(true, state.isEnabled)
            assertEquals(20, Prefs.customTimeboxLimit)
        }

    @Test
    fun `failed enable followed by failed edit restores the persisted disabled state`() =
        runTest {
            Prefs.customTimeboxLimit = 20
            CollectionPreferences.setTimeboxTimeLimit(0.minutes)
            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()
            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    viewModel.toggleEnabled(true)
                    viewModel.setMinutes(25)
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(0.minutes, CollectionPreferences.getTimeboxTimeLimit())
            val state = viewModel.uiState.value
            assertEquals(false, state.isEnabled)
            assertEquals(20, state.limit.minutes)
            assertEquals(20, Prefs.customTimeboxLimit)
        }

    @Test
    fun `a successful edit after a failed enable is retained when disabling fails`() =
        runTest {
            Prefs.customTimeboxLimit = 20
            CollectionPreferences.setTimeboxTimeLimit(0.minutes)
            val viewModel = TimeboxSettingsViewModel()
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
                viewModel.toggleEnabled(true)
                viewModel.setMinutes(25)
                advanceUntilIdle()
                advanceRobolectricLooper()
            }
            assertEquals(25.minutes, CollectionPreferences.getTimeboxTimeLimit())
            try {
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                viewModel.toggleEnabled(false)
                advanceRobolectricLooper()
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
            assertEquals(25.minutes, CollectionPreferences.getTimeboxTimeLimit())
            val state = viewModel.uiState.value
            assertEquals(true, state.isEnabled)
            assertEquals(25, state.limit.minutes)
            assertEquals(25, Prefs.customTimeboxLimit)
        }
}
