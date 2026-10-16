// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import androidx.compose.ui.test.isDialog
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.noteeditor.presentation.ToolbarLayoutMode
import com.ichi2.anki.settings.enums.DayTheme
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Covers each layout and large text with every app theme.
 *
 * Record: `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests '*.ToolbarLayoutDialogThemeScreenshotTest'`
 * Verify: `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests '*.ToolbarLayoutDialogThemeScreenshotTest'`
 */
class ToolbarLayoutDialogThemeScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun scroll() = captureLayout(ToolbarLayoutMode.SCROLL)

    @Test
    fun stack() = captureLayout(ToolbarLayoutMode.STACK)

    @Test
    fun hidden() = captureLayout(ToolbarLayoutMode.HIDDEN)

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun hiddenLargeText() = captureLayout(ToolbarLayoutMode.HIDDEN, fontScale = 2f)

    private fun captureLayout(
        selected: ToolbarLayoutMode,
        fontScale: Float = 1f,
    ) {
        val font = if (fontScale == 1f) "" else "_large_text"
        capture("${selected.name.lowercase()}$font", node = isDialog(), fontScale = fontScale) {
            ToolbarLayoutDialog(
                selected = selected,
                onSelect = {},
                onDismiss = {},
                showSelectionBackground = theme != DayTheme.EINK,
            )
        }
    }
}
