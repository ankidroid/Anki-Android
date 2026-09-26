// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.account

import androidx.lifecycle.viewModelScope
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class LoginViewModelTest : JvmTest() {
    private val unreachableEndpoint = "http://127.0.0.1:1/"

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
