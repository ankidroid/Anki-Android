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

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filterable
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.utils.TagsUtil
import com.ichi2.utils.TypedFilter
import java.util.*

/**
 * @param tags A reference to the {@link TagsList} passed.
 */
class TagsArrayAdapter(private val tags: TagsList) : RecyclerView.Adapter<TagsArrayAdapter.ViewHolder>(), Filterable {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val mExpandButton: ImageButton = itemView.findViewById(R.id.id_expand_button)
        internal val mCheckBoxView: CheckBoxTriStates = itemView.findViewById(R.id.tags_dialog_tag_item_checkbox)
        // TextView contains the displayed tag (only the last part).
        internal val mTextView: TextView = itemView.findViewById(R.id.tags_dialog_tag_item_text)
        // RawTag contains the full tag.
        internal var mRawTag = String()
        // Position is used to index the tag in the fully expanded tag node array.
        internal var mPosition = -1
        // Level of the tag in the hierarchy. Used to calculate the shift distance.
        internal var mLevel = -1

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val text: String
            get() = mRawTag

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val isChecked: Boolean
            get() = mCheckBoxView.isChecked

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val checkboxState: CheckBoxTriStates.State
            get() = mCheckBoxView.state
    }

    data class TagTreeNode(var parent: Int, val children: ArrayList<Int>, var subtreeSize: Int, var expandState: Boolean) {
        fun getContributeSize(): Int {
            return if (expandState) {
                subtreeSize
            } else {
                1
            }
        }
    }

    /**
     * A subset of all tags in [tags] satisfying the user's search.
     * @see getFilter
     */
    private val mFilteredList: ArrayList<String>

    private val mTreeRoot: TagTreeNode
    private val mTree: ArrayList<TagTreeNode>

    fun sortData() {
        tags.sort()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.tags_item_list_dialog, parent, false)
        val vh = ViewHolder(v.findViewById(R.id.tags_dialog_tag_item))
        // clicking the checkbox toggles the tag's check state
        vh.mCheckBoxView.setOnClickListener {
            val checkBox = vh.mCheckBoxView
            val tag = vh.mRawTag
            if (checkBox.state == CheckBoxTriStates.State.CHECKED) {
                tags.check(tag)
                // ancestors may turn into indeterminate
                notifyDataSetChanged()
            } else if (checkBox.state == CheckBoxTriStates.State.UNCHECKED) {
                tags.uncheck(tag)
            }
        }
        // clicking other parts toggles the expansion state
        vh.itemView.setOnClickListener {
            toggleExpansionState(vh.mPosition)
            updateExpanderBackgroundImage(vh.mExpandButton, mTree[vh.mPosition])
            // result of getTagByIndex() may change due to the expansion / collapse
            if (mTree[vh.mPosition].children.isNotEmpty()) {
                notifyDataSetChanged()
            }
        }
        return vh
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val realPosition = getRealPosition(position)
        val tag = mFilteredList[realPosition]
        val tagParts = TagsUtil.getTagParts(tag)

        holder.mTextView.text = tagParts.last()
        holder.mRawTag = tag
        holder.mPosition = realPosition
        holder.mLevel = tagParts.size - 1

        holder.mExpandButton.visibility = if (mTree[realPosition].children.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        updateExpanderBackgroundImage(holder.mExpandButton, mTree[realPosition])
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(HIERARCHY_SHIFT_BASE * holder.mLevel, 0, 0, 0)
        holder.mExpandButton.layoutParams = lp

        if (tags.isIndeterminate(tag)) {
            holder.mCheckBoxView.state = CheckBoxTriStates.State.INDETERMINATE
        } else {
            holder.mCheckBoxView.state = if (tags.isChecked(tag)) CheckBoxTriStates.State.CHECKED else CheckBoxTriStates.State.UNCHECKED
        }
    }

    /**
     * Find the position of the index-th visible tag in mFilteredList.
     * Implemented by walking the tree using subtree size.
     */
    private fun getRealPosition(index: Int): Int {
        var remain = index
        var node = mTreeRoot
        while (remain < node.subtreeSize) {
            for (ch in node.children) {
                val subtreeSize = mTree[ch].getContributeSize()
                if (remain >= subtreeSize) {
                    remain -= subtreeSize
                } else {
                    if (remain == 0) {
                        return ch
                    } else {
                        remain -= 1
                        node = mTree[ch]
                        break
                    }
                }
            }
        }
        // unexpected
        return -1
    }

    /**
     * Return the number of visible tags.
     */
    override fun getItemCount(): Int {
        return mTreeRoot.subtreeSize
    }

    /**
     * Build the tag tree.
     * The tags have been sorted using the hierarchical comparator, which is also a DFN order.
     * Use a stack to build the tree without using recursion.
     */
    private fun buildTagTree(initExpandState: Boolean) {
        mTreeRoot.children.clear()
        mTreeRoot.subtreeSize = 0
        mTree.clear()

        val stack = Stack<Int>()
        for (i in 0 until mFilteredList.size) {
            mTree.add(TagTreeNode(-1, ArrayList(), 1, initExpandState))
            while (!stack.empty()) {
                if (!mFilteredList[i].startsWith(mFilteredList[stack.peek()] + "::")) {
                    val x = stack.pop()
                    if (!stack.empty()) {
                        val y = stack.peek()
                        mTree[x].parent = y
                        mTree[y].children.add(x)
                        mTree[y].subtreeSize += mTree[x].getContributeSize()
                    } else {
                        mTreeRoot.children.add(x)
                        mTreeRoot.subtreeSize += mTree[x].getContributeSize()
                    }
                } else {
                    break
                }
            }
            stack.push(i)
        }
        while (stack.size > 1) {
            val x = stack.pop()
            val y = stack.peek()
            mTree[x].parent = y
            mTree[y].children.add(x)
            mTree[y].subtreeSize += mTree[x].getContributeSize()
        }
        if (stack.isNotEmpty()) {
            mTreeRoot.children.add(stack.peek())
            mTreeRoot.subtreeSize += mTree[stack.peek()].getContributeSize()
        }
    }

    /**
     * Toggle the expansion state of the position-th tag in mFilteredList.
     * Return: the toggled expansion state.
     */
    private fun toggleExpansionState(position: Int): Boolean {
        mTree[position].expandState = !mTree[position].expandState
        val delta = if (mTree[position].expandState) {
            mTree[position].subtreeSize - 1
        } else {
            1 - mTree[position].subtreeSize
        }
        var x = position
        while (x != -1) {
            val y = mTree[x].parent
            if (x != position && !mTree[x].expandState) {
                break
            }
            if (y != -1) {
                mTree[y].subtreeSize += delta
            } else {
                mTreeRoot.subtreeSize += delta
            }
            x = y
        }
        return mTree[position].expandState
    }

    private fun getExpansionState(position: Int): Boolean {
        return mTree[position].expandState
    }

    private fun updateExpanderBackgroundImage(button: ImageButton, node: TagTreeNode) {
        when (node.expandState) {
            true -> {
                button.setBackgroundResource(R.drawable.ic_expand_more_black_24dp_xml)
            }
            false -> {
                button.setBackgroundResource(R.drawable.ic_baseline_chevron_right_24)
            }
        }
    }

    /**
     * Expand all ancestors of the tag.
     * Need to call notifyDataSetChanged() to apply the changes.
     */
    private fun expandPathToTag(tag: String) {
        val ancestors = TagsUtil.getTagAncestors(tag)
        for (ancestor in ancestors) {
            val position = mFilteredList.indexOf(ancestor)
            if (!getExpansionState(position)) {
                toggleExpansionState(position)
            }
        }
    }

    override fun getFilter(): TagsFilter {
        return TagsFilter()
    }

    /* Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026 */
    inner class TagsFilter : TypedFilter<String>({ tags.toList() }) {
        // a target tag may be set so that the path to is is always expanded
        // it is cleared after the filter is updated
        private var mExpandTarget: String? = null

        override fun filterResults(constraint: CharSequence, items: List<String>): List<String> {
            val shownTags = TreeSet<String>()

            val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
            val crucialTags = items.filter {
                it.lowercase(Locale.getDefault()).contains(filterPattern)
            }
            shownTags.addAll(crucialTags)

            // the ancestors should be displayed as well
            for (tag in crucialTags) {
                shownTags.addAll(TagsUtil.getTagAncestors(tag))
            }

            // show tags in the relative order in original list
            return items.filter {
                shownTags.contains(it)
            }
        }

        override fun publishResults(constraint: CharSequence?, results: List<String>) {
            mFilteredList.clear()
            mFilteredList.addAll(results)
            // if the search constraint is empty, collapse all tags when constructing the tree
            sortData()
            buildTagTree(!constraint.isNullOrEmpty())
            if (!TextUtils.isEmpty(mExpandTarget)) {
                expandPathToTag(mExpandTarget!!)
            }
            mExpandTarget = null
            notifyDataSetChanged()
        }

        fun setExpandTarget(tag: String?) {
            mExpandTarget = tag
        }
    }

    init {
        sortData()
        mFilteredList = ArrayList(tags.toList())
        mTreeRoot = TagTreeNode(-1, ArrayList(), 0, true)
        mTree = ArrayList(mFilteredList.size)
        buildTagTree(false)
    }

    companion object {
        const val HIERARCHY_SHIFT_BASE = 40
    }
}
