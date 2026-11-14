// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.fragment.app.commit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LearnAheadInitialErrorToastRegressionTest : RobolectricTest() {
    @Test
    fun `opening Learn Ahead reports a collection load failure on the live activity`() =
        runTest {
            val intent = PreferencesActivity.getIntent(targetContext, ReviewingSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    val preferences =
                        activity.supportFragmentManager.fragments
                            .filterIsInstance<PreferencesFragment>()
                            .single()
                    ShadowToast.reset()
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    preferences.childFragmentManager.commit {
                        replace(R.id.settings_container, LearnAheadSettingsFragment())
                        addToBackStack(null)
                    }
                    preferences.childFragmentManager.executePendingTransactions()
                    advanceRobolectricLooper()
                }
                try {
                    scenario.onActivity { advanceRobolectricLooper() }
                    assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
                } finally {
                    CollectionManager.emulatedOpenFailure = null
                }
            }
        }
}
