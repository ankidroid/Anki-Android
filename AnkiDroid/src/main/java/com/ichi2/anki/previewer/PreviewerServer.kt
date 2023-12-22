/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import com.ichi2.anki.pages.AnkiServer.Companion.LOCALHOST
import com.ichi2.anki.pages.AnkiServer.Companion.getMimeFromUri
import com.ichi2.anki.pages.PORT
import com.ichi2.utils.AssetHelper
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

// TODO inherit from AnkiServer when it stops depending on a Activity
class PreviewerServer(private val mediaDir: String) : NanoHTTPD(LOCALHOST, PORT) {
    fun baseUrl() = "http://$LOCALHOST:$listeningPort/"

    override fun start() {
        super.start()
        Timber.i("Starting server on http://%s:%d", hostname, listeningPort)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Timber.d("%s %s", session.method, uri)
        if (session.method == Method.GET) {
            if (uri == "/favicon.ico") {
                return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
            } else if (uri.startsWith(ASSETS_PREFIX)) {
                val mime = getMimeFromUri(uri)
                val uriSub = uri.substring(ASSETS_PREFIX.length)
                val stream =
                    if (uriSub.startsWith("web/")) {
                        this.javaClass.classLoader!!.getResourceAsStream(uriSub)
                    } else {
                        this.javaClass.classLoader!!.getResourceAsStream(uri.substring(1))
                    }
                if (stream != null) {
                    return newChunkedResponse(Response.Status.OK, mime, stream)
                }
            }

            // fall back to looking in media folder
            val file = File(mediaDir, uri)
            if (file.exists()) {
                return newChunkedResponse(
                    Response.Status.OK,
                    AssetHelper.guessMimeType(uri),
                    FileInputStream(file),
                )
            }
        } else if (session.method == Method.POST) {
            Timber.w("Unhandled POST request: %s", uri)
        }

        Timber.d("not found: %s", uri)
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
    }

    override fun useGzipWhenAccepted(r: Response?) = false

    companion object {
        const val ASSETS_PREFIX = "/assets/"
    }
}
