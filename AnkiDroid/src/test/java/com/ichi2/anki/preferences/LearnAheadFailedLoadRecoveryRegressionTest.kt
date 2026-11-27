// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadFailedLoadRecoveryRegressionTest : RobolectricTest() {
    @Test
    fun `a failed initial read does not expose defaults that overwrite the saved limit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(70.minutes)
            val viewModel =
                try {
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    LearnAheadSettingsViewModel().also { advanceRobolectricLooper() }
                } finally {
                    CollectionManager.emulatedOpenFailure = null
                }
            // The lock has cleared, but the screen has never successfully read the user's settings.
            viewModel.toggleEnabled(true)
            advanceRobolectricLooper()
            assertEquals(70.minutes, CollectionPreferences.getLearnAheadLimit())
        }
}
