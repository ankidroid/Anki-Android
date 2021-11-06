/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import androidx.annotation.CheckResult

object ClipboardUtil {
    // JPEG is sent via pasted content
    @JvmField
    val IMAGE_MIME_TYPES = arrayOf("image/gif", "image/png", "image/jpg", "image/jpeg")

    @JvmStatic
    fun hasImage(clipboard: ClipboardManager?): Boolean {
        if (clipboard == null) {
            return false
        }
        if (!clipboard.hasPrimaryClip()) {
            return false
        }
        val primaryClip = clipboard.primaryClip
        return hasImage(primaryClip!!.description)
    }

    @JvmStatic
    fun hasImage(description: ClipDescription?): Boolean {
        if (description == null) {
            return false
        }
        for (mimeType in IMAGE_MIME_TYPES) {
            if (description.hasMimeType(mimeType)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun getImageUri(clipboard: ClipboardManager?): Uri? {
        if (clipboard == null) {
            return null
        }
        if (!clipboard.hasPrimaryClip()) {
            return null
        }
        val primaryClip = clipboard.primaryClip
        return if (primaryClip!!.itemCount == 0) {
            null
        } else primaryClip.getItemAt(0).uri
    }

    @JvmStatic
    @CheckResult
    fun getText(clipboard: ClipboardManager?): CharSequence? {
        if (clipboard == null) {
            return null
        }
        if (!clipboard.hasPrimaryClip()) {
            return null
        }
        val data = clipboard.primaryClip
        if (data!!.itemCount == 0) {
            return null
        }
        val i = data.getItemAt(0)
        return i.text
    }

    @JvmStatic
    @CheckResult
    fun getDescriptionLabel(clip: ClipData?): CharSequence? {
        if (clip == null) {
            return null
        }
        return if (clip.description == null) {
            null
        } else clip.description.label
    }
}
