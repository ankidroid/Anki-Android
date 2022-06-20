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
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.libanki.Collection
import com.ichi2.libanki.CollectionGetter
import com.ichi2.themes.Themes

@Suppress("DEPRECATION")
class BottomSheetFragment :
    BottomSheetDialogFragment(),
    DeckNamesAdapter.OnDeckItemClickListener,
    TagsAdapter.OnItemClickListener,
    FlagsAdapter.OnItemClickListener,
    NoteTypesAdapter.OnItemClickListener,
    CardStateAdapter.OnItemClickListener,
    CollectionGetter {
    private lateinit var behavior: BottomSheetBehavior<View>

    var deckSearchItems = mutableListOf<String>()
    var tagSearchItems = mutableListOf<String>()
    var flagSearchItems = mutableListOf<String>()
    var noteTypeSearchItems = mutableListOf<String>()
    var cardStateSearchItems = mutableListOf<String>()

    private lateinit var mDeckRecyclerView: RecyclerView
    private lateinit var mTagRecyclerView: RecyclerView
    private lateinit var mFlagRecyclerView: RecyclerView
    private lateinit var mNoteTypeRecyclerView: RecyclerView
    private lateinit var mCardStateRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.filter_bottom_sheet, container, false)

        val applyButton = view.findViewById<Button>(R.id.apply_filter_button)
        applyButton.setOnClickListener {
            val filterQuery = createQuery(
                deckSearchItems, tagSearchItems, flagSearchItems, noteTypeSearchItems, cardStateSearchItems
            )

            (activity as CardBrowser).searchWithFilterQuery(filterQuery)
            dismiss()
        }

        val cancelButton = view.findViewById<Button>(R.id.cancel_filter_button)
        cancelButton.setOnClickListener {
            dismiss()
        }
        return view
    }

    @SuppressLint("VariableNamingDetector", "DirectToastMakeTextUsage")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.skipCollapsed = true

        /* list of all deck names */
        mDeckRecyclerView = requireView().findViewById(R.id.filter_bottom_deck_list)
        mDeckRecyclerView.layoutManager = LinearLayoutManager(activity)

        val deckNames = col.decks.allNames().sorted()
        val mDeckListAdapter = DeckNamesAdapter(activity, deckNames, this)

        mDeckRecyclerView.adapter = mDeckListAdapter
        mDeckRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                DividerItemDecoration.VERTICAL
            )
        )

        /* list of all tag names */
        val tags = col.tags.all().sorted()
        val mTagListAdapter = TagsAdapter(activity, tags, this)

        mTagRecyclerView = requireView().findViewById(R.id.filter_bottom_tag_list)
        mTagRecyclerView.layoutManager = LinearLayoutManager(activity)

        mTagRecyclerView.adapter = mTagListAdapter
        mTagRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                DividerItemDecoration.VERTICAL
            )
        )

        /* list of all flags */
        val flags = listOf("Red flag", "Orange flag", "Green flag", "Blue flag", "Pink flag", "Turquoise flag", "Pink flag")
        val mFlagListAdapter = FlagsAdapter(activity, flags, this)

        mFlagRecyclerView = requireView().findViewById(R.id.filter_bottom_flag_list)
        mFlagRecyclerView.layoutManager = LinearLayoutManager(activity)

        mFlagRecyclerView.adapter = mFlagListAdapter
        mFlagRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                DividerItemDecoration.VERTICAL
            )
        )

        /* list of all note types */
        val noteTypes = col.models.allNames()
        val mNoteTypeListAdapter = NoteTypesAdapter(activity, noteTypes, this)

        mNoteTypeRecyclerView = requireView().findViewById(R.id.filter_bottom_noteType_list)
        mNoteTypeRecyclerView.layoutManager = LinearLayoutManager(activity)

        mNoteTypeRecyclerView.adapter = mNoteTypeListAdapter
        mNoteTypeRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                DividerItemDecoration.VERTICAL
            )
        )

        /* list of card states */
        val cardStates = listOf("New", "Learn", "Review", "Suspended", "Buried")
        val mCardStateListAdapter = CardStateAdapter(activity, cardStates, this)

        mCardStateRecyclerView = requireView().findViewById(R.id.filter_bottom_cardState_list)
        mCardStateRecyclerView.layoutManager = LinearLayoutManager(activity)

        mCardStateRecyclerView.adapter = mCardStateListAdapter
        mCardStateRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                DividerItemDecoration.VERTICAL
            )
        )

        /**
         * Set the filter headings to be clickable:
         * Show/Hide the filter list on clicking
         */

        val decksButton = requireView().findViewById<LinearLayout>(R.id.filterByDecksText)
        val deckIcon = requireView().findViewById<ImageView>(R.id.filter_deckListToggle)
        val decksRecyclerViewLayout =
            requireView().findViewById<LinearLayout>(R.id.decksRecyclerViewLayout)
        decksButton.setOnClickListener {
            if (decksRecyclerViewLayout.isVisible) {
                decksRecyclerViewLayout.visibility = View.GONE
                deckIcon.setImageResource(R.drawable.filter_sheet_unopened_list_icon)
            } else {
                decksRecyclerViewLayout.visibility = View.VISIBLE
                deckIcon.setImageResource(R.drawable.filter_sheet_opened_list_icon)
            }
        }

        val tagsButton = requireView().findViewById<LinearLayout>(R.id.filterByTagsText)
        val tagIcon = requireView().findViewById<ImageView>(R.id.filter_tagListToggle)
        val tagsRecyclerViewLayout =
            requireView().findViewById<LinearLayout>(R.id.tagsRecyclerViewLayout)
        tagsButton.setOnClickListener {
            if (tagsRecyclerViewLayout.isVisible) {
                tagsRecyclerViewLayout.visibility = View.GONE
                tagIcon.setImageResource(R.drawable.filter_sheet_unopened_list_icon)
            } else {
                tagsRecyclerViewLayout.visibility = View.VISIBLE
                tagIcon.setImageResource(R.drawable.filter_sheet_opened_list_icon)
            }
        }

        val flagsButton = requireView().findViewById<LinearLayout>(R.id.filterByFlagsText)
        val flagIcon = requireView().findViewById<ImageView>(R.id.filter_flagListToggle)
        val flagsRecyclerViewLayout =
            requireView().findViewById<LinearLayout>(R.id.flagsRecyclerViewLayout)
        flagsButton.setOnClickListener {
            if (flagsRecyclerViewLayout.isVisible) {
                flagsRecyclerViewLayout.visibility = View.GONE
                flagIcon.setImageResource(R.drawable.filter_sheet_unopened_list_icon)
            } else {
                flagsRecyclerViewLayout.visibility = View.VISIBLE
                flagIcon.setImageResource(R.drawable.filter_sheet_opened_list_icon)
            }
        }

        val noteTypesButton = requireView().findViewById<LinearLayout>(R.id.filterByNoteTypesText)
        val noteTypeIcon = requireView().findViewById<ImageView>(R.id.filter_noteTypesListToggle)
        val noteTypesRecyclerViewLayout =
            requireView().findViewById<LinearLayout>(R.id.noteTypesRecyclerViewLayout)
        noteTypesButton.setOnClickListener {
            if (noteTypesRecyclerViewLayout.isVisible) {
                noteTypesRecyclerViewLayout.visibility = View.GONE
                noteTypeIcon.setImageResource(R.drawable.filter_sheet_unopened_list_icon)
            } else {
                noteTypesRecyclerViewLayout.visibility = View.VISIBLE
                noteTypeIcon.setImageResource(R.drawable.filter_sheet_opened_list_icon)
            }
        }

        val cardStatesButton = requireView().findViewById<LinearLayout>(R.id.filterByCardStatesText)
        val cardStateIcon = requireView().findViewById<ImageView>(R.id.filter_cardStateListToggle)
        val cardStatesRecyclerViewLayout =
            requireView().findViewById<LinearLayout>(R.id.cardStatesRecyclerViewLayout)
        cardStatesButton.setOnClickListener {
            if (cardStatesRecyclerViewLayout.isVisible) {
                cardStatesRecyclerViewLayout.visibility = View.GONE
                cardStateIcon.setImageResource(R.drawable.filter_sheet_unopened_list_icon)
            } else {
                cardStatesRecyclerViewLayout.visibility = View.VISIBLE
                cardStateIcon.setImageResource(R.drawable.filter_sheet_opened_list_icon)
            }
        }
    }

    /**
     * Clear the filters if Bottom Sheet is dismissed
     * TODO: Filters should be retained on swiping down
     * (user might swipe down accidentally)
     */
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        clearQuery()
    }

    private fun createQuery(
        deckList: MutableList<String>,
        tagList: MutableList<String>,
        flagList: MutableList<String>,
        noteTypeList: MutableList<String>,
        cardStateList: MutableList<String>
    ): String {

        var filterQuery = ""

        if (deckList.isNotEmpty()) {
            for (deckIndex in deckList.indices) {
                filterQuery += if (deckIndex == 0) {
                    "(deck:'${deckList[deckIndex]}'"
                } else {
                    " OR deck:'${deckList[deckIndex]}'"
                }
            }
            filterQuery += ")"
        }

        if (tagList.isNotEmpty()) {
            for (tagIndex in tagList.indices) {
                filterQuery += if (tagIndex == 0) {
                    "(tag:'${tagList[tagIndex]}'"
                } else {
                    " OR tag:'${tagList[tagIndex]}'"
                }
            }
            filterQuery += ')'
        }

        if (flagList.isNotEmpty()) {
            for (flagIndex in flagList.indices) {
                filterQuery += if (flagIndex == 0) {
                    "(flag:'${flagList[flagIndex]}'"
                } else {
                    " OR flag:'${flagList[flagIndex]}'"
                }
            }
            filterQuery += ')'
        }

        if (noteTypeList.isNotEmpty()) {
            for (noteTypeIndex in noteTypeList.indices) {
                filterQuery += if (noteTypeIndex == 0) {
                    "(note:'${noteTypeList[noteTypeIndex]}'"
                } else {
                    " OR note:'${noteTypeList[noteTypeIndex]}'"
                }
            }
            filterQuery += ')'
        }

        if (cardStateList.isNotEmpty()) {
            for (cardStateIndex in cardStateList.indices) {
                filterQuery += if (cardStateIndex == 0) {
                    "(is:'${cardStateList[cardStateIndex]}'"
                } else {
                    " OR is:'${cardStateList[cardStateIndex]}'"
                }
            }
            filterQuery += ')'
        }

        return filterQuery
    }

    private fun clearQuery() {
        tagSearchItems.clear()
        deckSearchItems.clear()
        flagSearchItems.clear()
        noteTypeSearchItems.clear()
        cardStateSearchItems.clear()
    }

    companion object {
        const val TAG = "ModalBottomSheet"
    }

    override fun getCol(): Collection {
        return CollectionHelper.getInstance().getCol(activity)
    }

    /**
     * Add/remove items from list of selected filters
     * Change background color accordingly
     * TODO: background color should be retained if selected and swiped down
     */
    override fun onDeckItemClicked(item: String, position: Int) {

        val itemBackground: ColorDrawable = mDeckRecyclerView[position].background as ColorDrawable

        if (itemBackground.color == Themes.getColorFromAttr(
                activity,
                R.attr.filterItemBackground
            )
        ) {
            mDeckRecyclerView[position].setBackgroundColor(
                ContextCompat.getColor(requireActivity(), R.color.material_light_blue_300)
            )

            deckSearchItems.add(item)
        } else {
            mDeckRecyclerView[position].setBackgroundColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemBackground
                )
            )

            deckSearchItems.remove(item)
        }
    }

    override fun onTagItemClicked(item: String, position: Int) {

        val itemBackground: ColorDrawable = mTagRecyclerView[position].background as ColorDrawable

        if (itemBackground.color == Themes.getColorFromAttr(
                activity,
                R.attr.filterItemBackground
            )
        ) {
            mTagRecyclerView[position].setBackgroundColor(
                ContextCompat.getColor(requireActivity(), R.color.material_light_blue_300)
            )

            tagSearchItems.add(item)
        } else {
            mTagRecyclerView[position].setBackgroundColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemBackground
                )
            )

            tagSearchItems.remove(item)
        }
    }

    override fun onFlagItemClicked(item: String, position: Int) {

        var flagNumber = "0"

        when (item) {
            "Red flag" -> {
                flagNumber = "1"
            }
            "Orange flag" -> {
                flagNumber = "2"
            }
            "Green flag" -> {
                flagNumber = "3"
            }
            "Blue flag" -> {
                flagNumber = "4"
            }
            "Pink flag" -> {
                flagNumber = "5"
            }
            "Turquoise flag" -> {
                flagNumber = "6"
            }
            "Purple flag" -> {
                flagNumber = "7"
            }
        }

        val itemBackground: ColorDrawable = mFlagRecyclerView[position].background as ColorDrawable

        if (itemBackground.color == Themes.getColorFromAttr(
                activity,
                R.attr.filterItemBackground
            )
        ) {
            mFlagRecyclerView[position].setBackgroundColor(
                ContextCompat.getColor(requireActivity(), R.color.material_light_blue_300)
            )

            flagSearchItems.add(flagNumber)
        } else {
            mFlagRecyclerView[position].setBackgroundColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemBackground
                )
            )

            flagSearchItems.remove(flagNumber)
        }
    }

    override fun onNoteTypeItemClicked(item: String, position: Int) {

        val itemBackground: ColorDrawable = mNoteTypeRecyclerView[position].background as ColorDrawable

        if (itemBackground.color == Themes.getColorFromAttr(
                activity,
                R.attr.filterItemBackground
            )
        ) {
            mNoteTypeRecyclerView[position].setBackgroundColor(
                ContextCompat.getColor(requireActivity(), R.color.material_light_blue_300)
            )

            noteTypeSearchItems.add(item)
        } else {
            mNoteTypeRecyclerView[position].setBackgroundColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemBackground
                )
            )

            noteTypeSearchItems.remove(item)
        }
    }

    override fun onCardStateItemClicked(item: String, position: Int) {

        val itemBackground: ColorDrawable = mCardStateRecyclerView[position].background as ColorDrawable

        if (itemBackground.color == Themes.getColorFromAttr(
                activity,
                R.attr.filterItemBackground
            )
        ) {
            mCardStateRecyclerView[position].setBackgroundColor(
                ContextCompat.getColor(requireActivity(), R.color.material_light_blue_300)
            )

            cardStateSearchItems.add(item)
        } else {
            mCardStateRecyclerView[position].setBackgroundColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemBackground
                )
            )

            cardStateSearchItems.remove(item)
        }
    }
}

