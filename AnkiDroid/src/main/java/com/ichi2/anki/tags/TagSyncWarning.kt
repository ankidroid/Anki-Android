// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews
import com.ichi2.anki.common.android.R as CommonR

@Composable
internal fun TagSyncWarning(
    message: AnnotatedString,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TagSyncWarningSurface(modifier.testTag("tag_sync_warning")) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_sync_problem), contentDescription = null)
            Text(
                message,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_clear_white), stringResource(CommonString.manage_tags_dismiss_sync_warning))
            }
        }
    }
}

@Composable
internal fun TagSyncDetails(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) CommonString.manage_tags_state_expanded else CommonString.manage_tags_state_collapsed)
    TagSyncWarningSurface(modifier) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("tag_sync_warning")
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .semantics { stateDescription = state }
                        .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_sync_problem),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(CommonString.manage_tags_dialog_sync_summary),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    painterResource(CommonR.drawable.ic_expand_more_black),
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Text(
                    tagSyncMessage(CommonString.manage_tags_dialog_sync_instructions),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun tagSyncMessage(
    @StringRes messageRes: Int,
): AnnotatedString {
    val action = TagAction.CLEAR_UNUSED.dialogTitle()
    val message = stringResource(messageRes, action)
    return buildAnnotatedString {
        append(message)
        val start = message.indexOf(action)
        if (start >= 0 && action.isNotEmpty()) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + action.length)
        }
    }
}

@Composable
private fun TagSyncWarningSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val border = MaterialTheme.widgetBorder
    val background =
        if (border != null) {
            MaterialTheme.colorScheme.surface
        } else {
            colorResource(CommonR.color.material_orange_300).copy(alpha = 0.24f)
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = background,
        border = border,
        content = content,
    )
}

/** @see TagSyncWarning */
@ThemePreviews
@Composable
private fun TagSyncWarningPreview() {
    AnkiDroidPreview {
        TagSyncWarning(
            message = tagSyncMessage(CommonString.manage_tags_deleted_sync_warning),
            onDismiss = {},
        )
    }
}
