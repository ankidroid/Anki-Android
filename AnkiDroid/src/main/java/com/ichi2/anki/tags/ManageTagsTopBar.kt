// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.trCase
import androidx.appcompat.R as AppCompatR

/** Displays the tag count, navigation, and clear-unused actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageTagsTopBar(
    content: ManageTagsState.Content?,
    onNavigateUp: () -> Unit,
    onClearUnused: () -> Unit,
) {
    val actionsEnabled = content != null && !content.isWorking
    TopAppBar(
        title = {
            Column {
                Text(stringResource(CommonString.manage_tags))
                if (content != null) {
                    Text(
                        pluralStringResource(CommonPlurals.manage_tags_summary, content.totalTagCount, content.totalTagCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    painterResource(R.drawable.ic_baseline_arrow_back_24),
                    stringResource(AppCompatR.string.abc_action_bar_up_description),
                )
            }
        },
        actions = {
            if (content != null && content.totalTagCount > 0) {
                IconButton(
                    enabled = actionsEnabled,
                    // Avoid flashing the disabled tint during quick operations; clicks stay disabled.
                    colors = IconButtonDefaults.iconButtonColors(disabledContentColor = LocalContentColor.current),
                    onClick = onClearUnused,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete_sweep),
                        contentDescription = trCase().clearUnusedTags(),
                    )
                }
            }
        },
    )
}
