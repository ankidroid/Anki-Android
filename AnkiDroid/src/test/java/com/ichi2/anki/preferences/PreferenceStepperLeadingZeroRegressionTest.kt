// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.components.PreferenceStepperCard
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PreferenceStepperLeadingZeroRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `leading zeros do not use a Learn Ahead digit slot`() = checkPaddedInput(1..999, "0015")

    @Test
    fun `leading zeros do not use a Timebox digit slot`() = checkPaddedInput(TimeboxTimeLimit.INPUT_RANGE, "00015")

    private fun checkPaddedInput(
        range: IntRange,
        digits: String,
    ) {
        var limit by mutableIntStateOf(20)
        composeRule.setContent {
            AnkiDroidTheme {
                PreferenceStepperCard(
                    title = "Limit",
                    value = limit,
                    onValueChange = { limit = it },
                    validRange = range,
                    unitSuffix = { "mins" },
                )
            }
        }
        val input = composeRule.onNode(hasSetTextAction())
        input.performClick()
        input.performTextClearance()
        digits.forEach { input.performTextInput(it.toString()) }
        input.performImeAction()
        composeRule.runOnIdle { assertEquals(15, limit) }
    }
}
