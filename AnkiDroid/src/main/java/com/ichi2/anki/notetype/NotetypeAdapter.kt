/****************************************************************************************
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.notetype

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R

private val notetypeNamesAndCountDiff =
    object : DiffUtil.ItemCallback<NotetypeItemUiState>() {
        override fun areItemsTheSame(
            oldItem: NotetypeItemUiState,
            newItem: NotetypeItemUiState,
        ): Boolean = oldItem.id == newItem.id && oldItem.name == newItem.name && oldItem.useCount == newItem.useCount

        override fun areContentsTheSame(
            oldItem: NotetypeItemUiState,
            newItem: NotetypeItemUiState,
        ): Boolean = oldItem.id == newItem.id && oldItem.name == newItem.name && oldItem.useCount == newItem.useCount
    }

internal class NotetypesAdapter(
    context: Context,
    private val onRename: (NotetypeItemUiState) -> Unit,
    private val onDelete: (NotetypeItemUiState) -> Unit,
) : ListAdapter<NotetypeItemUiState, NotetypeViewHolder>(notetypeNamesAndCountDiff) {
    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): NotetypeViewHolder =
        NotetypeViewHolder(
            rowView = layoutInflater.inflate(R.layout.item_manage_note_type, parent, false),
            onDelete = onDelete,
            onRename = onRename,
        )

    override fun onBindViewHolder(
        holder: NotetypeViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }
}

internal class NotetypeViewHolder(
    rowView: View,
    onRename: (NotetypeItemUiState) -> Unit,
    onDelete: (NotetypeItemUiState) -> Unit,
) : RecyclerView.ViewHolder(rowView) {
    val name: TextView = rowView.findViewById(R.id.note_name)
    val useCount: TextView = rowView.findViewById(R.id.note_use_count)
    private val btnDelete: Button = rowView.findViewById(R.id.note_delete)
    private val btnRename: Button = rowView.findViewById(R.id.note_rename)
    private val btnEditCards: Button = rowView.findViewById(R.id.note_edit_cards)
    private var notetypeItemUiState: NotetypeItemUiState? = null
    private val resources = rowView.context.resources

    init {
        rowView.setOnClickListener {
            notetypeItemUiState?.let { state ->
                state.onNavigateTo(
                    NotetypesDestination.Fields(
                        mapOf(
                            "title" to state.name,
                            "noteTypeID" to state.id,
                        ),
                    ),
                )
            }
        }
        btnEditCards.setOnClickListener {
            notetypeItemUiState?.let { state ->
                state.onNavigateTo(
                    NotetypesDestination.CardTemplateEditor(
                        mapOf("modelId" to state.id),
                    ),
                )
            }
        }
        btnDelete.setOnClickListener { notetypeItemUiState?.let(onDelete) }
        btnRename.setOnClickListener { notetypeItemUiState?.let(onRename) }
    }

    fun bind(notetypeItemUiState: NotetypeItemUiState) {
        this.notetypeItemUiState = notetypeItemUiState
        name.text = notetypeItemUiState.name
        useCount.text =
            resources.getQuantityString(
                R.plurals.model_browser_of_type,
                notetypeItemUiState.useCount,
                notetypeItemUiState.useCount,
            )
    }
}
