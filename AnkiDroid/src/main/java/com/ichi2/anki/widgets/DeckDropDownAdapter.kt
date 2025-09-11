/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.DropdownDeckItemBinding
import com.ichi2.anki.databinding.DropdownDeckSelectedItemBinding
import com.ichi2.anki.libanki.DeckNameId

class DeckDropDownAdapter(
    private val context: Context,
    private val subtitleProvider: SubtitleProvider?,
    decks: List<DeckNameId>,
) : BaseAdapter() {
    private val deckList = decks.toMutableList()
    val decks: List<DeckNameId>
        get() = deckList.toList()

    /**
     * A class providing this interface must provide a subtitle to display under the deck name when the deck drop-down menu is closed.
     */
    interface SubtitleProvider {
        /**
         * The subtitle to the closed deck drop-down menu.
         */
        val deckDropDownSubtitle: String
    }

    internal data class DeckDropDownViewHolder(
        val binding: DropdownDeckSelectedItemBinding,
    )

    fun addDeck(deck: DeckNameId) {
        deckList.add(deck)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = deckList.size + 1

    override fun getItem(position: Int): Any? =
        if (position == 0) {
            null
        } else {
            deckList[position + 1]
        }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(
        position: Int,
        view: View?,
        parent: ViewGroup,
    ): View? {
        var convertView = view
        val viewHolder: DeckDropDownViewHolder
        if (convertView == null) {
            val layoutInflater = LayoutInflater.from(context)
            val binding = DropdownDeckSelectedItemBinding.inflate(layoutInflater, parent, false)
            convertView = binding.root
            viewHolder = DeckDropDownViewHolder(binding)
            convertView.tag = viewHolder
        } else {
            viewHolder = convertView.tag as DeckDropDownViewHolder
        }
        if (position == 0) {
            viewHolder.binding.deckName.text = context.resources.getString(R.string.card_browser_all_decks)
        } else {
            val deck = deckList[position - 1]
            val deckName = deck.name
            viewHolder.binding.deckName.text = deckName
        }
        viewHolder.binding.deckCounts.text = subtitleProvider?.deckDropDownSubtitle ?: ""
        return viewHolder.binding.root
    }

    override fun getDropDownView(
        position: Int,
        view: View?,
        parent: ViewGroup,
    ): View? {
        var convertView = view // support mutation of view
        val deckNameView: TextView
        if (convertView == null) {
            val layoutInflater = LayoutInflater.from(context)
            val binding = DropdownDeckItemBinding.inflate(layoutInflater, parent, false)
            deckNameView = binding.dropdownDeckName
            binding.root.tag = deckNameView
            convertView = binding.root
        } else {
            deckNameView = view.tag as TextView
        }
        if (position == 0) {
            deckNameView.text = context.resources.getString(R.string.card_browser_all_decks)
        } else {
            val deck = deckList[position - 1]
            val deckName = deck.name
            deckNameView.text = deckName
        }
        return convertView
    }
}
