// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews

/** Offers rename and delete actions for a tag in an expanded, scrollable bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagActionsBottomSheet(
    tag: TagName,
    enabled: Boolean,
    onAction: (TagAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (LocalInspectionMode.current) {
        Surface(shape = BottomSheetDefaults.ExpandedShape, color = BottomSheetDefaults.ContainerColor) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                TagActions(tag, enabled, onAction)
            }
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        TagActions(tag, enabled, onAction)
    }
}

/** Displays the selected tag's path above its rename and delete actions. */
@Composable
private fun TagActions(
    tag: TagName,
    enabled: Boolean,
    onAction: (TagAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        TagPathText(
            text = tag.value,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        for (action in listOf(TagAction.RENAME, TagAction.DELETE)) {
            val (label, icon) =
                when (action) {
                    TagAction.RENAME -> CommonString.rename to R.drawable.ic_mode_edit_white
                    else -> CommonString.dialog_positive_delete to R.drawable.ic_delete
                }
            val color = if (action == TagAction.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            ListItem(
                headlineContent = { Text(stringResource(label), color = color) },
                leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = color) },
                modifier = Modifier.clickable(enabled = enabled) { onAction(action) },
            )
        }
    }
}

/** @see TagActionsBottomSheet */
@DialogPreviews
@Composable
private fun TagActionsBottomSheetPreview() {
    AnkiDroidPreview {
        TagActionsBottomSheet(
            tag = requireNotNull(TagName.build("Science::Biology")),
            enabled = true,
            onAction = {},
            onDismissRequest = {},
        )
    }
}

/** @see TagActionsBottomSheet */
@Preview(name = "Extremely long tag", widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun TagActionsBottomSheetLongNamePreview() {
    AnkiDroidPreview {
        TagActionsBottomSheet(
            tag = requireNotNull(TagName.build("AnKing::${"Very_long_parent::".repeat(100)}DNA_replication")),
            enabled = true,
            onAction = {},
            onDismissRequest = {},
        )
    }
}
