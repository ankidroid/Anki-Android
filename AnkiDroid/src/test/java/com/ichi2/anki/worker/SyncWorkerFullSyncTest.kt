// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import anki.sync.FullUploadOrDownloadRequest
import anki.sync.SyncCollectionResponse.ChangesRequired
import anki.sync.syncAuth
import anki.sync.syncCollectionResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.sync.SyncAuth
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import net.ankiweb.rsdroid.Backend
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SyncWorkerFullSyncTest : RobolectricTest() {
    private val auth =
        syncAuth {
            hkey = "test"
            ioTimeoutSecs = 123
        }
    private val endpoint = "https://sync.example.com/"
    private val redirectedAuth get() = auth.toBuilder().setEndpoint(endpoint).build()
    private var required = ChangesRequired.FULL_UPLOAD
    private var transferError: Exception? = null
    private val transfers = mutableListOf<FullUploadOrDownloadRequest>()
    private val mediaAuth = mutableListOf<anki.sync.SyncAuth>()
    private lateinit var collection: Collection
    private lateinit var backend: Backend

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setUpBackend() {
        collection = CollectionManager.getColUnsafe()
        backend = collection.backend
        mockkObject(backend, SyncMediaWorker.Companion)
        every { backend.syncCollection(any<anki.sync.SyncAuth>(), any()) } answers {
            assertEquals(auth, firstArg())
            assertFalse(secondArg())
            syncCollectionResponse {
                required = this@SyncWorkerFullSyncTest.required
                newEndpoint = endpoint
            }
        }
        every { backend.fullUploadOrDownload(any()) } answers {
            assertTrue(collection.dbClosed)
            transfers.add(firstArg())
            transferError?.let { throw it }
        }
        coEvery { SyncMediaWorker.start(any(), any(), any()) } coAnswers {
            assertCollectionOpen()
            assertEquals(1, transfers.size, "media must start after the collection transfer")
            mediaAuth.add(secondArg<SyncAuth>().toProto())
        }
    }

    @After
    fun tearDownBackend() {
        unmockkObject(backend, SyncMediaWorker.Companion)
    }

    @Test
    fun `automatic full upload respects media preference`() =
        runTest {
            for (media in listOf(true, false)) {
                assertEquals(Result.success(), runSync(ChangesRequired.FULL_UPLOAD, media))
                val request = transfers.single()
                assertTrue(request.upload)
                assertEquals(redirectedAuth, request.auth)
                assertFalse(request.hasServerUsn(), "media must start through WorkManager")
                assertEquals(if (media) listOf(redirectedAuth) else emptyList(), mediaAuth)
                assertCollectionOpen()
            }
        }

    @Test
    fun `failed full upload reopens the collection without starting media`() =
        runTest {
            transferError = IllegalStateException("transfer failed")
            assertEquals(Result.failure(), runSync(ChangesRequired.FULL_UPLOAD))
            assertEquals(1, transfers.size)
            assertTrue(mediaAuth.isEmpty())
            assertCollectionOpen()
        }

    @Test
    fun `ambiguous full sync waits for a user direction`() =
        runTest {
            runSync(ChangesRequired.FULL_SYNC)
            assertTrue(transfers.isEmpty())
            assertTrue(mediaAuth.isEmpty())
            assertCollectionOpen()
        }

    private fun assertCollectionOpen() {
        assertFalse(collection.dbClosed)
        assertTrue(collection.decks.count() > 0)
    }

    private suspend fun runSync(
        required: ChangesRequired,
        media: Boolean = true,
    ): Result {
        this.required = required
        transfers.clear()
        mediaAuth.clear()
        val input =
            Data
                .Builder()
                .putAll(SyncAuth(auth).toWorkData())
                .putBoolean("syncMedia", media)
                .build()
        return TestListenableWorkerBuilder<SyncWorker>(targetContext, input).build().doWork()
    }
}
