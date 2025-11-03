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
package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.databinding.DrawingFragmentBinding
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardView
import com.ichi2.compat.CompatHelper
import com.ichi2.themes.Themes
import dev.androidbroadcast.vbpd.viewBinding

class DrawingFragment : Fragment(R.layout.drawing_fragment) {
    private val binding by viewBinding(DrawingFragmentBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.apply {
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_save -> onSaveDrawing()
                    else -> {}
                }
                true
            }
        }
    }

    private fun onSaveDrawing() {
        val fragment = childFragmentManager.findFragmentById(R.id.fragment_container) as? WhiteboardFragment
        val whiteboardView = fragment?.binding?.whiteboardView ?: return
        val imagePath = saveWhiteboard(whiteboardView)
        val result =
            Intent().apply {
                putExtra(IMAGE_PATH_KEY, imagePath)
            }
        // TODO don't depend on an Activity
        requireActivity().setResult(Activity.RESULT_OK, result)
        requireActivity().finish()
    }

    private fun saveWhiteboard(view: WhiteboardView): Uri {
        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)

        val backgroundColor =
            if (Themes.currentTheme.isNightMode) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        canvas.drawColor(backgroundColor)

        view.draw(canvas)

        val baseFileName = "Whiteboard" + getTimestamp(TimeManager.time)
        return CompatHelper.compat.saveImage(requireContext(), bitmap, baseFileName, "jpg", Bitmap.CompressFormat.JPEG, 95)
    }

    companion object {
        const val IMAGE_PATH_KEY = "path"

        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, DrawingFragment::class)
    }
}
