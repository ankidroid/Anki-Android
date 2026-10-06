// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.databinding.FragmentAudioRecordingBinding
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import com.ichi2.utils.FileUtil
import com.ichi2.utils.Permissions
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber

class AudioRecordingFragment : MultimediaFragment(R.layout.fragment_audio_recording) {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val binding by viewBinding(FragmentAudioRecordingBinding::bind)

    override val title: String
        get() = resources.getString(CommonString.multimedia_editor_field_editing_audio)

    private var audioRecordingController: AudioRecordingController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-media")
        if (ankiCacheDirectory == null) {
            Timber.e("createUI() failed to get cache directory")
            showErrorDialog(errorMessage = resources.getString(CommonString.multimedia_editor_failed))
            return
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (view == null) return@registerForActivityResult
            if (isGranted) {
                Timber.d("Audio permission granted")
                initializeAudioRecorder()
                setupDoneButton()
            } else {
                Timber.d("Audio permission denied")
                showErrorDialog(resources.getString(CommonString.multimedia_editor_audio_permission_refused))
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (!hasMicPermission()) {
            return
        }

        initializeAudioRecorder()
        setupDoneButton()
    }

    private fun hasMicPermission(): Boolean {
        if (!Permissions.canRecordAudio(requireContext())) {
            Timber.i("Requesting Audio Permissions")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        return true
    }

    private fun setupDoneButton() {
        lifecycleScope.launch {
            viewModel.currentMultimediaPath.collect { path ->
                binding.actionDone.isEnabled = path != null
            }
        }
        binding.actionDone.setOnClickListener {
            Timber.d("AudioRecordingFragment:: Done button pressed")
            if (viewModel.selectedMediaFileSize == 0L) {
                Timber.d("Audio length not valid")
                return@setOnClickListener
            }

            finishWithMedia()
        }
    }

    private fun initializeAudioRecorder() {
        if (audioRecordingController != null) return
        Timber.d("Initialising AudioRecordingController")
        try {
            audioRecordingController =
                AudioRecordingController(
                    context = requireActivity(),
                    linearLayout = binding.audioRecorderLayout,
                    viewModel = viewModel,
                    note = note,
                )
        } catch (e: Exception) {
            Timber.w(e, "unable to add the audio recorder to toolbar")
            CrashReportService.sendExceptionReport(e, "Unable to create recorder tool bar")
            showErrorDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecordingController?.onDestroy()
    }

    override fun onDetach() {
        super.onDetach()
        audioRecordingController?.onFocusLost()
    }

    companion object {
        fun getIntent(
            context: Context,
            multimediaExtra: MultimediaActivityExtra,
        ): Intent =
            MultimediaActivity.getIntent(
                context,
                AudioRecordingFragment::class,
                multimediaExtra,
            )
    }
}
