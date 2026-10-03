// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.sameInstance
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class IntentSyncTest : RobolectricTest() {
    @Before
    fun setUpSync() {
        mockkObject(NetworkUtils, MeteredSyncPolicy)
        every { NetworkUtils.isOnline } returns true
        every { MeteredSyncPolicy.shouldBlock() } returns false
    }

    @After
    fun resetMocks() {
        unmockkAll()
    }

    @Test
    fun `sync intent respects metered connection policy`() =
        deckPicker {
            Prefs.hkey = "test"
            val previousSyncTime = 1_000L
            Prefs.lastSyncTime = previousSyncTime
            every { MeteredSyncPolicy.shouldBlock() } returns true
            val previousDialog = ShadowDialog.getLatestDialog()

            IntentHandler.Companion.DoSync().handleAsyncMessage(this)

            assertThat("sync must not open a confirmation dialog", ShadowDialog.getLatestDialog(), sameInstance(previousDialog))
            assertThat(isFinishing, equalTo(true))
            assertThat("blocked sync must not count as a sync attempt", Prefs.lastSyncTime, equalTo(previousSyncTime))
        }
}
