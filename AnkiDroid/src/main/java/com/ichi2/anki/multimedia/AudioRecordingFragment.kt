/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.multimedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT_FIELD_INDEX
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.FileUtil
import com.ichi2.utils.Permissions
import kotlinx.coroutines.launch
import timber.log.Timber

class AudioRecordingFragment : MultimediaFragment(R.layout.fragment_audio_recording) {
    override val title: String
        get() = resources.getString(R.string.multimedia_editor_field_editing_audio)

    private var audioRecordingController: AudioRecordingController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-media")
        if (ankiCacheDirectory == null) {
            Timber.e("createUI() failed to get cache directory")
            showErrorDialog(errorMessage = resources.getString(R.string.multimedia_editor_failed))
            return
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Audio permission granted")
            initializeAudioRecorder()
            setupDoneButton()
        } else {
            Timber.d("Audio permission denied")
            showErrorDialog(resources.getString(R.string.multimedia_editor_audio_permission_refused))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

    @NeedsTest("Done button is enabled only when the length is not null")
    private fun setupDoneButton() {
        lifecycleScope.launch {
            viewModel.currentMultimediaPath.collect { path ->
                view?.findViewById<MaterialButton>(R.id.action_done)?.isEnabled = path != null
            }
        }
        view?.findViewById<MaterialButton>(R.id.action_done)?.setOnClickListener {
            Timber.d("AudioRecordingFragment:: Done button pressed")
            if (viewModel.selectedMediaFileSize == 0L) {
                Timber.d("Audio length not valid")
                return@setOnClickListener
            }

            field.mediaPath = viewModel.currentMultimediaPath.value
            field.hasTemporaryMedia = true

            val resultData = Intent().apply {
                putExtra(MULTIMEDIA_RESULT, field)
                putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
            }
            requireActivity().setResult(AppCompatActivity.RESULT_OK, resultData)
            requireActivity().finish()
        }
    }

    @NeedsTest("AudioRecordingController is correctly initialized")
    private fun initializeAudioRecorder() {
        if (audioRecordingController != null) return
        Timber.d("Initialising AudioRecordingController")
        try {
            audioRecordingController = AudioRecordingController(
                context = requireActivity(),
                linearLayout = view?.findViewById(R.id.audio_recorder_layout)!!,
                viewModel = viewModel,
                note = note
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
            multimediaExtra: MultimediaActivityExtra
        ): Intent {
            return MultimediaActivity.getIntent(
                context,
                AudioRecordingFragment::class,
                multimediaExtra
            )
        }
    }
}
