// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "sw600dp-w800dp-h800dp")
class LearnAheadSearchReturnRegressionTest : RobolectricTest() {
    @Test
    fun `returning from a search result refreshes the original Learn Ahead screen`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, LearnAheadSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    val preferences = activity.fragment as PreferencesFragment
                    val manager = preferences.childFragmentManager
                    val original = manager.findFragmentById(R.id.settings_container) as LearnAheadSettingsFragment
                    val originalModel = ViewModelProvider(original)[LearnAheadSettingsViewModel::class.java]

                    SearchConfiguration(activity).showSearchFragment()
                    activity.supportFragmentManager.executePendingTransactions()
                    advanceRobolectricLooper()
                    val result =
                        mock<SearchPreferenceResult> {
                            on { key } doReturn activity.getString(R.string.learn_cutoff_preference)
                            on { resourceFile } doReturn R.xml.preferences_reviewing
                        }
                    activity.onSearchResultClicked(result)
                    activity.supportFragmentManager.executePendingTransactions()
                    manager.executePendingTransactions()
                    advanceRobolectricLooper()

                    val searched = manager.findFragmentById(R.id.settings_container) as LearnAheadSettingsFragment
                    ViewModelProvider(searched)[LearnAheadSettingsViewModel::class.java].setLearnAheadLimit(50)
                    advanceRobolectricLooper()
                    manager.popBackStackImmediate()
                    advanceRobolectricLooper()

                    assertEquals(original, manager.findFragmentById(R.id.settings_container))
                    assertEquals(50, (originalModel.uiState.value as LearnAheadUiState.Loaded).limit.minutes)
                }
            }
        }
}
