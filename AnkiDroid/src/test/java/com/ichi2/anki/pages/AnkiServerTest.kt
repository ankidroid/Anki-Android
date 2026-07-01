// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 mixelas <michelakisgio@gmail.com>
package com.ichi2.anki.pages

import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class AnkiServerTest {
    @Mock
    private lateinit var mockPostHandler: PostRequestHandler

    @Test
    fun testBaseUrlHttpWhenNoContext() {
        // Without context, server should use HTTP
        val server = AnkiServer(mockPostHandler, port = 0)
        assertTrue(server.baseUrl().startsWith("http://"), "Should return HTTP URL when no context provided")
        assertTrue(!server.baseUrl().startsWith("https://"), "Should not be HTTPS when context is null")
    }

    @Test
    fun testBaseUrlContainsLocalhost() {
        val server = AnkiServer(mockPostHandler, port = 0)
        assertTrue(server.baseUrl().contains("127.0.0.1"), "Should contain localhost address")
    }

    @Test
    fun testBaseUrlEndsWithSlash() {
        val server = AnkiServer(mockPostHandler, port = 0)
        val baseUrl = server.baseUrl()
        assertTrue(baseUrl.endsWith("/"), "Base URL should end with /")
    }
}
