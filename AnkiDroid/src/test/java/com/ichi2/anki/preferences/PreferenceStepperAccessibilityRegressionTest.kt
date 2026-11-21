// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.components.PreferenceStepperCard
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PreferenceStepperAccessibilityRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `setting the text through accessibility saves the limit without first focusing the field`() {
        var limit by mutableIntStateOf(20)
        composeRule.setContent {
            AnkiDroidTheme {
                PreferenceStepperCard(
                    title = "Timebox",
                    value = limit,
                    onValueChange = { limit = it },
                    validRange = TimeboxTimeLimit.INPUT_RANGE,
                    unitSuffix = { "mins" },
                )
            }
        }
        val field = composeRule.onNode(hasSetTextAction())
        field.assertIsNotFocused()
        field.performSemanticsAction(SemanticsActions.SetText) { setText ->
            assertTrue(setText(AnnotatedString("45")))
        }
        field.performImeAction()
        composeRule.runOnIdle { assertEquals(45, limit) }
    }
}
