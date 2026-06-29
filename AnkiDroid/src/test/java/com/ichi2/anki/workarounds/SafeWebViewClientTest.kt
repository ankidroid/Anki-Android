// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 mixelas <michelakisgio@gmail.com>
package com.ichi2.anki.workarounds

import android.net.http.SslError
import android.webkit.SslErrorHandler
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafeWebViewClientTest {
    private val client = SafeWebViewClient()

    @Test
    fun onReceivedSslError_proceeds_forLoopbackHost() {
        val handler = mock<SslErrorHandler>()
        val sslError = mock<SslError>()
        whenever(sslError.url).thenReturn("https://127.0.0.1:12345/page")

        client.onReceivedSslError(null, handler, sslError)

        verify(handler).proceed()
    }

    @Test
    fun onReceivedSslError_doesNotProceed_forNonLoopbackHost() {
        val handler = mock<SslErrorHandler>()
        val sslError = mock<SslError>()
        whenever(sslError.url).thenReturn("https://example.com")

        client.onReceivedSslError(null, handler, sslError)

        verify(handler, never()).proceed()
    }

    @Test
    fun onReceivedSslError_proceeds_forLocalhost() {
        val handler = mock<SslErrorHandler>()
        val sslError = mock<SslError>()
        whenever(sslError.url).thenReturn("https://localhost:40000/index.html")

        client.onReceivedSslError(null, handler, sslError)

        verify(handler).proceed()
    }
}
