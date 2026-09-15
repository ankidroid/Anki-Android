// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.shareddeck

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.dimensions
import com.ichi2.compose.ui.preview.ThemePreviews
import com.ichi2.anki.common.android.R as CommonR

/** Stateless download screen. State lives in [SharedDecksDownloadViewModel]. */
@Composable
fun SharedDecksDownloadScreen(
    state: SharedDecksDownloadUiState,
    onCancelClick: () -> Unit,
    onImportClick: () -> Unit,
    onTryAgainClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val dimensions = MaterialTheme.dimensions
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top))
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(vertical = dimensions.space300),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.fileName?.let {
            Text(
                text = stringResource(R.string.downloading_file, it),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = dimensions.space200),
            )
        }
        Text(
            text = stringResource(R.string.deck_download_progress_message),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = dimensions.space200, end = dimensions.space200, top = dimensions.space300),
        )
        Text(
            text = statusText(state),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = dimensions.space300),
        )
        LinearProgressIndicator(
            progress = { (state.percent ?: 0f) / 100f },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = dimensions.space200, end = dimensions.space200, top = dimensions.space200),
        )
        if (state.isWaitingForNetwork) {
            Text(
                text = stringResource(R.string.check_network),
                style = MaterialTheme.typography.titleLarge,
                color = colorResource(CommonR.color.material_red_500),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = dimensions.space200, end = dimensions.space200, top = dimensions.space400),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.space300),
            verticalArrangement = Arrangement.spacedBy(dimensions.space300),
        ) {
            when (state.phase) {
                DownloadPhase.Downloading -> FilledButton(R.string.cancel_download, CommonR.color.material_red_500, onCancelClick)
                DownloadPhase.Complete -> FilledButton(R.string.import_deck, CommonR.color.material_blue_500, onImportClick)
                DownloadPhase.Failed -> {
                    TextButton(onClick = onOpenInBrowserClick, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_in_browser), color = colorResource(CommonR.color.material_blue_500))
                    }
                    FilledButton(R.string.try_again, CommonR.color.material_blue_grey_500, onTryAgainClick)
                }
            }
        }
    }
}

@Composable
private fun statusText(state: SharedDecksDownloadUiState): String =
    when {
        state.phase == DownloadPhase.Failed -> stringResource(R.string.download_failed)
        state.percent == null -> TR.syncDownloadingFromAnkiweb()
        else -> stringResource(R.string.percentage, formatDownloadPercent(state.percent))
    }

@Composable
private fun FilledButton(
    @StringRes text: Int,
    @ColorRes color: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = colorResource(color), contentColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(text))
    }
}

@ThemePreviews
@Composable
private fun SharedDecksDownloadScreenPreview() {
    AnkiDroidTheme {
        SharedDecksDownloadScreen(
            state = SharedDecksDownloadUiState(fileName = "Japanese Core 2000.apkg", percent = 42.7f),
            onCancelClick = {},
            onImportClick = {},
            onTryAgainClick = {},
            onOpenInBrowserClick = {},
        )
    }
}
