/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID
import com.ichi2.anki.R
import com.ichi2.anki.browser.TreeAdapter.Item
import com.ichi2.anki.convertDpToPixel

/**
 * An [adapter][RecyclerView.Adapter], displaying a list of clickable items with text and an icon
 *
 * Typically used as a list of filters, to allows quick selection of a single filter, or checking
 * multiple filters for use in an OR clause
 *
 * All items may be [clicked][onItemCheckedListener].
 *
 * Items may be [checked][onItemClickedListener] (dependent on [Item.checked]). Multiple items may
 * be checked at the same time
 */
abstract class TreeAdapter(val context: Context) : RecyclerView.Adapter<TreeAdapter.Holder>() {
    init { this@TreeAdapter.setHasStableIds(true) }

    enum class Collapsed { Yes, No, NotCollapsible }

    enum class Checked { Yes, No, NotCheckable }

    data class Item(
        val id: Long,
        @DrawableRes val icon: Int?,
        val text: CharSequence,
        val subtitle: String?,
        val indent: Int,
        val collapsed: Collapsed,
        val checked: Checked
    )

    var onItemClickedListener: ((Long) -> Unit) = { }
    var onItemCheckedListener: ((Long) -> Unit) = { }

    abstract fun updateCollapsedItemIds(ids: Set<Long>)
    abstract fun updateCheckedItemIds(ids: Set<Long>)

    protected fun setItems(items: List<Item>, collapsedItemIds: Set<Long>, checkedItemIds: Set<Long>) {
        val diff = DiffUtil.calculateDiff(DiffCallback(this.items, items))

        this.items = items
        this.collapsedItemIds = collapsedItemIds
        this.checkedItemIds = checkedItemIds

        diff.dispatchUpdatesTo(this)
    }

    object UpdateCollapsedOrCheckedOnly

    private class DiffCallback(old: List<Item>, new: List<Item>) : ListDiffCallback<Item>(old, new) {
        override fun areItemsTheSame(old: Item, new: Item) = old.id == new.id
        override fun areContentsTheSame(old: Item, new: Item) = old == new
        override fun getChangePayload(old: Item, new: Item) =
            if (old.icon != new.icon || old.text != new.text || old.indent != new.indent) {
                null
            } else {
                UpdateCollapsedOrCheckedOnly
            }
    }

    var collapsedItemIds = setOf<Long>()
        private set
    var checkedItemIds = setOf<Long>()
        private set

    private var items: List<Item> = listOf()

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val chevron: ImageView = view.findViewById(R.id.chevron)
        val icon: ImageView = view.findViewById(R.id.icon)
        val text: TextView = view.findViewById(R.id.text)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)

        init {
            checkbox.setOnClickListenerWithId(this) { id ->
                updateCheckedItemIds(checkedItemIds xor id)
                onItemCheckedListener(id)
            }

            view.setOnClickListenerWithId(this) { id ->
                onItemClickedListener(id)
            }
        }

        fun fullBind(item: Item) {
            chevron.updateLayoutParams<MarginLayoutParams> {
                marginStart = (mandatoryMargin + indentMargin * item.indent).toInt()
            }

            text.text = item.text

            subtitle.text = item.subtitle
            subtitle.isVisible = !subtitle.text.isNullOrEmpty()

            item.icon?.let { iconToUse ->
                icon.setImageDrawable(AppCompatResources.getDrawable(context, iconToUse))
            }

            // isVisible takes precedence over .alpha
            chevron.visibility = if (item.indent == -1) View.GONE else View.VISIBLE
            chevron.animate().cancel()

            when (item.collapsed) {
                Collapsed.No -> {
                    chevron.alpha = 1f
                    chevron.rotation = expandedChevronAngle
                }
                Collapsed.Yes -> {
                    chevron.alpha = 1f
                    chevron.rotation = collapsedChevronAngle
                }
                Collapsed.NotCollapsible -> {
                    chevron.alpha = 0f
                }
            }

            chevron.isClickable = item.collapsed != Collapsed.NotCollapsible

            checkbox.isVisible = item.checked != Checked.NotCheckable
            checkbox.isChecked = item.checked == Checked.Yes
        }

        fun bindCollapsedAndCheckedOnly(item: Item) {
            chevron.animate().apply {
                when (item.collapsed) {
                    Collapsed.No -> rotation(expandedChevronAngle).alpha(1f)
                    Collapsed.Yes -> rotation(collapsedChevronAngle).alpha(1f)
                    Collapsed.NotCollapsible -> alpha(0f)
                }
            }.start()

            chevron.isClickable = item.collapsed != Collapsed.NotCollapsible

            checkbox.isVisible = item.checked != Checked.NotCheckable
            checkbox.isChecked = item.checked == Checked.Yes
        }
    }

    override fun getItemId(position: Int) = items[position].id

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(inflater.inflate(R.layout.bottom_sheet_item, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.fullBind(items[position])
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.any { it is UpdateCollapsedOrCheckedOnly }) {
            holder.bindCollapsedAndCheckedOnly(items[position])
        } else {
            holder.fullBind(items[position])
        }
    }

    private val mandatoryMargin = convertDpToPixel(0f, context)
    private val indentMargin = convertDpToPixel(24f, context)

    private val inflater = LayoutInflater.from(context)

    private val expandedChevronAngle = if (context.isRtl) -90f else 90f
    private val collapsedChevronAngle = 0f
}

private val Context.isRtl get() = resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL

private infix fun <E> Set<E>.xor(element: E) =
    if (contains(element)) this - element else this + element

// TODO: Context Parameter
private fun View.setOnClickListenerWithId(
    viewHolder: RecyclerView.ViewHolder,
    block: (Long) -> Unit
) {
    setOnClickListener {
        // TODO: The ripple effect does not occur properly on click
        val id = viewHolder.itemId
        if (id != NO_ID) block(id)
    }
}

private abstract class ListDiffCallback<T>(val old: List<T>, val new: List<T>) : DiffUtil.Callback() {
    abstract fun areItemsTheSame(old: T, new: T): Boolean
    abstract fun areContentsTheSame(old: T, new: T): Boolean
    abstract fun getChangePayload(old: T, new: T): Any?

    override fun getOldListSize() = old.size
    override fun getNewListSize() = new.size
    override fun areItemsTheSame(oldPos: Int, newPos: Int) = areItemsTheSame(old[oldPos], new[newPos])
    override fun areContentsTheSame(oldPos: Int, newPos: Int) = areContentsTheSame(old[oldPos], new[newPos])
    override fun getChangePayload(oldPos: Int, newPos: Int) = getChangePayload(old[oldPos], new[newPos])
}
