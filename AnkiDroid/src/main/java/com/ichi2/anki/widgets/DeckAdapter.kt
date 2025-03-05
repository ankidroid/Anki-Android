/****************************************************************************************
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.anki.widgets

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.getDrawableOrThrow
import androidx.core.content.withStyledAttributes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.findViewById
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.sched.DeckNode
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber

/**
 * A [RecyclerView.Adapter] used to show the list of decks inside [com.ichi2.anki.DeckPicker].
 *
 * @param activityHasBackground true if [com.ichi2.anki.DeckPicker] has a background set, false
 * otherwise. If true the adapter will make the rows transparent so the background can be seen.
 * @param onDeckSelected callback triggered when the user selects a deck
 * @param onDeckCountsSelected callback triggered when the user selects the counts of a deck
 * @param onDeckChildrenToggled callback triggered when the user toggles the visibility of its
 * children to show/hide the children. Only for decks that have children.
 * @param onDeckContextRequested callback triggered when the user requested to see extra actions for
 * a deck. This consists in a context menu brought in by either a long touch or a right click.
 */
@RustCleanup("Differs from legacy backend: Create deck 'One', create deck 'One::two'. 'One::two' was not expanded")
class DeckAdapter(
    context: Context,
    private val activityHasBackground: Boolean,
    private val onDeckSelected: (DeckId) -> Unit,
    private val onDeckCountsSelected: (DeckId) -> Unit,
    private val onDeckChildrenToggled: (DeckId) -> Unit,
    private val onDeckContextRequested: (DeckId) -> Unit,
) : ListAdapter<DeckNode, DeckAdapter.ViewHolder>(deckNodeDiffCallback) {
    private val layoutInflater = LayoutInflater.from(context)
    private val zeroCountColor: Int
    private val newCountColor: Int
    private val learnCountColor: Int
    private val reviewCountColor: Int
    private val rowCurrentDrawable: Int
    private val deckNameDefaultColor: Int
    private val deckNameDynColor: Int
    private val expandImage: Drawable
    private val collapseImage: Drawable

    // intended to be `val` but was declared a `var` to allow initialization from the init {} block
    private var selectableItemBackground: Int = 0
    private val endPadding: Int = context.resources.getDimension(R.dimen.deck_picker_right_padding).toInt()
    private val startPadding: Int = context.resources.getDimension(R.dimen.deck_picker_left_padding).toInt()
    private val startPaddingSmall: Int = context.resources.getDimension(R.dimen.deck_picker_left_padding_small).toInt()
    private val nestedIndent = context.resources.getDimension(R.dimen.keyline_1).toInt()
    private var currentDeckId: DeckId = 0

    // Flags
    private var hasSubdecks = false

    class ViewHolder(
        v: View,
    ) : RecyclerView.ViewHolder(v) {
        val deckLayout: RelativeLayout = findViewById(R.id.DeckPickerHoriz)
        val countsLayout: LinearLayout = findViewById(R.id.counts_layout)
        val deckExpander: ImageButton = findViewById(R.id.deckpicker_expander)
        val indentView: ImageButton = findViewById(R.id.deckpicker_indent)
        val deckName: TextView = findViewById(R.id.deckpicker_name)
        val deckNew: TextView = findViewById(R.id.deckpicker_new)
        val deckLearn: TextView = findViewById(R.id.deckpicker_lrn)
        val deckRev: TextView = findViewById(R.id.deckpicker_rev)
    }

    /**
     * Set new data in the adapter. This should be used instead of [submitList] (which is called
     * by this method) so there's no need to call [notifyDataSetChanged] on the adapter.
     */
    fun submit(
        data: List<DeckNode>,
        hasSubDecks: Boolean,
        currentDeckId: DeckId,
    ) {
        // submitList is smart to not trigger a refresh if the new list is the same, but we do need
        // an adapter refresh if the other two properties have changed even if the new data is the
        // same as they modify some of the adapter's content appearance
        val forceRefresh =
            areDataSetsEqual(currentList, data) &&
                (this.hasSubdecks != hasSubDecks || this.currentDeckId != currentDeckId)
        this.hasSubdecks = hasSubDecks
        this.currentDeckId = currentDeckId
        submitList(data)
        if (forceRefresh) notifyDataSetChanged()
    }

    private fun areDataSetsEqual(
        currentSet: List<DeckNode>,
        newSet: List<DeckNode>,
    ): Boolean {
        if (currentSet.size != newSet.size) return false
        return currentSet.zip(newSet).all { (fst, snd) ->
            fst.fullDeckName == snd.fullDeckName
        }
    }

    /**
     * Update the current selected deck so the adapter shows the proper backgrounds.
     * Calls [notifyDataSetChanged].
     */
    fun updateSelectedDeck(deckId: DeckId) {
        this.currentDeckId = deckId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(layoutInflater.inflate(R.layout.deck_item, parent, false))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val node = getItem(position)
        // Set the expander icon and padding according to whether or not there are any subdecks
        val deckLayout = holder.deckLayout
        if (hasSubdecks) {
            deckLayout.setPaddingRelative(startPaddingSmall, 0, endPadding, 0)
            holder.deckExpander.visibility = View.VISIBLE
            // Create the correct expander for this deck
            runBlocking { setDeckExpander(holder.deckExpander, holder.indentView, node) }
        } else {
            holder.deckExpander.visibility = View.GONE
            deckLayout.setPaddingRelative(startPadding, 0, endPadding, 0)
        }
        if (node.children.isNotEmpty()) {
            holder.deckExpander.setOnClickListener {
                onDeckChildrenToggled(node.did)
                notifyItemChanged(position) // Ensure UI updates
            }
        } else {
            holder.deckExpander.isClickable = false
            holder.deckExpander.setOnClickListener(null)
        }
        holder.deckLayout.setBackgroundResource(rowCurrentDrawable)
        // set a different background color for the current selected deck
        if (node.did == currentDeckId) {
            holder.deckLayout.setBackgroundResource(rowCurrentDrawable)
            if (activityHasBackground) {
                val background = holder.deckLayout.background.mutate()
                background.alpha = (255 * SELECTED_DECK_ALPHA_AGAINST_BACKGROUND).toInt()
                holder.deckLayout.background = background
            }
        } else {
            holder.deckLayout.setBackgroundResource(selectableItemBackground)
        }
        // Set deck name and colour. Filtered decks have their own colour
        holder.deckName.text = node.lastDeckNameComponent
        holder.deckName.setTextColor(if (node.filtered) deckNameDynColor else deckNameDefaultColor)

        // Set the card counts and their colors
        holder.deckNew.text = node.newCount.toString()
        holder.deckNew.setTextColor(if (node.newCount == 0) zeroCountColor else newCountColor)
        holder.deckLearn.text = node.lrnCount.toString()
        holder.deckLearn.setTextColor(if (node.lrnCount == 0) zeroCountColor else learnCountColor)
        holder.deckRev.text = node.revCount.toString()
        holder.deckRev.setTextColor(if (node.revCount == 0) zeroCountColor else reviewCountColor)

        holder.deckLayout.setOnClickListener { onDeckSelected(node.did) }
        holder.deckLayout.setOnContextAndLongClickListener {
            onDeckContextRequested(node.did)
            true
        }
        holder.countsLayout.setOnClickListener { onDeckCountsSelected(node.did) }
    }

    private fun setDeckExpander(
        expander: ImageButton,
        indent: ImageButton,
        node: DeckNode,
    ) {
        // Apply the correct expand/collapse drawable
        if (node.children.isNotEmpty()) {
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            if (node.collapsed) {
                expander.setImageDrawable(expandImage)
                expander.contentDescription = expander.context.getString(R.string.expand)
                Timber.d("Deck Collapsed")
            } else {
                expander.setImageDrawable(collapseImage)
                expander.contentDescription = expander.context.getString(R.string.collapse)
                Timber.d("Deck Expanded")
            }
        } else {
            expander.visibility = View.INVISIBLE
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Add some indenting for each nested level
        indent.minimumWidth = nestedIndent * node.depth
    }

    companion object {
        // Make the selected deck roughly half transparent if there is a background
        private const val SELECTED_DECK_ALPHA_AGAINST_BACKGROUND = 0.45
    }

    init {
        // Get the colors from the theme attributes
        val attrs =
            intArrayOf(
                R.attr.zeroCountColor,
                R.attr.newCountColor,
                R.attr.learnCountColor,
                R.attr.reviewCountColor,
                R.attr.currentDeckBackground,
                android.R.attr.textColor,
                R.attr.dynDeckColor,
                R.attr.expandRef,
                R.attr.collapseRef,
            )
        val ta = context.obtainStyledAttributes(attrs)
        zeroCountColor = ta.getColor(0, context.getColor(R.color.black))
        newCountColor = ta.getColor(1, context.getColor(R.color.black))
        learnCountColor = ta.getColor(2, context.getColor(R.color.black))
        reviewCountColor = ta.getColor(3, context.getColor(R.color.black))
        rowCurrentDrawable = ta.getResourceId(4, 0)
        deckNameDefaultColor = ta.getColor(5, context.getColor(R.color.black))
        deckNameDynColor = ta.getColor(6, context.getColor(R.color.material_blue_A700))
        expandImage = ta.getDrawableOrThrow(7)
        expandImage.isAutoMirrored = true
        collapseImage = ta.getDrawableOrThrow(8)
        collapseImage.isAutoMirrored = true
        ta.recycle()
        context.withStyledAttributes(attrs = intArrayOf(android.R.attr.selectableItemBackground)) {
            selectableItemBackground = ta.getResourceId(0, 0)
        }
    }
}

private val deckNodeDiffCallback =
    object : DiffUtil.ItemCallback<DeckNode>() {
        override fun areItemsTheSame(
            oldItem: DeckNode,
            newItem: DeckNode,
        ): Boolean = oldItem.did == newItem.did

        override fun areContentsTheSame(
            oldItem: DeckNode,
            newItem: DeckNode,
        ): Boolean =
            oldItem.did == newItem.did &&
                oldItem.filtered == newItem.filtered &&
                oldItem.fullDeckName == newItem.fullDeckName &&
                oldItem.newCount == newItem.newCount &&
                oldItem.lrnCount == newItem.lrnCount &&
                oldItem.revCount == newItem.revCount
    }
