/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ichi2.anki.R
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.databinding.FragmentStandardSearchBinding
import com.ichi2.anki.databinding.ViewSavedSearchItemBinding
import com.ichi2.anki.databinding.ViewSearchHistoryItemBinding
import dev.androidbroadcast.vbpd.viewBinding

class StandardSearchFragment : Fragment(R.layout.fragment_standard_search) {
    @VisibleForTesting
    val binding by viewBinding(FragmentStandardSearchBinding::bind)

    private val viewModel: CardBrowserSearchViewModel by activityViewModels()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.toggleAdvancedSearch.setOnClickListener { viewModel.toggleAdvancedSearch() }

        setupSearchHistory()
        setupSavedSearches()
    }

    private fun setupSearchHistory() {
        class SearchHistoryAdapter(
            private val context: Context,
            searches: List<SearchHistoryEntry>,
        ) : ArrayAdapter<SearchHistoryEntry>(context, 0, searches) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View {
                val binding =
                    if (convertView != null) {
                        ViewSearchHistoryItemBinding.bind(convertView)
                    } else {
                        ViewSearchHistoryItemBinding.inflate(LayoutInflater.from(context), parent, false)
                    }

                val item = getItem(position)!!

                binding.title.text = item.query
                return binding.root
            }
        }

        binding.searchHistory.apply {
            adapter =
                SearchHistoryAdapter(
                    context = requireContext(),
                    searches =
                        arrayListOf(
                            SearchHistoryEntry("rated:1"),
                            SearchHistoryEntry("nid:1764375217155"),
                            SearchHistoryEntry("is:learn -is:review induction"),
                            SearchHistoryEntry("deck:* hello AnkiDroid"),
                        ),
                )
        }
    }

    private fun setupSavedSearches() {
        binding.savedSearches.adapter =
            SavedSearchAdapter(
                requireContext(),
                arrayListOf(
                    SavedSearch("Red flag", "flag:1"),
                    SavedSearch("Title", "search query"),
                    SavedSearch("ya-ya, ya-ya", "blah-blah, blah-blah"),
                ),
            )
    }

    companion object {
        const val TAG = "STANDARD"
    }
}

class SavedSearchAdapter(
    private val context: Context,
    searches: MutableList<SavedSearch>,
) : ArrayAdapter<SavedSearch>(context, 0, searches) {
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val binding =
            if (convertView != null) {
                ViewSavedSearchItemBinding.bind(convertView)
            } else {
                ViewSavedSearchItemBinding.inflate(LayoutInflater.from(context), parent, false)
            }

        binding.title.text = getItem(position)!!.name
        binding.content.text = getItem(position)!!.query

        return binding.root
    }
}
