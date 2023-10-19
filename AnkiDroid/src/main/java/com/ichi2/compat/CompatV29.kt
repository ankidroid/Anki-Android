/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.compat

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.ichi2.libanki.utils.TimeManager
import java.io.File

/** Implementation of [Compat] for SDK level 29  */
@TargetApi(29)
open class CompatV29 : CompatV26(), Compat {
    override fun hasVideoThumbnail(path: String): Boolean {
        return try {
            ThumbnailUtils.createVideoThumbnail(File(path), THUMBNAIL_MINI_KIND, null)
            // createVideoThumbnail throws an exception if it's null
            true
        } catch (e: Exception) {
            // The default for audio is an IOException, so don't log it
            // A log line is still produced:
            // E/MediaMetadataRetrieverJNI: getEmbeddedPicture: Call to getEmbeddedPicture failed
            false
        }
    }

    override fun saveImage(context: Context, bitmap: Bitmap, baseFileName: String, extension: String, format: Bitmap.CompressFormat, quality: Int): Uri {
        val imagesCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val destDir = File(Environment.DIRECTORY_PICTURES, "AnkiDroid")
        val date = TimeManager.time.intTimeMS()

        val newImage = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$date.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/$extension")
            put(MediaStore.MediaColumns.DATE_ADDED, date)
            put(MediaStore.MediaColumns.DATE_MODIFIED, date)
            put(MediaStore.MediaColumns.SIZE, bitmap.byteCount)
            put(MediaStore.MediaColumns.WIDTH, bitmap.width)
            put(MediaStore.MediaColumns.HEIGHT, bitmap.height)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$destDir${File.separator}")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val newImageUri = context.contentResolver.insert(imagesCollection, newImage)
        context.contentResolver.openOutputStream(newImageUri!!).use {
            if (it != null) {
                bitmap.compress(format, quality, it)
            }
        }
        newImage.clear()
        newImage.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(newImageUri, newImage, null, null)
        return newImageUri
    }

    companion object {
        // obtained from AOSP source
        private val THUMBNAIL_MINI_KIND = Size(512, 384)
    }
}
