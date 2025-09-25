package com.ichi2.anki.ui.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.DisplayDeckNode

@Composable
fun DeckItem(
    deck: DisplayDeckNode,
    modifier: Modifier = Modifier,
    onDeckClick: () -> Unit,
    onExpandClick: () -> Unit,
    onDeckOptions: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onRebuild: () -> Unit,
    onEmpty: () -> Unit,
) {
    var isContextMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDeckClick() },
                        onLongPress = { isContextMenuOpen = true },
                    )
                }.padding(start = (deck.depth * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (deck.canCollapse) {
            Icon(
                painter = painterResource(if (deck.collapsed) R.drawable.ic_expand_more_black_24dp else R.drawable.ic_expand_less_black_24dp),
                contentDescription = if (deck.collapsed) stringResource(R.string.expand) else stringResource(R.string.collapse),
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { onExpandClick() })
                    },
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
        Text(
            text = deck.lastDeckNameComponent,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = deck.newCount.toString(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = deck.lrnCount.toString(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = deck.revCount.toString(),
            color = Color(0xFF4CAF50), // Green 500
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        DropdownMenu(
            expanded = isContextMenuOpen,
            onDismissRequest = { isContextMenuOpen = false },
        ) {
            if (deck.filtered) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rebuild_cram_label)) },
                    onClick = {
                        onRebuild()
                        isContextMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.empty_cram_label)) },
                    onClick = {
                        onEmpty()
                        isContextMenuOpen = false
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename_deck)) },
                    onClick = {
                        onRename()
                        isContextMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_deck)) },
                    onClick = {
                        onExport()
                        isContextMenuOpen = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Options") }, // TODO: Replace with R.string.deck_options
                onClick = {
                    onDeckOptions()
                    isContextMenuOpen = false
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") }, // TODO: Replace with R.string.delete_deck
                onClick = {
                    onDelete()
                    isContextMenuOpen = false
                },
            )
        }
    }
}
