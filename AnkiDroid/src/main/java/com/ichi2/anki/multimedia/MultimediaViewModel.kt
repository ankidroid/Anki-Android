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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MultimediaViewModel : ViewModel() {

    /** Errors or Warnings related to the edit fields that might occur when trying to save note */
    val multimediaAction = MutableSharedFlow<MultimediaBottomSheet.MultimediaAction>()

    private var prevMultimediaPath: String? = null
    private var prevMultimediaUri: Uri? = null

    private val _currentMultimediaUri = MutableStateFlow<Uri?>(null)
    val currentMultimediaUri: StateFlow<Uri?> get() = _currentMultimediaUri

    private val _currentMultimediaPath = MutableStateFlow<String?>(null)
    val currentMultimediaPath: StateFlow<String?> get() = _currentMultimediaPath

    var selectedMediaFileSize: Long = 0

    fun setMultimediaAction(action: MultimediaBottomSheet.MultimediaAction) {
        viewModelScope.launch {
            multimediaAction.emit(action)
        }
    }

    fun saveMultimediaForRevert(imagePath: String?, imageUri: Uri?) {
        prevMultimediaPath = imagePath
        prevMultimediaUri = imageUri
    }

    fun restoreMultimedia() {
        _currentMultimediaUri.value = prevMultimediaUri
        _currentMultimediaPath.value = prevMultimediaPath
    }

    fun updateMediaFileLength(length: Long) {
        selectedMediaFileSize = length
    }

    fun updateCurrentMultimediaUri(uri: Uri?) {
        _currentMultimediaUri.value = uri
    }

    fun updateCurrentMultimediaPath(path: String?) {
        _currentMultimediaPath.value = path
    }
}
