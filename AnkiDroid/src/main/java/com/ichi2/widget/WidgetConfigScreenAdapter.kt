/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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

package com.ichi2.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter class for displaying and managing a list of selectable decks in a RecyclerView.
 *
 * @property decks the list of selectable decks to display
 * @property onDeleteDeck a function to call when a deck is removed
 */
class WidgetConfigScreenAdapter(
    private val onDeleteDeck: (SelectableDeck, Int) -> Unit
) : RecyclerView.Adapter<WidgetConfigScreenAdapter.DeckViewHolder>() {

    private val decks: MutableList<SelectableDeck> = mutableListOf()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Property to get the list of deck IDs
    val deckIds: List<Long> get() = decks.map { it.deckId }

    class DeckViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deckNameTextView: TextView = itemView.findViewById(R.id.deck_name)
        val removeButton: ImageButton = itemView.findViewById(R.id.action_button_remove_deck)
    }

    /** Creates and inflates the view for each item in the RecyclerView
     * @param parent the parent ViewGroup
     * @param viewType the type of the view
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.widget_item_deck_config, parent, false)
        return DeckViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeckViewHolder, position: Int) {
        val deck = decks[position]

        coroutineScope.launch {
            val deckName = withContext(Dispatchers.IO) {
                withCol { decks.get(deck.deckId)!!.name }
            }
            holder.deckNameTextView.text = deckName
        }

        holder.removeButton.setOnClickListener {
            onDeleteDeck(deck, position)
        }
    }

    override fun getItemCount(): Int = decks.size

    fun addDeck(deck: SelectableDeck) {
        decks.add(deck)
        notifyItemInserted(decks.size - 1)
    }

    fun removeDeck(deckId: Long) {
        // Find the position of the deck with the given ID
        val position = decks.indexOfFirst { it.deckId == deckId }
        if (position != -1) {
            decks.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun moveDeck(fromPosition: Int, toPosition: Int) {
        val deck = decks.removeAt(fromPosition)
        decks.add(toPosition, deck)
        notifyItemMoved(fromPosition, toPosition)
    }
}
