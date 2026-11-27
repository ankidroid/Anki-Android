// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
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
class TimeboxExitedSaveFailureRegressionTest : RobolectricTest() {
    @Test
    fun `a queued timebox save failure after leaving does not escape uncaught`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(20.minutes)
            val viewModel = TimeboxSettingsViewModel()
            val store = ViewModelStore().apply { put("timebox", viewModel) }
            advanceRobolectricLooper()
            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    viewModel.setMinutes(45)
                    store.clear()
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    advanceUntilIdle()
                    advanceRobolectricLooper()
                }
            } finally {
                CollectionManager.emulatedOpenFailure = null
                store.clear()
            }
            assertEquals(20.minutes, CollectionPreferences.getTimeboxTimeLimit())
        }
}
