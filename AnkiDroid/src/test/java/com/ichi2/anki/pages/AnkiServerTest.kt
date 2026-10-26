// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.ResponseException
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AnkiServerTest {
    @Test
    fun `a request body can arrive in several reads`() {
        val bytes = ByteArray(100) { it.toByte() }
        val stream =
            object : ByteArrayInputStream(bytes) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = super.read(buffer, offset, minOf(length, 3))
            }
        val session = postSession(stream, contentLength = bytes.size)

        assertContentEquals(bytes, AnkiServer.getSessionBytes(session))
    }

    @Test
    fun `a truncated request propagates to NanoHTTPD without dispatching`() {
        val session = postSession(ByteArrayInputStream(byteArrayOf(1, 2)), contentLength = 10)
        val handler = mock<PostRequestHandler>()
        val server = AnkiServer(handler)

        assertFailsWith<EOFException> { server.serve(session) }
        verifyNoInteractions(handler)
    }

    @Test
    fun `a body read timeout propagates to NanoHTTPD without dispatching`() {
        val timeout = SocketTimeoutException("Read timed out")
        val stream =
            object : InputStream() {
                override fun read(): Int = throw timeout
            }
        val session = postSession(stream, contentLength = 1)
        val handler = mock<PostRequestHandler>()
        val server = AnkiServer(handler)

        assertSame(timeout, assertFailsWith<SocketTimeoutException> { server.serve(session) })
        verifyNoInteractions(handler)
    }

    @Test
    fun `reading a body leaves bytes beyond content length unread`() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        val session = postSession(stream, contentLength = 3)

        assertContentEquals(byteArrayOf(1, 2, 3), AnkiServer.getSessionBytes(session))
        assertEquals(4, stream.read())
    }

    @Test
    fun `an empty protobuf request is accepted`() {
        val session = postSession(ByteArrayInputStream(byteArrayOf()), contentLength = 0)

        assertContentEquals(byteArrayOf(), AnkiServer.getSessionBytes(session))
    }

    @Test
    fun `a missing content length is rejected without dispatching`() {
        assertRejected(contentLengthHeader = null, Status.LENGTH_REQUIRED)
    }

    @Test
    fun `an invalid content length is rejected without dispatching`() {
        // lengths beyond Int can't be allocated, and must not be truncated
        val beyondInt = listOf((Int.MAX_VALUE + 1L).toString(), Long.MAX_VALUE.toString())
        // HTTP only allows ASCII digits; "١" is ARABIC-INDIC DIGIT ONE
        val notAsciiDigits = listOf("+1", "-0", " 1", "١")
        for (header in listOf("abc", "-1", "") + beyondInt + notAsciiDigits) {
            assertRejected(contentLengthHeader = header, Status.BAD_REQUEST)
        }
    }

    private fun assertRejected(
        contentLengthHeader: String?,
        expected: Status,
    ) {
        val session = postSession(ByteArrayInputStream(byteArrayOf(1, 2, 3)), contentLengthHeader)
        val handler = mock<PostRequestHandler>()

        val message = "content-length: $contentLengthHeader"
        val exception = assertFailsWith<ResponseException>(message) { AnkiServer(handler).serve(session) }

        assertEquals(expected, exception.status, message)
        verifyNoInteractions(handler)
    }

    private fun postSession(
        stream: InputStream,
        contentLength: Int,
    ): IHTTPSession = postSession(stream, contentLength.toString())

    private fun postSession(
        stream: InputStream,
        contentLengthHeader: String?,
    ): IHTTPSession =
        mock {
            on { method } doReturn Method.POST
            on { uri } doReturn "/_anki/test"
            on { headers } doReturn listOfNotNull(contentLengthHeader?.let { "content-length" to it }).toMap()
            on { inputStream } doReturn stream
        }
}
