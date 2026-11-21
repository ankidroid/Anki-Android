// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.ankiColors
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews
import com.ichi2.anki.common.android.R as CommonR

/**
 * Material 3 FAB menu for the Deck Picker.
 */
@Composable
fun DeckPickerFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddNote: () -> Unit,
    onCreateDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onAddSharedDeck: () -> Unit,
    createDeckLabel: String,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        BackHandler { onExpandedChange(false) }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (expanded && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onExpandedChange(false)
                        true
                    } else {
                        false
                    }
                },
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (expanded) {
            // Background scrim that closes the menu when tapped
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onExpandedChange(false) },
                        ),
            )
        }

        Column(
            modifier = Modifier.padding(end = 24.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StaggeredFabMenuItem(visible = expanded, indexFromBottom = 3) {
                    DeckPickerFabMenuItem(
                        text = stringResource(CommonR.string.menu_get_shared_decks),
                        iconRes = R.drawable.ic_file_download_white,
                        onClick = {
                            onExpandedChange(false)
                            onAddSharedDeck()
                        },
                    )
                }
                StaggeredFabMenuItem(visible = expanded, indexFromBottom = 2) {
                    DeckPickerFabMenuItem(
                        text = stringResource(CommonR.string.new_dynamic_deck),
                        iconRes = R.drawable.ic_add_filtered_deck,
                        onClick = {
                            onExpandedChange(false)
                            onAddFilteredDeck()
                        },
                    )
                }
                StaggeredFabMenuItem(visible = expanded, indexFromBottom = 1) {
                    DeckPickerFabMenuItem(
                        text = createDeckLabel,
                        iconRes = R.drawable.ic_add_deck_filled,
                        onClick = {
                            onExpandedChange(false)
                            onCreateDeck()
                        },
                    )
                }
                StaggeredFabMenuItem(visible = expanded, indexFromBottom = 0) {
                    DeckPickerFabMenuItem(
                        text = stringResource(CommonString.menu_add),
                        iconRes = R.drawable.ic_add_note,
                        onClick = {
                            onExpandedChange(false)
                            onAddNote()
                        },
                    )
                }
            }

            val cornerRadius by animateDpAsState(
                targetValue = if (expanded) 28.dp else 16.dp,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "fab_corner_radius",
            )
            val iconRotation by animateFloatAsState(
                targetValue = if (expanded) 45f else 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "fab_icon_rotation",
            )

            FloatingActionButton(
                onClick = { onExpandedChange(!expanded) },
                shape = RoundedCornerShape(cornerRadius),
                containerColor = MaterialTheme.ankiColors.fabContainer,
                contentColor = MaterialTheme.ankiColors.onFab,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(CommonR.drawable.ic_add),
                    contentDescription =
                        if (expanded) {
                            stringResource(CommonString.dialog_cancel)
                        } else {
                            stringResource(CommonString.menu_add)
                        },
                    modifier =
                        Modifier
                            .size(24.dp)
                            .rotate(iconRotation),
                )
            }
        }
    }
}

@Composable
private fun StaggeredFabMenuItem(
    visible: Boolean,
    indexFromBottom: Int,
    totalItems: Int = 4,
    content: @Composable () -> Unit,
) {
    val enterDelay = indexFromBottom * 40
    val exitDelay = (totalItems - 1 - indexFromBottom) * 30

    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(
                animationSpec =
                    tween(
                        durationMillis = 200,
                        delayMillis = enterDelay,
                        easing = FastOutSlowInEasing,
                    ),
            ) +
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth * 2 / 3 },
                    animationSpec =
                        tween(
                            durationMillis = 250,
                            delayMillis = enterDelay,
                            easing = FastOutSlowInEasing,
                        ),
                ),
        exit =
            fadeOut(
                animationSpec =
                    tween(
                        durationMillis = 150,
                        delayMillis = exitDelay,
                        easing = FastOutSlowInEasing,
                    ),
            ) +
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth * 2 / 3 },
                    animationSpec =
                        tween(
                            durationMillis = 150,
                            delayMillis = exitDelay,
                            easing = FastOutSlowInEasing,
                        ),
                ),
    ) {
        content()
    }
}

/**
 * Individual action item
 */
@Composable
private fun DeckPickerFabMenuItem(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 3.dp,
        modifier = modifier.height(56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun DeckPickerFabMenuPreview() {
    var expanded by remember { mutableStateOf(false) }
    AnkiDroidPreview {
        DeckPickerFabMenu(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onAddNote = {},
            onCreateDeck = {},
            onAddFilteredDeck = {},
            onAddSharedDeck = {},
            createDeckLabel = "Create deck",
        )
    }
}
