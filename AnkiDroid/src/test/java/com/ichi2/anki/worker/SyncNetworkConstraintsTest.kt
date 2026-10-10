// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import anki.sync.syncAuth
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.sync.SyncAuth
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncNetworkConstraintsTest : RobolectricTest() {
    private val auth = SyncAuth(syncAuth { hkey = "test" })

    @Test
    fun `collection work requires an unmetered network when metered syncing is disabled`() {
        Prefs.allowSyncOnMeteredConnections = false
        val request = captureCollectionRequest()

        assertThat(request.workSpec.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
    }

    @Test
    fun `background media also respects the collection metered restriction`() {
        Prefs.allowSyncOnMeteredConnections = false
        Prefs.putEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.ALWAYS)

        val request = SyncMediaWorker.getWorkRequest(auth)

        assertThat(request.workSpec.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
    }

    @Test
    fun `media unmetered preference applies even when collection may use metered networks`() {
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.putEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.ONLY_UNMETERED)

        val request = SyncMediaWorker.getWorkRequest(auth)

        assertThat(request.workSpec.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
    }

    @Test
    fun `metered networks remain usable when both preferences allow them`() {
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.putEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.ALWAYS)

        val collection = captureCollectionRequest()
        val media = SyncMediaWorker.getWorkRequest(auth)

        assertThat(collection.workSpec.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
        assertThat(media.workSpec.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
    }

    private fun captureCollectionRequest(): OneTimeWorkRequest {
        val manager = mockk<WorkManager>(relaxed = true)
        val request = slot<OneTimeWorkRequest>()
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any()) } returns manager
            every { manager.enqueueUniqueWork(any(), any(), capture(request)) } returns mockk(relaxed = true)
            SyncWorker.start(targetContext, auth, syncMedia = true)
            return request.captured
        } finally {
            unmockkObject(WorkManager.Companion)
        }
    }
}