/**
 * Adapters for the different types of filters
 * Each filter has a different adapter because
 * all of them are handled _slightly_ differently
 * TODO: can these be unified?
 */
class DeckNamesAdapter(
    val context: Context?,
    private var dataSet: List<String>,
    private val listener: OnDeckItemClickListener
) :
    RecyclerView.Adapter<DeckNamesAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deckName: TextView = view.findViewById(R.id.filter_list_item)

        fun bind(
            currDeck: String,
            clickListener: OnDeckItemClickListener,
            position: Int
        ) {
            deckName.text = currDeck

            itemView.setOnClickListener {
                clickListener.onDeckItemClicked(currDeck, position)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.filter_list_item_layout, viewGroup, false)

        val deckName: TextView = view.findViewById<TextView>(R.id.filter_list_item)

        if (BottomSheetFragment().deckSearchItems.contains(deckName.text)) {
            view.setBackgroundColor(
                ContextCompat.getColor(view.context, R.color.material_light_blue_300)
            )
        }

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val currDeck = dataSet[position]

        // Get element from your dataset at this position and replace the
        // contents of the view with that element

        viewHolder.bind(currDeck, listener, position)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    interface OnDeckItemClickListener {
        fun onDeckItemClicked(item: String, position: Int)
    }
}

class TagsAdapter(
    val context: Context?,
    private var dataSet: List<String>,
    private val listener: OnItemClickListener
) :
    RecyclerView.Adapter<TagsAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deckName: TextView = view.findViewById(R.id.filter_list_item)

        fun bind(
            currTag: String,
            clickListener: OnItemClickListener,
            position: Int
        ) {
            deckName.text = currTag

            itemView.setOnClickListener {
                clickListener.onTagItemClicked(currTag, position)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.filter_list_item_layout, viewGroup, false)

        val itemName: TextView = view.findViewById<TextView>(R.id.filter_list_item)

        if (BottomSheetFragment().tagSearchItems.contains(itemName.text)) {
            view.setBackgroundColor(
                ContextCompat.getColor(view.context, R.color.material_light_blue_300)
            )
        }

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val currTag = dataSet[position]

        // Get element from your dataset at this position and replace the
        // contents of the view with that element

        viewHolder.bind(currTag, listener, position)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    interface OnItemClickListener {
        fun onTagItemClicked(item: String, position: Int)
    }
}

