// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * A [BasicAlertDialog] whose content renders inline in Compose previews.
 *
 * Callers supply the card's styling and any action buttons, which appear in both modes.
 * [modifier] applies to the content container; [properties] and [onDismissRequest] configure
 * the dialog window at runtime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiDroidDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    if (LocalInspectionMode.current) {
        // TODO: Preview the surrounding dialog window, including its scrim, positioning and system UI.
        // Match BasicAlertDialog's content constraints, including caller overrides.
        Box(
            modifier = modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
            propagateMinConstraints = true,
        ) {
            content()
        }
    } else {
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            properties = properties,
            content = content,
        )
    }
}
