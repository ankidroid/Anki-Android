// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import anki.sync.SyncAuth
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import net.ankiweb.rsdroid.Backend
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerAutomaticSyncTest : RobolectricTest() {
    @Test
    fun `back exits without syncing if the collection closes before the status request`() =
        withAutomaticSync(syncMedia = true) { activity, backend ->
            every { backend.syncStatus(any<SyncAuth>()) } answers {
                // Close after any caller-side check, then let the real backend report CollectionNotOpen.
                CollectionManager.closeCollectionBlocking()
                callOriginal()
            }

            activity.onBackPressedDispatcher.onBackPressed()
            advanceRobolectricLooperUntil { activity.isFinishing }

            assertFalse(CollectionManager.isOpenUnsafe())
            assertEquals(0L, Prefs.lastSyncTime)
            assertNoSyncWorkEnqueued(activity)
        }

    private fun assertNoSyncWorkEnqueued(activity: DeckPicker) {
        val workManager = WorkManager.getInstance(activity)
        for (name in listOf(UniqueWorkNames.SYNC, UniqueWorkNames.SYNC_MEDIA)) {
            assertTrue(workManager.getWorkInfosForUniqueWork(name).get().isEmpty(), "$name must not be enqueued")
        }
    }

    private fun withAutomaticSync(
        syncMedia: Boolean = false,
        block: (DeckPicker, Backend) -> Unit,
    ) = withDeckPicker(deckCount = 0) { activity ->
        getPreferences().edit {
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), true)
            val mediaPreference = if (syncMedia) R.string.sync_media_always_value else R.string.sync_media_never_value
            putString(getResourceString(R.string.sync_fetch_media_key), getResourceString(mediaPreference))
        }
        assertEquals(syncMedia, shouldFetchMedia())
        Prefs.hkey = "test"
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.lastSyncTime = 0
        val backend = CollectionManager.getBackend()
        mockkObject(NetworkUtils, backend)
        try {
            every { NetworkUtils.isOnline } returns true
            block(activity, backend)
        } finally {
            unmockkObject(NetworkUtils, backend)
        }
    }
}
