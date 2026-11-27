// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.components.PreferenceStepperCard
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PreferenceStepperRestoredDisposeRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `leaving after a restored edit keeps the last saved limit`() {
        var value by mutableIntStateOf(20)
        var accept = true
        var shown by mutableStateOf(true)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            AnkiDroidTheme {
                if (shown) {
                    PreferenceStepperCard(
                        title = "Limit",
                        value = value,
                        onValueChange = { if (accept) value = it },
                        validRange = 1..999,
                        unitSuffix = { "min" },
                    )
                }
            }
        }
        // the typed text survives process death, but the saved limit is still 20
        accept = false
        val field = composeRule.onNode(hasSetTextAction())
        field.performClick()
        field.performTextReplacement("45")
        composeRule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        accept = true
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).performClick()
        composeRule.waitForIdle()
        assertEquals(21, value)

        shown = false
        composeRule.waitForIdle()
        assertEquals(21, value)
    }
}
