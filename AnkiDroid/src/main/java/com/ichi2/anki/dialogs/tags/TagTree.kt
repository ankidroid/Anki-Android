// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import com.ichi2.anki.dialogs.tags.TagsArrayAdapter.ViewHolder
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.ui.CheckBoxTriStates.State.INDETERMINATE
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import com.ichi2.utils.TagsUtil
import java.util.Locale
import java.util.Stack
import java.util.TreeSet

internal class TagTree(
    private val tags: TagsList,
) {
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
     * @param vh The reference to currently bound [ViewHolder]. A node bound with some [ViewHolder] must have [vh] nonnull.
     * @see TagsArrayAdapter.onBindViewHolder for the binding
     */
    data class TagTreeNode(
        val tag: String,
        val parent: TagTreeNode?,
        val children: ArrayList<TagTreeNode>,
        val level: Int,
        var subtreeSize: Int,
        var isExpanded: Boolean,
        var vh: ViewHolder?,
    ) {
        /**
         * Get or set the checkbox state of the currently bound ViewHolder.
         * [vh] must be nonnull.
         */
        private var checkBoxState: CheckBoxTriStates.State?
            get() = vh?.binding?.checkBoxView?.state
            set(state) {
                state?.let { vh?.binding?.checkBoxView?.state = it }
            }

        /**
         * Return the number of visible nodes in the subtree.
         * If [isExpanded] is set to false, the subtree is collapsed, only the node itself is visible.
         * Otherwise, the subtree is expanded and visible.
         *
         * @return The number of visible nodes in the subtree.
         */
        fun getContributeSize(): Int = if (isExpanded) subtreeSize else 1

        /**
         * @return If the node has one or more children.
         */
        fun isNotLeaf(): Boolean = children.isNotEmpty()

        /**
         * Toggle the expand state of the node, then iterate up to the root to maintain the tree.
         * Should never be called on the root node [treeRoot].
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
         * Set the cycle style of the node's [vh]'s checkbox.
         * For nodes without selected descendants: CHECKED -> UNCHECKED -> CHECKED -> ...
         * For nodes with checked or indeterminate descendants: CHECKED -> INDETERMINATE -> CHECKED -> ...
         *
         * @param tags The [TagsList] that manages tags.
         */
        fun updateCheckBoxCycleStyle(tags: TagsList) {
            val hasSelectedDescendants = tags.hasSelectedDescendants(tag)
            vh?.binding?.checkBoxView?.cycleIndeterminateToChecked = hasSelectedDescendants
            vh?.binding?.checkBoxView?.cycleCheckedToIndeterminate = hasSelectedDescendants
        }

        /**
         * Update ancestor states from the complete selection, independently of which nodes are visible.
         * Preserve tags present on some notes even when their last selected descendant is cleared.
         *
         * @param tags The [TagsList] that manages tags.
         */
        fun onCheckStateChanged(tags: TagsList) {
            fun update(node: TagTreeNode) {
                val hasSelectedDescendants = tags.hasSelectedDescendants(node.tag)
                if (!tags.isChecked(node.tag) && hasSelectedDescendants) {
                    tags.setIndeterminate(node.tag)
                    node.checkBoxState = INDETERMINATE
                }
                if (tags.isIndeterminate(node.tag) && !tags.isPartiallySelected(node.tag) && !hasSelectedDescendants) {
                    tags.uncheck(node.tag)
                    node.checkBoxState = UNCHECKED
                }
                node.updateCheckBoxCycleStyle(tags)
                node.vh
                    ?.binding
                    ?.checkBoxView
                    ?.refreshDrawableState()
            }
            update(this)
            for (ancestor in iterateAncestorsOf(this)) {
                // do not perform update on the virtual root
                if (ancestor.parent != null) {
                    update(ancestor)
                }
            }
        }

        companion object {
            fun iterateAncestorsOf(start: TagTreeNode) =
                object : Iterator<TagTreeNode> {
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
    private val filteredList: ArrayList<String>

    /**
     * The root node of the tag tree.
     */
    private lateinit var treeRoot: TagTreeNode

    /**
     * A mapping from tag strings to corresponding nodes.
     */
    private val tagToNode: HashMap<String, TagTreeNode>

    /**
     * Whether there exists visible nested tag.
     * Recalculated everytime [rebuildTagTree] is called.
     */
    var hasVisibleNestedTag: Boolean
        private set

    /**
     * A mapping from tag strings to its expand state.
     * Used to restore expand states between filtering results.
     * Must be updated every time a node's [TagTreeNode.isExpanded] changed.
     */
    val tagToIsExpanded: HashMap<String, Boolean>

    fun sortData() {
        tags.sort()
    }

    /**
     * Find the [TagTreeNode] of the [index]-th visible tag.
     * Implemented by walking the tree using [TagTreeNode.subtreeSize].
     *
     * @param index The index of the node to find.
     * @return The corresponding [TagTreeNode]. Null if out-of-bound.
     */
    fun getVisibleTagTreeNode(index: Int): TagTreeNode? {
        var remain = index
        var node = treeRoot
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
    fun getItemCount(): Int = treeRoot.subtreeSize

    /**
     * Rebuild the tag tree. The tags have been sorted using the hierarchical comparator
     * [TagsUtil.compareTag], which leads to a DFN order. Use a stack to build the tree without
     * recursion.
     * The initial expand states are inherited through [tagToIsExpanded].
     * A special tag can be set so that the path to it is expanded.
     *
     * @param expandTarget The target tag to expand. Do nothing if it is empty or not found.
     */
    fun rebuildTagTree(expandTarget: String = "") {
        // init mapping for newly added tags
        filteredList.forEach {
            if (!tagToIsExpanded.containsKey(it)) {
                tagToIsExpanded[it] = tags.isChecked(it) || tags.isIndeterminate(it)
            }
        }
        TagsUtil.getTagAncestors(expandTarget).forEach {
            if (tagToIsExpanded.containsKey(it)) {
                tagToIsExpanded[it] = true
            }
        }
        hasVisibleNestedTag = false
        val stack = Stack<TagTreeNode>()
        treeRoot = TagTreeNode("", null, ArrayList(), -1, 0, true, null)
        stack.add(treeRoot)
        tagToNode.clear()

        fun stackPopAndPushUp() {
            val popped = stack.pop()
            stack.peek().subtreeSize += popped.getContributeSize()
        }
        for (tag in filteredList) {
            // root will never be popped
            while (stack.size > 1) {
                if (!tag.startsWith(stack.peek().tag + "::", ignoreCase = true)) {
                    stackPopAndPushUp()
                } else {
                    break
                }
            }
            val parent = stack.peek()
            val node =
                TagTreeNode(tag, parent, ArrayList(), parent.level + 1, 1, tagToIsExpanded[tag]!!, null)
            parent.children.add(node)
            tagToNode[tag] = node
            stack.add(node)
            if (stack.size > 2) {
                hasVisibleNestedTag = true
            }
        }
        while (stack.size > 1) {
            stackPopAndPushUp()
        }
    }

    fun filterResults(
        constraint: CharSequence,
        items: List<String>,
    ): List<String> {
        val shownTags = TreeSet<String>()
        val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
        val crucialTags =
            items.filter {
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
        res.forEach { tagToIsExpanded[it] = true }
        return res
    }

    fun publishResults(
        results: List<String>,
        expandTarget: String,
    ) {
        filteredList.clear()
        filteredList.addAll(results)
        sortData()
        rebuildTagTree(expandTarget)
    }

    init {
        sortData()
        filteredList = ArrayList(tags.toList())
        tagToNode = HashMap()
        tagToIsExpanded = HashMap()
        // set the initial expand state according to its checked state
        tags.forEach { tagToIsExpanded[it] = tags.isChecked(it) || tags.isIndeterminate(it) }
        hasVisibleNestedTag = false
        rebuildTagTree()
    }
}
