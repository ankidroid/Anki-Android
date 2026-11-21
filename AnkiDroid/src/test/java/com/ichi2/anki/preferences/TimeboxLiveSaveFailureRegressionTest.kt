// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class TimeboxLiveSaveFailureRegressionTest : RobolectricTest() {
    @Test
    fun `a failed limit edit is reported while Timebox remains open`() = checkSaveFailure { it.setMinutes(45) }

    @Test
    fun `a failed disable is reported while Timebox remains open`() = checkSaveFailure { it.toggleEnabled(false) }

    private fun checkSaveFailure(change: (TimeboxSettingsViewModel) -> Unit) =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, TimeboxSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                lateinit var viewModel: TimeboxSettingsViewModel
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    val preferences =
                        activity.supportFragmentManager.fragments
                            .filterIsInstance<PreferencesFragment>()
                            .single()
                    val fragment = preferences.childFragmentManager.findFragmentById(R.id.settings_container)!!
                    viewModel = ViewModelProvider(fragment)[TimeboxSettingsViewModel::class.java]
                }
                ShadowToast.reset()
                try {
                    CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
                    scenario.onActivity {
                        change(viewModel)
                        advanceRobolectricLooper()
                    }
                } finally {
                    CollectionManager.emulatedOpenFailure = null
                }
                assertEquals(20.minutes, CollectionPreferences.getTimeboxTimeLimit())
                assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
            }
        }
}
