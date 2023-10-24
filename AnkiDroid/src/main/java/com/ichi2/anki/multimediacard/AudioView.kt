/*
 *  Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 *  Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 *  Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 *  Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
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

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.Permissions.canRecordAudio
import timber.log.Timber
import java.io.File
import java.io.IOException

// Not designed for visual editing
@SuppressLint("ViewConstructor")
class AudioView private constructor(context: Context, resPlay: Int, resPause: Int, resStop: Int, audioPath: String) : LinearLayout(context) {
    val audioPath: String?
    protected var playPause: PlayPauseButton?
    protected var stop: StopButton?
    protected var record: RecordButton? = null
    private var mAudioRecorder = AudioRecorder()
    private var mPlayer = AudioPlayer()
    private var mOnRecordingFinishEventListener: OnRecordingFinishEventListener? = null

    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var status = Status.IDLE
        private set
    private val mResPlayImage: Int
    private val mResPauseImage: Int
    private val mResStopImage: Int
    private var mResRecordImage = 0
    private var mResRecordStopImage = 0
    private val mContext: Context

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    enum class Status {
        IDLE, // Default initial state
        INITIALIZED, // When datasource has been set
        PLAYING, PAUSED, STOPPED, // The different possible states once playing

        // has started
        RECORDING // The recorder being played status
    }

    private fun gtxt(id: Int): String {
        return mContext.getText(id).toString()
    }

    private constructor(
        context: Context,
        resPlay: Int,
        resPause: Int,
        resStop: Int,
        resRecord: Int,
        resRecordStop: Int,
        audioPath: String
    ) : this(context, resPlay, resPause, resStop, audioPath) {
        mResRecordImage = resRecord
        mResRecordStopImage = resRecordStop
        this.orientation = HORIZONTAL
        this.gravity = Gravity.CENTER
        record = RecordButton(context)
        addView(record, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setOnRecordingFinishEventListener(listener: OnRecordingFinishEventListener?) {
        mOnRecordingFinishEventListener = listener
    }

    fun notifyPlay() {
        playPause!!.update()
        stop!!.update()
        if (record != null) {
            record!!.update()
        }
    }

    fun notifyStop() {
        // Send state change signal to all buttons
        playPause!!.update()
        stop!!.update()
        if (record != null) {
            record!!.update()
        }
    }

    fun notifyPause() {
        playPause!!.update()
        stop!!.update()
        if (record != null) {
            record!!.update()
        }
    }

    fun notifyRecord() {
        playPause!!.update()
        stop!!.update()
        if (record != null) {
            record!!.update()
        }
    }

    fun notifyStopRecord() {
        if (status == Status.RECORDING) {
            try {
                mAudioRecorder.stopRecording()
            } catch (e: RuntimeException) {
                Timber.i(e, "Recording stop failed, this happens if stop was hit immediately after start")
                showSnackbar(R.string.multimedia_editor_audio_view_recording_failed, Snackbar.LENGTH_SHORT)
            }
            status = Status.IDLE
            if (mOnRecordingFinishEventListener != null) {
                mOnRecordingFinishEventListener!!.onRecordingFinish(this@AudioView)
            }
        }
        playPause!!.update()
        stop!!.update()
        if (record != null) {
            record!!.update()
        }
    }

    fun notifyReleaseRecorder() {
        mAudioRecorder.release()
    }

    /** Stops playing and records  */
    fun toggleRecord() {
        stopPlaying()
        if (record != null) {
            record!!.callOnClick()
        }
    }

    /** Stops recording and presses the play button  */
    fun togglePlay() {
        // cancelling recording is done via pressing the record button again
        stopRecording()
        if (playPause != null) {
            playPause!!.callOnClick()
        }
    }

    /** If recording is occurring, stop it  */
    private fun stopRecording() {
        if (status == Status.RECORDING && record != null) {
            record!!.callOnClick()
        }
    }

    /** If playing, stop it  */
    protected fun stopPlaying() {
        // The stop button only applies to the player, and does nothing if no action is needed
        if (stop != null) {
            stop!!.callOnClick()
        }
    }

    protected inner class PlayPauseButton(context: Context?) : AppCompatImageButton(context!!) {
        private val mOnClickListener: OnClickListener = object : OnClickListener {
            override fun onClick(v: View) {
                if (audioPath == null) {
                    return
                }
                when (status) {
                    Status.IDLE -> try {
                        mPlayer.play(audioPath)
                        setImageResource(mResPauseImage)
                        status = Status.PLAYING
                        notifyPlay()
                    } catch (e: Exception) {
                        Timber.e(e)
                        showSnackbar(R.string.multimedia_editor_audio_view_playing_failed, Snackbar.LENGTH_SHORT)
                        status = Status.IDLE
                    }
                    Status.PAUSED -> {
                        // -> Play, continue playing
                        status = Status.PLAYING
                        setImageResource(mResPauseImage)
                        mPlayer.start()
                        notifyPlay()
                    }
                    Status.STOPPED -> {
                        // -> Play, start from beginning
                        status = Status.PLAYING
                        setImageResource(mResPauseImage)
                        mPlayer.stop()
                        mPlayer.start()
                        notifyPlay()
                    }
                    Status.PLAYING -> {
                        setImageResource(mResPlayImage)
                        mPlayer.pause()
                        status = Status.PAUSED
                        notifyPause()
                    }
                    Status.RECORDING -> {
                    }
                    else -> {
                    }
                }
            }
        }

        fun update() {
            isEnabled = when (status) {
                Status.IDLE, Status.STOPPED -> {
                    setImageResource(mResPlayImage)
                    true
                }
                Status.RECORDING -> false
                else -> true
            }
        }

        init {
            setImageResource(mResPlayImage)
            setOnClickListener(mOnClickListener)
        }
    }

    protected inner class StopButton(context: Context?) : AppCompatImageButton(context!!) {
        private val mOnClickListener = OnClickListener {
            when (status) {
                Status.PAUSED, Status.PLAYING -> {
                    mPlayer.stop()
                    status = Status.STOPPED
                    notifyStop()
                }
                Status.IDLE, Status.STOPPED, Status.RECORDING, Status.INITIALIZED -> {
                }
            }
        }

        fun update() {
            isEnabled = status != Status.RECORDING
            // It doesn't need to update itself on any other state changes
        }

        init {
            setImageResource(mResStopImage)
            setOnClickListener(mOnClickListener)
        }
    }

    protected inner class RecordButton(context: Context?) : AppCompatImageButton(context!!) {
        private val mOnClickListener: OnClickListener = object : OnClickListener {
            override fun onClick(v: View) {
                // Since mAudioPath is not compulsory, we check if it exists
                if (audioPath == null) {
                    return
                }

                // We can get to this screen without permissions through the "Pronunciation" feature.
                if (!canRecordAudio(mContext)) {
                    Timber.w("Audio recording permission denied.")
                    showSnackbar(R.string.multimedia_editor_audio_permission_denied, Snackbar.LENGTH_SHORT)
                    return
                }
                when (status) {
                    Status.IDLE, Status.STOPPED -> {
                        try {
                            mAudioRecorder.startRecording(mContext, audioPath)
                        } catch (e: Exception) {
                            // either output file failed or codec didn't work, in any case fail out
                            Timber.e("RecordButton.onClick() :: error recording to %s\n%s", audioPath, e.message)
                            showSnackbar(R.string.multimedia_editor_audio_view_recording_failed, Snackbar.LENGTH_SHORT)
                            status = Status.STOPPED
                        }
                        status = Status.RECORDING
                        setImageResource(mResRecordImage)
                        notifyRecord()
                    }
                    Status.RECORDING -> {
                        setImageResource(mResRecordStopImage)
                        notifyStopRecord()
                    }
                    else -> {
                    }
                }
            }
        }

        fun update() {
            isEnabled = when (status) {
                Status.PLAYING, Status.PAUSED -> false
                else -> true
            }
        }

        init {
            setImageResource(mResRecordStopImage)
            setOnClickListener(mOnClickListener)
        }
    }

    interface OnRecordingFinishEventListener {
        fun onRecordingFinish(v: View)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun setRecorder(recorder: AudioRecorder) {
        mAudioRecorder = recorder
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun setPlayer(player: AudioPlayer) {
        mPlayer = player
    }

    companion object {
        fun createRecorderInstance(
            context: Context,
            resPlay: Int,
            resPause: Int,
            resStop: Int,
            resRecord: Int,
            resRecordStop: Int,
            audioPath: String
        ): AudioView? {
            return try {
                AudioView(context, resPlay, resPause, resStop, resRecord, resRecordStop, audioPath)
            } catch (e: Exception) {
                Timber.e(e)
                CrashReportService.sendExceptionReport(e, "Unable to create recorder tool bar")
                showThemedToast(
                    context,
                    context.getText(R.string.multimedia_editor_audio_view_create_failed).toString(),
                    true
                )
                null
            }
        }

        fun generateTempAudioFile(context: Context): String? {
            val tempAudioPath: String?
            tempAudioPath = try {
                val storingDirectory = context.cacheDir
                File.createTempFile("ankidroid_audiorec", ".3gp", storingDirectory).absolutePath
            } catch (e: IOException) {
                Timber.e(e, "Could not create temporary audio file.")
                null
            }
            return tempAudioPath
        }
    }

    init {
        mPlayer.onStoppingListener = { status = Status.STOPPED }
        mPlayer.onStoppedListener = { notifyStop() }
        mAudioRecorder.setOnRecordingInitializedHandler { status = Status.INITIALIZED }
        mContext = context
        mResPlayImage = resPlay
        mResPauseImage = resPause
        mResStopImage = resStop
        this.audioPath = audioPath
        this.orientation = HORIZONTAL
        playPause = PlayPauseButton(context)
        addView(playPause, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        stop = StopButton(context)
        addView(stop, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }
}
