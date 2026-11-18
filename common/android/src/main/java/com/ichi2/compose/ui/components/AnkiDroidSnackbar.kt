// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.ichi2.anki.common.android.R
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider

/** Uses the Material snackbar with AnkiDroid's close icon. */
@Composable
internal fun AnkiDroidSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier,
        action =
            data.visuals.actionLabel?.let { label ->
                {
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                    ) {
                        Text(label)
                    }
                }
            },
        dismissAction =
            if (data.visuals.withDismissAction) {
                {
                    IconButton(onClick = data::dismiss) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                            tint = SnackbarDefaults.dismissActionContentColor,
                        )
                    }
                }
            } else {
                null
            },
    ) {
        Text(data.visuals.message)
    }
}

@Preview(widthDp = 360)
@Composable
private fun AnkiDroidSnackbarPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    val data =
        object : SnackbarData {
            override val visuals =
                object : SnackbarVisuals {
                    override val message = "Tag removed"
                    override val actionLabel = "Undo"
                    override val withDismissAction = true
                    override val duration = SnackbarDuration.Indefinite
                }

            override fun performAction() = Unit

            override fun dismiss() = Unit
        }
    AnkiDroidPreview(theme) {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) {
            AnkiDroidSnackbar(data)
        }
    }
}
