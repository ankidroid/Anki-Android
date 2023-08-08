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
import com.ichi2.libanki.DeckNameId

class DeckDropDownAdapter(private val context: Context, private val decks: List<DeckNameId>) : BaseAdapter() {
    interface SubtitleListener {
        val subtitleText: String?
    }

    internal class DeckDropDownViewHolder {
        var deckNameView: TextView? = null
        var deckCountsView: TextView? = null
    }

    override fun getCount(): Int {
        return decks.size + 1
    }

    override fun getItem(position: Int): Any? {
        return if (position == 0) {
            null
        } else {
            decks[position + 1]
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View? {
        var convertView = view
        val viewHolder: DeckDropDownViewHolder
        val deckNameView: TextView?
        val deckCountsView: TextView?
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_selected_item, parent, false)
            deckNameView = convertView.findViewById(R.id.dropdown_deck_name)
            deckCountsView = convertView.findViewById(R.id.dropdown_deck_counts)
            viewHolder = DeckDropDownViewHolder()
            viewHolder.deckNameView = deckNameView
            viewHolder.deckCountsView = deckCountsView
            convertView.tag = viewHolder
        } else {
            viewHolder = convertView.tag as DeckDropDownViewHolder
            deckNameView = viewHolder.deckNameView
            deckCountsView = viewHolder.deckCountsView
        }
        if (position == 0) {
            deckNameView!!.text = context.resources.getString(R.string.card_browser_all_decks)
        } else {
            val deck = decks[position - 1]
            val deckName = deck.name
            deckNameView!!.text = deckName
        }
        deckCountsView!!.text = (context as SubtitleListener).subtitleText
        return convertView
    }

    override fun getDropDownView(position: Int, view: View?, parent: ViewGroup): View? {
        var convertView = view
        val deckNameView: TextView
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_item, parent, false)
            deckNameView = convertView.findViewById(R.id.dropdown_deck_name)
            convertView.tag = deckNameView
        } else {
            deckNameView = convertView.tag as TextView
        }
        if (position == 0) {
            deckNameView.text = context.resources.getString(R.string.card_browser_all_decks)
        } else {
            val deck = decks[position - 1]
            val deckName = deck.name
            deckNameView.text = deckName
        }
        return convertView
    }
}
