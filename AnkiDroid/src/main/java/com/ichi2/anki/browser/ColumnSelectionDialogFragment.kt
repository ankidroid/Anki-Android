/*
 *  Copyright (c) 2025 Siddhesh Jondhale <jondhale2004@gmail.com>
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.R
import com.ichi2.utils.create
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class ColumnSelectionDialogFragment : DialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()
    private val columnToReplace: ColumnWithSample
        get() =
            requireNotNull(
                BundleCompat.getParcelable(requireArguments(), "selected_column", ColumnWithSample::class.java),
            )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.fetchAvailableColumns(viewModel.cardsOrNotes)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val listView = ListView(requireContext())

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
                    val view =
                        convertView ?: LayoutInflater
                            .from(context)
                            .inflate(R.layout.item_column_selection, parent, false)

                    val column = getItem(position)

                    // Column Label
                    view.findViewById<TextView>(R.id.column_title).text =
                        column?.label ?: "No Columns Available"

                    // Column Example Value
                    view.findViewById<TextView>(R.id.column_example).text =
                        if (column?.sampleValue.isNullOrBlank()) "-" else column?.sampleValue

                    return view
                }
            }

        listView.adapter = adapter

        // Handle column selection from the list
        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)

            if (selected?.label == "No Columns Available") {
                Timber.e("Ignoring then click when there is not Columns Available")
                return@setOnItemClickListener
            }

            if (selected != null) {
                viewModel.updateSelectedColumn(columnToReplace, selected)
                dismissAllowingStateLoss()
            }
        }

        // Observe availableColumns and update ListView dynamically
        lifecycleScope.launch {
            viewModel.availableColumns.collectLatest { availableColumns ->
                Timber.d("Updating dialog with available columns")
                adapter.clear()

                if (availableColumns.isEmpty()) {
                    Timber.e("No available columns found")
                    adapter.add(ColumnWithSample("No Columns Available", CardBrowserColumn.QUESTION, null))
                } else {
                    adapter.addAll(availableColumns)
                }

                adapter.notifyDataSetChanged()
            }
        }

        return AlertDialog.Builder(requireActivity()).create {
            setTitle(("Change: " + columnToReplace.label) ?: "Default")
            setView(listView)
            setNegativeButton(android.R.string.cancel) { _, _ -> dismissAllowingStateLoss() }
        }
    }

    companion object {
        fun newInstance(selectedColumn: ColumnWithSample): ColumnSelectionDialogFragment =
            ColumnSelectionDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putParcelable("selected_column", selectedColumn)
                    }
            }

        fun CardBrowserViewModel.updateSelectedColumn(
            selectedColumn: ColumnWithSample,
            newColumn: ColumnWithSample,
        ) = viewModelScope.launch {
            val replacementKey = selectedColumn.columnType.ankiColumnKey
            val replacements =
                activeColumns.toMutableList().apply {
                    replaceAll { if (it.ankiColumnKey == replacementKey) newColumn.columnType else it }
                }
            updateActiveColumns(replacements, cardsOrNotes)
        }
    }
}
