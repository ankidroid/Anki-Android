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

import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.importCsvRaw
import com.ichi2.anki.runBlockingCatching
import com.ichi2.libanki.*
import com.ichi2.libanki.stats.*
import fi.iki.elonen.NanoHTTPD
import timber.log.Timber
import java.io.ByteArrayInputStream

private const val PORT = 0
// const val PORT = 40001

// local debugging:
// ~/Local/Android/Sdk/platform-tools/adb forward tcp:40001 tcp:40001

open class AnkiServer(
    val activity: FragmentActivity
) : NanoHTTPD("127.0.0.1", PORT) {

    fun baseUrl(): String {
        return "http://127.0.0.1:$listeningPort/"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val mime = getMimeFromUri(uri)

        if (session.method == Method.GET) {
            val resourcePath = "web$uri"
            val stream = this.javaClass.classLoader!!.getResourceAsStream(resourcePath)
            Timber.d("GET: Requested %s (%s), found? %b", uri, resourcePath, stream != null)
            return newChunkedResponse(Response.Status.OK, mime, stream)
        }

        if (session.method == Method.POST) {
            Timber.d("POST: Requested %s", uri)
            val inputBytes = getSessionBytes(session)
            if (uri.startsWith(ANKI_PREFIX)) {
                val data: ByteArray? = activity.runBlockingCatching {
                    handlePostRequest(uri.substring(ANKI_PREFIX.length), inputBytes)
                }
                return newChunkedResponse(data)
            }
        }
        return newFixedLengthResponse(null)
    }

    private suspend fun handlePostRequest(methodName: String, bytes: ByteArray): ByteArray? {
        return when (methodName) {
            "i18nResources" -> withCol { i18nResourcesRaw(bytes) }
            "getGraphPreferences" -> withCol { getGraphPreferencesRaw() }
            "setGraphPreferences" -> withCol { setGraphPreferencesRaw(bytes) }
            "graphs" -> withCol { graphsRaw(bytes) }
            "getNotetypeNames" -> withCol { getNotetypeNamesRaw(bytes) }
            "getDeckNames" -> withCol { getDeckNamesRaw(bytes) }
            "getCsvMetadata" -> withCol { getCsvMetadataRaw(bytes) }
            "importCsv" -> activity.importCsvRaw(bytes)
            "getFieldNames" -> withCol { getFieldNamesRaw(bytes) }
            "cardStats" -> withCol { cardStatsRaw(bytes) }
            "getDeckConfig" -> withCol { getDeckConfigRaw(bytes) }
            "getDeckConfigsForUpdate" -> withCol { getDeckConfigsForUpdateRaw(bytes) }
            "updateDeckConfigs" -> activity.updateDeckConfigsRaw(bytes)
            else -> { Timber.w("Unhandled Anki request: %s", methodName); null }
        }
    }

    private fun getSessionBytes(session: IHTTPSession): ByteArray {
        val contentLength = session.headers["content-length"]!!.toInt()
        val bytes = ByteArray(contentLength)
        session.inputStream.read(bytes, 0, contentLength)
        return bytes
    }

    companion object {
        /** Common prefix used on Anki requests */
        const val ANKI_PREFIX = "/_anki/"

        fun getMimeFromUri(uri: String): String {
            return when (uri.substringAfterLast(".")) {
                "ico" -> "image/x-icon"
                "css" -> "text/css"
                "js" -> "text/javascript"
                "html" -> "text/html"
                else -> "application/binary"
            }
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
