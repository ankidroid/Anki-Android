/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.dialogs.viewmodel

import androidx.lifecycle.ViewModel
import com.ichi2.libanki.MediaCheckResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class MediaCheckViewModel : ViewModel() {
    val startMediaCheck: MutableSharedFlow<Boolean> = MutableSharedFlow()
    val deleteUnused: MutableStateFlow<List<String>?> = MutableStateFlow(null)
    val tagMissing: MutableStateFlow<List<Long>?> = MutableStateFlow(null)

    var mediaCheckResult: MediaCheckResult? = null

    suspend fun checkMedia() {
        startMediaCheck.emit(true)
    }

    suspend fun tagMissingMediaFiles() {
        tagMissing.emit(mediaCheckResult?.missingMediaNotes)
    }

    suspend fun deleteUnusedMedia() {
        deleteUnused.emit(mediaCheckResult?.unusedFileNames)
    }

    fun updateMediaCheckResult(result: MediaCheckResult) {
        mediaCheckResult = result
    }
}
