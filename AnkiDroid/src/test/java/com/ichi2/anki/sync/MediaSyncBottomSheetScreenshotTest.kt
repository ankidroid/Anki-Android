// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.databinding.ViewSyncProgressLayoutBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.ext.menu
import org.junit.Test
import java.util.TimeZone
import kotlin.test.assertNotNull

class MediaSyncBottomSheetScreenshotTest : ScreenshotTest() {
    @Test
    fun `media sync incomplete`() {
        val defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            Prefs.hkey = "test"
            Prefs.recordMediaSync(time = 1_700_000_000_000L, failed = true)
            withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
                val syncButton = ViewSyncProgressLayoutBinding.bind(assertNotNull(deckPicker.menu().findItem(R.id.action_sync).actionView))
                syncButton.button.performLongClick()
                advanceRobolectricLooperUntil {
                    val sheet = deckPicker.supportFragmentManager.findFragmentByTag(MediaSyncBottomSheet.TAG) as? MediaSyncBottomSheet
                    sheet?.view != null && sheet.binding.summary.length() > 0
                }
                captureScreen("media_sync_incomplete")
            }
        } finally {
            TimeZone.setDefault(defaultTimeZone)
        }
    }
}
