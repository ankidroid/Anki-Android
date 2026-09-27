// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.core.view.WindowInsetsCompat
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.withCardBrowser
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.windowInsetsOf
import com.ichi2.utils.dp
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class ChangeNoteTypeDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun portrait() =
        captureChangeNoteType(
            "portrait",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
        )

    @Test
    fun gestureNavigation() =
        captureChangeNoteType(
            "gesture_navigation",
            with(targetContext) { windowInsetsOf(navBarBottom = 24.dp) },
        )

    @Test
    fun portraitCutout() =
        captureChangeNoteType(
            "portrait_cutout",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp, cutoutTop = 40.dp) },
        )

    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        captureChangeNoteType(
            "landscape",
            with(targetContext) { windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp) },
        )
    }

    private fun captureChangeNoteType(
        name: String,
        insets: WindowInsetsCompat,
    ) = withCardBrowser(noteCount = 1) { browser ->
        val dialog = ChangeNoteTypeDialog.newInstance(col.findNotes(""))
        dialog.showNow(browser.supportFragmentManager, "change_note_type")
        advanceRobolectricLooper()
        try {
            dialog.requireDialog().window!!.simulateSystemBars(insets)
            captureScreen(name)
        } finally {
            dialog.dismissNow()
        }
    }
}
