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
import com.ichi2.anki.UIUtils
import com.ichi2.anki.multimediacard.AudioPlayer
import com.ichi2.anki.multimediacard.AudioRecorder
import com.ichi2.anki.multimediacard.fields.FieldControllerBase
import com.ichi2.anki.multimediacard.fields.IFieldController
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compat.CompatHelper
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.Permissions
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
    private val audioPlayer = AudioPlayer()

    private lateinit var recordButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var audioTimeView: TextView
    private lateinit var audioTimer: AudioTimer
    private lateinit var playAudioButton: MaterialButton
    private lateinit var forwardAudioButton: MaterialButton
    private lateinit var rewindAudioButton: MaterialButton
    private lateinit var audioWaveform: AudioWaveform
    private lateinit var audioProgressBar: LinearProgressIndicator
    private var isRecording = false
    private var isPaused = false
    private var isCleared = false
    private var isPlaying = false
    private lateinit var cancelAudioRecordingButton: MaterialButton
    private lateinit var discardRecordingButton: MaterialButton

    // wave layout takes up a lot of screen in HORIZONTAL layout so we need to hide it
    private var orientationEventListener: OrientationEventListener? = null

    override fun createUI(context: Context, layout: LinearLayout) {
        if (inEditField) {
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
        }

        val layoutInflater = LayoutInflater.from(context)
        val inflatedLayout =
            layoutInflater.inflate(R.layout.activity_audio_recording, null) as LinearLayout
        layout.addView(inflatedLayout, LinearLayout.LayoutParams.MATCH_PARENT)
        (context as Activity).window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val recordAudioButtonLayout: LinearLayout = layout.findViewById(R.id.record_buttons_layout)

        if (inEditField) {
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

            AudioRecordingController.context = context
        }
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
        discardRecordingButton = layout.findViewById(R.id.action_discard_recording)
        cancelAudioRecordingButton.isEnabled = false
        saveButton.isEnabled = false

        audioTimer = AudioTimer(this)
        recordButton.setOnClickListener {
            // We can get to this screen without permissions through the "Pronunciation" feature.
            if (!Permissions.canRecordAudio(context)) {
                Timber.w("Audio recording permission denied.")
                UIUtils.showThemedToast(
                    context,
                    context.resources.getString(R.string.multimedia_editor_audio_permission_denied),
                    true
                )
                return@setOnClickListener
            }
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

        discardRecordingButton.setOnClickListener {
            CompatHelper.compat.vibrate(context, 20)
            playAudioButtonLayout.visibility = View.GONE
            recordAudioButtonLayout.visibility = View.VISIBLE
            recordButton.apply {
                iconTint = ContextCompat.getColorStateList(context, R.color.flag_red)
                strokeColor = ContextCompat.getColorStateList(context, R.color.flag_red)
                setIconResource(R.drawable.ic_record)
            }
            cancelAudioRecordingButton.isEnabled = false
            audioTimeView.text = DEFAULT_TIME
            audioWaveform.clear()
            isPaused = false
            isRecording = false
            saveButton.isEnabled = false
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
        audioPlayer.prepareAudioPlayer(tempAudioPath!!, { time ->
            audioTimeView.text = time
        }, {
            audioTimer.stop()
            audioProgressBar.progress = 0
            playAudioButton.apply {
                iconTint = ContextCompat.getColorStateList(context, R.color.flag_red)
                strokeColor = ContextCompat.getColorStateList(context, R.color.flag_red)
                setIconResource(R.drawable.round_play_arrow_24)
            }
            audioTimeView.text = DEFAULT_TIME
        })
    }

    fun playPausePlayer() {
        val totalDuration = audioPlayer.duration()
        audioProgressBar.max = totalDuration
        if (!audioPlayer.isAudioPlaying()) {
            isPlaying = true
            audioPlayer.startPlayer()
            audioTimer.start()
            playAudioButton.apply {
                setIconResource(R.drawable.round_pause_24)
                iconTint = ContextCompat.getColorStateList(context, R.color.flag_green)
                strokeColor = ContextCompat.getColorStateList(context, R.color.flag_green)
            }
        } else {
            audioTimer.stop()
            isPlaying = false
            audioPlayer.pause()
            playAudioButton.apply {
                setIconResource(R.drawable.round_play_arrow_24)
                iconTint = ContextCompat.getColorStateList(context, R.color.flag_red)
                strokeColor = ContextCompat.getColorStateList(context, R.color.flag_red)
            }
        }
        rewindAudioButton.setOnClickListener {
            audioPlayer.audioSeekTo(audioPlayer.currentPosition() - JUMP_VALUE)
            audioProgressBar.progress -= JUMP_VALUE
            audioPlayer.currentPosition()
            audioTimer.start(audioPlayer.currentPosition().toLong())
        }
        forwardAudioButton.setOnClickListener {
            audioTimer.start(audioPlayer.currentPosition().toLong())
            audioPlayer.audioSeekTo(audioPlayer.currentPosition() + JUMP_VALUE)
            audioProgressBar.progress += JUMP_VALUE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // do nothing
    }

    private fun startRecording(context: Context, audioPath: String) {
        try {
            audioRecorder.startRecording(context, audioPath)
        } catch (e: Exception) {
            // either output file failed or codec didn't work, in any case fail out
            Timber.e("RecordButton.onClick() :: error recording to %s\n%s", audioPath, e.message)
            (context as Activity).showSnackbar(R.string.multimedia_editor_audio_view_recording_failed)
        }

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
        try {
            audioTimer.stop()
            audioRecorder.stopRecording()
        } catch (e: RuntimeException) {
            Timber.i(e, "Recording stop failed, this happens if stop was hit immediately after start")
            (context as Activity).showSnackbar(R.string.multimedia_editor_audio_view_recording_failed)
        }
        isPaused = false
        isRecording = false
        saveButton.isEnabled = false
        cancelAudioRecordingButton.isEnabled = false
        audioTimeView.text = DEFAULT_TIME
        audioWaveform.clear()
        if (inEditField) saveRecording()
    }

    fun toggleRecord() {
        startRecording(context, tempAudioPath!!)
    }

    fun toggleStopOnly() {
        try {
            audioRecorder.stopRecording()
        } catch (e: RuntimeException) {
            Timber.i(e, "Recording stop failed, this happens if stop was hit immediately after start")
            (context as Activity).showSnackbar(R.string.multimedia_editor_audio_view_recording_failed)
        }
        audioRecorder.stopRecording()
        isPaused = false
        isRecording = false
        saveButton.isEnabled = false
        cancelAudioRecordingButton.isEnabled = false
        audioTimeView.text = DEFAULT_TIME
        audioWaveform.clear()
    }

    private fun pauseRecorder() {
        audioRecorder.pause()
        isPaused = true
        if (inEditField) saveRecording()
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
            audioProgressBar.progress = audioPlayer.currentPosition()
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
        private var inEditField: Boolean = true
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

        fun setReviewerStatus(isReviewer: Boolean) {
            this.inEditField = isReviewer
        }

        var tempAudioPath: String? = null

        lateinit var context: Context
    }
}
