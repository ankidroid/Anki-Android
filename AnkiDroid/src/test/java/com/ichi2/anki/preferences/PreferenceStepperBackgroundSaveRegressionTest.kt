// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class PreferenceStepperBackgroundSaveRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `backgrounding Learn Ahead persists the draft before the process can be killed`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(20.minutes)
            checkBackgroundSave(LearnAheadSettingsFragment::class) {
                assertEquals(45.minutes, CollectionPreferences.getLearnAheadLimit())
            }
        }

    @Test
    fun `backgrounding Timebox persists the draft before the process can be killed`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(20.minutes)
            checkBackgroundSave(TimeboxSettingsFragment::class) {
                assertEquals(45.minutes, CollectionPreferences.getTimeboxTimeLimit())
            }
        }

    private suspend fun checkBackgroundSave(
        fragment: KClass<out Fragment>,
        checkSaved: suspend () -> Unit,
    ) {
        val intent = PreferencesActivity.getIntent(targetContext, fragment)
        ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
            scenario.onActivity { advanceRobolectricLooper() }
            val input = composeRule.onNode(hasSetTextAction())
            input.performClick()
            input.performTextReplacement("45")
            composeRule.waitForIdle()

            // Home stops the activity without disposing its Compose view. Android may
            // subsequently kill this background process without another callback.
            scenario.moveToState(Lifecycle.State.CREATED)
            advanceRobolectricLooper()
            checkSaved()
        }
    }
}
