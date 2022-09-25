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
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R

private val notetypeFieldsDiffCallback = object : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}

internal class NotetypeFieldsAdapter(
    context: Context,
    private val onReposition: (String) -> Unit,
    private val onRename: (String) -> Unit,
    private val onSortBy: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onToggleSticky: (String) -> Unit,
    private val onAddLanguageHint: (String) -> Unit,
) : ListAdapter<String, NotetypeFieldViewHolder>(notetypeFieldsDiffCallback) {
    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotetypeFieldViewHolder {
        return NotetypeFieldViewHolder(
            rowView = layoutInflater.inflate(R.layout.item_note_type_field, parent, false),
            onReposition = onReposition,
            onRename = onRename,
            onSortBy = onSortBy,
            onDelete = onDelete,
            onToggleSticky = onToggleSticky,
            onAddLanguageHint = onAddLanguageHint,
        )
    }

    override fun onBindViewHolder(holder: NotetypeFieldViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

internal class NotetypeFieldViewHolder(
    rowView: View,
    private val onReposition: (String) -> Unit,
    private val onRename: (String) -> Unit,
    private val onSortBy: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onToggleSticky: (String) -> Unit,
    private val onAddLanguageHint: (String) -> Unit,
) : RecyclerView.ViewHolder(rowView) {
    private val nameTextView: TextView = rowView.findViewById(R.id.field_name)
    private val btnReposition: Button = rowView.findViewById(R.id.field_reposition)
    private val btnRename: Button = rowView.findViewById(R.id.field_rename)
    private val btnMoreOptions: ImageButton = rowView.findViewById(R.id.field_more_options)
    private var name: String? = null

    init {
        btnReposition.apply {
            setOnClickListener { name?.let(onReposition) }
            text = nameReposition
        }
        btnRename.apply {
            setOnClickListener { name?.let(onRename) }
            text = nameRename
        }
        btnMoreOptions.setOnClickListener(::showMorePopup)
    }

    fun bind(fieldName: String) {
        name = fieldName
        nameTextView.text = fieldName
    }

    private fun showMorePopup(target: View) {
        PopupMenu(target.context, target).apply {
            menu.add(0, MENU_SORT_ID, 0, nameSort)
            menu.add(0, MENU_TOGGLE_STICKY_ID, 1, nameSticky)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                menu.add(
                    0,
                    MENU_ADD_LANGUAGE_HINT_ID,
                    2,
                    target.context.getString(R.string.model_field_editor_language_hint)
                )
            }
            menu.add(0, MENU_DELETE_ID, 3, nameDelete)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    MENU_SORT_ID -> name?.let(onSortBy)
                    MENU_DELETE_ID -> name?.let(onDelete)
                    MENU_TOGGLE_STICKY_ID -> name?.let(onToggleSticky)
                    MENU_ADD_LANGUAGE_HINT_ID -> name?.let(onAddLanguageHint)
                }
                true
            }
        }.show()
    }

    private companion object {
        const val MENU_SORT_ID = 1000
        const val MENU_DELETE_ID = 2000
        const val MENU_TOGGLE_STICKY_ID = 3000
        const val MENU_ADD_LANGUAGE_HINT_ID = 4000
        private val nameReposition: String
        private val nameRename: String
        private val nameDelete: String
        private val nameSticky: String
        private val nameSort: String

        init {
            with(TR) {
                nameReposition = actionsReposition()
                nameRename = actionsRename()
                nameSort = fieldsSortByThisFieldInThe()
                nameSticky = editingToggleSticky()
                nameDelete = actionsDelete()
            }
        }
    }
}
