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
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.AssetHelper.guessMimeType
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.pathString
import kotlin.text.get

private const val RANGE_HEADER = "Range"
private const val MATHJAX_PATH_PREFIX = "/_anki/js/vendor/mathjax"
private val srcPattern = Pattern.compile("src=\"([^\"]*)\"")

class ViewerResourceHandler(
    private val context: Context,
) {
    private val assetManager = context.assets

    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val path = request.url.path ?: return null
        val range = request.requestHeaders[RANGE_HEADER]
        return when {
            request.method != "GET" -> null
            path == "/favicon.ico" ->
                WebResourceResponse(
                    null,
                    null,
                    ByteArrayInputStream(ByteArray(0)),
                )

            path.startsWith(MATHJAX_PATH_PREFIX) -> {
                val mathjaxAssetPath =
                    Paths
                        .get(
                            "backend/js/vendor/mathjax",
                            path.removePrefix(MATHJAX_PATH_PREFIX),
                        ).pathString
                val inputStream = assetManager.open(mathjaxAssetPath)
                try {
                    WebResourceResponse(guessMimeType(path), null, inputStream)
                } catch (_: Exception) {
                    Timber.d("File $mathjaxAssetPath not found")
                    null
                }
            }
            range != null -> {
                handlePartialContent(file(context, path) ?: return null, range)
            }
            else -> {
                try {
                    val inputStream = inputStream(context, path) ?: return null
                    val mimeType = guessMimeType(path)
                    return WebResourceResponse(mimeType, null, inputStream)
                } catch (_: Exception) {
                    Timber.d("File $path not found")
                    return null
                }
            }
        }
    }

    @NeedsTest("seeking audio - 16513")
    private fun handlePartialContent(
        file: File,
        range: String,
    ): WebResourceResponse {
        val rangeHeader = RangeHeader.from(range, defaultEnd = file.length() - 1)

        val mimeType = guessMimeType(file.path)
        val (start, end) = rangeHeader
        val responseHeaders =
            mapOf(
                "Content-Range" to "bytes $start-$end/${file.length()}",
                "Accept-Ranges" to "bytes",
            )
        // WARN: WebResourceResponse appears to handle truncating the stream internally
        // This is NOT the same as NanoHTTPD

        // sending a truncated stream caused:
        // -> `net::ERR_FAILED`

        // returning a 'full' input stream with the provided header
        // returns a 'correct' Content-Length (example below)
        //
        // Content-Range: bytes 2916352-2931180/2931181
        // Content-Length: 14829
        // The above needs more investigation
        val fileStream = FileInputStream(file)
        return WebResourceResponse(
            mimeType,
            null,
            206,
            "Partial Content",
            responseHeaders,
            fileStream,
        )
    }

    companion object {
        /**
         * Returns the file at path if it exists,
         */
        private fun file(
            context: Context,
            path: String,
        ): File? {
            val mediaDir = CollectionHelper.getMediaDirectory(context)
            return try {
                File(mediaDir, path).takeIf { it.exists() }
            } catch (_: Exception) {
                Timber.d("can't check whether $path exists.")
                null
            }
        }

        private fun inputStream(
            context: Context,
            path: String,
        ): InputStream? = getByteArray(path)?.let { ByteArrayInputStream(it) } ?: file(context, path)?.let { FileInputStream(it) }

        /**
         * Associate to file name the byte array of this file.
         */
        private val prefetch = mutableMapOf<String, ByteArray>()

        fun getByteArray(path: String): ByteArray? = prefetch[path]

        private fun findSrcs(html: String): Iterable<String> {
            val paths = mutableSetOf<String>()
            val m = srcPattern.matcher(html)
            while (m.find()) {
                paths.add(m.group(1)!!)
            }
            return paths
        }

        fun prefetch(
            context: Context,
            html: String,
        ) {
            data class PrefetchData(
                val length: Int,
                val stream: FileInputStream,
                val path: String,
            )
            val srcs = findSrcs(html)
            val inputStreams =
                srcs.mapNotNull { path ->
                    val file = file(context, path) ?: return@mapNotNull null
                    val length =
                        try {
                            file.length()
                        } catch (_: Exception) {
                            Timber.d("File $path exists but its length can't be determined.")
                            return@mapNotNull null
                        }
                    if (length > 10 * 1024 * 1024) {
                        Timber.d("File $path length is $length, greater than 10 mb, not caching it")
                        return@mapNotNull null
                    }
                    return@mapNotNull PrefetchData(length.toInt(), FileInputStream(file), path)
                }
            prefetch.clear()
            for (data in inputStreams.take(20)) {
                val bytes = ByteArray(data.length)
                data.stream.read(bytes)
                prefetch[data.path] = bytes
            }
        }
    }
}

/**
 * Handles the "range" header in a HTTP Request
 */
data class RangeHeader(
    val start: Long,
    val end: Long,
) {
    companion object {
        fun from(
            range: String,
            defaultEnd: Long,
        ): RangeHeader {
            val numbers = range.substring("bytes=".length).split('-')
            val unspecifiedEnd = numbers.getOrNull(1).isNullOrEmpty()
            return RangeHeader(
                start = numbers[0].toLong(),
                end = if (unspecifiedEnd) defaultEnd else numbers[1].toLong(),
            )
        }
    }
}
