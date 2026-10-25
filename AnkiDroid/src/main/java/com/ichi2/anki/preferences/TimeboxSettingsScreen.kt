// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.ThemePreviews
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.android.R as CommonR

/**
 * Screen allowing users to enable/disable and configure the review timebox time limit.
 *
 * Uses the modern Material 3 main switch card styling from Android Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeboxSettingsScreen(
    isLoading: Boolean,
    isEnabled: Boolean,
    minutes: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetMinutes: (Int) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val verticalSpacing = if (isLandscape) 8.dp else 16.dp

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonString.timeboxing)) },
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
        if (isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .consumeWindowInsets(contentPadding)
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 8.dp)
                        .padding(bottom = 32.dp),
            ) {
                TimeboxMainSwitch(
                    isEnabled = isEnabled,
                    onToggleEnabled = onToggleEnabled,
                )

                Spacer(modifier = Modifier.height(verticalSpacing))

                AnimatedVisibility(
                    visible = isEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        TimeboxDurationCard(
                            minutes = minutes,
                            onSetMinutes = onSetMinutes,
                        )
                        Spacer(modifier = Modifier.height(verticalSpacing))
                    }
                }

                TimeboxHelpSection()
            }
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = timeboxContentColor()

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .clickable { onToggleEnabled(!isEnabled) },
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
    ) {
        val switchLabel = stringResource(CommonString.use_timebox)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = if (isLandscape) 12.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = switchLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                modifier =
                    Modifier.semantics {
                        contentDescription = switchLabel
                    },
            )
        }
    }
}

/**
 * Stepper card allowing users to increment/decrement or directly edit the timebox duration inline.
 */
@Composable
private fun TimeboxDurationCard(
    minutes: Int,
    onSetMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var textValue by rememberSaveable { mutableStateOf(minutes.toString()) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(minutes) {
        if (textValue.toIntOrNull() != minutes) {
            textValue = minutes.toString()
        }
    }

    val validRange = TimeboxSettingsViewModel.MIN_TIMEBOX_MINUTES..TimeboxSettingsViewModel.MAX_TIMEBOX_MINUTES
    val count = textValue.toIntOrNull() ?: minutes
    val safeCount = if (count in validRange) count else minutes
    val unitSuffix = pluralStringResource(CommonPlurals.timebox_minutes_suffix, safeCount)

    val parsed = textValue.toIntOrNull()
    val isValid = parsed != null && parsed in validRange
    val isError = textValue.isNotEmpty() && !isValid
    val controlBorder = MaterialTheme.widgetBorder
    val inputBorder =
        controlBorder?.let {
            if (isError) BorderStroke(it.width, MaterialTheme.colorScheme.error) else it
        }
    val inputContainerColor = if (controlBorder != null) MaterialTheme.colorScheme.surface else Color.Transparent
    val buttonColors =
        if (controlBorder != null) {
            IconButtonDefaults.outlinedIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = if (isLandscape) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(CommonString.time_limit_label),
                style = MaterialTheme.typography.titleSmall,
                color = timeboxContentColor(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedIconButton(
                    onClick = {
                        val currentVal = textValue.toIntOrNull() ?: minutes
                        val prevVal = (currentVal - 1).coerceIn(validRange)
                        textValue = prevVal.toString()
                        onSetMinutes(prevVal)
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = buttonColors,
                    border = controlBorder,
                    enabled = (textValue.toIntOrNull() ?: minutes) > TimeboxSettingsViewModel.MIN_TIMEBOX_MINUTES,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(CommonString.minus_sign),
                    )
                }

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        val noLeadingZeros =
                            if (digits.length > 1 && digits.startsWith("0")) {
                                digits.trimStart('0').ifEmpty { "0" }
                            } else {
                                digits
                            }
                        val filtered = noLeadingZeros.take(3)
                        textValue = filtered
                        val p = filtered.toIntOrNull()
                        if (p != null && p in validRange) {
                            onSetMinutes(p)
                        }
                    },
                    modifier =
                        Modifier
                            .width(136.dp)
                            .then(
                                if (inputBorder != null) {
                                    Modifier.border(inputBorder, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            ).onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    val p = textValue.toIntOrNull()
                                    if (p != null && p in validRange) {
                                        onSetMinutes(p)
                                    } else {
                                        textValue = minutes.toString()
                                    }
                                }
                            },
                    suffix = {
                        Text(
                            text = unitSuffix,
                            style = MaterialTheme.typography.bodyMedium,
                            color = timeboxContentColor(),
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputContainerColor,
                            unfocusedContainerColor = inputContainerColor,
                            errorContainerColor = inputContainerColor,
                        ),
                    textStyle =
                        LocalTextStyle.current.copy(
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                    singleLine = true,
                    isError = isError,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                val p = textValue.toIntOrNull()
                                if (p != null && p in validRange) {
                                    onSetMinutes(p)
                                } else {
                                    textValue = minutes.toString()
                                }
                                focusManager.clearFocus()
                            },
                        ),
                )

                OutlinedIconButton(
                    onClick = {
                        val currentVal = textValue.toIntOrNull() ?: minutes
                        val nextVal = (currentVal + 1).coerceIn(validRange)
                        textValue = nextVal.toString()
                        onSetMinutes(nextVal)
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = buttonColors,
                    border = controlBorder,
                    enabled = (textValue.toIntOrNull() ?: minutes) < TimeboxSettingsViewModel.MAX_TIMEBOX_MINUTES,
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
            tint = timeboxContentColor(),
            modifier =
                Modifier
                    .padding(top = 2.dp, end = 12.dp)
                    .size(20.dp),
        )
        Text(
            text = stringResource(CommonString.pref_timebox_help_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = timeboxContentColor(),
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun timeboxContentColor(): Color =
    if (MaterialTheme.widgetBorder != null) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

@ThemePreviews
@Composable
private fun TimeboxSettingsScreenEnabledPreview() {
    AnkiDroidTheme {
        TimeboxSettingsScreen(
            isLoading = false,
            isEnabled = true,
            minutes = 20,
            onToggleEnabled = {},
            onIncrement = {},
            onDecrement = {},
            onSetMinutes = {},
            onNavigateUp = {},
        )
    }
}

@ThemePreviews
@Composable
private fun TimeboxSettingsScreenDisabledPreview() {
    AnkiDroidTheme {
        TimeboxSettingsScreen(
            isLoading = false,
            isEnabled = false,
            minutes = 20,
            onToggleEnabled = {},
            onIncrement = {},
            onDecrement = {},
            onSetMinutes = {},
            onNavigateUp = {},
        )
    }
}
