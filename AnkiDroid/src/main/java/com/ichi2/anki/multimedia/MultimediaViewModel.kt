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
import kotlinx.coroutines.launch

class MultimediaViewModel : ViewModel() {

    /** Errors or Warnings related to the edit fields that might occur when trying to save note */
    val multimediaAction = MutableSharedFlow<MultimediaBottomSheet.MultimediaAction>()

    private var prevMultimediaPath: String? = null
    private var prevMultimediaUri: Uri? = null

    var currentMultimediaUri: Uri? = null

    var currentMultimediaPath: String? = null

    var selectedMediaFileSize: Long = 0

    fun setMultimediaAction(action: MultimediaBottomSheet.MultimediaAction) {
        viewModelScope.launch {
            multimediaAction.emit(action)
        }
    }

    fun getMultimediaFileSize(): Long {
        return selectedMediaFileSize
    }

    fun saveMultimediaForRevert(imagePath: String?, imageUri: Uri?) {
        prevMultimediaPath = imagePath
        prevMultimediaUri = imageUri
    }
}
