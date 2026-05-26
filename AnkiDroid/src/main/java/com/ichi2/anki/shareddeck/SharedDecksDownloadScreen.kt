// SPDX-FileCopyrightText: 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.theme.Theme
import com.ichi2.compose.theme.dimensions
import com.ichi2.compose.ui.preview.ThemePreviews

/**
 * Maximum content width for the screen. The progress circle, stat cards and
 * info card all flow inside this — on a tablet or unfolded foldable the
 * content stays a comfortable reading width instead of stretching edge-to-edge.
 */
private val MaxContentWidth = 600.dp

/**
 * Diameter of the hero progress ring. Not a design token: this is a
 * single-purpose visual centerpiece, not shared spacing.
 */
private val ProgressRingSize = 240.dp

/** Stroke width of [ProgressRingSize]. */
private val ProgressRingStroke = 10.dp

/** Diameter of the "safe to leave" circular badge in the info card. */
private val InfoBadgeSize = 28.dp

/**
 * Compose UI for the shared-decks download screen (redesign of #20962).
 *
 * The cancel-confirmation dialog and the back-press intercept are managed
 * inside this composable instead of the ViewModel: the dialog visibility is
 * a UI-only concern and doesn't outlive the screen.
 */
@Composable
fun SharedDecksDownloadScreen(
    state: SharedDecksDownloadUiState,
    onAction: (SharedDecksDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.phase) {
        if (state.phase != DownloadPhase.Downloading) {
            showCancelDialog = false
        }
    }

    BackHandler(enabled = state.phase == DownloadPhase.Downloading) {
        showCancelDialog = true
    }

    // Every Context-dependent string is formatted once, at the screen root, so
    // the leaf composables stay pure functions of their parameters. The
    // helpers key on LocalConfiguration where the underlying formatter is
    // locale-aware, so a config change (locale, font scale) re-formats
    // automatically without going through stale Context.getString reads.
    val sizeRangeText =
        rememberDownloadSizeRange(
            downloadedBytes = state.progress.downloadedBytes,
            totalBytes = state.progress.totalBytes,
        )
    val speedText = rememberSpeedText(state.downloadStats.speedBytesPerSecond)
    val timeRemainingText = rememberTimeRemainingText(state.downloadStats.secondsRemaining)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .widthIn(max = MaxContentWidth)
                        .fillMaxSize(),
            ) {
                // The Column needs to be at least as tall as the viewport so
                // the `weight(1f)` spacers can push the progress ring to the
                // vertical centre — verticalScroll alone collapses to content
                // height.
                val minColumnHeight = maxHeight
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = minColumnHeight),
                ) {
                    HeaderSection(title = state.title)

                    Spacer(modifier = Modifier.weight(1f))

                    CircularProgressSection(
                        percent = state.progress.percent,
                        phase = state.phase,
                        sizeRangeText = sizeRangeText,
                        modifier = Modifier.padding(vertical = MaterialTheme.dimensions.screenEdge),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    SpeedTimeCards(speedText = speedText, timeRemainingText = timeRemainingText)
                    InfoCard()
                    if (state.showNetworkError) NetworkErrorText()
                    ActionButtons(
                        phase = state.phase,
                        onCancelClick = { showCancelDialog = true },
                        onAction = onAction,
                    )
                }
            }
        }
    }

    if (showCancelDialog) {
        CancelConfirmationDialog(
            onConfirm = {
                showCancelDialog = false
                onAction(SharedDecksDownloadAction.CancelConfirmed)
            },
            onDismiss = { showCancelDialog = false },
        )
    }
}

/**
 * User gestures the screen surfaces back to the fragment. Kept symmetric with
 * [SharedDecksDownloadEvent] so the screen has one outbound channel for VM
 * data updates and one for user actions, instead of a callback per button.
 */
sealed interface SharedDecksDownloadAction {
    /** Confirmed in the cancel-download dialog. */
    data object CancelConfirmed : SharedDecksDownloadAction

    data object ImportClicked : SharedDecksDownloadAction

    data object TryAgainClicked : SharedDecksDownloadAction

    data object OpenInBrowserClicked : SharedDecksDownloadAction
}

@Composable
private fun rememberSpeedText(speedBytesPerSecond: Long): String {
    val unknown = stringResource(R.string.download_speed_unknown)
    if (speedBytesPerSecond <= 0) return unknown
    // Formatter.formatFileSize reads Resources.getConfiguration() internally,
    // so we key the cached value on LocalConfiguration to invalidate when the
    // user changes locale or font scale. The localised template itself comes
    // from stringResource, which already handles config invalidation.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val speedFmt =
        remember(speedBytesPerSecond, configuration) {
            Formatter.formatFileSize(context, speedBytesPerSecond)
        }
    return stringResource(R.string.download_speed_unit, speedFmt)
}

@Composable
private fun rememberTimeRemainingText(secondsRemaining: Long?): String {
    val unknown = stringResource(R.string.download_time_unknown)
    return remember(secondsRemaining, unknown) {
        secondsRemaining?.let(DateUtils::formatElapsedTime) ?: unknown
    }
}

@Composable
private fun HeaderSection(
    title: String,
    modifier: Modifier = Modifier,
) {
    val edge = MaterialTheme.dimensions.screenEdge
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = edge, start = edge, end = edge),
    )
}

