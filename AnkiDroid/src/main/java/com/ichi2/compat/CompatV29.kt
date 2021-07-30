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
import android.media.ThumbnailUtils
import android.util.Size
import java.io.File

/** Implementation of [Compat] for SDK level 29  */
@TargetApi(29)
class CompatV29 : CompatV26(), Compat {
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

    companion object {
        // obtained from AOSP source
        private val THUMBNAIL_MINI_KIND = Size(512, 384)
    }
}
