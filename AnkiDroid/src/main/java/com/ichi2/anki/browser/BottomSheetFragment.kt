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
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import kotlinx.coroutines.runBlocking

open class BottomSheetFragment : BottomSheetDialogFragmentFix() {
    open val layoutResource = R.layout.bottom_sheet_list_without_filter

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
            onItemsSelected(if (id == ALL_ITEMS_ID) emptySet() else setOf(id))
            dismiss()
        }

        listRecyclerView.adapter = adapter

        return layout
    }

    open suspend fun onPrepareAdapter() {}

    open fun onItemsSelected(ids: Set<Long>) {}
}

const val ALL_ITEMS_ID = -123L
