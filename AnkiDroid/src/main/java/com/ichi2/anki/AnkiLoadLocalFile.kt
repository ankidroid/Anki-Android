/****************************************************************************************
 * Copyright (c) 2022 Mani infinyte01@gmail.com                                         *
 *                                                                                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                            *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.HashMap

class AnkiLoadLocalFile(val context: Context) {

    /**
     * Return web response for file requested over file:/// protocol
     *
     * @param url decoded url loading in reviewer
     * @return web response with content of the file from collection dir
     */
    fun getLocalFileFromCollectionDir(url: String): WebResourceResponse? {
        val streamResponse = { data: InputStream, mime: String ->
            WebResourceResponse(mime, "utf-8", 200, "OK", HashMap(), data)
        }

        val fileResponse = { path: String, mime: String ->
            val inputStream: InputStream = FileInputStream(path)
            streamResponse(inputStream, mime)
        }

        val filename = url.split("/").last()
        var mimeType = ""

        if (filename.endsWith(".json")) {
            mimeType = "text/json"
        }

        if (filename.endsWith(".txt")) {
            mimeType = "text/plain"
        }

        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        val filePath = "$currentAnkiDroidDirectory/collection.media/$filename"
        if (File(filePath).exists()) {
            return fileResponse(filePath, mimeType)
        }

        return null
    }

    fun isLoadingLocalFile(url: String): Boolean {
        if (url.startsWith("file:///")) {
            if (url.endsWith(".json")) {
                return true
            }

            if (url.endsWith(".txt")) {
                return true
            }
        }
        return false
    }
}
