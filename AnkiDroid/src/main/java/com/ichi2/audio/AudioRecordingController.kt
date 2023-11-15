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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.Gravity
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.anki.R
import com.ichi2.anki.multimediacard.AudioRecorder
import com.ichi2.anki.multimediacard.MediaPlayer
import com.ichi2.anki.multimediacard.fields.FieldControllerBase
import com.ichi2.anki.multimediacard.fields.IFieldController
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.CompatHelper
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.UiUtil
import timber.log.Timber
import java.io.File
import java.io.IOException
// TODO : stop audio time view flickering
class AudioRecordingController :
    FieldControllerBase(),
    IFieldController,
    AudioTimer.OnTimerTickListener {
    private val audioRecorder = AudioRecorder()
    private lateinit var mediaPlayer: MediaPlayer
    private var tempAudioPath: String? = null

    private lateinit var recordButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var audioTimeView: TextView
    private lateinit var audioTimer: AudioTimer
    private lateinit var playAudioButton: MaterialButton
    private lateinit var forwardAudioButton: MaterialButton
    private lateinit var rewindAudioButton: MaterialButton
    private lateinit var audioWaveform: AudioWaveform
    private lateinit var audioProgressBar: LinearProgressIndicator
    private lateinit var context: Context
    private var isRecording = false
    private var isPaused = false
    private var isCleared = false
    private var isPlaying = false
    private lateinit var cancelAudioRecordingButton: MaterialButton

    // wave layout takes up a lot of screen in HORIZONTAL layout so we need to hide it
    private var orientationEventListener: OrientationEventListener? = null

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
        val inflatedLayout =
            layoutInflater.inflate(R.layout.activity_audio_recording, null) as LinearLayout
        layout.addView(inflatedLayout, LinearLayout.LayoutParams.MATCH_PARENT)
        (context as Activity).window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val recordAudioButtonLayout: LinearLayout = layout.findViewById(R.id.record_buttons_layout)

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
        cancelAudioRecordingButton = layout.findViewById(R.id.action_cancel_recording)
        playAudioButton = layout.findViewById(R.id.action_play_recording)
        forwardAudioButton = layout.findViewById(R.id.action_forward)
        rewindAudioButton = layout.findViewById(R.id.action_rewind)
        val playAudioButtonLayout = layout.findViewById<LinearLayout>(R.id.play_buttons_layout)
        audioProgressBar = layout.findViewById(R.id.audio_progress_indicator)
        val audioFileView = layout.findViewById<ShapeableImageView>(R.id.audio_file_imageview)
        cancelAudioRecordingButton.isEnabled = false
        saveButton.isEnabled = false
        mediaPlayer = MediaPlayer()

        audioTimer = AudioTimer(this)
        recordButton.setOnClickListener {
            when {
                isPaused -> resumeRecording()
                isRecording -> pauseRecorder()
                isCleared -> startRecording(context, tempAudioPath!!)
                else -> startRecording(context, tempAudioPath!!)
            }
            CompatHelper.compat.vibrate(context, 20)
        }

        saveButton.setOnClickListener {
            CompatHelper.compat.vibrate(context, 20)
            stopAndSaveRecording()
            recordButton.visibility = View.GONE
            playAudioButton.visibility = View.VISIBLE
            playAudioButtonLayout.visibility = View.VISIBLE
            recordAudioButtonLayout.visibility = View.GONE
            context.showSnackbar(context.resources.getString(R.string.audio_saved))
            prepareAudioPlayer()
        }

        playAudioButton.setOnClickListener {
            playPausePlayer()
        }

        cancelAudioRecordingButton.setOnClickListener {
            CompatHelper.compat.vibrate(context, 20)
            isCleared = true
            clearRecording()
        }

        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                when (context.resources.configuration.orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> {
                        audioFileView.visibility = View.GONE
                        audioWaveform.visibility = View.GONE
                    }
                    Configuration.ORIENTATION_PORTRAIT -> {
                        audioFileView.visibility = View.VISIBLE
                        audioWaveform.visibility = View.VISIBLE
                    }
                }
            }
        }
        orientationEventListener?.enable()
    }

    private fun prepareAudioPlayer() {
        mediaPlayer.apply {
            setDataSource(tempAudioPath)
            prepareAsync()
            onPrepared(mediaPlayer)
            setOnPreparedListener {
                audioTimeView.text = DEFAULT_TIME
            }
        }
    }

    private fun playPausePlayer() {
        val totalDuration = mediaPlayer.duration
        audioProgressBar.max = totalDuration
        if (!mediaPlayer.isPlaying) {
            isPlaying = true
            mediaPlayer.start()
            audioTimer.start()
            playAudioButton.apply {
                setIconResource(R.drawable.round_pause_24)
                iconTint = ContextCompat.getColorStateList(context, R.color.flag_green)
                strokeColor = ContextCompat.getColorStateList(context, R.color.flag_green)
            }
        } else {
            audioTimer.stop()
            isPlaying = false
            mediaPlayer.pause()
            playAudioButton.apply {
                setIconResource(R.drawable.round_play_arrow_24)
                iconTint = ContextCompat.getColorStateList(context, R.color.flag_red)
                strokeColor = ContextCompat.getColorStateList(context, R.color.flag_red)
            }
        }
        rewindAudioButton.setOnClickListener {
            mediaPlayer.seekTo(mediaPlayer.currentPosition - JUMP_VALUE)
            audioProgressBar.progress -= JUMP_VALUE
            mediaPlayer.currentPosition
            audioTimer.start(mediaPlayer.currentPosition.toLong())
        }
        forwardAudioButton.setOnClickListener {
            audioTimer.start(mediaPlayer.currentPosition.toLong())
            mediaPlayer.seekTo(mediaPlayer.currentPosition + JUMP_VALUE)
            audioProgressBar.progress += JUMP_VALUE
        }

        mediaPlayer.setOnCompletionListener {
            audioTimer.stop()
            audioProgressBar.progress = 0
            playAudioButton.apply {
                iconTint = ContextCompat.getColorStateList(context, R.color.flag_green)
                strokeColor = ContextCompat.getColorStateList(context, R.color.flag_green)
                setIconResource(R.drawable.round_play_arrow_24)
            }
            audioTimeView.text = DEFAULT_TIME
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // do nothing
    }

    private fun startRecording(context: Context, audioPath: String) {
        audioRecorder.startRecording(context, audioPath)
        recordButton.apply {
            iconTint = ContextCompat.getColorStateList(context, R.color.flag_green)
            strokeColor = ContextCompat.getColorStateList(context, R.color.flag_green)
            setIconResource(R.drawable.round_pause_24)
        }
        isRecording = true
        saveButton.isEnabled = true
        isPaused = false
        isCleared = false
        audioTimer.start()
        cancelAudioRecordingButton.isEnabled = true
    }

    private fun saveRecording() {
        mField.audioPath = tempAudioPath
        mField.hasTemporaryMedia = true
    }

    private fun stopAndSaveRecording() {
        audioTimer.stop()
        audioRecorder.stopRecording()
        isPaused = false
        isRecording = false
        saveButton.isEnabled = false
        cancelAudioRecordingButton.isEnabled = false
        audioTimeView.text = DEFAULT_TIME
        audioWaveform.clear()
        saveRecording()
    }

    private fun pauseRecorder() {
        audioRecorder.pause()
        isPaused = true
        saveRecording()
        recordButton.setIconResource(R.drawable.ic_record)
        audioTimer.pause()
    }

    private fun resumeRecording() {
        audioRecorder.resume()
        isPaused = false
        audioTimer.start()
        recordButton.setIconResource(R.drawable.round_pause_24)
    }

    private fun clearRecording() {
        audioTimer.stop()
        recordButton.apply {
            iconTint = ContextCompat.getColorStateList(context, R.color.flag_red)
            strokeColor = ContextCompat.getColorStateList(context, R.color.flag_red)
            setIconResource(R.drawable.ic_record)
        }
        cancelAudioRecordingButton.isEnabled = false
        audioRecorder.stopRecording()
        tempAudioPath = generateTempAudioFile(context).also { tempAudioPath = it }
        audioTimeView.text = DEFAULT_TIME
        audioWaveform.clear()
        isPaused = false
        isRecording = false
        saveButton.isEnabled = false
    }

    override fun onDone() {
        // do nothing
    }

    override fun onFocusLost() {
        audioRecorder.release()
    }

    override fun onDestroy() {
        audioRecorder.release()
    }

    override fun onTimerTick(duration: String) {
        audioTimeView.text = duration
        if (isPlaying) {
            audioProgressBar.progress = mediaPlayer.currentPosition
        } else {
            audioProgressBar.progress = 0
        }
        try {
            val maxAmplitude = audioRecorder.maxAmplitude() / 10
            audioWaveform.addAmplitude(maxAmplitude.toFloat())
        } catch (e: IllegalStateException) {
            Timber.d("Audio recorder interrupted")
        }
    }

    companion object {
        const val DEFAULT_TIME = "00:00.0"
        const val JUMP_VALUE = 500
        fun generateTempAudioFile(context: Context): String? {
            val tempAudioPath: String? = try {
                val storingDirectory = context.cacheDir
                File.createTempFile("ankidroid_audiorec", ".3gp", storingDirectory).absolutePath
            } catch (e: IOException) {
                Timber.e(e, "Could not create temporary audio file.")
                null
            }
            return tempAudioPath
        }
    }
}
