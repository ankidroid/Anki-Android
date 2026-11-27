// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SnackbarDismissAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `dismiss button receives the accessibility timeout for controls`() {
        val host = SnackbarHostState()
        val manager =
            object : AccessibilityManager {
                override fun calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis: Long,
                    containsIcons: Boolean,
                    containsText: Boolean,
                    containsControls: Boolean,
                ): Long = if (containsControls) 10_000 else 0
            }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides manager) {
                MaterialTheme { SnackbarHost(host) }
            }
            LaunchedEffect(host) {
                host.showSnackbar("Message", withDismissAction = true, duration = SnackbarDuration.Short)
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertNotNull(host.currentSnackbarData)
    }
}
