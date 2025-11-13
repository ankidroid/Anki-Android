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
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R

private val notetypeNamesAndCountDiff =
    object : DiffUtil.ItemCallback<NoteTypeItemState>() {
        override fun areItemsTheSame(
            oldItem: NoteTypeItemState,
            newItem: NoteTypeItemState,
        ): Boolean = oldItem.id == newItem.id && oldItem.name == newItem.name && oldItem.useCount == newItem.useCount

        override fun areContentsTheSame(
            oldItem: NoteTypeItemState,
            newItem: NoteTypeItemState,
        ): Boolean = oldItem.id == newItem.id && oldItem.name == newItem.name && oldItem.useCount == newItem.useCount
    }

internal class NotetypesAdapter(
    context: Context,
    private val onItemClick: (NoteTypeItemState) -> Unit,
    private val onEditCards: (NoteTypeItemState) -> Unit,
    private val onRename: (NoteTypeItemState) -> Unit,
    private val onDelete: (NoteTypeItemState) -> Unit,
) : ListAdapter<NoteTypeItemState, NotetypeViewHolder>(notetypeNamesAndCountDiff) {
    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): NotetypeViewHolder =
        NotetypeViewHolder(
            rowView = layoutInflater.inflate(R.layout.item_manage_note_type, parent, false),
            onDelete = onDelete,
            onRename = onRename,
            onEditCards = onEditCards,
            onItemClick = onItemClick,
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
    onItemClick: (NoteTypeItemState) -> Unit,
    onEditCards: (NoteTypeItemState) -> Unit,
    onRename: (NoteTypeItemState) -> Unit,
    onDelete: (NoteTypeItemState) -> Unit,
) : RecyclerView.ViewHolder(rowView) {
    val name: TextView = rowView.findViewById(R.id.note_name)
    val useCount: TextView = rowView.findViewById(R.id.note_use_count)
    private val btnEditCards: MaterialButton = rowView.findViewById(R.id.note_edit_cards)
    private val btnMoreActions: MaterialButton = rowView.findViewById(R.id.btn_more)
    private var noteTypeItemState: NoteTypeItemState? = null
    private val resources = rowView.context.resources

    init {
        rowView.setOnClickListener { noteTypeItemState?.let(onItemClick) }
        btnEditCards.setOnClickListener { noteTypeItemState?.let(onEditCards) }
        btnMoreActions.setOnClickListener {
            PopupMenu(rowView.context, btnMoreActions)
                .apply {
                    inflate(R.menu.note_types_more_actions)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_rename -> noteTypeItemState?.let(onRename)
                            R.id.action_delete -> noteTypeItemState?.let(onDelete)
                            else -> error("Unexpected menu item!")
                        }
                        true
                    }
                }.show()
        }
    }

    fun bind(state: NoteTypeItemState) {
        this.noteTypeItemState = state
        name.text = state.name
        useCount.text =
            resources.getQuantityString(
                R.plurals.model_browser_of_type,
                state.useCount,
                state.useCount,
            )
    }
}
