/*
 *  Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 *  Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 *  Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 *  Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
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

package com.ichi2.anki.multimediacard

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.IOException

class AudioRecorder {
    private lateinit var recorder: MediaRecorder
    private var onRecordingInitialized: Runnable? = null
    private var previousNonZeroAmplitude = 0
    private fun initMediaRecorder(context: Context, audioPath: String): MediaRecorder {
        val mr = CompatHelper.compat.getMediaRecorder(context)
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        onRecordingInitialized()
        mr.setOutputFile(audioPath) // audioPath could change
        return mr
    }

    private fun onRecordingInitialized() {
        onRecordingInitialized?.run()
    }

    @Throws(IOException::class)
    fun startRecording(context: Context, audioPath: String) {
        var highSampling = false
        try {
            // try high quality AAC @ 44.1kHz / 192kbps first
            // can throw IllegalArgumentException if codec isn't supported
            recorder = initMediaRecorder(context, audioPath)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(2)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(192000)
            // this can also throw IOException if output path is invalid
            recorder.prepare()
            recorder.start()
            highSampling = true
        } catch (e: Exception) {
            Timber.w(e)
            // in all cases, fall back to low sampling
        }
        if (!highSampling) {
            // if we are here, either the codec didn't work or output file was invalid
            // fall back on default
            recorder = initMediaRecorder(context, audioPath)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.prepare()
            recorder.start()
        }
    }

    fun stopRecording() {
        if (this::recorder.isInitialized) {
            recorder.stop()
        }
    }

    fun setOnRecordingInitializedHandler(onRecordingInitialized: Runnable?) {
        this.onRecordingInitialized = onRecordingInitialized
    }

    fun release() {
        if (this::recorder.isInitialized) {
            recorder.release()
        }
    }

    fun maxAmplitude(): Int {
        val currentAmplitude = if (this::recorder.isInitialized) {
            recorder.maxAmplitude
        } else {
            0
        }
        return if (currentAmplitude == 0) {
            previousNonZeroAmplitude
        } else {
            previousNonZeroAmplitude = currentAmplitude
            currentAmplitude
        }
    }

    fun pause() {
        if (!this::recorder.isInitialized) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder.pause()
        } else {
            recorder.stop()
        }
    }

    fun resume() {
        if (this::recorder.isInitialized) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder.resume()
            } else {
                recorder.start()
            }
        }
    }
}
