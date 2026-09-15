// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.shareddeck

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.dimensions
import com.ichi2.compose.ui.preview.ThemePreviews

private val MaxContentWidth = 600.dp
private val ProgressRingSize = 240.dp
private val ProgressRingStroke = 10.dp
private val InfoBadgeSize = 28.dp

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
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top))
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
        ) {
            Header(fileName = state.fileName)
            Spacer(Modifier.weight(1f))
            ProgressRing(
                state = state,
                modifier = Modifier.padding(vertical = MaterialTheme.dimensions.screenEdge),
            )
            Spacer(Modifier.weight(1f))
            InfoCard()
            if (state.isWaitingForNetwork) NetworkWarning()
            Actions(
                phase = state.phase,
                onCancelClick = onCancelClick,
                onImportClick = onImportClick,
                onTryAgainClick = onTryAgainClick,
                onOpenInBrowserClick = onOpenInBrowserClick,
            )
        }
    }
}

/** Title naming the file being downloaded. */
@Composable
private fun Header(fileName: String?) {
    val edge = MaterialTheme.dimensions.screenEdge
    Text(
        text = fileName?.let { stringResource(R.string.downloading_file, it) }.orEmpty(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(top = edge, start = edge, end = edge),
    )
}

/** Progress ring with the percentage and size in the middle. */
@Composable
private fun ProgressRing(
    state: SharedDecksDownloadUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val percent = state.percent
        if (percent == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(ProgressRingSize),
                strokeWidth = ProgressRingStroke,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            CircularProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.size(ProgressRingSize),
                strokeWidth = ProgressRingStroke,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.phase == DownloadPhase.Failed -> RingLabel(stringResource(R.string.download_failed))
                percent == null -> RingLabel(TR.syncDownloadingFromAnkiweb())
                else -> {
                    PercentageText(percent)
                    downloadSizeText(state.downloadedBytes, state.totalBytes)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MaterialTheme.dimensions.space50),
                        )
                    }
                }
            }
        }
    }
}

/** Text in the ring when there's no percentage to show. */
@Composable
private fun RingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = ProgressRingSize).padding(horizontal = MaterialTheme.dimensions.space400),
    )
}

/** The percentage in the ring, with a smaller % sign. */
@Composable
private fun PercentageText(percent: Float) {
    val text = stringResource(R.string.percentage, formatDownloadPercent(percent))
    val style = MaterialTheme.typography.displayLarge
    val sign = text.indexOf('%')
    Text(
        text =
            buildAnnotatedString {
                append(text)
                if (sign >= 0) addStyle(SpanStyle(fontSize = style.fontSize * 0.6f), sign, sign + 1)
            },
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

/** Size downloaded out of the total, or null while neither is known. */
@Composable
private fun downloadSizeText(
    downloadedBytes: Long,
    totalBytes: Long,
): String? {
    val context = LocalContext.current
    if (totalBytes <= 0 && downloadedBytes <= 0) return null
    val downloaded = Formatter.formatFileSize(context, downloadedBytes)
    if (totalBytes <= 0) return downloaded
    return stringResource(R.string.progress_amount_bytes, downloaded, Formatter.formatFileSize(context, totalBytes))
}

/** Card saying it's fine to use other apps while the download runs. */
@Composable
private fun InfoCard() {
    val dimensions = MaterialTheme.dimensions
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = dimensions.screenEdge, end = dimensions.screenEdge, bottom = dimensions.screenEdge),
    ) {
        Row(
            modifier = Modifier.padding(dimensions.sectionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(InfoBadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(dimensions.space50),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_done),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                )
            }
            Text(
                text = stringResource(R.string.deck_download_progress_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = dimensions.sectionGap),
            )
        }
    }
}

/** Warning shown while the download waits for a network connection. */
@Composable
private fun NetworkWarning() {
    val dimensions = MaterialTheme.dimensions
    Text(
        text = stringResource(R.string.check_network),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.sectionGap, vertical = dimensions.space100)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Buttons for the current [DownloadPhase]. */
@Composable
private fun Actions(
    phase: DownloadPhase,
    onCancelClick: () -> Unit,
    onImportClick: () -> Unit,
    onTryAgainClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit,
) {
    val dimensions = MaterialTheme.dimensions
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = dimensions.screenEdge, end = dimensions.screenEdge, bottom = dimensions.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(dimensions.sectionGap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(dimensions.space100),
    ) {
        when (phase) {
            DownloadPhase.Downloading ->
                FilledTonalButton(
                    onClick = onCancelClick,
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                ) {
                    Text(stringResource(R.string.cancel_download))
                }
            DownloadPhase.Complete ->
                Button(onClick = onImportClick) {
                    Text(stringResource(R.string.import_deck))
                }
            DownloadPhase.Failed -> {
                FilledTonalButton(onClick = onTryAgainClick) {
                    Text(stringResource(R.string.try_again))
                }
                TextButton(onClick = onOpenInBrowserClick) {
                    Text(stringResource(R.string.open_in_browser))
                }
            }
        }
    }
}

private class DownloadStateProvider : PreviewParameterProvider<SharedDecksDownloadUiState> {
    private val downloading =
        SharedDecksDownloadUiState(
            fileName = "Japanese Core 2000.apkg",
            percent = 42.7f,
            downloadedBytes = 19_200_000,
            totalBytes = 44_900_000,
        )

    override val values =
        sequenceOf(
            downloading,
            downloading.copy(isWaitingForNetwork = true),
            downloading.copy(phase = DownloadPhase.Complete, percent = 100f, downloadedBytes = 44_900_000),
            downloading.copy(phase = DownloadPhase.Failed, percent = 0f),
        )
}

@ThemePreviews
@Composable
private fun SharedDecksDownloadScreenPreview(
    @PreviewParameter(DownloadStateProvider::class) state: SharedDecksDownloadUiState,
) {
    AnkiDroidTheme {
        SharedDecksDownloadScreen(
            state = state,
            onCancelClick = {},
            onImportClick = {},
            onTryAgainClick = {},
            onOpenInBrowserClick = {},
        )
    }
}
