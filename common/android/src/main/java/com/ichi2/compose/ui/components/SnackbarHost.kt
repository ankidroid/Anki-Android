// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.material3.SnackbarHost as MaterialSnackbarHost

/** A Material snackbar host with the same durations as View snackbars, including accessibility extensions. */
@Composable
fun SnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { Snackbar(it) },
) {
    val accessibilityManager = LocalAccessibilityManager.current
    val duration = hostState.currentSnackbarData?.visuals?.duration
    val timeouts = remember(accessibilityManager, duration) { SnackbarAccessibilityManager(accessibilityManager, duration) }
    // Material's host owns the timeout and animations. Adjust its base duration before asking
    // accessibility for extra time, so there is only one timer for the currently displayed message.
    CompositionLocalProvider(LocalAccessibilityManager provides timeouts) {
        MaterialSnackbarHost(hostState, modifier) { data ->
            // The timeout override applies only to the host, not to custom snackbar content.
            CompositionLocalProvider(LocalAccessibilityManager provides accessibilityManager) {
                snackbar(data)
            }
        }
    }
}

private class SnackbarAccessibilityManager(
    private val delegate: AccessibilityManager?,
    private val duration: SnackbarDuration?,
) : AccessibilityManager {
    override fun calculateRecommendedTimeoutMillis(
        originalTimeoutMillis: Long,
        containsIcons: Boolean,
        containsText: Boolean,
        containsControls: Boolean,
    ): Long {
        // Match Material Views' SnackbarManager, rather than Compose's 4s and 10s defaults.
        val timeout =
            when (duration) {
                SnackbarDuration.Short -> 1_500L
                SnackbarDuration.Long -> 2_750L
                SnackbarDuration.Indefinite -> return Long.MAX_VALUE
                null -> originalTimeoutMillis
            }
        return delegate?.calculateRecommendedTimeoutMillis(timeout, containsIcons, containsText, containsControls) ?: timeout
    }
}
