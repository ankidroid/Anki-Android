// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class LearnAheadSubMinuteLimitRegressionTest : RobolectricTest() {
    @Test
    fun `an active limit shorter than one minute can be disabled`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(30.seconds)
            assertEquals(30.seconds, CollectionPreferences.getLearnAheadLimit())
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            val state = viewModel.uiState.value as LearnAheadUiState.Loaded
            assertTrue(state.isEnabled, "A nonzero collection limit must leave the switch on")
            viewModel.toggleEnabled(false)
            advanceRobolectricLooper()

            assertEquals(0.seconds, CollectionPreferences.getLearnAheadLimit())
        }
}
