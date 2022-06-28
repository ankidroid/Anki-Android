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
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StringRes
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

/**
 * This class handles the Filter Bottom Sheet present in the Card Browser
 * This class is used to apply filters for search queries
 */
class FilterSheetBottomFragment :
    BottomSheetDialogFragment(),
    FlagsAdapter.OnItemClickListener,
    CollectionGetter {
    private lateinit var behavior: BottomSheetBehavior<View>

    private var flagSearchItems = mutableListOf<String>()

    private lateinit var mFlagRecyclerView: RecyclerView

    private var mLastClickTime = 0

    // flagName is displayed in filter sheet as the name of the filter
    enum class Flags(@StringRes private val flagNameRes: Int, val flagNumber: Int) {
        RED(R.string.menu_flag_card_one, 1),
        ORANGE(R.string.menu_flag_card_two, 2),
        GREEN(R.string.menu_flag_card_three, 3),
        BLUE(R.string.menu_flag_card_four, 4),
        PINK(R.string.menu_flag_card_five, 5),
        TURQUOISE(R.string.menu_flag_card_six, 6),
        PURPLE(R.string.menu_flag_card_seven, 7);

        fun getFlagName(context: Context): String = context.getString(flagNameRes)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.filter_bottom_sheet, container, false)

        val applyButton = view.findViewById<Button>(R.id.apply_filter_button)
        applyButton.setOnClickListener {
            val filterQuery = createQuery(flagSearchItems)

            (activity as CardBrowser).searchWithFilterQuery(filterQuery)
            dismiss()
        }

        val cancelButton = view.findViewById<Button>(R.id.cancel_filter_button)
        cancelButton.setOnClickListener {
            dismiss()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        behavior = BottomSheetBehavior.from(requireView().parent as View)

        /* list of all flags */

        val flagListAdapter = FlagsAdapter(activity, Flags.values(), this)

        mFlagRecyclerView = requireView().findViewById(R.id.filter_bottom_flag_list)
        mFlagRecyclerView.layoutManager = LinearLayoutManager(activity)

        mFlagRecyclerView.adapter = flagListAdapter
        mFlagRecyclerView.addItemDecoration(
            DividerItemDecoration(
                activity,
                DividerItemDecoration.VERTICAL
            )
        )

        /*
         * Set the filter headings to be clickable:
         * Show/Hide the filter list on clicking
         */

        val flagsButton = requireView().findViewById<LinearLayout>(R.id.filterByFlagsText)
        val flagIcon = requireView().findViewById<ImageView>(R.id.filter_flagListToggle)
        val flagsRecyclerViewLayout =
            requireView().findViewById<LinearLayout>(R.id.flagsRecyclerViewLayout)

        flagsButton.setOnClickListener {

            if (SystemClock.elapsedRealtime() - mLastClickTime > DELAY_TIME) {

                mLastClickTime = SystemClock.elapsedRealtime().toInt()

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
        flagList: MutableList<String>
    ): StringBuffer {

        val filterQuery = StringBuffer()

        if (flagList.isEmpty()) {
            return filterQuery
        }

        for (flagIndex in flagList.indices) {
            filterQuery.append(
                if (flagIndex == 0) {
                    "flag:${flagList[flagIndex]}"
                } else {
                    " OR flag:${flagList[flagIndex]}"
                }
            )
        }

        return filterQuery
    }

    private fun clearQuery() {
        flagSearchItems.clear()
    }

    companion object {
        const val TAG = "ModalBottomSheet"
        private const val DELAY_TIME = 500
    }

    override fun getCol(): Collection {
        return CollectionHelper.getInstance().getCol(activity)
    }

    /**
     * Add/remove items from list of selected filters
     * Change background color accordingly
     * TODO: background color should be retained if selected and swiped down
     */

    override fun onFlagItemClicked(item: Flags, position: Int) {

        val itemBackground: ColorDrawable = mFlagRecyclerView[position].background as ColorDrawable
        val itemTextView = mFlagRecyclerView[position].findViewById<TextView>(R.id.filter_list_item)

        if (itemBackground.color == Themes.getColorFromAttr(
                activity,
                R.attr.filterItemBackground
            )
        ) {
            mFlagRecyclerView[position].setBackgroundColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemBackgroundSelected
                )
            )

            itemTextView.setTextColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemTextColorSelected
                )
            )

            flagSearchItems.add("${item.flagNumber}")
        } else {
            mFlagRecyclerView[position].setBackgroundColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemBackground
                )
            )

            itemTextView.setTextColor(
                Themes.getColorFromAttr(
                    activity,
                    R.attr.filterItemTextColor
                )
            )

            flagSearchItems.remove("${item.flagNumber}")
        }
    }
}

class FlagsAdapter(
    val context: Context?,
    private var dataSet: Array<FilterSheetBottomFragment.Flags>,
    private val listener: OnItemClickListener
) :
    RecyclerView.Adapter<FlagsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val item: TextView = view.findViewById(R.id.filter_list_item)

        fun bind(
            currFlag: FilterSheetBottomFragment.Flags,
            clickListener: OnItemClickListener,
            position: Int
        ) {
            item.text = currFlag.getFlagName(itemView.context)

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

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val currTag = dataSet[position]
        viewHolder.bind(currTag, listener, position)
    }

    override fun getItemCount() = dataSet.size

    interface OnItemClickListener {
        fun onFlagItemClicked(item: FilterSheetBottomFragment.Flags, position: Int)
    }
}
