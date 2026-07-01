// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.dimensions
import com.ichi2.compose.ui.components.AnkiDroidExtendedFab
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
    onNavigateUp: () -> Unit,
    onAddProfileClick: () -> Unit,
    onAddProfileConfirm: (ProfileName) -> Unit,
    onAddProfileDismiss: () -> Unit,
    onEditProfile: (ProfileItem) -> Unit,
    onDeleteProfile: (ProfileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.switch_profile)) },
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
                text = { Text(stringResource(R.string.add_profile)) },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    onEditClick = { onEditProfile(profile) },
                    onDeleteClick = { onDeleteProfile(profile) },
                )
            }
        }
    }

    if (isAddProfileDialogVisible) {
        AddProfileDialog(
            onDismissRequest = onAddProfileDismiss,
            onConfirm = onAddProfileConfirm,
        )
    }
}

private val AvatarSize = 40.dp

@Composable
private fun ProfileRow(
    profile: ProfileItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
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
        Text(
            text = profile.name,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        start = MaterialTheme.dimensions.space150,
                        end = MaterialTheme.dimensions.space100,
                    ),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onEditClick) {
            Icon(
                painterResource(R.drawable.ic_popup_menu_item_editor),
                contentDescription = stringResource(R.string.edit_profile),
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.delete_profile),
            )
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
                    ProfileItem(id = "default", name = "Default"),
                    ProfileItem(id = "work", name = "Work"),
                ),
            isAddProfileDialogVisible = false,
            onNavigateUp = {},
            onAddProfileClick = {},
            onAddProfileConfirm = {},
            onAddProfileDismiss = {},
            onEditProfile = {},
            onDeleteProfile = {},
        )
    }
}
