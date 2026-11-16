// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.libanki.exception.InvalidSearchException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class LaunchCatchingTest {
    @Test
    fun `launchCatching does not include class names for InvalidSearchException`() =
        runTest {
            val underlying = IllegalStateException("Invalid search: an and")
            val captured = captureErrorMessage { throw InvalidSearchException(underlying) }

            assertThat(captured, equalTo("Invalid search: an and"))
        }

    @Test
    fun `launchCatching catches a failed child coroutine`() =
        runTest {
            val captured = captureErrorMessage { launch { throw IllegalStateException("child") } }

            assertThat(captured, equalTo("java.lang.IllegalStateException: child"))
        }

    private suspend fun CoroutineScope.captureErrorMessage(block: suspend CoroutineScope.() -> Unit): String? {
        var captured: String? = null
        launchCatching(
            errorMessageHandler = { captured = it },
            block = block,
        ).join()
        return captured
    }
}
