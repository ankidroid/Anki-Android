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

import com.ichi2.anki.AnkiDroidApp
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException

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
        val uri = session.uri
        val mime = getMimeFromUri(uri)

        if (session.method == Method.GET) {
            val resourcePath = "backend/web$uri"
            val stream = try {
                AnkiDroidApp.instance.assets.open(resourcePath)
            } catch (e: IOException) {
                null
            }
            Timber.d("GET: Requested %s (%s), found? %b", uri, resourcePath, stream != null)
            return newChunkedResponse(Response.Status.OK, mime, stream)
        }

        if (session.method == Method.POST) {
            Timber.d("POST: Requested %s", uri)
            val inputBytes = getSessionBytes(session)
            return buildResponse {
                postHandler.handlePostRequest(uri, inputBytes)
            }
        }
        return newFixedLengthResponse(null)
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

        fun getMimeFromUri(uri: String): String {
            return when (uri.substringAfterLast(".")) {
                "ico" -> "image/x-icon"
                "css" -> "text/css"
                "js" -> "text/javascript"
                "html" -> "text/html"
                else -> "application/binary"
            }
        }

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
