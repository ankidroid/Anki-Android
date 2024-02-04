/*
 *  Copyright (c) 2022 Mani <infinyte01@gmail.com>
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.pages

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream

const val PORT = 0
// const val PORT = 40001

// local debugging:
// ~/Local/Android/Sdk/platform-tools/adb forward tcp:40001 tcp:40001

open class AnkiServer(
    private val postHandler: PostRequestHandler
) : NanoHTTPD(LOCALHOST, PORT) {

    fun baseUrl(): String {
        return "http://$LOCALHOST:$listeningPort/"
    }

    // it's faster to serve local files without GZip. see 'page render' in logs
    // This also removes 'W/System: A resource failed to call end.'
    override fun useGzipWhenAccepted(r: Response?) = false

    override fun serve(session: IHTTPSession): Response {
        return when (session.method) {
            Method.POST -> {
                val uri = session.uri
                Timber.d("POST: Requested %s", uri)
                val inputBytes = getSessionBytes(session)
                buildResponse {
                    postHandler.handlePostRequest(uri, inputBytes)
                }
            }
            Method.GET -> newFixedLengthResponse(Response.Status.NOT_FOUND, null, null)
            else -> newFixedLengthResponse(null)
        }
    }

    private fun buildResponse(
        block: suspend CoroutineScope.() -> ByteArray
    ): Response {
        return try {
            val data = runBlocking {
                block()
            }
            newChunkedResponse(data)
        } catch (exc: Exception) {
            newChunkedResponse(exc.localizedMessage?.encodeToByteArray(), status = Response.Status.INTERNAL_ERROR)
        }
    }

    companion object {
        const val LOCALHOST = "127.0.0.1"

        /** Common prefix used on Anki requests */
        const val ANKI_PREFIX = "/_anki/"
        const val ANKIDROID_JS_PREFIX = "/jsapi/"

        fun getSessionBytes(session: IHTTPSession): ByteArray {
            val contentLength = session.headers["content-length"]!!.toInt()
            val bytes = ByteArray(contentLength)
            session.inputStream.read(bytes, 0, contentLength)
            return bytes
        }

        fun newChunkedResponse(
            data: ByteArray?,
            mimeType: String = "application/binary",
            status: Response.IStatus = Response.Status.OK
        ): Response {
            return if (data == null) {
                newFixedLengthResponse(null)
            } else {
                newChunkedResponse(status, mimeType, ByteArrayInputStream(data))
            }
        }
    }
}
