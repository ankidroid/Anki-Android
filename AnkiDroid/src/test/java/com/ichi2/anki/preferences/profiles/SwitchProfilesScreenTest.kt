// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SwitchProfilesScreenTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a profile can be renamed to a different case of its own name`() {
        val work = ProfileItem(id = ProfileId("p_work"), name = "work")
        var renamedTo: ProfileName? = null
        composeRule.setContent {
            AnkiDroidTheme {
                SwitchProfilesScreen(
                    profiles = listOf(ProfileItem(id = ProfileId.DEFAULT, name = "Default"), work),
                    isAddProfileDialogVisible = false,
                    profileBeingRenamed = work,
                    onNavigateUp = {},
                    onAddProfileClick = {},
                    onAddProfileConfirm = {},
                    onAddProfileDismiss = {},
                    onEditProfile = {},
                    onRenameProfileConfirm = { renamedTo = it },
                    onRenameProfileDismiss = {},
                    onDeleteProfile = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Work")
        composeRule.onNodeWithText(targetContext.getString(CommonString.rename)).performClick()

        assertEquals("Work", renamedTo?.value)
    }
}
