// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ichi2.anki.CommonString
import com.ichi2.anki.noteeditor.AudioRecordingViewModel.Failure
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.themes.Themes
import com.ichi2.utils.listItems
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber
import java.io.File

class InlineAudioRecorderFragment : Fragment() {
    private val viewModel: AudioRecordingViewModel by viewModels()

    var fieldIndex = 0
        private set
    private var fieldName by mutableStateOf("")
    var isFieldKnown = true
        private set
    private var fieldNames = emptyList<String?>()
    private var fieldChooser: AlertDialog? = null
    private var insetLeft by mutableIntStateOf(0)
    private var insetRight by mutableIntStateOf(0)

    val hasRecording: Boolean
        get() = viewModel.recorderState.value != RecorderState.Idle

    internal val animated: Boolean
        get() = Themes.currentTheme != DayTheme.EINK && Themes.currentTheme != NightTheme.EINK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            fieldIndex = savedInstanceState.getInt(STATE_FIELD_INDEX)
            fieldName = savedInstanceState.getString(STATE_FIELD_NAME).orEmpty()
            isFieldKnown = savedInstanceState.getBoolean(STATE_FIELD_KNOWN)
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
                        animated = animated,
                        contentPadding = sideInsets,
                    )
                }
            }
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FIELD_INDEX, fieldIndex)
        outState.putString(STATE_FIELD_NAME, fieldName)
        outState.putBoolean(STATE_FIELD_KNOWN, isFieldKnown)
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
        isFieldKnown = true
    }

    fun forgetField() {
        Timber.i("The field of the recording is no longer known")
        isFieldKnown = false
    }

    fun setFieldNames(fieldNames: List<String?>) {
        this.fieldNames = fieldNames
    }

    fun onFieldsChanged(fieldNames: List<String?>) {
        if (fieldNames != this.fieldNames) fieldChooser?.dismiss()
        this.fieldNames = fieldNames
        val name = fieldNames.getOrNull(fieldIndex)
        when {
            name == fieldName -> return
            hasRecording -> forgetField()
            name == null -> {
                Timber.i("Field %d no longer exists", fieldIndex)
                close()
            }
            else -> fieldName = name
        }
    }

    private fun insert() {
        if (isFieldKnown) return insertRecording()
        val choices = fieldNames.withIndex().filter { it.value != null }
        fieldChooser =
            AlertDialog
                .Builder(requireContext())
                .show {
                    title(CommonString.insert_recording_into)
                    setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    listItems(choices.map { it.value!! }) { _, index ->
                        retarget(choices[index].index, choices[index].value!!)
                        insertRecording()
                    }
                }.also { it.cancelOnDestroy(viewLifecycleOwner) }
    }

    private fun insertRecording() {
        if (requireActivity().blocksTouches) return
        viewModel.consumeRecording { recording ->
            parentFragmentManager.setInlineRecordingResult(fieldIndex, recording)
            close()
        }
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
            arguments =
                Bundle().apply {
                    putInt(ARG_FIELD_INDEX, fieldIndex)
                    putString(ARG_FIELD_NAME, fieldName)
                }
        }
    }
}

private const val ARG_FIELD_INDEX = "fieldIndex"
private const val ARG_FIELD_NAME = "fieldName"
private const val STATE_FIELD_INDEX = "fieldIndex"
private const val STATE_FIELD_NAME = "fieldName"
private const val STATE_FIELD_KNOWN = "fieldKnown"
private const val REQUEST_INSERT_RECORDING = "insertInlineRecording"
private const val RESULT_FIELD_INDEX = "fieldIndex"
private const val RESULT_RECORDING = "recording"

private fun FragmentManager.setInlineRecordingResult(
    fieldIndex: Int,
    recording: File,
) = setFragmentResult(
    REQUEST_INSERT_RECORDING,
    Bundle().apply {
        putInt(RESULT_FIELD_INDEX, fieldIndex)
        putString(RESULT_RECORDING, recording.path)
    },
)

internal fun Dialog.cancelOnDestroy(owner: LifecycleOwner) {
    owner.lifecycle.addObserver(LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY && isShowing) cancel() })
}

fun Fragment.registerInlineRecordingHandler(onInsert: (fieldIndex: Int, recording: File) -> Unit) =
    childFragmentManager.setFragmentResultListener(REQUEST_INSERT_RECORDING, this) { _, bundle ->
        onInsert(bundle.getInt(RESULT_FIELD_INDEX), File(bundle.requireString(RESULT_RECORDING)))
    }
