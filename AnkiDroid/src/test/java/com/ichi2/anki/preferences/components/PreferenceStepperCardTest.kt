// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceStepperCardTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `showing the card does not report a value change`() {
        val changes = mutableListOf<Int>()
        composeRule.setContent {
            AnkiDroidTheme {
                PreferenceStepperCard(
                    title = "Limit",
                    value = 20,
                    onValueChange = { changes += it },
                    validRange = 1..999,
                    unitSuffix = { "mins" },
                )
            }
        }
        composeRule.waitForIdle()
        assertThat(changes, equalTo(emptyList()))
    }
}
