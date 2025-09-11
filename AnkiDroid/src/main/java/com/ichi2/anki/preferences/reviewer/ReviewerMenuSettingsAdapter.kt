/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences.reviewer

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ReviewerMenuDisplayTypeBinding
import com.ichi2.anki.databinding.ReviewerMenuItemBinding

class ReviewerMenuSettingsAdapter(
    private val items: List<ReviewerMenuSettingsRecyclerItem>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ReviewerMenuSettingsRecyclerItem.ACTION_VIEW_TYPE -> {
                val binding = ReviewerMenuItemBinding.inflate(inflater, parent, false)
                ActionViewHolder(binding)
            }
            ReviewerMenuSettingsRecyclerItem.DISPLAY_TYPE_VIEW_TYPE -> {
                val binding = ReviewerMenuDisplayTypeBinding.inflate(inflater, parent, false)
                DisplayTypeViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unexpected viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val item = items[position]
        when (holder) {
            is ActionViewHolder -> holder.bind((item as ReviewerMenuSettingsRecyclerItem.Action).viewerAction)
            is DisplayTypeViewHolder -> holder.bind((item as ReviewerMenuSettingsRecyclerItem.DisplayType).menuDisplayType)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position].viewType

    private var onDragHandleTouchedListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    fun setOnDragHandleTouchedListener(listener: (RecyclerView.ViewHolder) -> Unit) {
        this.onDragHandleTouchedListener = listener
    }

    /** @see [R.layout.reviewer_menu_item] */
    private inner class ActionViewHolder(
        private val binding: ReviewerMenuItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(action: ViewerAction) {
            binding.title.text = action.title(itemView.context)
            action.drawableRes?.let { binding.icon.setBackgroundResource(it) }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onDragHandleTouchedListener?.invoke(this)
                }
                return@setOnTouchListener false
            }
        }
    }

    /** @see [R.layout.reviewer_menu_display_type] */
    private class DisplayTypeViewHolder(
        private val binding: ReviewerMenuDisplayTypeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(displayCategory: MenuDisplayType) {
            binding.title.setText(displayCategory.title)
        }
    }
}

/**
 * @param viewType type to be returned at [RecyclerView.Adapter.getItemViewType]
 */
sealed class ReviewerMenuSettingsRecyclerItem(
    val viewType: Int,
) {
    data class Action(
        val viewerAction: ViewerAction,
    ) : ReviewerMenuSettingsRecyclerItem(ACTION_VIEW_TYPE)

    data class DisplayType(
        val menuDisplayType: MenuDisplayType,
    ) : ReviewerMenuSettingsRecyclerItem(DISPLAY_TYPE_VIEW_TYPE)

    companion object {
        const val ACTION_VIEW_TYPE = 0
        const val DISPLAY_TYPE_VIEW_TYPE = 1
    }
}
