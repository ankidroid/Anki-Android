// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.preferences.components.PreferenceHelpSection
import com.ichi2.anki.preferences.components.PreferenceStepperCard
import com.ichi2.anki.preferences.components.PreferenceSwitchCard
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.preview.ThemePreviews
import androidx.appcompat.R as AppCompatR

/**
 * Screen allowing users to configure the learn ahead limit.
 *
 * Uses modern Material 3 cards matching Android Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnAheadSettingsScreen(
    isLoading: Boolean,
    isEnabled: Boolean,
    limit: LearnAheadLimit,
    onToggleEnabled: (Boolean) -> Unit,
    onSetMinutes: (Int) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val verticalSpacing = if (isLandscape) 8.dp else 16.dp
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    Scaffold(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                    )
                },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonString.learn_cutoff)) },
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
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 8.dp)
                        .padding(bottom = 32.dp),
            ) {
                PreferenceSwitchCard(
                    title = stringResource(CommonString.use_learn_ahead),
                    isChecked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                )

                Spacer(modifier = Modifier.height(verticalSpacing))

                AnimatedVisibility(
                    visible = isEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        PreferenceStepperCard(
                            title = stringResource(CommonString.pref_learn_ahead_stepper_title),
                            value = limit.minutes,
                            onValueChange = onSetMinutes,
                            validRange = LearnAheadLimit.INPUT_RANGE,
                            unitSuffix = { count -> pluralStringResource(CommonPlurals.timebox_minutes_suffix, count) },
                        )
                        Spacer(modifier = Modifier.height(verticalSpacing))
                    }
                }

                PreferenceHelpSection(
                    text = stringResource(CommonString.pref_learn_ahead_help_summary),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun LearnAheadSettingsScreenEnabledPreview() {
    AnkiDroidTheme {
        LearnAheadSettingsScreen(
            isLoading = false,
            isEnabled = true,
            limit = LearnAheadLimit.DEFAULT,
            onToggleEnabled = {},
            onSetMinutes = {},
            onNavigateUp = {},
        )
    }
}

@ThemePreviews
@Composable
private fun LearnAheadSettingsScreenDisabledPreview() {
    AnkiDroidTheme {
        LearnAheadSettingsScreen(
            isLoading = false,
            isEnabled = false,
            limit = LearnAheadLimit.DEFAULT,
            onToggleEnabled = {},
            onSetMinutes = {},
            onNavigateUp = {},
        )
    }
}
