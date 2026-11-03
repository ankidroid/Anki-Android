// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.ichi2.anki.CommonString
import com.ichi2.anki.noteeditor.AudioRecordingViewModel.Failure
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.themes.Themes
import timber.log.Timber
import java.io.File

class InlineAudioRecorderFragment : Fragment() {
    private val viewModel: AudioRecordingViewModel by viewModels()

    private var fieldIndex = 0
    private var fieldName by mutableStateOf("")
    private var insetLeft by mutableIntStateOf(0)
    private var insetRight by mutableIntStateOf(0)

    val hasRecording: Boolean
        get() = viewModel.recorderState.value != RecorderState.Idle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            fieldIndex = savedInstanceState.getInt(STATE_FIELD_INDEX)
            fieldName = savedInstanceState.getString(STATE_FIELD_NAME).orEmpty()
        } else {
            fieldIndex = requireArguments().getInt(ARG_FIELD_INDEX)
            fieldName = requireArguments().getString(ARG_FIELD_NAME).orEmpty()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val sideInsets =
                        with(LocalDensity.current) { PaddingValues.Absolute(left = insetLeft.toDp(), right = insetRight.toDp()) }
                    InlineAudioRecorder(
                        viewModel = viewModel,
                        fieldName = fieldName,
                        onInsert = ::insert,
                        onClose = ::close,
                        onFailure = ::showFailure,
                        animated = Themes.currentTheme != DayTheme.EINK,
                        contentPadding = sideInsets,
                    )
                }
            }
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FIELD_INDEX, fieldIndex)
        outState.putString(STATE_FIELD_NAME, fieldName)
    }

    fun setSideInsets(
        left: Int,
        right: Int,
    ) {
        insetLeft = left
        insetRight = right
    }

    fun retarget(
        fieldIndex: Int,
        fieldName: String,
    ) {
        Timber.i("Recording audio for field %d", fieldIndex)
        this.fieldIndex = fieldIndex
        this.fieldName = fieldName
    }

    fun onFieldsChanged(fieldNames: List<String>) {
        val name = fieldNames.getOrNull(fieldIndex)
        if (name == null) {
            Timber.i("Field %d no longer exists", fieldIndex)
            close()
            return
        }
        fieldName = name
    }

    private fun insert(recording: File) {
        parentFragmentManager.setInlineRecordingResult(fieldIndex, recording)
        close()
    }

    fun interrupt() = viewModel.interrupt()

    fun close() {
        Timber.i("Closing the inline audio recorder")
        parentFragmentManager.commit { remove(this@InlineAudioRecorderFragment) }
    }

    private fun showFailure(failure: Failure) {
        val message =
            when (failure) {
                Failure.RECORDING -> CommonString.multimedia_editor_audio_view_recording_failed
                Failure.PLAYBACK -> CommonString.multimedia_editor_audio_view_playing_failed
            }
        requireParentFragment().showSnackbar(message)
    }

    companion object {
        fun newInstance(
            fieldIndex: Int,
            fieldName: String,
        ) = InlineAudioRecorderFragment().apply {
            arguments = bundleOf(ARG_FIELD_INDEX to fieldIndex, ARG_FIELD_NAME to fieldName)
        }
    }
}

private const val ARG_FIELD_INDEX = "fieldIndex"
private const val ARG_FIELD_NAME = "fieldName"
private const val STATE_FIELD_INDEX = "fieldIndex"
private const val STATE_FIELD_NAME = "fieldName"
private const val REQUEST_INSERT_RECORDING = "insertInlineRecording"
private const val RESULT_FIELD_INDEX = "fieldIndex"
private const val RESULT_RECORDING = "recording"

private fun FragmentManager.setInlineRecordingResult(
    fieldIndex: Int,
    recording: File,
) = setFragmentResult(REQUEST_INSERT_RECORDING, bundleOf(RESULT_FIELD_INDEX to fieldIndex, RESULT_RECORDING to recording.path))

fun Fragment.registerInlineRecordingHandler(onInsert: (fieldIndex: Int, recording: File) -> Unit) =
    childFragmentManager.setFragmentResultListener(REQUEST_INSERT_RECORDING, this) { _, bundle ->
        onInsert(bundle.getInt(RESULT_FIELD_INDEX), File(bundle.requireString(RESULT_RECORDING)))
    }
