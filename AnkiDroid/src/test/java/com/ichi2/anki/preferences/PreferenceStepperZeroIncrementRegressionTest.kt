// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
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
class PreferenceStepperZeroIncrementRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `incrementing a typed zero sets the minimum limit`() {
        var minutes by mutableIntStateOf(20)
        composeRule.setContent {
            AnkiDroidTheme {
                PreferenceStepperCard(
                    title = "Timebox",
                    value = minutes,
                    onValueChange = { minutes = it },
                    validRange = TimeboxTimeLimit.INPUT_RANGE,
                    unitSuffix = { "mins" },
                )
            }
        }
        val input = composeRule.onNode(hasSetTextAction())
        input.performClick()
        input.performTextReplacement("0")
        input.assertIsFocused()

        composeRule.onNodeWithContentDescription(targetContext.getString(CommonString.plus_sign)).performClick()

        composeRule.runOnIdle { assertEquals(1, minutes) }
    }
}
