// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.LinearProgressIndicator as MaterialLinearProgressIndicator

/**
 * Shows indeterminate progress after [delayDuration], avoiding a flash for quick operations.
 * Disappears immediately when [isLoading] becomes false. No space is reserved while hidden.
 */
@Composable
fun LinearProgressIndicator(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    delayDuration: Duration = 600.milliseconds,
) {
    var visible by remember(isLoading, delayDuration) { mutableStateOf(false) }
    LaunchedEffect(isLoading, delayDuration) {
        if (isLoading) {
            delay(delayDuration)
            visible = true
        }
    }
    if (visible) MaterialLinearProgressIndicator(modifier)
}
