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

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.utils.customView
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A DialogFragment for renaming flags through a RecyclerView.
 */
class FlagRenameDialog : DialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var flagAdapter: FlagAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = requireActivity().layoutInflater.inflate(R.layout.rename_flag_layout, null)
        val builder = AlertDialog.Builder(requireContext()).apply {
            customView(view = dialogView, 4, 4, 4, 4)
            title(R.string.rename_flag)
        }
        val dialog = builder.create()

        recyclerView = dialogView.findViewById(R.id.recyclerview_flags)
        setupRecyclerView()
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.invalidateOptionsMenu()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
    }

    private fun setupRecyclerView() = requireActivity().lifecycleScope.launch {
        val flagItems = createFlagList()
        flagAdapter = FlagAdapter(lifecycleScope = lifecycleScope)
        recyclerView.adapter = flagAdapter
        flagAdapter.submitList(flagItems)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private suspend fun createFlagList(): List<FlagItem> {
        Timber.d("Creating flag list")
        return Flag.queryDisplayNames()
            .filter { it.key != Flag.NONE }
            .map { (flag, displayName) ->
                FlagItem(
                    flag = flag,
                    title = displayName,
                    icon = flag.drawableRes
                )
            }
    }
}
