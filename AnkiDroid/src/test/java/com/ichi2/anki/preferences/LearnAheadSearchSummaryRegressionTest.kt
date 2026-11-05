// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bytehamster.lib.preferencesearch.PreferenceItem
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.requireAccessibleJavaField
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadSearchSummaryRegressionTest : RobolectricTest() {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun `search does not label enabled Learn Ahead as disabled`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            ActivityScenario.launch<PreferencesActivity>(PreferencesActivity.getIntent(targetContext)).use { scenario ->
                scenario.onActivity { activity ->
                    advanceRobolectricLooper()
                    val configuration = SearchConfiguration(activity)
                    HeaderFragment.configureSearchBar(activity, configuration)
                    val search = configuration.showSearchFragment()
                    activity.supportFragmentManager.executePendingTransactions()
                    advanceRobolectricLooper()
                    search.setSearchTerm(activity.getString(CommonString.learn_cutoff))
                    advanceRobolectricLooper()
                    val results = requireAccessibleJavaField<SearchPreferenceFragment>("results").get(search) as List<PreferenceItem>
                    val keyField = requireAccessibleJavaField<PreferenceItem>("key")
                    val result = results.single { keyField.get(it) == activity.getString(R.string.learn_cutoff_preference) }
                    val summary = requireAccessibleJavaField<PreferenceItem>("summary").get(result)
                    assertNotEquals(activity.getString(CommonString.disabled), summary)
                }
            }
        }
}
