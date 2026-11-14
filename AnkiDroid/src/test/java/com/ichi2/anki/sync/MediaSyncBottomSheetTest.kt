// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import android.text.format.DateUtils
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.testutils.launchFragment
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class MediaSyncBottomSheetTest : RobolectricTest() {
    @Test
    fun `logged out offers a log in instead of a sync`() {
        withMediaSyncSheet {
            assertThat(binding.title.text.toString(), equalTo(getString(CommonString.sync_account_summ_logged_out)))
            assertThat(binding.summary.text.toString(), equalTo(getString(CommonString.media_sync_not_logged_in_summary)))
            assertThat(binding.logIn.isVisible, equalTo(true))
            assertThat(binding.syncNow.isVisible, equalTo(false))
            assertThat(binding.abort.isVisible, equalTo(false))
        }
    }

    @Test
    fun `log in opens the account screen`() {
        withMediaSyncSheet {
            binding.logIn.performClick()

            val started = shadowOf(requireActivity()).nextStartedActivity
            assertThat(started.component?.className, equalTo(AccountActivity::class.java.name))
        }
    }

    @Test
    fun `an incomplete media sync offers a sync`() {
        Prefs.hkey = "test"
        Prefs.recordMediaSync(TIME, failed = true)
        withMediaSyncSheet {
            val formattedTime =
                DateUtils.formatDateTime(
                    requireContext(),
                    TIME,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                )
            assertThat(binding.title.text.toString(), equalTo(getString(CommonString.media_sync_incomplete_title)))
            assertThat(binding.summary.text.toString(), equalTo(getString(CommonString.media_sync_incomplete_summary, formattedTime)))
            assertThat(binding.syncNow.isVisible, equalTo(true))
            assertThat(binding.logIn.isVisible, equalTo(false))
            assertThat(binding.abort.isVisible, equalTo(false))
        }
    }

    @Test
    fun `sync now starts a media sync`() {
        Prefs.hkey = "test"
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.recordMediaSync(TIME, failed = true)
        withMediaSyncSheet {
            binding.syncNow.performClick()
            advanceRobolectricLooperUntil { binding.abort.isVisible }

            assertThat(mediaSyncStates(), equalTo(listOf(WorkInfo.State.ENQUEUED)))
            assertThat(binding.syncNow.isVisible, equalTo(false))
            assertThat(binding.abort.isVisible, equalTo(true))
        }
    }

    @Test
    fun `abort cancels a pending media sync`() {
        Prefs.hkey = "test"
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.recordMediaSync(TIME, failed = true)
        withMediaSyncSheet {
            binding.syncNow.performClick()
            advanceRobolectricLooperUntil { binding.abort.isVisible }

            binding.abort.performClick()
            advanceRobolectricLooperUntil { binding.syncNow.isVisible }

            assertThat(mediaSyncStates(), equalTo(listOf(WorkInfo.State.CANCELLED)))
            assertThat(binding.syncNow.isVisible, equalTo(true))
            assertThat(binding.abort.isVisible, equalTo(false))
        }
    }

    private fun mediaSyncStates() =
        WorkManager
            .getInstance(targetContext)
            .getWorkInfosForUniqueWork(UniqueWorkNames.SYNC_MEDIA)
            .get()
            .map { it.state }

    private fun withMediaSyncSheet(block: MediaSyncBottomSheet.() -> Unit) {
        launchFragment { MediaSyncBottomSheet() }.use { scenario ->
            scenario.onFragment { sheet ->
                advanceRobolectricLooperUntil { sheet.binding.summary.length() > 0 }
                sheet.block()
            }
        }
    }

    companion object {
        private const val TIME = 1_700_000_000_000L
    }
}
