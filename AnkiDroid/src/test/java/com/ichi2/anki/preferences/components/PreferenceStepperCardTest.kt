// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.ichi2.anki.common.android.R.string as CommonString

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

    @Test
    fun `buttons remain visible on small screen with large font scale`() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale = 2.0f),
            ) {
                AnkiDroidTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        PreferenceStepperCard(
                            title = "Limit",
                            value = 20,
                            onValueChange = {},
                            validRange = 1..999,
                            unitSuffix = { "mins" },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val minus = targetContext.getString(CommonString.minus_sign)
        val plus = targetContext.getString(CommonString.plus_sign)
        composeRule.onNodeWithContentDescription(minus).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(plus).assertIsDisplayed()
    }
}
