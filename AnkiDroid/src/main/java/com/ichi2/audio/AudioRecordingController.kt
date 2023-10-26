/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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
package com.ichi2.audio

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.multimediacard.fields.FieldControllerBase
import com.ichi2.anki.multimediacard.fields.IFieldController
import com.ichi2.compat.CompatHelper
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.UiUtil
import timber.log.Timber
import java.io.File
import java.io.IOException

class AudioRecordingController : FieldControllerBase(), IFieldController, AudioTimer.OnTimerTickListener {
    private lateinit var audioRecorder: MediaRecorder
    private var tempAudioPath: String? = null
    private var onRecordingInitialized: Runnable? = null
    private lateinit var recordButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private var isRecording = false
    private var isPaused = false
    private lateinit var audioTimeView: TextView
    private lateinit var audioTimer: AudioTimer
    private lateinit var vibrator: Vibrator
    private lateinit var audioWaveform: AudioWaveform
    private lateinit var context: Context

    @Suppress("DEPRECATION")
    override fun createUI(context: Context, layout: LinearLayout) {
        val origAudioPath = this.mField.audioPath
        var bExist = false
        if (origAudioPath != null) {
            val f = File(origAudioPath)
            if (f.exists()) {
                tempAudioPath = f.absolutePath
                bExist = true
            }
        }
        if (!bExist) {
            tempAudioPath = generateTempAudioFile(mActivity)
        }

        val layoutInflater = LayoutInflater.from(context)
        val inflatedLayout = layoutInflater.inflate(R.layout.activity_audio_recording, null) as CoordinatorLayout
        layout.addView(inflatedLayout, LinearLayout.LayoutParams.MATCH_PARENT)

        context.apply {
            // add preview of the field data to provide context to the user
            // use a separate scrollview to ensure that the content does not push the buttons off-screen when scrolled
            val sv = ScrollView(this)
            layout.addView(sv)
            val previewLayout = LinearLayout(this) // scrollView can only have one child
            previewLayout.orientation = LinearLayout.VERTICAL
            sv.addView(previewLayout)
            val label = FixedTextView(this)
            label.textSize = 20f
            label.text = UiUtil.makeBold(this.getString(R.string.audio_recording_field_list))
            label.gravity = Gravity.CENTER_HORIZONTAL
            previewLayout.addView(label)
            var hasTextContents = false
            for (i in 0 until mNote.initialFieldCount) {
                val field = mNote.getInitialField(i)
                val textView = FixedTextView(this)
                textView.text = field?.text
                textView.textSize = 16f
                textView.setPadding(16, 0, 16, 24)
                previewLayout.addView(textView)
                hasTextContents = hasTextContents or !field?.text.isNullOrBlank()
            }
            label.visibility = if (hasTextContents) View.VISIBLE else View.GONE
        }

        this.context = context

        recordButton = layout.findViewById(R.id.action_start_recording)
        audioTimeView = layout.findViewById(R.id.audio_time_track)
        audioWaveform = layout.findViewById(R.id.audio_waveform_view)
        saveButton = layout.findViewById(R.id.action_save_recording)

        audioTimer = AudioTimer(this)
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        recordButton.setOnClickListener {
            when {
                isPaused -> resumeRecording()
                isRecording -> pauseRecorder()
                else -> startRecording(context, tempAudioPath!!)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }

        saveButton.setOnClickListener {
            onDone()
        }
    }

    private fun generateTempAudioFile(context: Context): String? {
        val tempAudioPath: String? = try {
            val storingDirectory = context.cacheDir
            File.createTempFile("ankidroid_audiorec", ".3gp", storingDirectory).absolutePath
        } catch (e: IOException) {
            Timber.e(e, "Could not create temporary audio file.")
            null
        }
        return tempAudioPath
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // do nothing
    }

    // ***************** audio recorder starts ***************** //
    private fun initMediaRecorder(context: Context, audioPath: String): MediaRecorder {
        val mr = CompatHelper.compat.getMediaRecorder(context)
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        onRecordingInitialized()
        mr.setOutputFile(audioPath) // audioPath could change
        return mr
    }

    private fun onRecordingInitialized() {
        if (onRecordingInitialized != null) {
            onRecordingInitialized!!.run()
        }
    }

    private fun startRecording(context: Context, audioPath: String) {
        audioRecorder = CompatHelper.compat.getMediaRecorder(context)
        var highSampling = false
        try {
            // try high quality AAC @ 44.1kHz / 192kbps first
            // can throw IllegalArgumentException if codec isn't supported
            audioRecorder = initMediaRecorder(context, audioPath)
            audioRecorder.apply {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(2)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(192000)
                // this can also throw IOException if output path is invalid
                prepare()
                start()
                highSampling = true
            }
        } catch (e: Exception) {
            Timber.w(e)
            // in all cases, fall back to low sampling
        }
        if (!highSampling) {
            // if we are here, either the codec didn't work or output file was invalid
            // fall back on default
            audioRecorder = initMediaRecorder(context, audioPath)
            audioRecorder.apply {
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                prepare()
                start()
            }
        }

        recordButton.setIconResource(R.drawable.round_pause_24)
        isRecording = true
        isPaused = false
        audioTimer.start()
    }

    private fun saveRecording() {
        mField.audioPath = tempAudioPath
        mField.hasTemporaryMedia = true
    }

    private fun stopRecording() {
        if (this::audioRecorder.isInitialized) {
            audioTimer.stop()
            audioRecorder.apply {
                this.stop()
                this.release()
            }
            isPaused = false
            isRecording = false
            audioTimeView.text = context.resources.getString(R.string.audio_text)
            audioWaveform.clear()
            saveRecording()
        }
    }

    fun release() {
        if (this::audioRecorder.isInitialized) {
            audioRecorder.release()
        }
    }

    private fun pauseRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioRecorder.pause()
            isPaused = true
        } else {
            audioRecorder.stop()
            isPaused = true
        }
        recordButton.setIconResource(R.drawable.ic_record)
        audioTimer.pause()
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioRecorder.resume()
            isPaused = false
        } else {
            audioRecorder.start()
            isPaused = false
        }
        audioTimer.start()
        recordButton.setIconResource(R.drawable.round_pause_24)
    }
    // ***************** audio recorder ends ***************** //

    override fun onDone() {
        UIUtils.showThemedToast(context, "Recording stopped", false)
        stopRecording()
    }

    override fun onFocusLost() {
        release()
    }

    override fun onDestroy() {
        release()
    }

    override fun onTimerTick(duration: String) {
        audioTimeView.text = duration
        try {
            val maxAmplitude = audioRecorder.maxAmplitude
            audioWaveform.addAmplitude(maxAmplitude.toFloat())
        } catch (e: IllegalStateException) {
            Timber.d("Audio recorder interrupted")
        }
    }
}
