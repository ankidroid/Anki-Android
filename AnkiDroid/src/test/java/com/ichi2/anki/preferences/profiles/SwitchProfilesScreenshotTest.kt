// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isDialog
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.multiprofile.ProfileId
import org.junit.Test

class SwitchProfilesScreenshotTest : ComposeScreenshotTest() {
    private val default = ProfileItem(id = ProfileId.DEFAULT, name = "Default", isActive = true)
    private val work = ProfileItem(id = ProfileId("p_work"), name = "Work")
    private val personal = ProfileItem(id = ProfileId("p_personal"), name = "Personal")

    @Test
    fun profileList() {
        capture("profile_list") { Screen() }
    }

    @Test
    fun deleteDialog() {
        capture("delete_profile", node = isDialog()) { Screen(profileToDelete = work) }
    }

    @Composable
    private fun Screen(profileToDelete: ProfileItem? = null) {
        SwitchProfilesScreen(
            profiles = listOf(default, personal, work),
            isAddProfileDialogVisible = false,
            profileBeingRenamed = null,
            profileToSwitchTo = null,
            isSwitchBlocked = false,
            profileSwitchFailed = null,
            profileToDelete = profileToDelete,
            profileDeleteFailed = null,
            onNavigateUp = {},
            onProfileClick = {},
            onAddProfileClick = {},
            onAddProfileConfirm = {},
            onAddProfileDismiss = {},
            onEditProfile = {},
            onRenameProfileConfirm = {},
            onRenameProfileDismiss = {},
            onSwitchProfileConfirm = {},
            onSwitchProfileDismiss = {},
            onSwitchBlockedDismiss = {},
            onSwitchFailedDismiss = {},
            onDeleteProfile = {},
            onDeleteProfileConfirm = {},
            onDeleteProfileDismiss = {},
            onDeleteFailedDismiss = {},
        )
    }
}
