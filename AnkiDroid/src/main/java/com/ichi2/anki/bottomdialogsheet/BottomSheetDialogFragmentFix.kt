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

package com.ichi2.anki.bottomdialogsheet

import android.content.Context
import android.content.res.Configuration
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.UIUtils

/**
 * The provided BottomSheetDialogFragmentFix focuses on adjusting the peek height of the Bottom Sheet based on the
 * device's orientation. By detecting the landscape orientation and dynamically setting the peek
 * height to a specific value, the Bottom Sheet is displayed appropriately without any alignment
 * issues. The code is integrated within the BottomSheetDialogFragmentFix class, ensuring that
 * the fix is applied during device rotation and when the Bottom Sheet is first displayed.
 * Fixing: https://stackoverflow.com/questions/41591733/bottom-sheet-landscape-issue
 **/
open class BottomSheetDialogFragmentFix : BottomSheetDialogFragment() {
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixBottomSheetPeekHeight() // After device rotation
    }

    override fun onResume() {
        super.onResume()
        fixBottomSheetPeekHeight() // When showing for the first time
    }

    private fun fixBottomSheetPeekHeight() {
        (dialog as? BottomSheetDialog)?.behavior?.peekHeight =
            if (requireContext().isLandscape) {
                UIUtils.convertDpToPixel(512f, requireContext()).toInt()
            } else {
                BottomSheetBehavior.PEEK_HEIGHT_AUTO
            }
    }
}

private val Context.isLandscape
    get() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
