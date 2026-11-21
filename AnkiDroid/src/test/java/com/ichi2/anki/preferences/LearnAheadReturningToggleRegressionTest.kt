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
class LearnAheadReturningToggleRegressionTest : RobolectricTest() {
    @Test
    fun `toggling while the returning screen refreshes preserves the last saved limit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val returningScreen = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            val secondScreen = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            secondScreen.setLearnAheadLimit(50)
            advanceRobolectricLooper()
            assertEquals(50.minutes, CollectionPreferences.getLearnAheadLimit())

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                returningScreen.refresh()
                returningScreen.toggleEnabled(false)
                returningScreen.toggleEnabled(true)
                advanceUntilIdle()
                advanceRobolectricLooper()
            }

            assertEquals(50.minutes, CollectionPreferences.getLearnAheadLimit())
            assertEquals(LearnAheadUiState.Loaded(true, LearnAheadLimit.fromMinutes(50)!!), returningScreen.uiState.value)
        }
}
