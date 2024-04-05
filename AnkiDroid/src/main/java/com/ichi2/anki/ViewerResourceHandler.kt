/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.ichi2.utils.AssetHelper.guessMimeType
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels

private const val RANGE_HEADER = "Range"

/**
 * @param authority the authority of the WebView Url, e.g. `127.0.0.1:40001`.
 */
class ViewerResourceHandler(context: Context, private val authority: String) {
    private val mediaDir = CollectionHelper.getMediaDirectory(context)

    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        val path = url.path

        if (request.method != "GET" || path == null || url.authority != authority) {
            return null
        }
        if (path == "/favicon.ico") {
            return WebResourceResponse(null, null, ByteArrayInputStream(ByteArray(0)))
        }

        try {
            val file = File(mediaDir, path)
            if (!file.exists()) {
                return null
            }
            request.requestHeaders[RANGE_HEADER]?.let { range ->
                return handlePartialContent(file, range)
            }
            val inputStream = FileInputStream(file)
            return WebResourceResponse(guessMimeType(path), null, inputStream)
        } catch (e: Exception) {
            Timber.d("File not found")
            return null
        }
    }

    private fun handlePartialContent(file: File, range: String): WebResourceResponse {
        val rangeHeader = RangeHeader.from(range, defaultEnd = file.length() - 1)

        val mimeType = guessMimeType(file.path)
        val inputStream = file.toInputStream(rangeHeader)
        val (start, end) = rangeHeader
        val responseHeaders = mapOf(
            "Content-Range" to "bytes $start-$end/${file.length()}",
            "Accept-Range" to "bytes"
        )
        return WebResourceResponse(
            mimeType,
            null,
            206,
            "Partial Content",
            responseHeaders,
            inputStream
        )
    }
}

/**
 * Handles the "range" header in a HTTP Request
 */
data class RangeHeader(val start: Long, val end: Long) {
    companion object {
        fun from(range: String, defaultEnd: Long): RangeHeader {
            val numbers = range.substring("bytes=".length).split('-')
            val unspecifiedEnd = numbers.getOrNull(1).isNullOrEmpty()
            return RangeHeader(
                start = numbers[0].toLong(),
                end = if (unspecifiedEnd) defaultEnd else numbers[1].toLong()
            )
        }
    }
}

fun File.toInputStream(header: RangeHeader): InputStream {
    // PERF: Test to see if a custom FileInputStream + available() would be faster
    val randomAccessFile = RandomAccessFile(this, "r")
    return Channels.newInputStream(randomAccessFile.channel).also {
        it.skip(header.start)
    }
}
