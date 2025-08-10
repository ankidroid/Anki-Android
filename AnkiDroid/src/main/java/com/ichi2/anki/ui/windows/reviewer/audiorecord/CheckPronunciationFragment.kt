/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.utils.show
import kotlinx.coroutines.launch

/**
 * Integrates [AudioRecordView] with [AudioPlayView] to play the recorded audios.
 */
class CheckPronunciationFragment : Fragment(R.layout.check_pronunciation_fragment) {
    private val viewModel: CheckPronunciationViewModel by viewModels()
    private val studyScreenViewModel: ReviewerViewModel by viewModels({ requireParentFragment() })

    private lateinit var playView: AudioPlayView
    private lateinit var recordView: AudioRecordView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                AlertDialog.Builder(requireContext()).show {
                    setTitle(R.string.permission_denied)
                    setMessage(R.string.recording_permission_denied_message)
                    setPositiveButton(R.string.dialog_ok) { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", requireContext().packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                    setNegativeButton(R.string.dialog_cancel, null)
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        playView = view.findViewById(R.id.audio_play_view)
        recordView = view.findViewById(R.id.audio_record_view)

        setupViewListeners()
        observeViewModel()
        observeStudyScreenViewModel()
    }

    override fun onPause() {
        super.onPause()
        if (requireActivity().isChangingConfigurations) {
            return
        }
        viewModel.resetAll()
        recordView.forceReset()
    }

    private fun setupViewListeners() {
        playView.setButtonPressListener(
            object : AudioPlayView.ButtonPressListener {
                override fun onPlayButtonPressed() {
                    viewModel.onPlayOrReplay()
                }

                override fun onCancelButtonPressed() {
                    viewModel.onCancelPlayback()
                }
            },
        )

        recordView.setRecordingListener(
            object : AudioRecordView.RecordingListener {
                override fun onRecordingPermissionRequired() {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                override fun onRecordingStarted() {
                    viewModel.onRecordingStarted()
                }

                override fun onRecordingCanceled() {
                    viewModel.onRecordingCancelled()
                }

                override fun onRecordingCompleted() {
                    viewModel.onRecordingCompleted()
                }
            },
        )
    }

    private fun observeViewModel() {
        viewModel.isPlaybackVisibleFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isVisible ->
            playView.isVisible = isVisible
            recordView.setRecordDisplayVisibility(!isVisible)
        }
        viewModel.playbackProgressFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { progress ->
                playView.setPlaybackProgress(progress)
            }
        viewModel.playbackProgressBarMaxFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { max ->
                playView.setPlaybackProgressBarMax(max)
            }
        viewModel.playIconFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { iconRes ->
            playView.changePlayIcon(iconRes)
        }
        viewModel.replayFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            playView.rotateReplayIcon()
        }
    }

    private fun observeStudyScreenViewModel() {
        studyScreenViewModel.voiceRecorderEnabledFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { isEnabled ->
                if (!isEnabled) {
                    viewModel.resetAll()
                    recordView.forceReset()
                }
            }
        studyScreenViewModel.replayVoiceFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) {
                viewModel.onPlayOrReplay()
            }
        studyScreenViewModel.onShowQuestionFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { showingAnswer ->
            playView.isVisible = false
            viewModel.onCancelPlayback()
            recordView.setRecordDisplayVisibility(true)
        }
    }
}
