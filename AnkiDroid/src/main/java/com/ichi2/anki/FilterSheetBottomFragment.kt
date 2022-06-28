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
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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

class FilterSheetBottomFragment :
    BottomSheetDialogFragment(),
    FlagsAdapter.OnItemClickListener,
    CollectionGetter {
    private lateinit var behavior: BottomSheetBehavior<View>

    var flagSearchItems = mutableListOf<String>()

    private lateinit var mFlagRecyclerView: RecyclerView

    private var mLastClickTime = 0

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

    @SuppressLint("VariableNamingDetector", "DirectToastMakeTextUsage")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        behavior = BottomSheetBehavior.from(requireView().parent as View)

        /* list of all flags */
        val flags = listOf(
            FilterItemFlag("Red flag", 1),
            FilterItemFlag("Orange flag", 2),
            FilterItemFlag("Green flag", 3),
            FilterItemFlag("Blue flag", 4),
            FilterItemFlag("Pink flag", 5),
            FilterItemFlag("Turquoise flag", 6),
            FilterItemFlag("Purple flag", 7)
        )

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

        /**
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

    override fun onFlagItemClicked(item: FilterItemFlag, position: Int) {

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

class FilterItemFlag(val name: String, val flagNumber: Int)

class FlagsAdapter(
    val context: Context?,
    private var dataSet: List<FilterItemFlag>,
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
            currFlag: FilterItemFlag,
            clickListener: OnItemClickListener,
            position: Int
        ) {
            name.text = currFlag.name

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
        fun onFlagItemClicked(item: FilterItemFlag, position: Int)
    }
}
