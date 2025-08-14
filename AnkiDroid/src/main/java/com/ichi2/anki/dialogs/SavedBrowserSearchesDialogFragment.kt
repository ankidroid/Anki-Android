/****************************************************************************************
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.utils.customListAdapter
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

/**
 * Dialog that displays the user's saved browser searches.
 */
// TODO modify the adapter to allow the user to delete multiple entries without dismissing the
//  dialog(or maybe even add an option to remove all entries directly)
class SavedBrowserSearchesDialogFragment : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val savedFilters: HashMap<String, String>? =
            requireArguments().getSerializableCompat(ARG_SAVED_FILTERS)
        val data =
            savedFilters
                ?.toList()
                ?.sortedWith { str1: Pair<String, String>, str2: Pair<String, String> ->
                    str1.first.compareTo(str2.first, ignoreCase = true)
                } ?: emptyList()
        val adapter =
            SavedSearchesAdapter(
                data,
                onSelection = { searchName ->
                    Timber.d("Saved search clicked: %s", searchName)
                    parentFragmentManager.setFragmentResult(
                        REQUEST_SAVED_SEARCH_ACTION,
                        bundleOf(
                            ARG_TYPE to TYPE_SEARCH_SELECTED,
                            ARG_SAVED_SEARCH to searchName,
                        ),
                    )
                    dismiss()
                },
                onRemoval = { searchName ->
                    Timber.d("Saved search delete button clicked: %s", searchName)
                    removeSearch(searchName)
                },
            )
        return AlertDialog
            .Builder(requireContext())
            .title(text = resources.getString(R.string.card_browser_list_my_searches_title))
            .negativeButton(R.string.dialog_cancel)
            .apply { customListAdapter(adapter) }
            .create()
    }

    private fun removeSearch(searchName: String) {
        AlertDialog.Builder(requireActivity()).show {
            message(text = resources.getString(R.string.card_browser_list_my_searches_remove_content, searchName))
            positiveButton(android.R.string.ok) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_SAVED_SEARCH_ACTION,
                    bundleOf(
                        ARG_TYPE to TYPE_SEARCH_REMOVED,
                        ARG_SAVED_SEARCH to searchName,
                    ),
                )
                dialog?.dismiss() // Dismiss the root dialog
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private inner class SavedSearchesAdapter(
        private val entries: List<Pair<String, String>>,
        private val onSelection: (String) -> Unit,
        private val onRemoval: (String) -> Unit,
    ) : RecyclerView.Adapter<SavedSearchesViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): SavedSearchesViewHolder {
            val rowView = layoutInflater.inflate(R.layout.card_browser_item_my_searches_dialog, parent, false)
            return SavedSearchesViewHolder(rowView)
        }

        override fun onBindViewHolder(
            holder: SavedSearchesViewHolder,
            position: Int,
        ) {
            val entry = entries[position]
            holder.name.text = entry.first
            holder.query.text = entry.second
            holder.itemView.setOnClickListener { onSelection(entry.first) }
            holder.deleteBtn.setOnClickListener { onRemoval(entry.first) }
        }

        override fun getItemCount(): Int = entries.size
    }

    private class SavedSearchesViewHolder(
        rowView: View,
    ) : RecyclerView.ViewHolder(rowView) {
        val name: TextView = rowView.findViewById(R.id.card_browser_my_search_name_textview)
        val query: TextView = rowView.findViewById(R.id.card_browser_my_search_query_textview)
        val deleteBtn: ImageButton = rowView.findViewById(R.id.card_browser_my_search_remove_button)
    }

    companion object {
        const val REQUEST_SAVED_SEARCH_ACTION = "request_saved_search_action"
        const val TYPE_SEARCH_SELECTED = 0
        const val TYPE_SEARCH_REMOVED = 1

        /**
         * The user given name for the saved search that can be used as an identifier.
         */
        const val ARG_SAVED_SEARCH = "arg_saved_search"

        /**
         * [Int] number identifying the type of action for the saved search specified by
         * [ARG_SAVED_SEARCH]. Currently there are just two possible values [TYPE_SEARCH_SELECTED]
         * and [TYPE_SEARCH_REMOVED].
         */
        const val ARG_TYPE = "arg_type"
        private const val ARG_SAVED_FILTERS = "arg_saved_filters"

        fun newInstance(savedFilters: Map<String, String>): SavedBrowserSearchesDialogFragment =
            SavedBrowserSearchesDialogFragment().apply {
                arguments =
                    Bundle().also {
                        it.putSerializable(ARG_SAVED_FILTERS, savedFilters.let(::HashMap))
                    }
            }
    }
}

/**
 * Registers a fragment result listener to notify [CardBrowser] about user actions on a saved search.
 * @param action a lambda with the type of action and the name of the target saved search
 */
fun CardBrowser.registerSavedSearchActionHandler(action: (Int, String?) -> Unit) {
    supportFragmentManager.setFragmentResultListener(
        SavedBrowserSearchesDialogFragment.REQUEST_SAVED_SEARCH_ACTION,
        this,
    ) { _, bundle ->
        val type = bundle.getInt(SavedBrowserSearchesDialogFragment.ARG_TYPE)
        val searchName = bundle.getString(SavedBrowserSearchesDialogFragment.ARG_SAVED_SEARCH)
        Timber.d("On user saved search selection named: %s", searchName)
        action(type, searchName)
    }
}
