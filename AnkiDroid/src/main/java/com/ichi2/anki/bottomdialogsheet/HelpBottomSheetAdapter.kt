/*
 * Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
 *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.bottomdialogsheet

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R

class HelpBottomSheetAdapter(val headerList: List<HelpDialogModel>) :
    RecyclerView.Adapter<HelpBottomSheetAdapter.ModelViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HelpBottomSheetAdapter.ModelViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.help_bottomsheet_items, parent, false)
        return ModelViewHolder(v)
    }

    override fun onBindViewHolder(holder: HelpBottomSheetAdapter.ModelViewHolder, position: Int) {
        val currentItem = headerList[position]
        holder.parentIcon.setImageResource(currentItem.icon)
        holder.parentText.text = currentItem.title
        holder.chevron.visibility = View.INVISIBLE
        currentItem.action?.invoke()
        if (currentItem.isHeader) {
            holder.chevron.visibility = View.VISIBLE
            val subAdapter = HelpBottomSheetChildAdapter(currentItem.childItems!!)
            holder.childRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
            holder.childRecyclerView.adapter = subAdapter

            var isChildRecyclerViewShown = false

            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = holder.headerLayout.context.obtainStyledAttributes(attrs)
            holder.headerLayout.setBackgroundResource(ta.getResourceId(0, 0))

            holder.linearLayout.setOnClickListener {
                val rotationAngle = if (isChildRecyclerViewShown) 0f else 90f
                val rotationAnimator =
                    ObjectAnimator.ofFloat(holder.chevron, "rotation", rotationAngle)
                rotationAnimator.duration = 200
                rotationAnimator.start()
                holder.childRecyclerView.visibility =
                    if (isChildRecyclerViewShown) View.GONE else View.VISIBLE
                isChildRecyclerViewShown = !isChildRecyclerViewShown
            }
        }
    }

    override fun getItemCount(): Int {
        return headerList.size
    }

    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var linearLayout: LinearLayout
        var parentIcon: ImageView
        var parentText: TextView
        var childRecyclerView: RecyclerView
        var chevron: ImageView
        var headerLayout: LinearLayout

        init {
            linearLayout = itemView.findViewById(R.id.linear_layout_recycler_view)
            parentIcon = itemView.findViewById(R.id.image_view_recycler_view)
            parentText = itemView.findViewById(R.id.text_view_recycler_view)
            childRecyclerView = itemView.findViewById(R.id.rvSubItem)
            chevron = itemView.findViewById(R.id.chevron_help)
            headerLayout = itemView.findViewById(R.id.header_item_layout)
        }
    }
}

private class HelpBottomSheetChildAdapter(val linkItem: List<HelpDialogModel>) :
    RecyclerView.Adapter<HelpBottomSheetChildAdapter.ModelViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HelpBottomSheetChildAdapter.ModelViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.help_bottomsheet_child_items, parent, false)
        return ModelViewHolder(v)
    }

    override fun onBindViewHolder(
        holder: HelpBottomSheetChildAdapter.ModelViewHolder,
        position: Int
    ) {
        holder.linkIcon.setImageResource(linkItem[position].icon)
        holder.linkText.text = linkItem[position].title

        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val ta = holder.linkLayout.context.obtainStyledAttributes(attrs)
        holder.linkLayout.setBackgroundResource(ta.getResourceId(0, 0))

        holder.linkLayout.setOnClickListener {
            linkItem[position].action?.invoke()
        }
    }

    override fun getItemCount(): Int {
        return linkItem.size
    }

    inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var linkLayout: LinearLayout
        var linkIcon: ImageView
        var linkText: TextView

        init {
            linkLayout = itemView.findViewById(R.id.linear_layout_recycler_view) as LinearLayout
            linkIcon = itemView.findViewById(R.id.image_view_recycler_view) as ImageView
            linkText = itemView.findViewById(R.id.text_view_recycler_view) as TextView
        }
    }
}

data class HelpDialogModel(
    val title: String,
    val isHeader: Boolean,
    val icon: Int,
    val childItems: List<HelpDialogModel>? = null,
    val action: (() -> Unit)? = null
)
