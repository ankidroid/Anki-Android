/*
 *  Copyright (c) 2025 Siddhesh Jondhale <jondhale2004@gmail.com>
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.browser

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.utils.dp
import com.ichi2.utils.setPaddingRelative
import kotlinx.coroutines.launch
import timber.log.Timber

class ColumnSelectionDialogFragment : DialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()
    private val columnToReplace: ColumnHeading
        get() =
            requireNotNull(
                BundleCompat.getParcelable(requireArguments(), SELECTED_COLUMN, ColumnHeading::class.java),
            )

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val listView =
            ListView(requireContext()).apply {
                setPaddingRelative(
                    start = 0.dp,
                    end = 0.dp,
                    top = 24.dp,
                    bottom = 0.dp,
                )
            }

        val adapter =
            object : ArrayAdapter<ColumnWithSample>(
                requireContext(),
                R.layout.item_column_selection,
                mutableListOf(),
            ) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup,
                ): View {
                    val view = convertView ?: layoutInflater.inflate(R.layout.item_column_selection, parent, false)

                    val column = getItem(position)

                    view.findViewById<TextView>(R.id.column_title).text =
                        column?.label ?: getString(R.string.no_columns_available)

                    view.findViewById<TextView>(R.id.column_example).text =
                        if (column?.sampleValue.isNullOrBlank()) "-" else column.sampleValue

                    return view
                }
            }
        listView.adapter = adapter
        listView.divider = null

        // Fetch columns
        lifecycleScope.launch {
            val (_, availableColumns) = viewModel.previewColumnHeadings(viewModel.cardsOrNotes)
            adapter.clear()
            adapter.addAll(availableColumns)
            adapter.notifyDataSetChanged()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            if (selected == null || selected.label == getString(R.string.no_columns_available)) {
                Timber.d("Ignoring click on 'No Columns Available'")
                return@setOnItemClickListener
            }
            viewModel.updateSelectedColumn(columnToReplace, selected)
            dismissAllowingStateLoss()
        }

        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(listView)
            }

        return AlertDialog
            .Builder(requireActivity())
            .setTitle(getString(R.string.chane_browser_column))
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismissAllowingStateLoss() }
            .create()
    }

    companion object {
        const val TAG = "ColumnSelectionDialog"

        private const val SELECTED_COLUMN = "selected_column"

        fun newInstance(selectedColumn: ColumnHeading): ColumnSelectionDialogFragment =
            ColumnSelectionDialogFragment().apply {
                arguments = bundleOf(SELECTED_COLUMN to selectedColumn)
            }
    }
}
