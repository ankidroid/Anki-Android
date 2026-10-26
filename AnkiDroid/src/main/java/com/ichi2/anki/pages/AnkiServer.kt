// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Mani <infinyte01@gmail.com>

package com.ichi2.anki.pages

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.DataInputStream

open class AnkiServer(
    private val postHandler: PostRequestHandler,
    port: Int = 0,
) : NanoHTTPD(LOCALHOST, port) {
    fun baseUrl(): String = "http://$LOCALHOST:$listeningPort/"

    // it's faster to serve local files without GZip. see 'page render' in logs
    // This also removes 'W/System: A resource failed to call end.'
    override fun useGzipWhenAccepted(r: Response?) = false

    override fun serve(session: IHTTPSession): Response =
        when (session.method) {
            Method.POST -> {
                val uri = session.uri
                Timber.d("POST: Requested %s", uri)
                // NanoHTTPD responds and closes the connection if the body is rejected or can't be read
                val inputBytes = getSessionBytes(session)
                try {
                    val data = runBlocking { postHandler.handlePostRequest(PostRequestUri(uri), inputBytes) }
                    buildResponse(data)
                } catch (exception: Exception) {
                    Timber.w(exception, "buildResponse failure")
                    buildResponse(exception.localizedMessage?.encodeToByteArray(), status = Response.Status.INTERNAL_ERROR)
                }
            }
            Method.GET -> {
                Timber.d("Rejecting GET request to server %s", session.uri)
                newFixedLengthResponse(Response.Status.NOT_FOUND, null, null)
            }
            else -> {
                Timber.d("Ignored request of unhandled method %s, uri %s", session.method, session.uri)
                newFixedLengthResponse(null)
            }
        }

    private fun buildResponse(
        data: ByteArray?,
        mimeType: String = "application/binary",
        status: Response.IStatus = Response.Status.OK,
    ): Response =
        if (data == null) {
            newFixedLengthResponse(null)
        } else {
            newChunkedResponse(status, mimeType, ByteArrayInputStream(data))
        }

    companion object {
        const val LOCALHOST = "127.0.0.1"

        /** Common prefix used on Anki requests */
        const val ANKI_PREFIX = "/_anki/"
        const val ANKIDROID_PREFIX = "/ankidroid/"
        const val ANKIDROID_JS_PREFIX = "/jsapi/"

        /**
         * @throws NanoHTTPD.ResponseException if the content-length is missing, or isn't
         * ASCII digits within [Int]. NanoHTTPD sends its status and closes the connection
         */
        fun getSessionBytes(session: IHTTPSession): ByteArray {
            val header =
                session.headers["content-length"]
                    ?: throw ResponseException(Response.Status.LENGTH_REQUIRED, "content-length is required")
            // RFC 9110: 1*DIGIT. toIntOrNull alone accepts a sign and non-ASCII digits
            val contentLength =
                header.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toIntOrNull()
                    ?: throw ResponseException(Response.Status.BAD_REQUEST, "invalid content-length: $header")
            val bytes = ByteArray(contentLength)
            DataInputStream(session.inputStream).readFully(bytes)
            return bytes
        }
    }
}
