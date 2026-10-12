// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.preview.ThemePreviews
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.android.R as CommonR

/**
 * Screen allowing users to enable/disable and configure the review timebox time limit.
 *
 * Uses the modern Material 3 main switch card styling from Android Settings.
 *
 * TODO: Follow-up issue: refine duplicated labels (Title: "Timeboxing", Switch: "Use timebox", Value: "Time limit").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeboxSettingsScreen(
    isEnabled: Boolean,
    minutes: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonString.time_limit)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TimeboxMainSwitch(
                isEnabled = isEnabled,
                onToggleEnabled = onToggleEnabled,
            )

            AnimatedVisibility(
                visible = isEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                TimeboxDurationCard(
                    minutes = minutes,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                )
            }

            TimeboxHelpSection()
        }
    }
}

/**
 * Modern Material 3 main switch card matching Google Android Settings.
 */
@Composable
private fun TimeboxMainSwitch(
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (isEnabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .clickable { onToggleEnabled(!isEnabled) },
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(CommonString.use_timebox_time_limit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
            )
        }
    }
}

/**
 * Stepper card allowing users to increment/decrement the timebox duration.
 */
@Composable
private fun TimeboxDurationCard(
    minutes: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(CommonString.time_limit),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalIconButton(
                    onClick = onDecrement,
                    enabled = minutes > TimeboxSettingsViewModel.MIN_TIMEBOX_MINUTES,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(CommonString.minus_sign),
                    )
                }

                Text(
                    text = pluralStringResource(CommonPlurals.pref_summ_minutes, minutes, minutes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                FilledTonalIconButton(
                    onClick = onIncrement,
                    enabled = minutes < TimeboxSettingsViewModel.MAX_TIMEBOX_MINUTES,
                ) {
                    Icon(
                        painter = painterResource(CommonR.drawable.ic_add),
                        contentDescription = stringResource(CommonString.plus_sign),
                    )
                }
            }
        }
    }
}

/**
 * Explanatory help section citing the official Anki manual.
 */
@Composable
private fun TimeboxHelpSection(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_outline_info_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(top = 2.dp, end = 12.dp)
                    .size(20.dp),
        )
        Text(
            text = stringResource(CommonString.pref_timebox_help_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}

@ThemePreviews
@Composable
private fun TimeboxSettingsScreenEnabledPreview() {
    AnkiDroidTheme {
        TimeboxSettingsScreen(
            isEnabled = true,
            minutes = 20,
            onToggleEnabled = {},
            onIncrement = {},
            onDecrement = {},
            onNavigateUp = {},
        )
    }
}

@ThemePreviews
@Composable
private fun TimeboxSettingsScreenDisabledPreview() {
    AnkiDroidTheme {
        TimeboxSettingsScreen(
            isEnabled = false,
            minutes = 20,
            onToggleEnabled = {},
            onIncrement = {},
            onDecrement = {},
            onNavigateUp = {},
        )
    }
}
