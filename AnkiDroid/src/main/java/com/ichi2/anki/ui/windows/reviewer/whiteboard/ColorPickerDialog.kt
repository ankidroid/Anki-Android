/*
 * Copyright (c) 2025 Divyansh Kushwaha <thedroiddiv@gmail.com>
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

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.Context
import com.ichi2.anki.R
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

/**
 * Shows a customizable color picker dialog with optional alpha and brightness controls.
 *
 * @receiver The Android context used to create the dialog.
 * @param initialColor The initial color to display in the picker as an `AARRGGBB` integer.
 * @param onColorSelected Callback invoked when the user confirms their color selection.
 *                        Receives the selected color as an `AARRGGBB` integer.
 *
 */
fun Context.showColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    ColorPickerDialog
        .Builder(this)
        .apply {
            colorPickerView.post { colorPickerView.setInitialColor(initialColor) }
            setPositiveButton(
                R.string.dialog_ok,
                object : ColorEnvelopeListener {
                    override fun onColorSelected(
                        envelope: ColorEnvelope?,
                        fromUser: Boolean,
                    ) {
                        envelope?.color?.let(onColorSelected)
                    }
                },
            )
            setTitle(R.string.choose_color)
            setNegativeButton(R.string.dialog_cancel, null)
            setBottomSpace(12) // 12Dp
        }.show()
}
