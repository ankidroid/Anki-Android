/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.dialogs.mediacheck

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.viewmodel.MediaCheckViewModel
import kotlinx.coroutines.launch

class ConfirmMediaCheckDialog : DialogFragment() {
    private val viewModel: MediaCheckViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog
            .Builder(requireContext())
            .setTitle(getString(R.string.check_media_title))
            .setMessage(getString(R.string.check_media_warning))
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                lifecycleScope.launch {
                    viewModel.checkMedia()
                }
            }.setNegativeButton(R.string.dialog_cancel) { _, _ ->
                dismiss()
            }.create()
}
