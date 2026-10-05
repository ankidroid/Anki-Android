// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.sync.SyncCollectionResponse
import anki.sync.syncCollectionResponse
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class ForegroundMediaNetworkTest : RobolectricTest() {
    private var metered = false
    private var syncRequired = SyncCollectionResponse.ChangesRequired.NO_CHANGES

    @Before
    fun prepareSync() {
        Prefs.hkey = "test"
        Prefs.lastSyncTime = 0
        Prefs.allowSyncOnMeteredConnections = false
        Prefs.putEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.ALWAYS)
        mockkObject(NetworkUtils)
        every { NetworkUtils.isActiveNetworkMetered() } answers { metered }
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun syncCollection(
                    auth: anki.sync.SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse {
                    // The network changes between the collection sync and the media handoff.
                    metered = true
                    return syncCollectionResponse { required = syncRequired }
                }

                override fun fullUploadOrDownload(input: anki.sync.FullUploadOrDownloadRequest) = Unit
            }
        }
    }

    @After
    fun resetSync() {
        BackendFactory.setOverride(null)
        unmockkObject(NetworkUtils)
    }

    @Test
    fun `starting on wifi does not authorize queued media on a metered network`() =
        deckPicker {
            val request = syncAndGetMediaWork()

            assertThat(metered, equalTo(true))
            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
        }

    @Test
    fun `one-time metered confirmation permits the queued media sync`() =
        deckPicker {
            metered = true

            val request = syncAndGetMediaWork(confirmWarning = true)

            assertThat("one-time confirmation must not change the preference", Prefs.allowSyncOnMeteredConnections, equalTo(false))
            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
        }

    @Test
    fun `one-time metered confirmation releases media already waiting for wifi`() =
        deckPicker {
            val waiting = syncAndGetMediaWork()
            assertThat(waiting.state, equalTo(WorkInfo.State.ENQUEUED))
            assertThat(waiting.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
            Prefs.lastSyncTime = 0

            val approved = syncAndGetMediaWork(confirmWarning = true)

            assertThat(Prefs.allowSyncOnMeteredConnections, equalTo(false))
            assertThat(approved.id, equalTo(waiting.id))
            assertThat(approved.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
        }

    @Test
    fun `media-only restriction survives a network change during collection sync`() =
        deckPicker {
            Prefs.allowSyncOnMeteredConnections = true
            Prefs.putEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.ONLY_UNMETERED)

            val request = syncAndGetMediaWork()

            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
        }

    @Test
    fun `conflict resolution preserves the original unmetered restriction`() =
        deckPicker {
            syncRequired = SyncCollectionResponse.ChangesRequired.FULL_SYNC

            val request = syncAndGetMediaWork(resolveConflict = true)

            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
        }

    @Test
    fun `conflict resolution preserves one-time metered confirmation`() =
        deckPicker {
            metered = true
            syncRequired = SyncCollectionResponse.ChangesRequired.FULL_SYNC

            val request = syncAndGetMediaWork(confirmWarning = true, resolveConflict = true)

            assertThat(Prefs.allowSyncOnMeteredConnections, equalTo(false))
            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
        }

    private fun DeckPicker.syncAndGetMediaWork(
        confirmWarning: Boolean = false,
        resolveConflict: Boolean = false,
    ): WorkInfo {
        val manager = WorkManager.getInstance(this)
        sync()
        if (confirmWarning) {
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }
        if (resolveConflict) {
            Prefs.lastSyncTime = 0
            // Choose "Keep AnkiDroid" and confirm replacing the server collection.
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }
        }
        return manager.getWorkInfosForUniqueWork(UniqueWorkNames.SYNC_MEDIA).get().single { !it.state.isFinished }
    }
}
