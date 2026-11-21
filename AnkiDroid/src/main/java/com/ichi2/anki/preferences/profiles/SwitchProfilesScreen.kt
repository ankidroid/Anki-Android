// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.dimensions
import com.ichi2.compose.ui.components.AnkiDroidExtendedFab
import com.ichi2.compose.ui.dialogs.MessageDialog
import com.ichi2.compose.ui.preview.ThemePreviews
import androidx.appcompat.R as AppCompatR

/**
 * Stateless screen listing the user's profiles, with a FAB to add a new one.
 * State lives in [SwitchProfilesViewModel], this only renders and forwards events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchProfilesScreen(
    profiles: List<ProfileItem>,
    isAddProfileDialogVisible: Boolean,
    profileBeingRenamed: ProfileItem?,
    profileToSwitchTo: ProfileItem?,
    isSwitchBlocked: Boolean,
    profileSwitchFailed: ProfileItem?,
    profileToDelete: ProfileItem?,
    profileDeleteFailed: ProfileItem?,
    onNavigateUp: () -> Unit,
    onProfileClick: (ProfileItem) -> Unit,
    onAddProfileClick: () -> Unit,
    onAddProfileConfirm: (ProfileName) -> Unit,
    onAddProfileDismiss: () -> Unit,
    onEditProfile: (ProfileItem) -> Unit,
    onRenameProfileConfirm: (ProfileName) -> Unit,
    onRenameProfileDismiss: () -> Unit,
    onSwitchProfileConfirm: () -> Unit,
    onSwitchProfileDismiss: () -> Unit,
    onSwitchBlockedDismiss: () -> Unit,
    onSwitchFailedDismiss: () -> Unit,
    onDeleteProfile: (ProfileItem) -> Unit,
    onDeleteProfileConfirm: () -> Unit,
    onDeleteProfileDismiss: () -> Unit,
    onDeleteFailedDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonString.switch_profile)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnkiDroidExtendedFab(
                onClick = onAddProfileClick,
                icon = {
                    Icon(painterResource(R.drawable.ic_switch_profile), contentDescription = null)
                },
                text = { Text(stringResource(CommonString.add_profile)) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(profiles, key = { it.id.value }) { profile ->
                ProfileRow(
                    profile = profile,
                    onClick = { onProfileClick(profile) },
                    onEditClick = { onEditProfile(profile) },
                    onDeleteClick = { onDeleteProfile(profile) },
                )
            }
        }
    }

    if (isAddProfileDialogVisible) {
        AddProfileDialog(
            existingNames = profiles.map { it.name },
            onDismissRequest = onAddProfileDismiss,
            onConfirm = onAddProfileConfirm,
        )
    }

    profileBeingRenamed?.let { profile ->
        RenameProfileDialog(
            currentName = profile.name,
            existingNames = profiles.filter { it.id != profile.id }.map { it.name },
            onDismissRequest = onRenameProfileDismiss,
            onConfirm = onRenameProfileConfirm,
        )
    }

    profileToSwitchTo?.let { profile ->
        MessageDialog(
            title = stringResource(CommonString.switch_profile_title, profile.name),
            message = stringResource(CommonString.switch_profile_message, stringResource(CommonString.app_name)),
            confirmText = stringResource(CommonString.switch_profile_confirm),
            onConfirm = onSwitchProfileConfirm,
            onDismissRequest = onSwitchProfileDismiss,
        )
    }

    if (isSwitchBlocked) {
        AlertDialog(
            onDismissRequest = onSwitchBlockedDismiss,
            text = { Text(stringResource(CommonString.switch_profile_blocked, stringResource(CommonString.app_name))) },
            confirmButton = {
                TextButton(onClick = onSwitchBlockedDismiss) {
                    Text(stringResource(CommonString.dialog_ok))
                }
            },
        )
    }

    profileSwitchFailed?.let { profile ->
        AlertDialog(
            onDismissRequest = onSwitchFailedDismiss,
            text = { Text(stringResource(CommonString.switch_profile_failed, profile.name)) },
            confirmButton = {
                TextButton(onClick = onSwitchFailedDismiss) {
                    Text(stringResource(CommonString.dialog_ok))
                }
            },
        )
    }

    profileToDelete?.let { profile ->
        MessageDialog(
            title = stringResource(CommonString.delete_profile_title, profile.name),
            message = stringResource(CommonString.delete_profile_message),
            confirmText = stringResource(CommonString.dialog_positive_delete),
            onConfirm = onDeleteProfileConfirm,
            onDismissRequest = onDeleteProfileDismiss,
        )
    }

    profileDeleteFailed?.let { profile ->
        AlertDialog(
            onDismissRequest = onDeleteFailedDismiss,
            text = { Text(stringResource(CommonString.delete_profile_failed, profile.name)) },
            confirmButton = {
                TextButton(onClick = onDeleteFailedDismiss) {
                    Text(stringResource(CommonString.dialog_ok))
                }
            },
        )
    }
}

private val AvatarSize = 40.dp

@Composable
private fun ProfileRow(
    profile: ProfileItem,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (profile.isActive) colors.onSecondaryContainer else LocalContentColor.current
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(if (profile.isActive) colors.secondaryContainer else Color.Transparent)
                    .clickable(onClick = onClick)
                    .padding(
                        horizontal = MaterialTheme.dimensions.space200,
                        vertical = MaterialTheme.dimensions.space100,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(AvatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profile.initial,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            start = MaterialTheme.dimensions.space150,
                            end = MaterialTheme.dimensions.space100,
                        ),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.isActive) {
                    Text(
                        text = stringResource(CommonString.current_profile),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    painterResource(R.drawable.ic_popup_menu_item_editor),
                    contentDescription = stringResource(CommonString.edit_profile),
                )
            }
            if (profile.isActive) {
                Spacer(Modifier.minimumInteractiveComponentSize())
            } else {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(CommonString.delete_profile),
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SwitchProfilesScreenPreview() {
    AnkiDroidTheme {
        SwitchProfilesScreen(
            profiles =
                listOf(
                    ProfileItem(id = ProfileId.DEFAULT, name = "Default", isActive = true),
                    ProfileItem(id = ProfileId("p_work"), name = "Work"),
                ),
            isAddProfileDialogVisible = false,
            profileBeingRenamed = null,
            profileToSwitchTo = null,
            isSwitchBlocked = false,
            profileSwitchFailed = null,
            profileToDelete = null,
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
