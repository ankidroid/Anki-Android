// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadCorruptCollectionRegressionTest : RobolectricTest() {
    @Test
    fun `a corrupt collection encountered during a save starts database recovery`() =
        runTest {
            val oldFailure = CollectionManager.emulatedOpenFailure
            val oldCorruptionFlag = DatabaseCorruption.isDetected
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, LearnAheadSettingsFragment::class)
            try {
                DatabaseCorruption.isDetected = false
                ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                    scenario.onActivity { activity ->
                        advanceRobolectricLooper()
                        val preferences = activity.fragment as PreferencesFragment
                        val fragment = preferences.childFragmentManager.findFragmentById(R.id.settings_container)!!
                        val viewModel = ViewModelProvider(fragment)[LearnAheadSettingsViewModel::class.java]
                        CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.DATABASE_CORRUPT
                        viewModel.setLearnAheadLimit(45)
                        advanceRobolectricLooper()
                    }
                    assertTrue(DatabaseCorruption.isDetected, "The active activity should offer database recovery")
                }
            } finally {
                CollectionManager.emulatedOpenFailure = oldFailure
                DatabaseCorruption.isDetected = oldCorruptionFlag
            }
        }
}
