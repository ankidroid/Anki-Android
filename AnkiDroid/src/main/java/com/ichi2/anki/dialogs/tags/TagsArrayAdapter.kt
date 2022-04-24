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
import android.widget.*
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

    /**
     * The node class of the tag tree.
     * @param tag The full tag that the node represents.
     * @param parent The parent of the node (null for the root).
     * @param children The children of the node.
     * @param level The level of the tag in the tree (-1 for the root).
     * @param subtreeSize The size of the subtree if the node is expanded. This exists because
     * we want to save the [isExpanded] state of child tags, even if the parent is not expanded.
     * When we expand a tag, we do not want to walk the tree to determine how many nodes were added or removed.
     * @param isExpanded whether the node is expanded or not
     * @param subtreeCheckedCnt The number of checked nodes in the subtree (self included). This exists
     * because we want to dynamically turn a checkbox from unchecked into indeterminate if at least one of its
     * descendants is checked, or from indeterminate to unchecked if all of its descendants are unchecked.
     * @param vh The reference to current bound [ViewHolder]. A node bound with some [ViewHolder] must have [vh] nonnull.
     * @see onBindViewHolder for the binding
     */
    data class TagTreeNode(
        val tag: String,
        val parent: TagTreeNode?,
        val children: ArrayList<TagTreeNode>,
        val level: Int,
        var subtreeSize: Int,
        var isExpanded: Boolean,
        var subtreeCheckedCnt: Int,
        var vh: ViewHolder?
    ) {
        /**
         * Return the number of visible nodes in the subtree.
         * If [isExpanded] is set to false, the subtree is collapsed, only the node itself is visible.
         * Otherwise, the subtree is expanded and visible.
         *
         * @return The number of visible nodes in the subtree.
         */
        fun getContributeSize(): Int {
            return if (isExpanded) subtreeSize else 1
        }

        /**
         * @return If the node has one or more children.
         */
        fun isNotLeaf(): Boolean {
            return children.isNotEmpty()
        }

        /**
         * Toggle the expand state of the node, then iterate up to the root to maintain the tree.
         * Should never be called on the root node [mTreeRoot].
         */
        fun toggleIsExpanded() {
            isExpanded = !isExpanded
            val delta = if (isExpanded) subtreeSize - 1 else 1 - subtreeSize
            for (ancestor in iterateAncestorsOf(this)) {
                ancestor.subtreeSize += delta
                if (!ancestor.isExpanded) {
                    break
                }
            }
        }

        /**
         * When the checkbox of the node changes (either into CHECK or UNCHECK), update [subtreeCheckedCnt]s of itself and its ancestors.
         *
         * Update the checkbox to INDETERMINATE if it was UNCHECK and now [subtreeCheckedCnt] > 0.
         * Update the checkbox to UNCHECK if it was INDETERMINATE and now [subtreeCheckedCnt] == 0.
         *
         * @param tags The [TagsList] that manages tags.
         * @return A list of [ViewHolder] whose [ViewHolder.mCheckBoxView] has changed.
         */
        fun onCheckStateChanged(tags: TagsList): List<ViewHolder> {
            assert(vh!!.mCheckBoxView.state != CheckBoxTriStates.State.INDETERMINATE)
            val delta = if (vh!!.mCheckBoxView.state == CheckBoxTriStates.State.CHECKED) 1 else -1
            val changedViewHolders = ArrayList<ViewHolder>()
            fun update(node: TagTreeNode) {
                node.subtreeCheckedCnt += delta
                if (node.vh!!.mCheckBoxView.state == CheckBoxTriStates.State.UNCHECKED && node.subtreeCheckedCnt > 0) {
                    tags.setIndeterminate(node.tag)
                    changedViewHolders.add(node.vh!!)
                }
                if (node.vh!!.mCheckBoxView.state == CheckBoxTriStates.State.INDETERMINATE && node.subtreeCheckedCnt == 0) {
                    tags.uncheck(node.tag)
                    changedViewHolders.add(node.vh!!)
                }
            }
            update(this)
            for (ancestor in iterateAncestorsOf(this)) {
                // do not perform update on the virtual root
                if (ancestor.parent != null) {
                    update(ancestor)
                }
            }
            return changedViewHolders
        }

        companion object {
            fun iterateAncestorsOf(start: TagTreeNode) = object : Iterator<TagTreeNode> {
                var current = start

                override fun hasNext(): Boolean = current.parent != null

                override fun next(): TagTreeNode {
                    current = current.parent!!
                    return current
                }
            }
        }
    }

    /**
     * A subset of all tags in [tags] satisfying the user's search.
     * @see getFilter
     */
    private val mFilteredList: ArrayList<String>

    /**
     * The root node of the tag tree.
     */

    private var mTreeRoot: TagTreeNode?

    /**
     * A mapping from tag strings to corresponding nodes.
     */
    private val mTagToNode: HashMap<String, TagTreeNode>

    /**
     * A mapping from tag strings to its expand state.
     * Used to restore expand states between filtering results.
     * Must be updated every time a node's [TagTreeNode.isExpanded] changed.
     */
    private val mTagToIsExpanded: HashMap<String, Boolean>

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
                tags.check(vh.node.tag, false)
                notifyViewHoldersChanged(vh.node.onCheckStateChanged(tags))
            } else if (checkBox.state == CheckBoxTriStates.State.UNCHECKED) {
                /* If the tag was indeterminate and the checkbox is now UNCHECKED, this is caused by
                 * the default behavior that clicking on INDETERMINATE changes into UNCHECK. We need
                 * to override it since clicking on an indeterminate tag should let user check it
                 * instead of uncheck. The state cycle should now be:
                 * INDETERMINATE -> CHECKED -> UNCHECKED -> CHECKED -> UNCHECKED -> ...
                 */
                if (tags.isIndeterminate(vh.node.tag)) {
                    checkBox.state = CheckBoxTriStates.State.CHECKED
                    tags.check(vh.node.tag, false)
                } else {
                    tags.uncheck(vh.node.tag)
                }
                notifyViewHoldersChanged(vh.node.onCheckStateChanged(tags))
            }
        }
        // clicking other parts toggles the expansion state, or the checkbox (for leaf)
        vh.itemView.setOnClickListener {
            if (vh.node.isNotLeaf()) {
                vh.node.toggleIsExpanded()
                mTagToIsExpanded[vh.node.tag] = !mTagToIsExpanded[vh.node.tag]!!
                updateExpanderBackgroundImage(vh.mExpandButton, vh.node)
                // content of RecyclerView may change due to the expansion / collapse
                val deltaSize = vh.node.subtreeSize - 1
                if (vh.node.isExpanded) {
                    notifyItemRangeInserted(vh.layoutPosition + 1, deltaSize)
                } else {
                    notifyItemRangeRemoved(vh.layoutPosition + 1, deltaSize)
                }
            } else {
                // tapping on a leaf node toggles the checkbox
                vh.mCheckBoxView.performClick()
            }
        }
        return vh
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.node = getVisibleTagTreeNode(position)!!
        holder.node.vh = holder

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

    private fun notifyViewHoldersChanged(viewHolders: List<ViewHolder>) {
        viewHolders.forEach { notifyItemChanged(it.layoutPosition) }
    }

    /**
     * Find the [TagTreeNode] of the [index]-th visible tag.
     * Implemented by walking the tree using [TagTreeNode.subtreeSize].
     *
     * @param index The index of the node to find.
     * @return The corresponding [TagTreeNode]. Null if out-of-bound.
     */
    private fun getVisibleTagTreeNode(index: Int): TagTreeNode? {
        if (mTreeRoot == null) {
            return null
        }
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
        return null
    }

    /**
     * @return The number of visible tags.
     */
    override fun getItemCount(): Int {
        return if (mTreeRoot != null) mTreeRoot!!.subtreeSize else 0
    }

    /**
     * Build the tag tree. The tags have been sorted using the hierarchical comparator
     * [TagsUtil.compareTag], which leads to a DFN order. Use a stack to build the tree without
     * recursion.
     * The initial expand states are inherited through [mTagToIsExpanded].
     * A special tag can be set so that the path to it is expanded.
     *
     * @param expandTarget The target tag to expand. Do nothing if it is empty or not found.
     */
    private fun buildTagTree(expandTarget: String) {
        // init mapping for newly added tags
        mFilteredList.forEach {
            if (!mTagToIsExpanded.containsKey(it)) {
                mTagToIsExpanded[it] = false
            }
        }
        TagsUtil.getTagAncestors(expandTarget).forEach {
            if (mTagToIsExpanded.containsKey(it)) {
                mTagToIsExpanded[it] = true
            }
        }
        val stack = Stack<TagTreeNode>()
        mTreeRoot = TagTreeNode("", null, ArrayList(), -1, 0, true, 0, null)
        stack.add(mTreeRoot)
        mTagToNode.clear()
        fun stackPopAndPushUp() {
            val popped = stack.pop()
            stack.peek().subtreeSize += popped.getContributeSize()
            stack.peek().subtreeCheckedCnt += popped.subtreeCheckedCnt
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
            val node = TagTreeNode(tag, parent, ArrayList(), parent.level + 1, 1, mTagToIsExpanded[tag]!!, if (tags.isChecked(tag)) 1 else 0, null)
            parent.children.add(node)
            mTagToNode[tag] = node
            stack.add(node)
        }
        while (stack.size > 1) {
            stackPopAndPushUp()
        }
    }

    /**
     * Set the background resource of the [ImageButton] according to information of the given [TagTreeNode].
     *
     * @param button The [ImageButton] to update.
     * @param node The corresponding [TagTreeNode].
     */
    private fun updateExpanderBackgroundImage(button: ImageButton, node: TagTreeNode) {
        // More custom display related to the node can be added here.
        // For example, display some icon if the node is a leaf? (assets required)
        when (node.isExpanded) {
            true -> button.setBackgroundResource(R.drawable.ic_expand_more_black_24dp_xml)
            false -> button.setBackgroundResource(R.drawable.ic_baseline_chevron_right_24)
        }
    }

    override fun getFilter(): TagsFilter {
        return TagsFilter()
    }

    /* Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026 */
    inner class TagsFilter : TypedFilter<String>({ tags.toList() }) {
        /**
         * A tag may be set so that the path to it is expanded immediately after the filter displays the result.
         * When empty, nothing is to be expanded.
         * It is cleared every time after the filtered result is calculated.
         * @see publishResults
         */
        private var mExpandTarget = String()

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
            val res = items.filter { shownTags.contains(it) }
            // mark shown tags as expanded, so that they will be expanded next time the tree is built
            res.forEach { mTagToIsExpanded[it] = true }
            return res
        }

        override fun publishResults(constraint: CharSequence?, results: List<String>) {
            mFilteredList.clear()
            mFilteredList.addAll(results)
            // if the search constraint is empty, collapse all tags when constructing the tree
            sortData()
            buildTagTree(mExpandTarget)
            mExpandTarget = String()
            notifyDataSetChanged()
        }

        /**
         * Set the target to expand in the next filtering (optional).
         * @see mExpandTarget
         */
        fun setExpandTarget(tag: String) {
            mExpandTarget = tag
        }
    }

    init {
        sortData()
        mFilteredList = ArrayList(tags.toList())
        mTreeRoot = null
        mTagToNode = HashMap()
        mTagToIsExpanded = HashMap()
        tags.forEach { mTagToIsExpanded[it] = false }
        buildTagTree(String())
    }

    companion object {
        const val HIERARCHY_SHIFT_BASE = 40
    }
}
