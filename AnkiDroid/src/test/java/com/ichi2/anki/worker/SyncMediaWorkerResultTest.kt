// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import anki.sync.syncAuth
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.sync.SyncAuth
import com.ichi2.anki.sync.lastMediaSyncAttempt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class SyncMediaWorkerResultTest : RobolectricTest() {
    private var syncError: Throwable? = null

    @Before
    fun fakeMediaSync() {
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun syncMedia(input: anki.sync.SyncAuth) {
                    syncError?.let { throw it }
                }
            }
        }
    }

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `a finished media sync is recorded as completed`() =
        runTest {
            val before = TimeManager.time.intTimeMS()
            assertThat(mediaSyncWorker().doWork(), equalTo(Result.success()))
            assertAttemptRecorded(after = before, failed = false)
        }

    @Test
    fun `a failed media sync is recorded as incomplete`() =
        runTest {
            syncError = IllegalStateException("network down")
            val before = TimeManager.time.intTimeMS()
            assertThat(mediaSyncWorker().doWork(), equalTo(Result.failure()))
            assertAttemptRecorded(after = before, failed = true)
        }

    @Test
    fun `a cancelled media sync is recorded as incomplete`() =
        runTest {
            syncError = CancellationException("cancelled from the notification")
            val before = TimeManager.time.intTimeMS()
            assertFailsWith<CancellationException> { mediaSyncWorker().doWork() }
            assertAttemptRecorded(after = before, failed = true)
        }

    private fun mediaSyncWorker() =
        TestListenableWorkerBuilder<SyncMediaWorker>(targetContext, SyncAuth(syncAuth { hkey = "test" }).toWorkData()).build()

    private fun assertAttemptRecorded(
        after: Long,
        failed: Boolean,
    ) {
        val attempt = assertNotNull(lastMediaSyncAttempt)
        assertThat(attempt.failed, equalTo(failed))
        assertThat(attempt.time, greaterThan(after))
    }
}
