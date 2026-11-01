// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.noteeditor.AudioRecordingViewModel.Failure
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.openAppSettingsScreen
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.sqrt

class InlineAudioRecorderFragment : Fragment() {
    private val viewModel: AudioRecordingViewModel by viewModels()

    private var fieldIndex = 0
    private var fieldName by mutableStateOf("")
    private var timerSeconds by mutableLongStateOf(0L)
    private val levels = mutableStateListOf<Float?>()
    private var insetLeft by mutableIntStateOf(0)
    private var insetRight by mutableIntStateOf(0)
    private var pressStartedRecording = false

    private val noteEditor: NoteEditorFragment
        get() = requireParentFragment() as NoteEditorFragment

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) showPermissionDeniedDialog()
        }

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
                    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
                    val sideInsets =
                        with(LocalDensity.current) { PaddingValues.Absolute(left = insetLeft.toDp(), right = insetRight.toDp()) }
                    InlineAudioRecorder(
                        state = recorderState,
                        elapsedSeconds = timerSeconds,
                        levels = levels,
                        fieldName = fieldName,
                        onRecordPress = ::onRecordPressed,
                        onRecordRelease = ::onRecordReleased,
                        onRecordClick = ::onRecordClicked,
                        onSave = ::saveRecording,
                        onDiscard = ::discardRecording,
                        onInsert = ::insertRecording,
                        onClose = ::close,
                        contentPadding = sideInsets,
                    )
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recorderState.collect { state ->
                        view.keepScreenOn = state == RecorderState.Recording
                        if (state == RecorderState.Idle) {
                            timerSeconds = 0
                            levels.clear()
                        }
                    }
                }
                launch {
                    viewModel.elapsed
                        .map { it.inWholeSeconds }
                        .filter { it > 0 }
                        .distinctUntilChanged()
                        .collect { timerSeconds = it }
                }
                launch {
                    viewModel.amplitudes.collect { amplitude ->
                        levels += amplitude?.let { sqrt(it.toFloat() / MAX_AMPLITUDE).coerceAtMost(1f) }
                        if (levels.size > MAX_LEVELS) levels.removeAt(0)
                    }
                }
                launch {
                    viewModel.failure.filterNotNull().collect { failure ->
                        showFailure(failure)
                        viewModel.failureShown()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FIELD_INDEX, fieldIndex)
        outState.putString(STATE_FIELD_NAME, fieldName)
    }

    override fun onStop() {
        super.onStop()
        view?.keepScreenOn = false
        if (!requireActivity().isChangingConfigurations) {
            viewModel.interrupt()
        }
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

    private fun onRecordPressed() {
        Timber.i("'record' pressed")
        pressStartedRecording = toggleRecording()
    }

    private fun onRecordReleased(held: Boolean) {
        val startedByThisPress = pressStartedRecording
        pressStartedRecording = false
        if (!startedByThisPress || !held || viewModel.recorderState.value != RecorderState.Recording) return
        Timber.i("'record' released after holding")
        viewModel.stopRecording()
    }

    private fun onRecordClicked() {
        Timber.i("'record' clicked")
        toggleRecording()
    }

    private fun toggleRecording(): Boolean =
        when (viewModel.recorderState.value) {
            RecorderState.Idle -> startRecording()
            RecorderState.Recording -> {
                viewModel.pauseRecording()
                false
            }
            RecorderState.Paused -> {
                viewModel.resumeRecording()
                viewModel.recorderState.value == RecorderState.Recording
            }
            is RecorderState.Recorded -> false
        }

    private fun saveRecording() {
        Timber.i("'save recording' clicked")
        viewModel.stopRecording()
    }

    private fun startRecording(): Boolean {
        if (!Permissions.canRecordAudio(requireContext())) {
            Timber.i("Requesting the microphone permission")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        viewModel.startRecording()
        return viewModel.recorderState.value == RecorderState.Recording
    }

    private fun discardRecording() {
        Timber.i("'discard recording' clicked")
        viewModel.discard()
    }

    private fun insertRecording() {
        Timber.i("'insert recording' clicked")
        var inserted = false
        viewModel.consumeRecording { file ->
            inserted = true
            noteEditor.insertRecording(fieldIndex, file)
        }
        if (inserted) close()
    }

    private fun close() {
        Timber.i("Closing the inline audio recorder")
        parentFragmentManager.commit { remove(this@InlineAudioRecorderFragment) }
    }

    private fun showFailure(failure: Failure) {
        val message =
            when (failure) {
                Failure.RECORDING -> CommonString.multimedia_editor_audio_view_recording_failed
                Failure.PLAYBACK -> CommonString.multimedia_editor_audio_view_playing_failed
            }
        noteEditor.showSnackbar(message)
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext()).show {
            title(CommonString.permission_denied)
            message(CommonString.microphone_permission_denied_message)
            positiveButton(CommonString.dialog_ok) { openAppSettingsScreen() }
            negativeButton(CommonString.dialog_cancel)
        }
    }

    companion object {
        private const val ARG_FIELD_INDEX = "fieldIndex"
        private const val ARG_FIELD_NAME = "fieldName"
        private const val STATE_FIELD_INDEX = "fieldIndex"
        private const val STATE_FIELD_NAME = "fieldName"
        private const val MAX_AMPLITUDE = 32767f
        private const val MAX_LEVELS = 300

        fun newInstance(
            fieldIndex: Int,
            fieldName: String,
        ) = InlineAudioRecorderFragment().apply {
            arguments = bundleOf(ARG_FIELD_INDEX to fieldIndex, ARG_FIELD_NAME to fieldName)
        }
    }
}
