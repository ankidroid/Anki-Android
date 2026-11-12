// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.res.use
import com.ichi2.anki.common.android.R
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

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
    val typography = dialogTypography()
    // Preserve TextView's legacy line-height path instead of inheriting Compose's default.
    if (LocalInspectionMode.current) {
        // TODO: Preview the surrounding dialog window, including its scrim, positioning and system UI.
        // Match BasicAlertDialog's content constraints, including caller overrides.
        Box(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp).sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
            propagateMinConstraints = true,
        ) {
            MaterialTheme(typography = typography) {
                CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge.copy(lineHeightStyle = null), content = content)
            }
        }
    } else {
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            properties = properties,
        ) {
            val window = (LocalView.current.parent as DialogWindowProvider).window
            // A slide here competes with initial field focus and keyboard positioning.
            SideEffect {
                if (window.attributes.windowAnimations != R.style.Animation_AnkiDroidDialog) {
                    window.setWindowAnimations(R.style.Animation_AnkiDroidDialog)
                }
            }
            MaterialTheme(typography = typography) {
                CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge.copy(lineHeightStyle = null), content = content)
            }
        }
    }
}

/** Insets inside the card, matching the View dialog title and action panels. */
@Composable
fun ankiDroidDialogContentPadding(): PaddingValues =
    PaddingValues(
        start = 24.dp,
        top = dimensionResource(AppCompatR.dimen.abc_dialog_padding_top_material),
        end = 24.dp,
        bottom = dimensionResource(MaterialR.dimen.m3_alert_dialog_action_bottom_padding),
    )

/** The View dialog's shadow elevation, including the zero-elevation e-ink theme. */
@Composable
fun ankiDroidDialogElevation(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        AlertDialog.Builder(context).context.obtainStyledAttributes(intArrayOf(android.R.attr.windowElevation)).use {
            with(density) { it.getDimension(0, 0f).toDp() }
        }
    }
}

/** View dialog actions: end aligned, with confirmation above dismissal when stacked. */
@Composable
fun AnkiDroidDialogButtons(
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    val topPadding = dimensionResource(MaterialR.dimen.m3_alert_dialog_action_top_padding)
    val spacing = dimensionResource(MaterialR.dimen.m3_btn_dialog_btn_spacing)
    val minWidth = dimensionResource(MaterialR.dimen.m3_btn_dialog_btn_min_width)
    val inset = dimensionResource(MaterialR.dimen.m3_btn_inset)
    val buttonPadding =
        PaddingValues(
            start = dimensionResource(MaterialR.dimen.m3_btn_text_btn_padding_left),
            top = dimensionResource(MaterialR.dimen.m3_btn_padding_top) + inset,
            end = dimensionResource(MaterialR.dimen.m3_btn_text_btn_padding_right),
            bottom = dimensionResource(MaterialR.dimen.m3_btn_padding_bottom) + inset,
        )
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        content = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.sizeIn(minWidth = minWidth, minHeight = 48.dp),
                contentPadding = buttonPadding,
            ) { Text(dismissText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.sizeIn(minWidth = minWidth, minHeight = 48.dp),
                contentPadding = buttonPadding,
                enabled = confirmEnabled,
            ) { Text(confirmText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
    ) { children, constraints ->
        val buttonConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val dismiss = children[0].measure(buttonConstraints)
        val confirm = children[1].measure(buttonConstraints)
        val gap = spacing.roundToPx()
        // Both View actions have a start margin, including the first action.
        val stacked = dismiss.width + confirm.width + 2 * gap > constraints.maxWidth
        val height = if (stacked) confirm.height + dismiss.height else maxOf(confirm.height, dismiss.height)
        layout(constraints.maxWidth, height) {
            if (stacked) {
                confirm.placeRelative(constraints.maxWidth - confirm.width, 0)
                dismiss.placeRelative(constraints.maxWidth - dismiss.width, confirm.height)
            } else {
                dismiss.placeRelative(constraints.maxWidth - confirm.width - gap - dismiss.width, (height - dismiss.height) / 2)
                confirm.placeRelative(constraints.maxWidth - confirm.width, (height - confirm.height) / 2)
            }
        }
    }
}
