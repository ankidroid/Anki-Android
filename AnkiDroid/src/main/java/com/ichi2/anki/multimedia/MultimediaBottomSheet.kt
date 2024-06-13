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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R

/**
 * A BottomSheetDialogFragment class that provides options for selecting multimedia actions.
 */
class MultimediaBottomSheet : BottomSheetDialogFragment() {

    var multimediaClickListener: MultiMediaBottomSheetListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottomsheet_multimedia, container, false)

        view.findViewById<LinearLayout>(R.id.multimedia_action_image).setOnClickListener {
            multimediaClickListener?.onImageClicked()
            dismiss()
        }

        view.findViewById<LinearLayout>(R.id.multimedia_action_audio).setOnClickListener {
            multimediaClickListener?.onAudioClicked()
            dismiss()
        }

        view.findViewById<LinearLayout>(R.id.multimedia_action_drawing).setOnClickListener {
            multimediaClickListener?.onDrawingClicked()
            dismiss()
        }

        view.findViewById<LinearLayout>(R.id.multimedia_action_recording).setOnClickListener {
            multimediaClickListener?.onRecordingClicked()
            dismiss()
        }

        view.findViewById<LinearLayout>(R.id.multimedia_action_video).setOnClickListener {
            multimediaClickListener?.onVideoClicked()
            dismiss()
        }

        view.findViewById<LinearLayout>(R.id.multimedia_action_camera).setOnClickListener {
            multimediaClickListener?.onCameraClicked()
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    /**
     * An interface that defines callbacks for multimedia action clicks.
     *
     * You need to implement this interface in the activity or fragment that hosts this
     * BottomSheetDialogFragment to listen for user selections.
     */
    interface MultiMediaBottomSheetListener {
        fun onAudioClicked()

        fun onVideoClicked()

        fun onCameraClicked()

        fun onDrawingClicked()

        fun onRecordingClicked()

        fun onImageClicked()
    }
}
