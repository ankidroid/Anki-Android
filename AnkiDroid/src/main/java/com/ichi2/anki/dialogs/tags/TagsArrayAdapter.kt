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
        internal lateinit var node: TagTreeNode
        internal val mExpandButton: ImageButton = itemView.findViewById(R.id.id_expand_button)
        internal val mCheckBoxView: CheckBoxTriStates = itemView.findViewById(R.id.tags_dialog_tag_item_checkbox)
        // TextView contains the displayed tag (only the last part), while the full tag is stored in TagTreeNode
        internal val mTextView: TextView = itemView.findViewById(R.id.tags_dialog_tag_item_text)

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val text: String
            get() = node.tag

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val isChecked: Boolean
            get() = mCheckBoxView.isChecked

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val checkboxState: CheckBoxTriStates.State
            get() = mCheckBoxView.state
    }

    data class TagTreeNode(
        // the full tag
        val tag: String,
        val parent: TagTreeNode?,
        val children: ArrayList<TagTreeNode>,
        // level of the tag in the tree (-1 for the root)
        val level: Int,
        // size of the subtree if the tag is expanded
        var subtreeSize: Int,
        var expandState: Boolean
    ) {
        fun getContributeSize(): Int {
            return if (expandState) subtreeSize else 1
        }

        fun isNotLeaf(): Boolean {
            return children.isNotEmpty()
        }

        // Toggle the expand state of the node.
        // Should not be called on the root node.
        fun toggleExpandState() {
            expandState = !expandState
            val delta = if (expandState) subtreeSize - 1 else 1 - subtreeSize
            var node = parent!!
            node.subtreeSize += delta
            while (node.parent != null && node.expandState) {
                node.parent!!.subtreeSize += delta
                node = node.parent!!
            }
        }

        // Expand all the way to root.
        // The callee itself should remain collapse.
        // Should not be called on the root node.
        fun expandToRoot() {
            var node = this
            assert(node.parent != null)
            var deltaFromBelow = 0
            while (true) {
                val oldSize = node.getContributeSize()
                node.subtreeSize += deltaFromBelow
                if (node == this) {
                    node.expandState = false
                } else if (!node.expandState) {
                    node.expandState = true
                }
                deltaFromBelow = node.getContributeSize() - oldSize
                if (node.parent == null) {
                    break
                }
                node = node.parent!!
            }
        }
    }

    /**
     * A subset of all tags in [tags] satisfying the user's search.
     * @see getFilter
     */
    private val mFilteredList: ArrayList<String>
    // root node of the tag tree
    private var mTreeRoot: TagTreeNode?
    // a map from tag strings to corresponding nodes
    private val mTagToNode: HashMap<String, TagTreeNode>

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
            if (checkBox.state == CheckBoxTriStates.State.CHECKED) {
                tags.check(vh.node.tag)
                // ancestors may turn into indeterminate
                notifyDataSetChanged()
            } else if (checkBox.state == CheckBoxTriStates.State.UNCHECKED) {
                tags.uncheck(vh.node.tag)
            }
        }
        // clicking other parts toggles the expansion state
        vh.itemView.setOnClickListener {
            vh.node.toggleExpandState()
            updateExpanderBackgroundImage(vh.mExpandButton, vh.node)
            // content of RecyclerView may change due to the expansion / collapse
            if (vh.node.isNotLeaf()) {
                val deltaSize = vh.node.subtreeSize - 1
                if (vh.node.expandState) {
                    notifyItemRangeInserted(vh.layoutPosition + 1, deltaSize)
                } else {
                    notifyItemRangeRemoved(vh.layoutPosition + 1, deltaSize)
                }
            }
        }
        return vh
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.node = getVisibleTagTreeNode(position)!!

        holder.mExpandButton.visibility = if (holder.node.isNotLeaf()) View.VISIBLE else View.INVISIBLE
        updateExpanderBackgroundImage(holder.mExpandButton, holder.node)
        // shift according to the level
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(HIERARCHY_SHIFT_BASE * holder.node.level, 0, 0, 0)
        holder.mExpandButton.layoutParams = lp

        if (tags.isIndeterminate(holder.node.tag)) {
            holder.mCheckBoxView.state = CheckBoxTriStates.State.INDETERMINATE
        } else {
            holder.mCheckBoxView.state = if (tags.isChecked(holder.node.tag)) CheckBoxTriStates.State.CHECKED else CheckBoxTriStates.State.UNCHECKED
        }

        holder.mTextView.text = TagsUtil.getTagParts(holder.node.tag).last()
    }

    /**
     * Find the TagTreeNode of the index-th visible tag.
     * Implemented by walking the tree using subtree size.
     * Return null if out-of-bound.
     */
    private fun getVisibleTagTreeNode(index: Int): TagTreeNode? {
        if (mTreeRoot == null || index >= mTreeRoot!!.subtreeSize) {
            return null
        }
        var remain = index
        return generateSequence(mTreeRoot) {
            if (remain < 0) {
                null
            } else {
                val children = it.children.iterator()
                var child: TagTreeNode? = null
                while (children.hasNext()) {
                    child = children.next()
                    if (remain >= child.getContributeSize()) {
                        remain -= child.getContributeSize()
                    } else {
                        remain -= 1
                        break
                    }
                }
                child
            }
        }.last()
        /*
        var remain = index
        var node = mTreeRoot!!
        while (remain < node.subtreeSize) {
            for (child in node.children) {
                if (remain >= child.getContributeSize()) {
                    remain -= child.getContributeSize()
                } else {
                    if (remain == 0) {
                        return child
                    } else {
                        remain -= 1
                        node = child
                        break
                    }
                }
            }
        }
         */
    }

    /**
     * Return the number of visible tags.
     */
    override fun getItemCount(): Int {
        return if (mTreeRoot != null) mTreeRoot!!.subtreeSize else 0
    }

    /**
     * Build the tag tree.
     * The tags have been sorted using the hierarchical comparator, which is also a DFN order.
     * Use a stack to build the tree without using recursion.
     */
    private fun buildTagTree(initExpandState: Boolean) {
        val stack = Stack<TagTreeNode>()
        mTreeRoot = TagTreeNode("", null, ArrayList(), -1, 0, true)
        stack.add(mTreeRoot)
        mTagToNode.clear()
        fun stackPopAndPushUp() {
            val popped = stack.pop()
            stack.peek().subtreeSize += popped.getContributeSize()
        }
        for (tag in mFilteredList) {
            // root will never be popped
            while (stack.size > 1) {
                if (!tag.startsWith(stack.peek().tag + "::")) {
                    stackPopAndPushUp()
                } else {
                    break
                }
            }
            val parent = stack.peek()
            val node = TagTreeNode(tag, parent, ArrayList(), parent.level + 1, 1, initExpandState)
            parent.children.add(node)
            mTagToNode[tag] = node
            stack.add(node)
        }
        while (stack.size > 1) {
            stackPopAndPushUp()
        }
    }

    private fun updateExpanderBackgroundImage(button: ImageButton, node: TagTreeNode) {
        // More custom display related to the node can be added here.
        // For example, display some icon if the node is a leaf? (assets required)
        when (node.expandState) {
            true -> button.setBackgroundResource(R.drawable.ic_expand_more_black_24dp_xml)
            false -> button.setBackgroundResource(R.drawable.ic_baseline_chevron_right_24)
        }
    }

    /**
     * Expand all ancestors of the tag while leaving the tag itself collapsed.
     * Need to call notifyDataSetChanged() to apply the changes.
     */
    private fun expandPathToTag(tag: String) {
        mTagToNode[tag]?.expandToRoot()
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
        mTreeRoot = null
        mTagToNode = HashMap()
        buildTagTree(false)
    }

    companion object {
        const val HIERARCHY_SHIFT_BASE = 40
    }
}
