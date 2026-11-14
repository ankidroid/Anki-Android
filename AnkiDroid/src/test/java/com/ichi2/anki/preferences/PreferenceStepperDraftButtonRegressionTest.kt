// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
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
class PreferenceStepperDraftButtonRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `plus increments the edited number rather than the previous limit`() = checkEditedButton(20, true)

    @Test
    fun `minus decrements the edited number rather than the previous limit`() = checkEditedButton(20, false)

    @Test
    fun `editing away from the minimum enables minus`() = checkEditedButton(1, false, click = false)

    @Test
    fun `editing away from the maximum enables plus`() = checkEditedButton(999, true, click = false)

    private fun checkEditedButton(
        initial: Int,
        increment: Boolean,
        click: Boolean = true,
    ) {
        var value by mutableIntStateOf(initial)
        composeRule.setContent {
            AnkiDroidTheme {
                PreferenceStepperCard(
                    title = "Limit",
                    value = value,
                    onValueChange = { value = it },
                    validRange = 1..999,
                    unitSuffix = { "mins" },
                )
            }
        }
        val input = composeRule.onNode(hasSetTextAction())
        input.performClick()
        input.performTextReplacement("45")
        val description = targetContext.getString(if (increment) CommonString.plus_sign else CommonString.minus_sign)
        val button = composeRule.onNodeWithContentDescription(description)
        button.assertIsEnabled()
        if (!click) return
        button.performClick()
        composeRule.runOnIdle { assertEquals(if (increment) 46 else 44, value) }
    }
}
