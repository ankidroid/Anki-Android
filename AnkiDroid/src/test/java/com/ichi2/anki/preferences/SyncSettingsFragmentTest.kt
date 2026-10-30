// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.text.format.DateUtils
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ichi2.anki.CommonPreferenceKey
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.worker.UniqueWorkNames
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSettingsFragmentTest : RobolectricTest() {
    @Test
    fun `media sync row is disabled while logged out`() {
        withSyncSettings {
            assertThat(mediaSyncRow.isEnabled, equalTo(false))
            assertThat(mediaSyncRow.summary, equalTo(getString(CommonString.sync_account_summ_logged_out)))
        }
    }

    @Test
    fun `media sync row shows when the last media sync failed`() {
        Prefs.hkey = "test"
        Prefs.recordMediaSync(TIME, failed = true)
        withSyncSettings {
            val formattedTime =
                DateUtils.formatDateTime(
                    requireContext(),
                    TIME,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                )
            assertThat(mediaSyncRow.isEnabled, equalTo(true))
            assertThat(mediaSyncRow.summary, equalTo(getString(CommonString.media_sync_status_failed, formattedTime)))
        }
    }

    @Test
    fun `tapping the media sync row starts a media sync`() {
        Prefs.hkey = "test"
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.recordMediaSync(TIME, failed = true)
        withSyncSettings {
            mediaSyncRow.performClick()
            advanceRobolectricLooper()

            val work = WorkManager.getInstance(targetContext).getWorkInfosForUniqueWork(UniqueWorkNames.SYNC_MEDIA).get()
            assertThat(work.map { it.state }, equalTo(listOf(WorkInfo.State.ENQUEUED)))
        }
    }

    private val SyncSettingsFragment.mediaSyncRow: Preference
        get() = requirePreference(CommonPreferenceKey.media_sync_status_key)

    private fun withSyncSettings(block: SyncSettingsFragment.() -> Unit) {
        val intent = PreferencesActivity.getIntent(targetContext, SyncSettingsFragment::class)
        ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val preferencesFragment = activity.fragment as PreferencesFragment
                val settingsFragment =
                    preferencesFragment.childFragmentManager.findFragmentById(R.id.settings_container) as SyncSettingsFragment
                advanceRobolectricLooper()
                settingsFragment.block()
            }
        }
    }

    companion object {
        private const val TIME = 1_700_000_000_000L
    }
}
