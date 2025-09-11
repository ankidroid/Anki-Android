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
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemManageNoteTypeBinding

private val notetypeNamesAndCountDiff =
    object : DiffUtil.ItemCallback<ManageNoteTypeUiModel>() {
        override fun areItemsTheSame(
            oldItem: ManageNoteTypeUiModel,
            newItem: ManageNoteTypeUiModel,
        ): Boolean = oldItem.id == newItem.id && oldItem.name == newItem.name && oldItem.useCount == newItem.useCount

        override fun areContentsTheSame(
            oldItem: ManageNoteTypeUiModel,
            newItem: ManageNoteTypeUiModel,
        ): Boolean = oldItem.id == newItem.id && oldItem.name == newItem.name && oldItem.useCount == newItem.useCount
    }

internal class NotetypesAdapter(
    context: Context,
    private val onShowFields: (ManageNoteTypeUiModel) -> Unit,
    private val onEditCards: (ManageNoteTypeUiModel) -> Unit,
    private val onRename: (ManageNoteTypeUiModel) -> Unit,
    private val onDelete: (ManageNoteTypeUiModel) -> Unit,
) : ListAdapter<ManageNoteTypeUiModel, NotetypeViewHolder>(notetypeNamesAndCountDiff) {
    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): NotetypeViewHolder =
        NotetypeViewHolder(
            binding = ItemManageNoteTypeBinding.inflate(layoutInflater, parent, false),
            onDelete = onDelete,
            onRename = onRename,
            onEditCards = onEditCards,
            onShowFields = onShowFields,
        )

    override fun onBindViewHolder(
        holder: NotetypeViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }
}

internal class NotetypeViewHolder(
    private val binding: ItemManageNoteTypeBinding,
    onShowFields: (ManageNoteTypeUiModel) -> Unit,
    onEditCards: (ManageNoteTypeUiModel) -> Unit,
    onRename: (ManageNoteTypeUiModel) -> Unit,
    onDelete: (ManageNoteTypeUiModel) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private var manageNoteTypeUiModel: ManageNoteTypeUiModel? = null
    private val resources = binding.root.context.resources

    init {
        binding.root.setOnClickListener { manageNoteTypeUiModel?.let(onShowFields) }
        binding.editCardsButton.setOnClickListener { manageNoteTypeUiModel?.let(onEditCards) }
        binding.deleteNoteTypeButton.setOnClickListener { manageNoteTypeUiModel?.let(onDelete) }
        binding.renameNoteTypeButton.setOnClickListener { manageNoteTypeUiModel?.let(onRename) }
    }

    fun bind(manageNoteTypeUiModel: ManageNoteTypeUiModel) {
        this.manageNoteTypeUiModel = manageNoteTypeUiModel
        binding.noteName.text = manageNoteTypeUiModel.name
        binding.noteUseCount.text =
            resources.getQuantityString(
                R.plurals.model_browser_of_type,
                manageNoteTypeUiModel.useCount,
                manageNoteTypeUiModel.useCount,
            )
    }
}
