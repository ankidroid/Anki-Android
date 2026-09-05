// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import com.ichi2.anki.ScreenshotTest
import com.ichi2.testutils.launchFragment
import org.junit.Test

class ExportDialogFragmentScreenshotTest : ScreenshotTest() {
    @Test
    fun collapsed() =
        withExportDialog {
            captureScreen("collapsed")
        }

    @Test
    fun expanded() =
        withExportDialog {
            binding.exportTypeSelector.performClick()
            captureScreen("expanded")
        }

    private fun withExportDialog(action: ExportDialogFragment.() -> Unit) {
        ensureCollectionLoadIsSynchronous()
        launchFragment<ExportDialogFragment>().use { scenario ->
            scenario.onFragment { fragment ->
                advanceRobolectricLooper()
                action(fragment)
            }
        }
    }
}
