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

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import anki.search.SearchNode
import anki.search.SearchNodeKt.group
import anki.search.searchNode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.libanki.Collection
import com.ichi2.libanki.CollectionGetter
import com.ichi2.libanki.bool
import com.ichi2.themes.Themes.getColorFromAttr

/**
 * This class handles the Filter Bottom Sheet present in the Card Browser
 * This class is used to apply filters for search queries
 */
class FilterSheetBottomFragment :
    BottomSheetDialogFragment(),
    CollectionGetter {
    private lateinit var behavior: BottomSheetBehavior<View>

    private var flagSearchItems = mutableListOf<SearchNode.Flag>()

    private lateinit var flagRecyclerView: RecyclerView
    private lateinit var flagListAdapter: FlagsAdapter

    private var lastClickTime = 0

    // flagName is displayed in filter sheet as the name of the filter
    enum class Flags(@StringRes private val flagNameRes: Int, val flagNode: SearchNode.Flag, @DrawableRes val flagIcon: Int) {
        NO_FLAG(R.string.menu_flag_card_zero, SearchNode.Flag.FLAG_NONE, R.drawable.label_icon_flags),
        RED(R.string.menu_flag_card_one, SearchNode.Flag.FLAG_RED, R.drawable.ic_flag_red),
        ORANGE(R.string.menu_flag_card_two, SearchNode.Flag.FLAG_ORANGE, R.drawable.ic_flag_orange),
        GREEN(R.string.menu_flag_card_three, SearchNode.Flag.FLAG_GREEN, R.drawable.ic_flag_green),
        BLUE(R.string.menu_flag_card_four, SearchNode.Flag.FLAG_BLUE, R.drawable.ic_flag_blue),
        PINK(R.string.menu_flag_card_five, SearchNode.Flag.FLAG_PINK, R.drawable.ic_flag_pink),
        TURQUOISE(R.string.menu_flag_card_six, SearchNode.Flag.FLAG_TURQUOISE, R.drawable.ic_flag_turquoise),
        PURPLE(R.string.menu_flag_card_seven, SearchNode.Flag.FLAG_PURPLE, R.drawable.ic_flag_purple);

        fun getFlagName(context: Context): String = context.getString(flagNameRes)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.filter_bottom_sheet, container, false).apply {
        val applyButton = this.findViewById<Button>(R.id.apply_filter_button)
        applyButton.setOnClickListener {
            val filterQuery = createQuery(flagSearchItems)

            if (filterQuery != "") {
                (activity as CardBrowser).searchWithFilterQuery(filterQuery)
            }
            dismiss()
        }

        val cancelButton = this.findViewById<Button>(R.id.cancel_filter_button)
        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        behavior = BottomSheetBehavior.from(requireView().parent as View)

        /* list of all flags */

        flagListAdapter = FlagsAdapter(Flags.values())

        flagRecyclerView = requireView().findViewById<RecyclerView?>(R.id.filter_bottom_flag_list).apply {
            this.layoutManager = LinearLayoutManager(activity)
            this.adapter = flagListAdapter
            this.addItemDecoration(
                DividerItemDecoration(
                    activity,
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        /*
         * Set the filter headings to be clickable:
         * Show/Hide the filter list on clicking
         */

        val flagsButton = requireView().findViewById<LinearLayout>(R.id.filterByFlagsText)
        val flagIcon = requireView().findViewById<ImageView>(R.id.filter_flagListToggle)
        val flagsRecyclerViewLayout =
            requireView().findViewById<LinearLayout>(R.id.flagsRecyclerViewLayout)

        flagsButton.setOnClickListener {

            if (SystemClock.elapsedRealtime() - lastClickTime > DELAY_TIME) {

                lastClickTime = SystemClock.elapsedRealtime().toInt()

                if (flagsRecyclerViewLayout.isVisible) {
                    flagsRecyclerViewLayout.visibility = View.GONE
                    flagIcon.setImageResource(R.drawable.filter_sheet_unopened_list_icon)
                } else {
                    flagsRecyclerViewLayout.visibility = View.VISIBLE
                    flagIcon.setImageResource(R.drawable.filter_sheet_opened_list_icon)
                }
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
        flagList: MutableList<SearchNode.Flag>
    ): String {

        if (flagList.isEmpty()) {
            return ""
        }

        val node = searchNode {
            group = group {
                joiner = SearchNode.Group.Joiner.OR

                for (flagNode in flagList) {
                    nodes += searchNode { flag = flagNode }
                }
            }
        }

        return col.buildSearchString(node)
    }

    private fun clearQuery() {
        flagSearchItems.clear()
    }

    companion object {
        const val TAG = "ModalBottomSheet"
        private const val DELAY_TIME = 500
    }

    override val col: Collection
        get() = CollectionHelper.instance.getCol(activity)!!

    /**
     * Add/remove items from list of selected filters
     * Change background color accordingly
     * TODO: background color should be retained if selected and swiped down
     */

    inner class FlagsAdapter(
        /** The collection of data to be displayed*/
        private var dataset: Array<Flags>,
    ) :
        RecyclerView.Adapter<FlagsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val itemTextView: TextView = view.findViewById(R.id.filter_list_item)
            val icon: ImageView = view.findViewById(R.id.filter_list_icon)

            private fun onFlagItemClicked(item: Flags, position: Int) {
                val itemTextView = flagRecyclerView[position].findViewById<TextView>(R.id.filter_list_item)

                if (!isSelected(item, flagSearchItems)) {
                    itemView.setBackgroundColor(getColorFromAttr(R.attr.filterItemBackgroundSelected))
                    itemTextView.setTextColor(getColorFromAttr(R.attr.filterItemTextColorSelected))

                    flagSearchItems.add(item.flagNode)
                } else {
                    flagRecyclerView[position].setBackgroundColor(getColorFromAttr(R.attr.filterItemBackground))
                    itemTextView.setTextColor(getColorFromAttr(R.attr.filterItemTextColor))

                    flagSearchItems.remove(item.flagNode)
                }
            }

            fun bind(
                currFlag: Flags,
                position: Int
            ) {
                itemTextView.text = currFlag.getFlagName(itemView.context)
                icon.setImageResource(currFlag.flagIcon)

                itemView.setOnClickListener {
                    onFlagItemClicked(currFlag, position)
                }
            }

            fun isSelected(flag: Flags, flagSearchItems: List<SearchNode.Flag>): bool {
                return flagSearchItems.contains(flag.flagNode)
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.filter_list_flag_item_layout, viewGroup, false)

            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val currTag = dataset[position]
            viewHolder.bind(currTag, position)
        }

        override fun getItemCount() = dataset.size
    }
}
