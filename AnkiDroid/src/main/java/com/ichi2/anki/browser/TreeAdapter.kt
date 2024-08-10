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
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID
import com.ichi2.anki.R
import com.ichi2.anki.browser.TreeAdapter.Item

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

    enum class Checked { Yes, No, NotCheckable }

    data class Item(
        val id: Long,
        val icon: Int,
        val text: CharSequence,
        val checked: Checked
    )

    var onItemClickedListener: ((Long) -> Unit) = { }
    var onItemCheckedListener: ((Long) -> Unit) = { }

    abstract fun updateCheckedItemIds(ids: Set<Long>)

    protected fun setItems(items: List<Item>, checkedItemIds: Set<Long>) {
        val diff = DiffUtil.calculateDiff(DiffCallback(this.items, items))

        this.items = items
        this.checkedItemIds = checkedItemIds

        diff.dispatchUpdatesTo(this)
    }

    object UpdateCollapsedOrCheckedOnly

    private class DiffCallback(old: List<Item>, new: List<Item>) : ListDiffCallback<Item>(old, new) {
        override fun areItemsTheSame(old: Item, new: Item) = old.id == new.id
        override fun areContentsTheSame(old: Item, new: Item) = old == new
        override fun getChangePayload(old: Item, new: Item) =
            if (old.icon != new.icon || old.text != new.text) {
                null
            } else {
                UpdateCollapsedOrCheckedOnly
            }
    }

    var checkedItemIds = setOf<Long>()
        private set

    private var items: List<Item> = listOf()

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val text: TextView = view.findViewById(R.id.text)
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
            text.text = item.text

            icon.setImageDrawable(AppCompatResources.getDrawable(context, item.icon))

            checkbox.isVisible = item.checked != Checked.NotCheckable
            checkbox.isChecked = item.checked == Checked.Yes
        }

        fun bindChecked(item: Item) {
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
            holder.bindChecked(items[position])
        } else {
            holder.fullBind(items[position])
        }
    }

    private val inflater = LayoutInflater.from(context)
}

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
