// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageEditorToolbarTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each action invokes its own callback`() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            AnkiDroidTheme {
                ImageEditorToolbar(
                    hasImage = true,
                    onReplace = { actions.add("replace") },
                    onCrop = { actions.add("crop") },
                )
            }
        }
        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_positive_replace)).performClick()
        composeRule.onNodeWithText(targetContext.getString(CommonString.crop_button)).performClick()
        assertEquals(listOf("replace", "crop"), actions)
    }

    @Test
    fun `crop becomes available when an image is selected`() {
        val hasImage = mutableStateOf(false)
        composeRule.setContent {
            AnkiDroidTheme {
                ImageEditorToolbar(hasImage.value, onReplace = {}, onCrop = {})
            }
        }
        composeRule.onNodeWithText(targetContext.getString(CommonString.dialog_positive_replace)).assertIsEnabled()
        composeRule.onNodeWithText(targetContext.getString(CommonString.crop_button)).assertIsNotEnabled()
        composeRule.runOnIdle { hasImage.value = true }
        composeRule.onNodeWithText(targetContext.getString(CommonString.crop_button)).assertIsEnabled()
    }
}
