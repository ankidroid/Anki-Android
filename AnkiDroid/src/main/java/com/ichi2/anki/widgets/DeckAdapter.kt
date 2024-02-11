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
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.sched.DeckNode
import com.ichi2.utils.KotlinCleanup
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber
import java.util.*

@KotlinCleanup("lots to do")
@RustCleanup("Lots of bad code: should not be using suspend functions inside an adapter")
@RustCleanup("Differs from legacy backend: Create deck 'One', create deck 'One::two'. 'One::two' was not expanded")
class DeckAdapter(private val layoutInflater: LayoutInflater, context: Context) : RecyclerView.Adapter<DeckAdapter.ViewHolder>(), Filterable {
    private var deckTree: DeckNode? = null

    /** The non-collapsed subset of the deck tree that matches the current search. */
    private var filteredDeckList: List<DeckNode> = ArrayList()
    private val zeroCountColor: Int
    private val newCountColor: Int
    private val learnCountColor: Int
    private val reviewCountColor: Int
    private val rowCurrentDrawable: Int
    private val deckNameDefaultColor: Int
    private val deckNameDynColor: Int
    private val expandImage: Drawable?
    private val collapseImage: Drawable?
    private var currentDeckId: DeckId = 0

    // Listeners
    private var deckClickListener: View.OnClickListener? = null
    private var deckExpanderClickListener: View.OnClickListener? = null
    private var deckLongClickListener: OnLongClickListener? = null
    private var countsClickListener: View.OnClickListener? = null

    // Totals accumulated as each deck is processed
    private var new = 0
    private var lrn = 0
    private var rev = 0
    private var numbersComputed = false

    // Flags
    private var hasSubdecks = false

    // Whether we have a background (so some items should be partially transparent).
    private var partiallyTransparentForBackground = false

    // ViewHolder class to save inflated views for recycling
    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val deckLayout: RelativeLayout
        val countsLayout: LinearLayout
        val deckExpander: ImageButton
        val indentView: ImageButton
        val deckName: TextView
        val deckNew: TextView
        val deckLearn: TextView
        val deckRev: TextView

