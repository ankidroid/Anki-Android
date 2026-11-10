// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filterable
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CommonString
import com.ichi2.anki.OnContextAndLongClickListener
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemTagBinding
import com.ichi2.anki.dialogs.tags.TagTree.TagTreeNode
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.ui.CheckBoxTriStates.State.CHECKED
import com.ichi2.ui.CheckBoxTriStates.State.INDETERMINATE
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import com.ichi2.utils.TagsUtil
import com.ichi2.utils.TypedFilter
import com.ichi2.anki.common.android.R as CommonR

/**
 * @param tags A reference to the [TagsList]
 */
class TagsArrayAdapter(
    private val tags: TagsList,
    private val onSelectionUpdate: () -> Unit,
) : RecyclerView.Adapter<TagsArrayAdapter.ViewHolder>(),
    Filterable {
    class ViewHolder(
        val binding: ItemTagBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        internal lateinit var node: TagTreeNode
            private set

        internal fun bindNode(node: TagTreeNode) {
            clearNodeBinding()
            this.node = node
            node.vh = this
        }

        internal fun clearNodeBinding() {
            // If a note already has a replacement holder, preserve its binding.
            if (::node.isInitialized && node.vh === this) {
                node.vh = null
            }
        }

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val text: String
            get() = node.tag

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val isChecked: Boolean
            get() = binding.checkBoxView.isChecked

        @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        val checkboxState: CheckBoxTriStates.State
            get() = binding.checkBoxView.state
    }

    private val tree: TagTree

    /**
     * Context and Long click listener for each tag item. Used to add a subtag for the clicked tag.
     * The full tag is passed through View.tag
     */
    var tagContextAndLongClickListener: OnContextAndLongClickListener? = null

    fun sortData() {
        tree.sortData()
    }

    /** Refresh the tag tree and checkbox states after bulk selection changes. */
    fun notifyCheckedStatusesChanged() {
        tree.rebuildTagTree()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val vh = ViewHolder(binding)
        // clicking the checkbox toggles the tag's check state
        binding.checkBoxView.setOnClickListener {
            val checkBox = binding.checkBoxView
            when (checkBox.state) {
                CHECKED -> tags.check(vh.node.tag, false)
                UNCHECKED -> tags.uncheck(vh.node.tag)
                INDETERMINATE -> tags.setIndeterminate(vh.node.tag)
            }
            vh.node.onCheckStateChanged(tags)
            onSelectionUpdate()
        }
        // clicking other parts toggles the expansion state, or the checkbox (for leaf)
        vh.itemView.setOnClickListener {
            if (vh.node.isNotLeaf()) {
                vh.node.toggleIsExpanded()
                tree.tagToIsExpanded[vh.node.tag] = !tree.tagToIsExpanded[vh.node.tag]!!
                updateExpanderBackgroundImage(binding.expandButton, vh.node)
                // content of RecyclerView may change due to the expansion / collapse
                val deltaSize = vh.node.subtreeSize - 1
                if (vh.node.isExpanded) {
                    notifyItemRangeInserted(vh.layoutPosition + 1, deltaSize)
                } else {
                    notifyItemRangeRemoved(vh.layoutPosition + 1, deltaSize)
                }
            } else {
                // tapping on a leaf node toggles the checkbox
                binding.checkBoxView.performClick()
                binding.checkBoxView.refreshDrawableState()
            }
        }
        // context and long clicking a tag opens the add tag dialog with the current tag as the prefix
        vh.itemView.setOnContextAndLongClickListener(tagContextAndLongClickListener)
        return vh
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val binding = holder.binding
        holder.bindNode(tree.getVisibleTagTreeNode(position)!!)
        holder.itemView.tag = holder.node.tag

        if (tree.hasVisibleNestedTag) {
            binding.expandButton.visibility = if (holder.node.isNotLeaf()) View.VISIBLE else View.INVISIBLE
            updateExpanderBackgroundImage(binding.expandButton, holder.node)
            // shift according to the level
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMarginStart(HIERARCHY_SHIFT_BASE * holder.node.level)
            binding.expandButton.layoutParams = lp
        } else {
            // do not add padding if there is no visible nested tag
            binding.expandButton.visibility = View.GONE
        }
        binding.expandButton.contentDescription =
            holder.itemView.context.getString(CommonString.expand_tag, holder.node.tag.replace("::", " "))

        binding.textView.text = TagsUtil.getTagParts(holder.node.tag).last()

        if (tags.isIndeterminate(holder.node.tag)) {
            binding.checkBoxView.state = INDETERMINATE
        } else {
            binding.checkBoxView.state = if (tags.isChecked(holder.node.tag)) CHECKED else UNCHECKED
        }
        holder.node.updateCheckBoxCycleStyle(tags)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clearNodeBinding()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = tree.getItemCount()

    /**
     * Set the background resource of the [ImageButton] according to information of the given [TagTreeNode].
     *
     * @param button The [ImageButton] to update.
     * @param node The corresponding [TagTreeNode].
     */
    private fun updateExpanderBackgroundImage(
        button: ImageButton,
        node: TagTreeNode,
    ) {
        // More custom display related to the node can be added here.
        // For example, display some icon if the node is a leaf? (assets required)
        when (node.isExpanded) {
            true -> button.setBackgroundResource(R.drawable.ic_expand_more_black_24dp_xml)
            false -> button.setBackgroundResource(CommonR.drawable.ic_baseline_chevron_right_24)
        }
    }

    override fun getFilter(): TagsFilter = TagsFilter()

    // Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026
    inner class TagsFilter : TypedFilter<String>({ tags.toList() }) {
        /**
         * A tag may be set so that the path to it is expanded immediately after the filter displays the result.
         * When empty, nothing is to be expanded.
         * It is cleared every time after the filtered result is calculated.
         * @see publishResults
         */
        private var expandTarget = String()

        override fun filterResults(
            constraint: CharSequence,
            items: List<String>,
        ): List<String> = tree.filterResults(constraint, items)

        override fun publishResults(
            constraint: CharSequence?,
            results: List<String>,
        ) {
            tree.publishResults(results, expandTarget)
            expandTarget = String()
            notifyDataSetChanged()
        }

        /**
         * Set the target to expand in the next filtering (optional).
         * @see expandTarget
         */
        fun setExpandTarget(tag: String) {
            expandTarget = tag
        }
    }

    init {
        tree = TagTree(tags)
    }

    companion object {
        const val HIERARCHY_SHIFT_BASE = 50
    }
}
