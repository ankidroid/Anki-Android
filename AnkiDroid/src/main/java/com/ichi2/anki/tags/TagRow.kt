// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
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
    // TODO: When the backend can display unused tags; fade out unused tag rows
    val expansionState = if (item.collapsed) CommonString.manage_tags_state_collapsed else CommonString.manage_tags_state_expanded
    val toggleLabel = stringResource(if (item.collapsed) CommonString.manage_tags_expand else CommonString.manage_tags_collapse)
    val description = stringResource(expansionState)
    val actionsLabel = stringResource(CommonString.manage_tags_more_actions, item.fullTag.value)
    val openActions by rememberUpdatedState(onOverflowClick)
    val haptics = LocalHapticFeedback.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("tag:${item.fullTag.value}")
                .then(
                    if (item.hasChildren) {
                        Modifier
                            .combinedClickable(
                                enabled = actionsEnabled,
                                onClickLabel = toggleLabel,
                                onClick = onToggleCollapsed,
                                onLongClickLabel = actionsLabel,
                                onLongClick = onOverflowClick,
                            ).semantics { stateDescription = description }
                    } else {
                        // Leaves have no tap action, but still offer tag actions on a long press.
                        val interactionSource = remember { MutableInteractionSource() }
                        Modifier
                            .indication(interactionSource, LocalIndication.current)
                            .pointerInput(actionsEnabled) {
                                if (actionsEnabled) {
                                    var press: PressInteraction.Press? = null
                                    detectTapGestures(
                                        onPress = {
                                            var released = false
                                            try {
                                                released = tryAwaitRelease()
                                            } finally {
                                                press?.let {
                                                    interactionSource.tryEmit(
                                                        if (released) PressInteraction.Release(it) else PressInteraction.Cancel(it),
                                                    )
                                                }
                                                press = null
                                            }
                                        },
                                        onLongPress = { offset ->
                                            // A short tap has no action, so only a recognized long press starts a ripple.
                                            press = PressInteraction.Press(offset).also { interactionSource.tryEmit(it) }
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            openActions()
                                        },
                                    )
                                }
                            }.semantics {
                                if (!actionsEnabled) disabled()
                                onLongClick(actionsLabel) {
                                    if (actionsEnabled) openActions()
                                    actionsEnabled
                                }
                            }
                    },
                ).padding(start = 8.dp + (item.level.coerceAtMost(MAX_INDENT_LEVEL) * 24).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.hasChildren) {
            Icon(
                painterResource(if (item.collapsed) CommonR.drawable.ic_chevron_right_black else CommonR.drawable.ic_expand_more_black),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
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
                actionsLabel,
            )
        }
    }
}

/** @see TagRow */
@Preview
@Preview(name = "RTL", locale = "ar")
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
