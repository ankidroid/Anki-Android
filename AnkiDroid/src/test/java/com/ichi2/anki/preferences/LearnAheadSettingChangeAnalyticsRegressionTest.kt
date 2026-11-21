// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.anki.common.analytics.UsageAnalytics
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class LearnAheadSettingChangeAnalyticsRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `disabling Learn Ahead reports the new setting value`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            val intent = PreferencesActivity.getIntent(targetContext, LearnAheadSettingsFragment::class)
            val analytics = mock<UsageAnalytics>()
            try {
                ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                    scenario.onActivity { advanceRobolectricLooper() }
                    Analytics.setAnalytics(analytics)
                    composeRule.onNode(isToggleable()).performClick()
                    scenario.onActivity { advanceRobolectricLooper() }
                    assertEquals(0.minutes, CollectionPreferences.getLearnAheadLimit())
                    verify(analytics).send(AnalyticsEvent.SettingChanged(targetContext.getString(R.string.learn_cutoff_preference), 0))
                }
            } finally {
                Analytics.setAnalytics(AnkiDroidUsageAnalytics)
            }
        }
}