class FlagsAdapter(
    val context: Context?,
    private var dataSet: List<String>,
    private val listener: OnItemClickListener
) :
    RecyclerView.Adapter<FlagsAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.filter_list_item)

        fun bind(
            currFlag: String,
            clickListener: OnItemClickListener,
            position: Int
        ) {
            name.text = currFlag

            itemView.setOnClickListener {
                clickListener.onFlagItemClicked(currFlag, position)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.filter_list_item_layout, viewGroup, false)

        val itemName: TextView = view.findViewById<TextView>(R.id.filter_list_item)

        if (BottomSheetFragment().flagSearchItems.contains(itemName.text)) {
            view.setBackgroundColor(
                ContextCompat.getColor(view.context, R.color.material_light_blue_300)
            )
        }

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val currTag = dataSet[position]

        // Get element from your dataset at this position and replace the
        // contents of the view with that element

        viewHolder.bind(currTag, listener, position)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    interface OnItemClickListener {
        fun onFlagItemClicked(item: String, position: Int)
    }
}

class NoteTypesAdapter(
    val context: Context?,
    private var dataSet: List<String>,
    private val listener: OnItemClickListener
) :
    RecyclerView.Adapter<NoteTypesAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deckName: TextView = view.findViewById(R.id.filter_list_item)

        fun bind(
            currTag: String,
            clickListener: OnItemClickListener,
            position: Int
        ) {
            deckName.text = currTag

            itemView.setOnClickListener {
                clickListener.onNoteTypeItemClicked(currTag, position)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.filter_list_item_layout, viewGroup, false)

        val itemName: TextView = view.findViewById<TextView>(R.id.filter_list_item)

        if (BottomSheetFragment().noteTypeSearchItems.contains(itemName.text)) {
            view.setBackgroundColor(
                ContextCompat.getColor(view.context, R.color.material_light_blue_300)
            )
        }

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val currTag = dataSet[position]

        // Get element from your dataset at this position and replace the
        // contents of the view with that element

        viewHolder.bind(currTag, listener, position)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    interface OnItemClickListener {
        fun onNoteTypeItemClicked(item: String, position: Int)
    }
}

class CardStateAdapter(
    val context: Context?,
    private var dataSet: List<String>,
    private val listener: OnItemClickListener
) :
    RecyclerView.Adapter<CardStateAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deckName: TextView = view.findViewById(R.id.filter_list_item)

        fun bind(
            currTag: String,
            clickListener: OnItemClickListener,
            position: Int
        ) {
            deckName.text = currTag

            itemView.setOnClickListener {
                clickListener.onCardStateItemClicked(currTag, position)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.filter_list_item_layout, viewGroup, false)

        val itemName: TextView = view.findViewById<TextView>(R.id.filter_list_item)

        if (BottomSheetFragment().cardStateSearchItems.contains(itemName.text)) {
            view.setBackgroundColor(
                ContextCompat.getColor(view.context, R.color.material_light_blue_300)
            )
        }

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val currTag = dataSet[position]

        // Get element from your dataset at this position and replace the
        // contents of the view with that element

        viewHolder.bind(currTag, listener, position)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

    interface OnItemClickListener {
        fun onCardStateItemClicked(item: String, position: Int)
    }
}
