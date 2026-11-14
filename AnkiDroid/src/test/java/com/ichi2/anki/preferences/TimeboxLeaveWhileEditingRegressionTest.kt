// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelStore
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class TimeboxLeaveWhileEditingRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `leaving Timebox while editing saves the typed limit`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, ReviewingSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                lateinit var preferences: PreferencesFragment
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    preferences =
                        activity.supportFragmentManager.fragments
                            .filterIsInstance<PreferencesFragment>()
                            .single()
                    preferences.childFragmentManager.commit {
                        replace(R.id.settings_container, TimeboxSettingsFragment())
                        addToBackStack(null)
                    }
                    preferences.childFragmentManager.executePendingTransactions()
                    advanceRobolectricLooper()
                }
                val field = composeRule.onNode(hasSetTextAction())
                field.performClick()
                field.performTextReplacement("45")
                composeRule.waitForIdle()
                scenario.onActivity { advanceRobolectricLooper() }
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    scenario.onActivity {
                        preferences.childFragmentManager.popBackStackImmediate()
                    }
                    advanceUntilIdle()
                    scenario.onActivity { advanceRobolectricLooper() }
                    advanceUntilIdle()
                }
                assertEquals(45.minutes, CollectionPreferences.getTimeboxTimeLimit())
                scenario.onActivity { activity ->
                    val reviewing =
                        preferences.childFragmentManager.findFragmentById(R.id.settings_container) as ReviewingSettingsFragment
                    val preference = reviewing.findPreference<Preference>(activity.getString(R.string.time_limit_preference))!!
                    assertEquals(
                        activity.resources.getQuantityString(CommonPlurals.pref_summ_minutes, 45, 45),
                        preference.summary.toString(),
                    )
                }
            }
        }

    @Test
    fun `reopening during an exited save displays the saved limit and preserves the custom limit`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(20.minutes)
            val first = TimeboxSettingsViewModel()
            val store = ViewModelStore().apply { put("timebox", first) }
            advanceRobolectricLooper()
            try {
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    first.setMinutes(45)
                    store.clear()
                    val reopened = TimeboxSettingsViewModel()
                    advanceUntilIdle()
                    assertEquals(45.minutes, CollectionPreferences.getTimeboxTimeLimit())
                    assertEquals(45, reopened.uiState.value.limit.minutes)
                    assertEquals(45, Prefs.timeboxCustomLimit.minutes)
                }
            } finally {
                store.clear()
            }
        }
}
