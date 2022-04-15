/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.tags

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filterable
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.utils.TypedFilter
import java.util.*
import kotlin.collections.ArrayList

/**
 * @param tags A reference to the {@link TagsList} passed.
 */
class TagsArrayAdapter(private val tags: TagsList) : RecyclerView.Adapter<TagsArrayAdapter.ViewHolder>(), Filterable {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val mCheckBoxView: CheckBoxTriStates = itemView.findViewById(R.id.tags_dialog_tag_item_checkbox)
        internal val mTextView: TextView = itemView.findViewById(R.id.tags_dialog_tag_item_text)

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val text: String
            get() = mTextView.text.toString()

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val isChecked: Boolean
            get() = mCheckBoxView.isChecked

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val checkboxState: CheckBoxTriStates.State
            get() = mCheckBoxView.state
    }

    /**
     * A subset of all tags in [tags] satisfying the user's search.
     * @see getFilter
     */
    private val mFilteredList: ArrayList<String>
    fun sortData() {
        tags.sort()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.tags_item_list_dialog, parent, false)
        val vh = ViewHolder(v.findViewById(R.id.tags_dialog_tag_item))
        vh.itemView.setOnClickListener {
            val checkBox = vh.mCheckBoxView
            val tag = vh.mTextView.text.toString()
            checkBox.toggle()
            if (checkBox.state == CheckBoxTriStates.State.CHECKED) {
                tags.check(tag)
            } else if (checkBox.state == CheckBoxTriStates.State.UNCHECKED) {
                tags.uncheck(tag)
            }
        }
        return vh
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = getTagByIndex(position)
        holder.mTextView.text = tag
        if (tags.isIndeterminate(tag)) {
            holder.mCheckBoxView.state = CheckBoxTriStates.State.INDETERMINATE
        } else {
            holder.mCheckBoxView.state = if (tags.isChecked(tag)) CheckBoxTriStates.State.CHECKED else CheckBoxTriStates.State.UNCHECKED
        }
    }

    private fun getTagByIndex(index: Int): String {
        return mFilteredList[index]
    }

    override fun getItemCount(): Int {
        return mFilteredList.size
    }

    override fun getFilter(): TagsFilter {
        return TagsFilter()
    }

    /* Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026 */
    inner class TagsFilter : TypedFilter<String>({ tags.toList() }) {
        override fun filterResults(constraint: CharSequence, items: List<String>): List<String> {
            val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
            return items.filter {
                it.lowercase(Locale.getDefault()).contains(filterPattern)
            }
        }

        override fun publishResults(constraint: CharSequence?, results: List<String>) {
            mFilteredList.clear()
            mFilteredList.addAll(results)
            sortData()
            notifyDataSetChanged()
        }
    }

    init {
        sortData()
        mFilteredList = ArrayList(tags.toList())
    }
}
