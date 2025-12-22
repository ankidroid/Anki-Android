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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.audio.AudioWaveform

/**
 * The default formatted time string displayed when the recorder is in an idle state.
 * * This represents a zero-duration timestamp in the format `HH:mm:ss`.
 */
const val DEFAULT_RECORDING_TIME = "00:00:00"

/**
 * A custom [LinearLayout] that provides a complete user interface for recording audio.
 *
 * This view encapsulates the recording controls (Start/Pause, Save, Cancel), a timer display,
 * and a visual waveform. It is designed to work in tandem with [AudioRecorderViewModel]
 * to handle recording logic and state persistence.
 *
 * @param context The Context the view is running in, through which it can access the current theme, resources, etc.
 * @param attrs The attributes of the XML tag that is inflating the view.
 * @param defStyleAttr An attribute in the current theme that contains a reference to a style resource
 * that supplies default values for the view.
 */
class AudioRecorderView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private var viewModel: AudioRecorderViewModel? = null

        private val audioTimeView: TextView
        private val recordButton: MaterialButton
        private val saveButton: MaterialButton
        private val cancelButton: MaterialButton
        private val waveformView: AudioWaveform

        init {
            LayoutInflater.from(context).inflate(R.layout.audio_recorder_view, this, true)

            recordButton = findViewById(R.id.action_start_recording)
            saveButton = findViewById(R.id.action_save_recording)
            cancelButton = findViewById(R.id.action_cancel_recording)
            waveformView = findViewById(R.id.audio_waveform_view)
            audioTimeView = findViewById(R.id.audio_time_track)

            audioTimeView.text = DEFAULT_RECORDING_TIME

            updateUiForState(RecorderState.Idle)
            setupClickListeners()
        }

        /**
         * Connects the view to its [AudioRecorderViewModel] and begins observing state changes.
         * * This method ensures that the UI stays in sync with the recording state and that
         * user interactions are forwarded to the ViewModel. This should typically be called
         * once in `onViewCreated` or `onCreate`.
         *
         * @param viewModel The ViewModel handling the recording logic and state.
         * @param lifecycleOwner The LifecycleOwner (Activity or Fragment) used to manage observations.
         */
        fun attach(
            viewModel: AudioRecorderViewModel,
            lifecycleOwner: LifecycleOwner,
        ) {
            if (this.viewModel != null) return

            this.viewModel = viewModel
            observeViewModel(lifecycleOwner)
        }

        private fun setupClickListeners() {
            recordButton.setOnClickListener { viewModel?.handleTapRecord() }
            saveButton.setOnClickListener { viewModel?.handleSave() }
            cancelButton.setOnClickListener { viewModel?.handleCancel() }
        }

        private fun observeViewModel(owner: LifecycleOwner) {
            val vm = viewModel ?: return
            // TODO: observe the states
        }

        private fun updateUiForState(newState: RecorderState?) {
            val state = newState ?: RecorderState.Idle

            if (state == RecorderState.Idle) {
                waveformView.clear()
            }

            when (state) {
                RecorderState.Idle -> {
                    audioTimeView.text = DEFAULT_RECORDING_TIME
                    recordButton.setIconResource(R.drawable.ic_record)
                    setButtonsEnabled(save = false, cancel = false)
                }
                RecorderState.Recording -> {
                    recordButton.setIconResource(R.drawable.round_pause_24)
                    setButtonsEnabled(save = true, cancel = true)
                }
                RecorderState.Paused -> {
                    recordButton.setIconResource(R.drawable.ic_record)
                    setButtonsEnabled(save = true, cancel = true)
                }
            }
        }

        private fun setButtonsEnabled(
            save: Boolean,
            cancel: Boolean,
        ) {
            recordButton.isEnabled = true
            saveButton.isEnabled = save
            cancelButton.isEnabled = cancel
        }
    }