@Composable
private fun CircularProgressSection(
    percent: Float,
    phase: DownloadPhase,
    sizeRangeText: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.size(ProgressRingSize),
            strokeWidth = ProgressRingStroke,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.primary,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                DownloadPhase.Failed ->
                    Text(
                        text = stringResource(R.string.download_failed),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = MaterialTheme.dimensions.itemGap),
                    )

                else -> {
                    PercentageText(percent = percent)
                    if (sizeRangeText.isNotEmpty()) {
                        Text(
                            text = sizeRangeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MaterialTheme.dimensions.microGap),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the percentage with the `%` character drawn at 60 % of the base size,
 * matching the legacy XML which used a [android.text.style.RelativeSizeSpan].
 *
 * Uses `displayLarge` (57sp) — the design's 56sp fits within 1sp.
 */
@Composable
private fun PercentageText(
    percent: Float,
    modifier: Modifier = Modifier,
) {
    val displayText = remember(percent) { formatPercent(percent) }
    val baseStyle = MaterialTheme.typography.displayLarge
    val annotated =
        remember(displayText, baseStyle.fontSize) {
            buildAnnotatedString {
                append(displayText)
                val firstPercent = displayText.indexOf('%')
                if (firstPercent >= 0) {
                    addStyle(
                        SpanStyle(fontSize = baseStyle.fontSize * 0.6f),
                        firstPercent,
                        firstPercent + 1,
                    )
                }
            }
        }
    Text(
        text = annotated,
        style = baseStyle,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

private fun formatPercent(percent: Float): String {
    val percentInt = percent.toInt()
    return if (percentInt == 0 || percentInt == 100) "$percentInt %" else "%.1f %%".format(percent)
}

@Composable
private fun SpeedTimeCards(
    speedText: String,
    timeRemainingText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.dimensions.screenEdge)
                .padding(bottom = MaterialTheme.dimensions.itemGap),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.dimensions.itemGap),
    ) {
        StatCard(
            label = stringResource(R.string.download_speed_label),
            value = speedText,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.download_time_left_label),
            value = timeRemainingText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    TonalCard(modifier = modifier) {
        Column(modifier = Modifier.padding(MaterialTheme.dimensions.sectionGap)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = MaterialTheme.dimensions.microGap),
            )
        }
    }
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier) {
    TonalCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.dimensions.screenEdge)
                .padding(bottom = MaterialTheme.dimensions.screenEdge),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.dimensions.sectionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(InfoBadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(MaterialTheme.dimensions.microGap),
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
                modifier = Modifier.padding(start = MaterialTheme.dimensions.sectionGap),
            )
        }
    }
}

/**
 * Surface-variant tonal card with no elevation. Shared between [StatCard] and
 * [InfoCard]; promote to `com.ichi2.compose.ui` if a second screen needs the
 * same pattern.
 */
@Composable
private fun TonalCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
private fun NetworkErrorText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.check_network),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.dimensions.sectionGap)
                .padding(vertical = MaterialTheme.dimensions.tightGap)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ColumnScope.ActionButtons(
    phase: DownloadPhase,
    onCancelClick: () -> Unit,
    onAction: (SharedDecksDownloadAction) -> Unit,
) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.dimensions.screenEdge)
                .padding(bottom = MaterialTheme.dimensions.screenEdge),
        horizontalArrangement =
            Arrangement.spacedBy(MaterialTheme.dimensions.sectionGap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimensions.tightGap),
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

            DownloadPhase.Failed -> {
                FilledTonalButton(onClick = { onAction(SharedDecksDownloadAction.TryAgainClicked) }) {
                    Text(stringResource(R.string.try_again))
                }
                TextButton(onClick = { onAction(SharedDecksDownloadAction.OpenInBrowserClicked) }) {
                    Text(stringResource(R.string.open_in_browser))
                }
            }

            DownloadPhase.Complete ->
                Button(onClick = { onAction(SharedDecksDownloadAction.ImportClicked) }) {
                    Text(stringResource(R.string.import_deck))
                }
        }
    }
}

@Composable
private fun CancelConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cancel_download_question_title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_no))
            }
        },
    )
}

// Previews //

@Suppress("unused")
private class SharedDecksDownloadPreviewProvider : PreviewParameterProvider<SharedDecksDownloadUiState> {
    override val values =
        sequenceOf(
            SharedDecksDownloadUiState(
                title = SAMPLE_TITLE,
                progress = ProgressInfo(percent = 0f, downloadedBytes = 0, totalBytes = 44_900_000),
            ),
            SharedDecksDownloadUiState(
                title = SAMPLE_TITLE,
                progress = ProgressInfo(percent = 42.7f, downloadedBytes = 19_200_000, totalBytes = 44_900_000),
                downloadStats = DownloadStats(speedBytesPerSecond = 1_200_000, secondsRemaining = 21),
            ),
            SharedDecksDownloadUiState(
                title = SAMPLE_TITLE,
                progress = ProgressInfo(percent = 42.7f, downloadedBytes = 19_200_000, totalBytes = 44_900_000),
                showNetworkError = true,
            ),
            SharedDecksDownloadUiState(
                title = SAMPLE_TITLE,
                progress = ProgressInfo(percent = 100f, downloadedBytes = 44_900_000, totalBytes = 44_900_000),
                phase = DownloadPhase.Complete,
            ),
            SharedDecksDownloadUiState(title = SAMPLE_TITLE, phase = DownloadPhase.Failed),
        )

    companion object {
        private const val SAMPLE_TITLE = "Core 2000: Japanese Vocabulary"
    }
}

/** Cycles every download phase / progress snapshot in light + dark uiMode. */
@ThemePreviews
@Composable
private fun SharedDecksDownloadScreenPreview(
    @PreviewParameter(SharedDecksDownloadPreviewProvider::class) state: SharedDecksDownloadUiState,
) {
    Theme {
        SharedDecksDownloadScreen(state = state, onAction = {})
    }
}
