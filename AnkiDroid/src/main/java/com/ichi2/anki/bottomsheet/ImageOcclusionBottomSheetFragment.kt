/*
 * Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.databinding.ImageOcclusionOptionsLayoutBinding

class ImageOcclusionBottomSheetFragment : BottomSheetDialogFragment() {
    var listener: ImagePickerListener? = null

    interface ImagePickerListener {
        fun onCameraClicked()

        fun onGalleryClicked()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = ImageOcclusionOptionsLayoutBinding.inflate(inflater, container, false)

        binding.occlusionActionCamera.setOnClickListener {
            listener?.onCameraClicked()
            dismiss()
        }
        binding.occlusionActionGallery.setOnClickListener {
            listener?.onGalleryClicked()
            dismiss()
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
