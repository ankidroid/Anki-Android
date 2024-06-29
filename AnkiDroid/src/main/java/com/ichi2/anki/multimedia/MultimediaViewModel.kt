/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.net.Uri
import androidx.lifecycle.ViewModel

class MultimediaViewModel : ViewModel() {

    private var prevImagePath: String? = null
    private var prevImageUri: Uri? = null

    private var currentImageUri: Uri? = null

    private var currentImagePath: String? = null

    private var imageLength: Long = 0

    fun setImageLength(length: Long) {
        imageLength = length
    }

    fun getImageLength(): Long {
        return imageLength
    }

    fun saveImageForRevert(imagePath: String?, imageUri: Uri?) {
        prevImagePath = imagePath
        prevImageUri = imageUri
    }

    fun saveCurrentImagePath(path: String) {
        currentImagePath = path
    }

    fun getCurrentImagePath(): String? {
        return currentImagePath
    }

    fun currentImageUri(uri: Uri?) {
        currentImageUri = uri
    }

    fun getCurrentImageUri(): Uri? {
        return currentImageUri
    }
}
