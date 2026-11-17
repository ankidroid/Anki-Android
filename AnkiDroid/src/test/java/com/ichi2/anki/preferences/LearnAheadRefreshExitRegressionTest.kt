// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
class LearnAheadRefreshExitRegressionTest : RobolectricTest() {
    @Test
    fun `leaving a refreshing screen still saves a committed limit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val store = ViewModelStore()
            val viewModel = ViewModelProvider.create(store, ViewModelProvider.NewInstanceFactory())[LearnAheadSettingsViewModel::class]
            advanceRobolectricLooper()
            assertEquals(20, (viewModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes)

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                viewModel.refresh()
                viewModel.setLearnAheadLimit(45)
                store.clear()
                advanceUntilIdle()
                advanceRobolectricLooper()
                advanceUntilIdle()
            }

            assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
