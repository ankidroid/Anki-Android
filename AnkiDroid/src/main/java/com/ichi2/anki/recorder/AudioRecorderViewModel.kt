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

package com.ichi2.anki.recorder

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import java.io.File

/**
 * ViewModel responsible for managing the audio recording flow and coordinating with [AudioRecordingService].
 *
 * This class serves as the 'Source of Truth' for the UI. It transforms user interactions into
 * service commands and exposes the recording state, elapsed time, and results for observation.
 *
 * @param application The standard Android Application context, used to access the service.
 */
@Suppress("Unused")
class AudioRecorderViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val context = getApplication<Application>().applicationContext
    private var currentAudioFile: File? = null

    /**
     * Configures the destination file where the audio data will be encoded.
     * **Must be called before starting a recording.**
     * * @param file The [File] object representing the target storage path.
     */
    fun setOutputFile(file: File) {
        currentAudioFile = file
    }

    /**
     * Handles the main record button tap, which is state-dependent.
     */
    fun handleTapRecord() {
    }

    /**
     * Requests the service to finalize and persist the current recording.
     * The service is expected to emit a success or failure event once complete.
     */
    fun handleSave() {
    }

    /**
     * Requests the service to discard the current recording and delete any temporary data.
     */
    fun handleCancel() {
    }

    private fun startRecording() {
    }

    private fun pauseRecording() {
    }

    private fun resumeRecording() {
    }

    /**
     * Sends a simple, action-only command to the service.
     */
    private fun sendServiceCommand(action: String) {
    }
}
