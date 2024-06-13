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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.OPEN_CAMERA
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.OPEN_DRAWING
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_FILE
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_RECORDING
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_IMAGE_FILE
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_VIDEO_FILE
import com.ichi2.annotations.NeedsTest

/**
 * A BottomSheetDialogFragment class that provides options for selecting multimedia actions.
 */
@NeedsTest("Test to ensure correct option is selected")
class MultimediaBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MultimediaViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottomsheet_multimedia, container, false)

        /** setup a click on the listener to emit [MultimediaViewModel.multimediaAction] */
        fun setupListener(@IdRes id: Int, action: MultimediaAction) =
            view.findViewById<LinearLayout>(id).setOnClickListener {
                viewModel.setMultimediaAction(action)
                dismiss()
            }

        setupListener(R.id.multimedia_action_image, SELECT_IMAGE_FILE)
        setupListener(R.id.multimedia_action_audio, SELECT_AUDIO_FILE)
        setupListener(R.id.multimedia_action_drawing, OPEN_DRAWING)
        setupListener(R.id.multimedia_action_recording, SELECT_AUDIO_RECORDING)
        setupListener(R.id.multimedia_action_video, SELECT_VIDEO_FILE)
        setupListener(R.id.multimedia_action_camera, OPEN_CAMERA)

        return view
    }

    override fun onStart() {
        super.onStart()
        BottomSheetBehavior.from(requireView().parent as View).state = BottomSheetBehavior.STATE_EXPANDED
    }

    /**
     * An enum representing the different actions available for multimedia selection within a multimedia note.
     *
     * This enum defines the possible actions a user can take when interacting with multimedia fields in a multimedia note.
     * These actions typically trigger UI updates or functionality related to adding or manipulating multimedia content.
     */
    enum class MultimediaAction {
        SELECT_IMAGE_FILE,
        SELECT_AUDIO_FILE,
        OPEN_DRAWING,
        SELECT_AUDIO_RECORDING,
        SELECT_VIDEO_FILE,
        OPEN_CAMERA
    }
}
