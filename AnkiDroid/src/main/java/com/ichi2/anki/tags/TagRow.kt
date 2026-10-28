// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider
import com.ichi2.anki.common.android.R as CommonR

// Leave room for the label and overflow button on compact screens.
private const val MAX_INDENT_LEVEL = 4

/** Displays one tag in the hierarchy, with expansion controls and an overflow button for tag actions. */
@Composable
internal fun TagRow(
    item: TagListItemState,
    actionsEnabled: Boolean,
    onToggleCollapsed: () -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: Display unused tags differently.
    val expansionState = if (item.collapsed) CommonString.manage_tags_state_collapsed else CommonString.manage_tags_state_expanded
    val toggleLabel = stringResource(if (item.collapsed) CommonString.manage_tags_expand else CommonString.manage_tags_collapse)
    val description = stringResource(expansionState)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("tag:${item.fullTag.value}")
                .then(
                    if (item.hasChildren) {
                        Modifier
                            .clickable(enabled = actionsEnabled, onClickLabel = toggleLabel, onClick = onToggleCollapsed)
                            .semantics { stateDescription = description }
                    } else {
                        Modifier
                    },
                ).padding(start = 8.dp + (item.level.coerceAtMost(MAX_INDENT_LEVEL) * 24).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.hasChildren) {
            Icon(
                painterResource(CommonR.drawable.ic_chevron_right_black),
                contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(if (item.collapsed) 0f else 90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 16.dp)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onOverflowClick, enabled = actionsEnabled) {
            Icon(
                painterResource(R.drawable.ic_more_vertical),
                stringResource(CommonString.manage_tags_more_actions, item.fullTag.value),
            )
        }
    }
}

/** @see TagRow */
@Preview
@Composable
private fun TagRowPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        Surface {
            Column {
                for (item in listOf(
                    TagListItemState("History", "History", 0, hasChildren = true, collapsed = true),
                    TagListItemState("Science", "Science", 0, hasChildren = true, collapsed = false),
                    TagListItemState("Science::Biology", "Biology", 1, hasChildren = false, collapsed = false),
                    TagListItemState(
                        (List(15) { "Level$it" } + "Leaf").joinToString("::"),
                        "Leaf",
                        15,
                        hasChildren = false,
                        collapsed = false,
                    ),
                )) {
                    TagRow(item, actionsEnabled = true, onToggleCollapsed = {}, onOverflowClick = {})
                }
                TagRow(
                    TagListItemState("Languages", "Languages", 0, hasChildren = true, collapsed = true),
                    actionsEnabled = false,
                    onToggleCollapsed = {},
                    onOverflowClick = {},
                )
            }
        }
    }
}
