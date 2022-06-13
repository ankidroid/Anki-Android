/****************************************************************************************
 * Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>                             *
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

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

public class BottomSheetFragment : BottomSheetDialogFragment(), OnItemClickListener {
    private lateinit var behavior: BottomSheetBehavior<View>

    override fun onStart() {
        super.onStart()

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.filter_bottom_sheet, container, false)
    }

    @SuppressLint("DirectToastMakeTextUsage")
    override fun onItemClicked(item: DeckName) {

        CardBrowser().searchCards("deck:test")

        Toast.makeText(activity, "Deck name: ${item.deckName}", Toast.LENGTH_SHORT)
            .show()
    }

    @SuppressLint("VariableNamingDetector")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.skipCollapsed = true

        val mRecyclerView = requireView().findViewById<RecyclerView>(R.id.filter_bottom_deck_list)

        mRecyclerView.layoutManager = LinearLayoutManager(activity)

        val d1 = DeckName("GSoC Test Deck")
        val d2 = DeckName("Test")
        val d3 = DeckName("GSoC Test Deck")
        val d4 = DeckName("Test")
        val d5 = DeckName("GSoC Test Deck")
        val d6 = DeckName("Test")
        val d7 = DeckName("GSoC Test Deck")
        val d8 = DeckName("Test")

        val decks = mutableListOf(d1, d2, d3, d4, d5, d6, d7, d8)

        val mDeckListAdapter = DeckNamesAdapter(activity, decks, this)

        mRecyclerView.adapter = mDeckListAdapter

        mRecyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
    }

    companion object {
        const val TAG = "ModalBottomSheet"
    }
}

class DeckName {
    val deckName: String

    constructor(deckName: String) {
        this.deckName = deckName
    }
}

class DeckNamesAdapter(val context: Context?, private var dataSet: MutableList<DeckName>, private val listener: OnItemClickListener) :
    RecyclerView.Adapter<DeckNamesAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deckName: TextView = view.findViewById(R.id.deck_name_text)

        fun bind(currDeck: DeckName, clickListener: OnItemClickListener) {
            deckName.text = currDeck.deckName

            itemView.setOnClickListener {
                clickListener.onItemClicked(currDeck)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.filter_deck_names_view, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val currDeck = dataSet[position]

        // Get element from your dataset at this position and replace the
        // contents of the view with that element

        viewHolder.bind(currDeck, listener)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size
}

interface OnItemClickListener {
    fun onItemClicked(item: DeckName)
}
