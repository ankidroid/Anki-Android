/*
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

package com.ichi2.anki.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ichi2.anki.R
import kotlinx.coroutines.runBlocking

open class BottomSheetFragment : BottomSheetDialogFragmentFix() {
    open val layoutResource = R.layout.bottom_sheet_list_without_filter
    open val filterHintResource: Int? = null

    protected lateinit var adapter: TemplatedTreeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = TemplatedTreeAdapter(requireContext())
        // TODO: This isn't good
        // In TagsSheetFragment, `layoutResource` depends on the number of tags, set in `onPrepareAdapter`
        runBlocking { onPrepareAdapter() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(layoutResource, container, false)

        val listRecyclerView = layout.findViewById<RecyclerView>(R.id.list)

        adapter.onItemCheckedListener = { _ -> onItemsSelected(adapter.checkedItemIds) }
        adapter.onItemClickedListener = { id ->
            val newSelection = when (id) {
                CLEAR_SEARCH_ID -> emptySet()
                // If the item is selected and tapped again, deselect it
                adapter.checkedItemIds.singleOrNull() -> emptySet()
                else -> setOf(id)
            }
            onItemsSelected(newSelection)
            dismiss()
        }

        listRecyclerView.adapter = adapter

        val filterEditText = layout.findViewById<EditText?>(R.id.filter)
        if (filterEditText != null) {
            val behavior = (dialog as BottomSheetDialog).behavior

            val clearFilterButton = layout.findViewById<ImageButton>(R.id.clear_filter)
            clearFilterButton.setOnClickListener { filterEditText.setText("") }

            filterEditText.addOnChangeListener { before, after ->
                onFilterInputChanged(after)

                when {
                    before.isEmpty() && after.isNotEmpty() ->
                        clearFilterButton.animate().rotation(90f).alpha(1f).start()
                    before.isNotEmpty() && after.isEmpty() ->
                        clearFilterButton.animate().rotation(-90f).alpha(0f).start()
                }
            }

            filterEditText.setOnFocusChangeListener { _, focus -> if (focus) behavior.state = STATE_EXPANDED }
            filterEditText.setOnClickListener { behavior.state = STATE_EXPANDED }

            filterHintResource?.let { filterEditText.hint = requireContext().getText(it) }
        }

        return layout
    }

    open suspend fun onPrepareAdapter() {}

    open fun onFilterInputChanged(filterInput: String) {}

    open fun onItemsSelected(ids: Set<Long>) {}
}

private fun EditText.addOnChangeListener(listener: (before: String, after: String) -> Unit) {
    var previousText = text?.toString() ?: ""

    addTextChangedListener(afterTextChanged = {
        val currentText = it?.toString() ?: ""
        listener(previousText, currentText)
        previousText = currentText
    })
}

const val CLEAR_SEARCH_ID = -123L
