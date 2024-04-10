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
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.anki.R
import com.ichi2.anki.multimediacard.AudioRecorder
import com.ichi2.anki.multimediacard.fields.FieldControllerBase
import com.ichi2.anki.multimediacard.fields.IFieldController
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.elapsed
import com.ichi2.anki.utils.formatAsString
import com.ichi2.compat.CompatHelper
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.Permissions.canRecordAudio
import com.ichi2.utils.UiUtil
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.time.Duration

// TODO : stop audio time view flickering
class AudioRecordingController :
    FieldControllerBase(),
    IFieldController,
    AudioTimer.OnTimerTickListener,
    AudioTimer.OnAudioTickListener {
    private lateinit var audioRecorder: AudioRecorder

    /**
     * It's Nullable and that it is set only if a sound is playing or paused, otherwise it is null.
     */
    private var audioPlayer: MediaPlayer? = null

    private lateinit var recordButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var audioTimeView: TextView
    private lateinit var audioTimer: AudioTimer
    private lateinit var playAudioButton: MaterialButton
    private lateinit var forwardAudioButton: MaterialButton
    private lateinit var rewindAudioButton: MaterialButton
    private lateinit var audioWaveform: AudioWaveform
    private lateinit var audioProgressBar: LinearProgressIndicator
    lateinit var context: Context
    private var isCleared = false
    private var isPaused = false
    private var isPlaying = false
    private lateinit var cancelAudioRecordingButton: MaterialButton
    private lateinit var playAudioButtonLayout: LinearLayout
    private lateinit var recordAudioButtonLayout: LinearLayout
    private lateinit var discardRecordingButton: MaterialButton

    // wave layout takes up a lot of screen in HORIZONTAL layout so we need to hide it
    private var orientationEventListener: OrientationEventListener? = null

    override fun createUI(context: Context, layout: LinearLayout) {
        audioRecorder = AudioRecorder()
        if (inEditField) {
            val origAudioPath = this._field.audioPath
            var bExist = false
            if (origAudioPath != null) {
                val f = File(origAudioPath)
                if (f.exists()) {
                    tempAudioPath = f.absolutePath
                    bExist = true
                }
            }
            if (!bExist) {
                tempAudioPath = generateTempAudioFile(_activity)
            }
        }

        val layoutInflater = LayoutInflater.from(context)
        val inflatedLayout =
            layoutInflater.inflate(R.layout.activity_audio_recording, null) as LinearLayout
        layout.addView(inflatedLayout, LinearLayout.LayoutParams.MATCH_PARENT)
        (context as Activity).window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recordAudioButtonLayout = layout.findViewById(R.id.record_buttons_layout)
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
                for (i in 0 until _note.initialFieldCount) {
                    val field = _note.getInitialField(i)
                    FixedTextView(this).apply {
                        text = field?.text
                        textSize = 16f
                        setPadding(16, 0, 16, 24)
                        previewLayout.addView(this)
                    }
                    hasTextContents = hasTextContents or !field?.text.isNullOrBlank()
                }
                label.isVisible = hasTextContents
            }
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
        playAudioButtonLayout = layout.findViewById(R.id.play_buttons_layout)
        audioProgressBar = layout.findViewById(R.id.audio_progress_indicator)
        val audioFileView = layout.findViewById<ShapeableImageView>(R.id.audio_file_imageview)
        discardRecordingButton = layout.findViewById(R.id.action_discard_recording)
        cancelAudioRecordingButton.isEnabled = false
        saveButton.isEnabled = false

        saveButton.setIconResource(if (!inEditField) R.drawable.ic_done_white else R.drawable.ic_save_white)

        setUpMediaPlayer()

        audioTimer = AudioTimer(this, this)
        recordButton.setOnClickListener {
            controlAudioRecorder()
        }

        saveButton.setOnClickListener {
            isAudioRecordingSaved = false
            toggleSave()
        }

        playAudioButton.setOnClickListener {
            playPausePlayer()
        }

        cancelAudioRecordingButton.setOnClickListener {
            clearRecording()
        }

        discardRecordingButton.setOnClickListener {
            discardAudio()
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

        (context as? Activity)?.let { activity ->
            activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    // Not needed
                }

                override fun onActivityStarted(activity: Activity) {
                    // Not needed
                }

                override fun onActivityResumed(activity: Activity) {
                    // not needed
                }

                override fun onActivityPaused(activity: Activity) {
                    if (activity == context) {
                        onViewFocusChanged()
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    // Not needed
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                    // Not needed
                }

                override fun onActivityDestroyed(activity: Activity) {
                    // not needed
                }
            })
        }
    }

    private fun setUpMediaPlayer() {
        try {
            if (audioPlayer == null) {
                Timber.d("Creating media player for playback")
                audioPlayer = MediaPlayer()
            } else {
                Timber.d("Resetting media for playback")
                audioPlayer!!.reset()
            }
        } catch (e: IllegalStateException) {
            Timber.w("Media Player couldn't be reset or already reset", e)
        }
    }

    fun onViewFocusChanged() {
        if (isRecording || isPaused) {
            clearRecording()
        }
        if (isPlaying) {
            stopAudioPlayer()
        }
    }

    private fun discardAudio() {
        CompatHelper.compat.vibrate(context, 20)
        recordButton.apply {
            iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
            strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
            setIconResource(R.drawable.ic_record)
        }
        playAudioButton.apply {
            setIconResource(R.drawable.round_play_arrow_24)
            iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
            strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
        }
        cancelAudioRecordingButton.isEnabled = false
        tempAudioPath = generateTempAudioFile(context).also { tempAudioPath = it }
        audioTimeView.text = DEFAULT_TIME
        stopAudioPlayer()
    }

    private fun stopAudioPlayer() {
        audioWaveform.clear()
        isPaused = false
        isCleared = true
        isRecording = false
        try {
            audioTimer.stop()
            audioPlayer?.stop()
            audioPlayer?.release()
        } catch (e: Exception) {
            Timber.w(e)
        }
        audioTimer = AudioTimer(this, this)
        saveButton.isEnabled = false
        playAudioButtonLayout.visibility = View.GONE
        recordAudioButtonLayout.visibility = View.VISIBLE
    }

    private fun prepareAudioPlayer() {
        audioPlayer = MediaPlayer()
        audioPlayer?.apply {
            if (tempAudioPath != null) setDataSource(tempAudioPath)
            setOnPreparedListener {
                audioTimeView.text = DEFAULT_TIME
            }
            prepareAsync()
        }
    }

    fun toggleSave() {
        CompatHelper.compat.vibrate(context, 20)
        stopAndSaveRecording()
        playAudioButtonLayout.visibility = View.VISIBLE
        recordAudioButtonLayout.visibility = View.GONE
        // show this snackbar only in the edit field/multimedia activity
        if (inEditField) (context as Activity).showSnackbar(context.resources.getString(R.string.audio_saved))
        prepareAudioPlayer()
    }

    fun toggleToRecorder() {
        if (audioPlayer!!.isPlaying) {
            audioPlayer?.stop()
        }
        playAudioButtonLayout.visibility = View.GONE
        recordAudioButtonLayout.visibility = View.VISIBLE
        controlAudioRecorder()
    }

    private fun controlAudioRecorder() {
        if (!canRecordAudio(context)) {
            Timber.w("Audio recording permission denied.")
            showThemedToast(
                context,
                context.resources.getString(R.string.multimedia_editor_audio_permission_denied),
                true
            )
            return
        }
        when {
            isPaused -> resumeRecording()
            isRecording -> pauseRecorder()
            isCleared -> startRecording(context, tempAudioPath!!)
            else -> startRecording(context, tempAudioPath!!)
        }
        CompatHelper.compat.vibrate(context, 20)
    }

    fun playPausePlayer() {
        audioProgressBar.max = audioPlayer?.duration ?: 0
        if (!audioPlayer!!.isPlaying) {
            isPlaying = true
            try {
                audioPlayer!!.start()
            } catch (e: Exception) {
                Timber.w(e, "error starting audioPlayer")
                showThemedToast(context, context.resources.getString(R.string.multimedia_editor_audio_view_playing_failed), true)
            }
            audioTimer.start()
            rewindAudioButton.isEnabled = true
            forwardAudioButton.isEnabled = true
            playAudioButton.apply {
                setIconResource(R.drawable.round_pause_24)
                iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_green)
                strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_green)
            }
        } else {
            rewindAudioButton.isEnabled = false
            forwardAudioButton.isEnabled = false
            isPlaying = false
            audioTimer.pause()
            audioPlayer?.pause()
            playAudioButton.apply {
                setIconResource(R.drawable.round_play_arrow_24)
                iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
            }
        }
        val shortAudioDuration = 5000
        rewindAudioButton.setOnClickListener {
            val audioDuration = audioPlayer?.duration ?: 0
            if (audioDuration < shortAudioDuration) {
                audioPlayer?.seekTo(0)
                audioProgressBar.progress = 0
            } else {
                audioPlayer?.seekTo(audioPlayer!!.currentPosition - JUMP_VALUE)
                audioProgressBar.progress -= JUMP_VALUE
                audioTimer.start(audioPlayer!!.elapsed)
            }
        }
        forwardAudioButton.setOnClickListener {
            val audioDuration = audioPlayer?.duration ?: 0
            if (audioDuration < shortAudioDuration) {
                audioPlayer?.seekTo(audioDuration)
                audioProgressBar.progress = audioDuration
            } else {
                audioTimer.start(audioPlayer!!.elapsed)
                audioPlayer?.seekTo(audioPlayer!!.currentPosition + JUMP_VALUE)
                audioProgressBar.progress += JUMP_VALUE
            }
        }

        audioPlayer!!.setOnCompletionListener {
            audioTimer.stop()
            rewindAudioButton.isEnabled = false
            forwardAudioButton.isEnabled = false
            audioProgressBar.progress = 0
            playAudioButton.apply {
                iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                setIconResource(R.drawable.round_play_arrow_24)
            }
            audioTimeView.text = DEFAULT_TIME
        }
    }

    private fun startRecording(context: Context, audioPath: String) {
        try {
            audioRecorder.startRecording(context, audioPath)
            isRecording = true
            isPaused = false
            isCleared = false
            audioTimer.start()
            cancelAudioRecordingButton.isEnabled = true
            saveButton.isEnabled = true
            recordButton.setIconResource(R.drawable.round_pause_24)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
        }
    }

    private fun saveRecording() {
        _field.audioPath = tempAudioPath
        _field.hasTemporaryMedia = true
    }

    fun stopAndSaveRecording() {
        audioTimer.stop()
        try {
            audioRecorder.stopRecording()
        } catch (e: RuntimeException) {
            Timber.i(e, "Recording stop failed, this happens if stop was hit immediately after start")
            showThemedToast(context, context.resources.getString(R.string.multimedia_editor_audio_view_recording_failed), true)
        }
        isPaused = false
        isRecording = false
        saveButton.isEnabled = false
        cancelAudioRecordingButton.isEnabled = false
        audioTimeView.text = DEFAULT_TIME
        audioWaveform.clear()
        isAudioRecordingSaved = true
        // save recording only in the edit field not in the reviewer but save it temporarily
        if (inEditField) saveRecording()
    }

    private fun pauseRecorder() {
        audioRecorder.pause()
        isPaused = true
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
        CompatHelper.compat.vibrate(context, 20)
        isCleared = true
        audioTimer.stop()
        recordButton.setIconResource(R.drawable.ic_record)
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
        audioPlayer!!.release()
    }

    override fun onDestroy() {
        audioRecorder.release()
    }

    // when answer button is clicked in reviewer
    fun updateUIForNewCard() {
        try {
            if (isPlaying) {
                discardAudio()
            }
            if (isRecording || isPaused) {
                clearRecording()
            }
        } catch (e: Exception) {
            Timber.d("Unable to reset the audio recorder", e)
        }
    }

    override fun onTimerTick(duration: Duration) {
        if (isPlaying && !isRecording) {
            // This may remain at 0 for a few hundred ms while the audio player starts
            val elapsed = audioPlayer!!.elapsed
            audioProgressBar.progress = elapsed.inWholeMilliseconds.toInt()
            audioTimeView.text = elapsed.formatAsString()
        } else {
            audioTimeView.text = duration.formatAsString()
            audioProgressBar.progress = 0
        }
    }

    override fun onAudioTick() {
        try {
            if (isRecording) {
                val maxAmplitude = audioRecorder.maxAmplitude() / 10
                audioWaveform.addAmplitude(maxAmplitude.toFloat())
            }
        } catch (e: IllegalStateException) {
            Timber.d("Audio recorder interrupted")
        }
    }

    companion object {
        var isRecording = false
        var isAudioRecordingSaved = false
        private var inEditField: Boolean = true
        const val DEFAULT_TIME = "00:00.00"
        const val JUMP_VALUE = 500
        fun generateTempAudioFile(context: Context): String? {
            val tempAudioPath: String? = try {
                val storingDirectory = context.cacheDir
                File.createTempFile("ankidroid_audiorec", ".3gp", storingDirectory).absolutePath
            } catch (e: IOException) {
                Timber.w(e, "Could not create temporary audio file.")
                null
            }
            return tempAudioPath
        }

        fun setEditorStatus(isReviewer: Boolean) {
            this.inEditField = isReviewer
        }

        /** File of the temporary mic record  */
        var tempAudioPath: String? = null
    }
}
