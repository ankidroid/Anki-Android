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

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import timber.log.Timber

class ColumnSelectionDialogFragment : DialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()
    private var selectedColumn: ColumnWithSample? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedColumn =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireArguments().getParcelable("selected_column", ColumnWithSample::class.java)
            } else {
                @Suppress("DEPRECATION")
                requireArguments().getParcelable("selected_column")
            }

        viewModel.fetchAvailableColumns(viewModel.cardsOrNotes)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {

        val dialogBuilder = AlertDialog.Builder(requireActivity())
        val listView = ListView(requireContext())

        val adapter = object : ArrayAdapter<ColumnWithSample>(
            requireContext(),
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val context = parent.context
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_list_item_2, parent, false)

                val column = getItem(position)

                // Column Label
                view.findViewById<TextView>(android.R.id.text1).apply {
                    text = column?.label ?: "No Columns Available"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(context.getColor(android.R.color.black))
                }
                // Column SampleValue
                view.findViewById<TextView>(android.R.id.text2).apply {
                    text = if (column?.sampleValue.isNullOrBlank()) "-" else column?.sampleValue
                    textSize = 12f
                    setTextColor(context.getColor(android.R.color.black))
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxLines = 1
                }

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
                viewModel.updateSelectedColumn(selectedColumn, selected)
                dismissAllowingStateLoss()
            }
        }

        // Observe availableColumns and update ListView dynamically
        viewModel.availableColumns.observe(this) { availableColumns ->
            Timber.d("Updating dialog with available columns")
            adapter.clear()

            if (availableColumns.isEmpty()) {
                Timber.e("No available columns found")
                adapter.add(ColumnWithSample("No Columns Available", CardBrowserColumn.QUESTION, null)) // Show placeholder
            } else {
                adapter.addAll(availableColumns)
            }

            adapter.notifyDataSetChanged()
        }

        return dialogBuilder
            .setTitle(selectedColumn?.label ?: "Default")
            .setView(listView)
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismissAllowingStateLoss() }
            .create()
    }

    companion object {
        fun newInstance(selectedColumn: ColumnWithSample): ColumnSelectionDialogFragment =
            ColumnSelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("selected_column", selectedColumn)
                }
            }
    }
}