        init {
            deckLayout = v.findViewById(R.id.DeckPickerHoriz)
            countsLayout = v.findViewById(R.id.counts_layout)
            deckExpander = v.findViewById(R.id.deckpicker_expander)
            indentView = v.findViewById(R.id.deckpicker_indent)
            deckName = v.findViewById(R.id.deckpicker_name)
            deckNew = v.findViewById(R.id.deckpicker_new)
            deckLearn = v.findViewById(R.id.deckpicker_lrn)
            deckRev = v.findViewById(R.id.deckpicker_rev)
        }
    }

    fun setDeckClickListener(listener: View.OnClickListener?) {
        deckClickListener = listener
    }

    fun setCountsClickListener(listener: View.OnClickListener?) {
        countsClickListener = listener
    }

    fun setDeckExpanderClickListener(listener: View.OnClickListener?) {
        deckExpanderClickListener = listener
    }

    fun setDeckLongClickListener(listener: OnLongClickListener?) {
        deckLongClickListener = listener
    }

    /** Sets whether the control should have partial transparency to allow a background to be seen  */
    fun enablePartialTransparencyForBackground(isTransparent: Boolean) {
        partiallyTransparentForBackground = isTransparent
    }

    private val mutex = Mutex()

    /**
     * Consume a list of [DeckNode]s to render a new deck list.
     * @param filter The string to filter the deck by
     */
    suspend fun buildDeckList(node: DeckNode, filter: CharSequence?) {
        Timber.d("buildDeckList")
        // TODO: This is a lazy hack to fix a bug. We hold the lock for far too long
        // and do I/O inside it. Better to calculate the new lists outside the lock, then swap
        mutex.withLock {
            deckTree = node
            hasSubdecks = node.children.any { it.children.any() }
            currentDeckId = withCol { decks.current().optLong("id") }
            rev = node.revCount
            lrn = node.lrnCount
            new = node.newCount
            numbersComputed = true
            // Filtering performs notifyDataSetChanged after the async work is complete
            getFilter()?.filter(filter)
        }
    }

    @CheckResult
    fun getNodeByDid(did: DeckId): DeckNode {
        val pos = findDeckPosition(did)
        return deckList[pos]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = layoutInflater.inflate(R.layout.deck_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Update views for this node
        val node = filteredDeckList[position]
        // Set the expander icon and padding according to whether or not there are any subdecks
        val deckLayout = holder.deckLayout
        val rightPadding = deckLayout.resources.getDimension(R.dimen.deck_picker_right_padding).toInt()
        if (hasSubdecks) {
            val smallPadding = deckLayout.resources.getDimension(R.dimen.deck_picker_left_padding_small).toInt()
            deckLayout.setPadding(smallPadding, 0, rightPadding, 0)
            holder.deckExpander.visibility = View.VISIBLE
            // Create the correct expander for this deck
            runBlocking { setDeckExpander(holder.deckExpander, holder.indentView, node) }
        } else {
            holder.deckExpander.visibility = View.GONE
            val normalPadding = deckLayout.resources.getDimension(R.dimen.deck_picker_left_padding).toInt()
            deckLayout.setPadding(normalPadding, 0, rightPadding, 0)
        }
        if (node.children.isNotEmpty()) {
            holder.deckExpander.tag = node.did
            holder.deckExpander.setOnClickListener(deckExpanderClickListener)
        } else {
            holder.deckExpander.isClickable = false
            holder.deckExpander.setOnClickListener(null)
        }
        holder.deckLayout.setBackgroundResource(rowCurrentDrawable)
        // Set background colour. The current deck has its own color
        if (isCurrentlySelectedDeck(node)) {
            holder.deckLayout.setBackgroundResource(rowCurrentDrawable)
            if (partiallyTransparentForBackground) {
                setBackgroundAlpha(holder.deckLayout, SELECTED_DECK_ALPHA_AGAINST_BACKGROUND)
            }
        } else {
            // Ripple effect
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = holder.deckLayout.context.obtainStyledAttributes(attrs)
            holder.deckLayout.setBackgroundResource(ta.getResourceId(0, 0))
            ta.recycle()
        }
        // Set deck name and colour. Filtered decks have their own colour
        holder.deckName.text = node.lastDeckNameComponent
        val filtered =
            node.filtered
        if (filtered) {
            holder.deckName.setTextColor(deckNameDynColor)
        } else {
            holder.deckName.setTextColor(deckNameDefaultColor)
        }

        // Set the card counts and their colors
        holder.deckNew.text = node.newCount.toString()
        holder.deckNew.setTextColor(if (node.newCount == 0) zeroCountColor else newCountColor)
        holder.deckLearn.text = node.lrnCount.toString()
        holder.deckLearn.setTextColor(if (node.lrnCount == 0) zeroCountColor else learnCountColor)
        holder.deckRev.text = node.revCount.toString()
        holder.deckRev.setTextColor(if (node.revCount == 0) zeroCountColor else reviewCountColor)

        // Store deck ID in layout's tag for easy retrieval in our click listeners
        holder.deckLayout.tag = node.did
        holder.countsLayout.tag = node.did

        // Set click listeners
        holder.deckLayout.setOnClickListener(deckClickListener)
        holder.deckLayout.setOnLongClickListener(deckLongClickListener)
        holder.countsLayout.setOnClickListener(countsClickListener)
    }

    private fun setBackgroundAlpha(view: View, alphaPercentage: Double) {
        val background = view.background.mutate()
        background.alpha = (255 * alphaPercentage).toInt()
        view.background = background
    }

    private fun isCurrentlySelectedDeck(node: DeckNode): Boolean {
        return node.did == currentDeckId
    }

    override fun getItemCount(): Int {
        return filteredDeckList.size
    }

    private fun setDeckExpander(expander: ImageButton, indent: ImageButton, node: DeckNode) {
        // Apply the correct expand/collapse drawable
        if (node.children.isNotEmpty()) {
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            if (node.collapsed) {
                expander.setImageDrawable(expandImage)
                expander.contentDescription = expander.context.getString(R.string.expand)
            } else {
                expander.setImageDrawable(collapseImage)
                expander.contentDescription = expander.context.getString(R.string.collapse)
            }
        } else {
            expander.visibility = View.INVISIBLE
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Add some indenting for each nested level
        val width = indent.resources.getDimension(R.dimen.keyline_1).toInt() * node.depth
        indent.minimumWidth = width
    }

    /**
     * Return the position of the deck in the deck list. If the deck is a child of a collapsed deck
     * (i.e., not visible in the deck list), then the position of the parent deck is returned instead.
     *
     * An invalid deck ID will return position 0.
     */
    fun findDeckPosition(did: DeckId): Int {
        filteredDeckList.forEachIndexed { index, treeNode ->
            if (treeNode.did == did) {
                return index
            }
        }

        // If the deck is not in our list, we search again using the immediate parent
        // If the deck is not found, return 0
        val collapsedDeck = deckTree?.find(did) ?: return 0
        val parent = collapsedDeck.parent?.get() ?: return 0
        return findDeckPosition(parent.did)
    }

    val due: Int?
        get() = if (numbersComputed) {
            new + lrn + rev
        } else {
            null
        }
    private val deckList: List<DeckNode>
        get() = filteredDeckList

    override fun getFilter(): Filter? {
        return deckTree?.let { DeckFilter(it) }
    }

    @VisibleForTesting
    inner class DeckFilter(private val top: DeckNode) : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val out = top.filterAndFlatten(constraint)
            Timber.i("deck filter: %d", out.size, constraint)
            return FilterResults().also {
                it.values = out
                it.count = out.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            @Suppress("unchecked_cast")
            filteredDeckList = results.values as List<DeckNode>
            notifyDataSetChanged()
        }
    }

    companion object {
        /* Make the selected deck roughly half transparent if there is a background */
        const val SELECTED_DECK_ALPHA_AGAINST_BACKGROUND = 0.45
    }

    init {
        // Get the colors from the theme attributes
        val attrs = intArrayOf(
            R.attr.zeroCountColor,
            R.attr.newCountColor,
            R.attr.learnCountColor,
            R.attr.reviewCountColor,
            R.attr.currentDeckBackground,
            android.R.attr.textColor,
            R.attr.dynDeckColor,
            R.attr.expandRef,
            R.attr.collapseRef
        )
        val ta = context.obtainStyledAttributes(attrs)
        zeroCountColor = ta.getColor(0, context.getColor(R.color.black))
        newCountColor = ta.getColor(1, context.getColor(R.color.black))
        learnCountColor = ta.getColor(2, context.getColor(R.color.black))
        reviewCountColor = ta.getColor(3, context.getColor(R.color.black))
        rowCurrentDrawable = ta.getResourceId(4, 0)
        deckNameDefaultColor = ta.getColor(5, context.getColor(R.color.black))
        deckNameDynColor = ta.getColor(6, context.getColor(R.color.material_blue_A700))
        expandImage = ta.getDrawable(7)
        expandImage!!.isAutoMirrored = true
        collapseImage = ta.getDrawable(8)
        collapseImage!!.isAutoMirrored = true
        ta.recycle()
    }
}
