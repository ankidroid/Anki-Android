/*
 * Copyright (c) 2022 Prateek Singh <prateeksingh3212@gmail.com>
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

package com.ichi2.anki.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.NotificationDatastore
import com.ichi2.anki.R
import com.ichi2.anki.servicelayer.DeckService
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.sched.AbstractDeckTreeNode
import com.ichi2.libanki.sched.TreeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber

class NotificationPreferenceAdapter(val layoutInflater: LayoutInflater, val context: Context) :
    RecyclerView.Adapter<NotificationPreferenceAdapter.ViewHolder>() {

    // Recyclerview Data
    private val mDeckList: MutableList<TreeNode<AbstractDeckTreeNode>>

    // Listener
    private var mTimeClickListener: OnClickListener? = null
    private var mDeckExpanderClickListener: OnClickListener? = null
    private var mDialogDismissListener: OnClickListener? = null
    private lateinit var mCol: Collection

    // Flags
    private var mHasSubdecks = false
    private var currentDeckId: DeckId = 0

    private var mDeckExpander: Drawable?
    private var mDeckCollapse: Drawable?
    private val mNoExpander: Drawable = ColorDrawable(Color.TRANSPARENT)

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deckLayout: RelativeLayout
        val timeLayout: LinearLayout
        val deckExpander: ImageButton
        val indentView: ImageButton
        val deckName: TextView
        val notificationTime: TextView

        init {
            deckLayout = itemView.findViewById(R.id.card_deck_notification)
            timeLayout = itemView.findViewById(R.id.time_layout)
            deckExpander = itemView.findViewById(R.id.card_deck_notification_expander)
            indentView = itemView.findViewById(R.id.card_deck_notification_indent)
            deckName = itemView.findViewById(R.id.card_deck_notification_name)
            notificationTime = itemView.findViewById(R.id.card_deck_notification_time)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = layoutInflater.inflate(R.layout.deck_notification_card, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val treeNode = mDeckList[position]

        // Get Notification Time
        CoroutineScope(Dispatchers.Main).launch {
            val deckNotification =
                NotificationDatastore.getInstance(context).getDeckSchedData(treeNode.value.did)
            holder.notificationTime.text =
                deckNotification?.let { "${it.schedHour}:${it.schedMinutes}" } ?: "HH:MM"
        }
        val rightPadding =
            holder.deckLayout.resources.getDimension(R.dimen.deck_picker_right_padding).toInt()
        if (mHasSubdecks) {
            val smallPadding =
                holder.deckLayout.resources.getDimension(R.dimen.deck_picker_left_padding_small)
                    .toInt()
            holder.deckLayout.setPadding(smallPadding, 0, rightPadding, 0)
            holder.deckExpander.visibility = View.VISIBLE
            // Create the correct expander for this deck
            setDeckExpander(holder.deckExpander, holder.indentView, treeNode)
        } else {
            holder.deckExpander.visibility = View.GONE
            val normalPadding =
                holder.deckLayout.resources.getDimension(R.dimen.deck_picker_left_padding).toInt()
            holder.deckLayout.setPadding(normalPadding, 0, rightPadding, 0)
        }

        if (treeNode.hasChildren()) {
            holder.deckExpander.tag = treeNode.value.did
            holder.deckExpander.setOnClickListener(mDeckExpanderClickListener)
        } else {
            holder.deckExpander.isClickable = false
            holder.deckExpander.setOnClickListener(null)
        }

        holder.deckName.text = treeNode.value.lastDeckNameComponent
        holder.timeLayout.tag =
            Triple(treeNode.value.did, treeNode.value.lastDeckNameComponent, position)
        holder.timeLayout.setOnClickListener(mTimeClickListener)
    }

    override fun getItemCount(): Int = mDeckList.size

    /**
     * Consume a list of [AbstractDeckTreeNode]s to render a new deck list.
     */
    fun buildDeckList(nodes: List<TreeNode<AbstractDeckTreeNode>>, col: Collection) {
        mCol = col
        mDeckList.clear()
        mHasSubdecks = false
        currentDeckId = mCol.decks.current().optLong("id")
        processNodes(nodes)
        notifyDataSetChanged()
    }

    private fun processNodes(nodes: List<TreeNode<AbstractDeckTreeNode>>) {
        for (node in nodes) {
            var shouldRecurse = true
            if (BackendFactory.defaultLegacySchema) {
                // If the default deck is empty, hide it by not adding it to the deck list.
                // We don't hide it if it's the only deck or if it has sub-decks.
                if (node.value.did == 1L && nodes.size > 1 && !node.hasChildren()) {
                    if (!DeckService.defaultDeckHasCards(mCol)) {
                        continue
                    }
                }
                // If any of this node's parents are collapsed, don't add it to the deck list
                for (parent in mCol.decks.parents(node.value.did)) {
                    mHasSubdecks =
                        true // If a deck has a parent it means it's a subdeck so set a flag
                    if (parent.optBoolean("collapsed")) {
                        return
                    }
                }
            } else {
                // backend takes care of excluding default, and includes collapsed info
                if (node.value.collapsed) {
                    mHasSubdecks = true
                    shouldRecurse = false
                }
            }

            mDeckList.add(node)
            Timber.d("${mDeckList.size}")

            // Process sub-decks
            if (shouldRecurse) {
                processNodes(node.children)
            }
        }
    }

    private fun setDeckExpander(
        expander: ImageButton,
        indent: ImageButton,
        node: TreeNode<AbstractDeckTreeNode>
    ) {
        val nodeValue = node.value
        val collapsed = if (BackendFactory.defaultLegacySchema) {
            mCol.decks.get(nodeValue.did).optBoolean("collapsed", false)
        } else {
            node.value.collapsed
        }
        // Apply the correct expand/collapse drawable
        if (node.hasChildren()) {
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            if (collapsed) {
                expander.setImageDrawable(mDeckExpander)
                expander.contentDescription = expander.context.getString(R.string.expand)
            } else {
                expander.setImageDrawable(mDeckCollapse)
                expander.contentDescription = expander.context.getString(R.string.collapse)
            }
        } else {
            expander.setImageDrawable(mNoExpander)
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Add some indenting for each nested level
        val width = indent.resources.getDimension(R.dimen.keyline_1).toInt() * nodeValue.depth
        indent.minimumWidth = width
    }

    fun setTimeClickListener(listener: OnClickListener?) {
        mTimeClickListener = listener
    }

    fun setDeckExpanderClickListener(listener: OnClickListener?) {
        mDeckExpanderClickListener = listener
    }

    fun setOnSheetDismissListener(listener: OnClickListener?) {
        mDialogDismissListener = listener
    }

    init {
        mDeckList = ArrayList()
        val attrs = intArrayOf(
            R.attr.expandRef,
            R.attr.collapseRef
        )

        val obtainedAttrs = context.obtainStyledAttributes(attrs)
        mDeckExpander = obtainedAttrs.getDrawable(0)
        mDeckCollapse = obtainedAttrs.getDrawable(1)
    }
}
