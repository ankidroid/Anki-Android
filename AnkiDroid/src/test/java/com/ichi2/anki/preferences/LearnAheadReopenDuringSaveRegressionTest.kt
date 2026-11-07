// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
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
class LearnAheadReopenDuringSaveRegressionTest : RobolectricTest() {
    @Test
    fun `returning to Reviewing displays the completed pending toggle change`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, ReviewingSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                lateinit var preferences: PreferencesFragment
                lateinit var viewModel: LearnAheadSettingsViewModel
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    preferences =
                        activity.supportFragmentManager.fragments
                            .filterIsInstance<PreferencesFragment>()
                            .single()
                    preferences.childFragmentManager.commit {
                        replace(R.id.settings_container, LearnAheadSettingsFragment())
                        addToBackStack(null)
                    }
                    preferences.childFragmentManager.executePendingTransactions()
                    advanceRobolectricLooper()
                    val fragment = preferences.childFragmentManager.findFragmentById(R.id.settings_container)!!
                    viewModel = ViewModelProvider(fragment)[LearnAheadSettingsViewModel::class.java]
                }
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    scenario.onActivity {
                        viewModel.toggleEnabled(false)
                        preferences.childFragmentManager.popBackStackImmediate()
                    }
                    advanceUntilIdle()
                    scenario.onActivity { advanceRobolectricLooper() }
                    advanceUntilIdle()
                    assertEquals(0.minutes, CollectionPreferences.getLearnAheadLimit())
                    scenario.onActivity { activity ->
                        val container = preferences.childFragmentManager.findFragmentById(R.id.settings_container)
                        val reviewing = container as ReviewingSettingsFragment
                        val key = activity.getString(R.string.learn_cutoff_preference)
                        val preference = reviewing.findPreference<androidx.preference.Preference>(key)!!
                        assertEquals(activity.getString(CommonString.disabled), preference.summary.toString())
                    }
                }
            }
        }

    @Test
    fun `reopening while a save is queued displays the saved limit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val first = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()
            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                first.setMinutes(25)
                val reopened = LearnAheadSettingsViewModel()
                advanceUntilIdle()
                assertEquals(25.minutes, CollectionPreferences.getLearnAheadLimit())
                assertEquals(25, reopened.uiState.value.limit.minutes)
            }
        }
}
