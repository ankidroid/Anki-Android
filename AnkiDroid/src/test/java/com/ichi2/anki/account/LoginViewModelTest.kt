// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.account

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class LoginViewModelTest : JvmTest() {
    private val unreachableEndpoint = "http://127.0.0.1:1/"

    @Test
    fun `cancelling a queued login prevents a login result`() =
        runTest {
            val previous = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            val viewModel = LoginViewModel()
            try {
                viewModel.handleLogin("user", "password", unreachableEndpoint)
                assertIs<ViewModelProgress.Active<Unit>>(viewModel.progressManager.progress.value)

                viewModel.progressManager.requestCancel()
                runCurrent()

                assertEquals(ViewModelProgress.Idle, viewModel.progressManager.progress.value)
                assertNull(withTimeoutOrNull(1.seconds) { viewModel.loginFlow.first() }, "cancelled login must not publish a result")
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                CollectionManager.setTestDispatcher(previous)
            }
        }

    @Test
    fun `cancelling an in-flight login aborts the network request`() =
        runTest {
            val previous = CollectionManager.setTestDispatcher(Dispatchers.IO, useReentrantLock = false)
            val viewModel = LoginViewModel()
            try {
                ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                    server.soTimeout = 5000
                    viewModel.handleLogin("user", "password", "http://127.0.0.1:${server.localPort}/")
                    server.accept().use { request ->
                        request.soTimeout = 5000
                        assertNotNull(request.getInputStream().bufferedReader().readLine())
                        // Keep the server response pending so only cancellation can finish login.
                        viewModel.progressManager.requestCancel()

                        val finished =
                            withContext(Dispatchers.IO) {
                                withTimeoutOrNull(5.seconds) {
                                    viewModel.viewModelScope.coroutineContext.job.children
                                        .toList()
                                        .joinAll()
                                    true
                                }
                            }
                        assertEquals(true, finished, "cancellation must finish login without waiting for a server response")
                        assertEquals(ViewModelProgress.Idle, viewModel.progressManager.progress.value)
                        assertNull(
                            withTimeoutOrNull(1.seconds) { viewModel.loginFlow.first() },
                            "cancelled login must not publish a result",
                        )
                    }
                }
            } finally {
                viewModel.viewModelScope.cancel()
                viewModel.viewModelScope.coroutineContext.job.children
                    .toList()
                    .joinAll()
                CollectionManager.setTestDispatcher(previous)
            }
        }

    @Test
    fun `login result is not lost while the view is stopped`() =
        runTest {
            val viewModel = LoginViewModel()

            // the view is STOPPED: repeatOnLifecycle(STARTED) has cancelled the collector
            viewModel.handleLogin("user", "password", unreachableEndpoint)
            viewModel.viewModelScope.coroutineContext.job
                .children
                .toList()
                .joinAll()

            // the view is STARTED again and re-subscribes
            val result = withTimeoutOrNull(1.seconds) { viewModel.loginFlow.first() }

            assertNotNull(result, "login result should reach the view after it restarts")
        }
}
