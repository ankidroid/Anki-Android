// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextInputDialogTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the length counter ignores surrounding spaces`() {
        composeRule.setContent {
            TextInputDialog(
                title = "Title",
                label = "Label",
                confirmText = "OK",
                onConfirm = {},
                onDismissRequest = {},
                maxLengthCounter = 3,
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput(" abc ")

        composeRule.onNodeWithText("3/3").assertIsDisplayed()
    }
}
