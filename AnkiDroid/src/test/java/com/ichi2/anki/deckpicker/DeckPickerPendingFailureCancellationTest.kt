// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

class DeckPickerPendingFailureCancellationTest : JvmTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `a replaced refresh still reports corruption`() {
        val reads = AtomicInteger()
        val fourthRead = CountDownLatch(1)
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    when (reads.incrementAndGet()) {
                        in 1..3 -> throw IllegalStateException("transient refresh failure")
                        4 -> {
                            fourthRead.countDown()
                            throw corruption
                        }
                        else -> super.deckTree(now)
                    }
            }
        }
        throwOnShowError = false
        runTest {
            val viewModel = DeckPickerViewModel()
            val notificationStarted = CompletableDeferred<Unit>()
            val releaseNotification = CompletableDeferred<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.flowOfDeckListLoadFailed.collect {
                    notificationStarted.complete(Unit)
                    releaseNotification.await()
                }
            }
            viewModel.reloadDeckCounts().join()
            notificationStarted.await()
            repeat(2) { viewModel.reloadDeckCounts().join() }
            repeat(3) { assertTrue(viewModel.onError.tryReceive().isSuccess) }

            val failingRefresh = viewModel.reloadDeckCounts()
            assertTrue(withContext(Dispatchers.IO) { fourthRead.await(10, TimeUnit.SECONDS) })
            // A healthy refresh reports the error without waiting for the slow collector.
            val reportedWithoutBlocking =
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(2_000) {
                        failingRefresh.join()
                        true
                    } ?: false
                }
            if (!reportedWithoutBlocking) {
                val replacement = viewModel.reloadDeckCounts()
                failingRefresh.join()
                releaseNotification.complete(Unit)
                replacement.join()
            }

            assertTrue(
                viewModel.onError.tryReceive().getOrNull() === corruption,
                "A failed backend read must report corruption even when a newer refresh supersedes it",
            )
        }
    }
}
