/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
import androidx.core.net.toFile
import com.ichi2.utils.AssetHelper.guessMimeType
import timber.log.Timber
import java.io.FileInputStream

class ViewerResourceHandler(context: Context) {
    private val mediaDir = CollectionHelper.getMediaDirectory(context).path

    /**
     * Loads resources from `collection.media` when requested by JS scripts.
     *
     * Differently from common media requests, scripts' requests have an `Origin` header
     * and are susceptible to CORS policy, so `Access-Control-Allow-Origin` is necessary.
     */
    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (request.method != "GET" || url.scheme != "file" || "Origin" !in request.requestHeaders) {
            return null
        }
        try {
            val file = url.toFile()
            if (file.parent != mediaDir || !file.exists()) {
                return null
            }
            val inputStream = FileInputStream(file)
            return WebResourceResponse(guessMimeType(file.path), null, inputStream).apply {
                responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
            }
        } catch (e: Exception) {
            Timber.d("File couldn't be loaded")
            return null
        }
    }
}
