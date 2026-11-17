// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadCollectionFailureRegressionTest : RobolectricTest() {
    @Test
    fun `a locked collection while saving does not escape the screen error handler`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            try {
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                viewModel.setLearnAheadLimit(25)
                advanceRobolectricLooper()
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
        }
}
