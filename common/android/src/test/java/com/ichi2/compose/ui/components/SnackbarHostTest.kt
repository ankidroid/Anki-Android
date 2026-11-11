// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnackbarHostTest {
    @get:Rule
    val compose = createComposeRule()
    private val host = SnackbarHostState()

    @Test
    fun `short messages last 1500 milliseconds`() {
        show { showSnackbar("Short") }
        compose.mainClock.advanceTimeBy(1_300)
        assertEquals("Short", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `long messages last 2750 milliseconds`() {
        show { showSnackbar("Long", duration = SnackbarDuration.Long) }
        compose.mainClock.advanceTimeBy(2_550)
        assertEquals("Long", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `accessibility can extend the View duration beyond the Compose default`() {
        val manager = RecordingAccessibilityManager()
        show(manager) { showSnackbar("Extended", actionLabel = "Undo", duration = SnackbarDuration.Short) }
        assertEquals(listOf(1_500L), manager.requestedTimeouts)
        assertTrue(manager.containsControls)
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("Extended", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `accessibility extends long messages too`() {
        val manager = RecordingAccessibilityManager()
        show(manager) { showSnackbar("Extended long", duration = SnackbarDuration.Long) }
        assertEquals(listOf(2_750L), manager.requestedTimeouts)
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("Extended long", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `actions stay indefinitely and queued messages get their own timeout`() {
        val results = mutableListOf<SnackbarResult>()
        show(RecordingAccessibilityManager()) {
            results.add(showSnackbar("First", actionLabel = "Undo"))
            results.add(showSnackbar("Second"))
        }
        compose.mainClock.advanceTimeBy(60_000)
        assertEquals("First", host.currentSnackbarData?.visuals?.message)
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.advanceTimeBy(64)
        assertEquals(listOf(SnackbarResult.ActionPerformed), results)
        assertEquals("Second", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("Second", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(host.currentSnackbarData)
        assertEquals(listOf(SnackbarResult.ActionPerformed, SnackbarResult.Dismissed), results)
    }

    private fun show(
        accessibilityManager: AccessibilityManager? = null,
        messages: suspend SnackbarHostState.() -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides accessibilityManager) {
                MaterialTheme { SnackbarHost(host) }
            }
            LaunchedEffect(host) { host.messages() }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
    }

    private class RecordingAccessibilityManager : AccessibilityManager {
        val requestedTimeouts = mutableListOf<Long>()
        var containsControls = false

        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            requestedTimeouts.add(originalTimeoutMillis)
            assertTrue(containsIcons)
            assertTrue(containsText)
            this.containsControls = containsControls
            return 6_000L
        }
    }
}
