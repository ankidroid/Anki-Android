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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ichi2.anki.R
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.databinding.FragmentStandardSearchBinding
import com.ichi2.anki.databinding.ViewSavedSearchItemBinding
import com.ichi2.anki.databinding.ViewSearchHistoryItemBinding
import com.ichi2.anki.dialogs.ManageSavedSearchAction
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment
import com.ichi2.anki.dialogs.registerSaveSearchHandler
import com.ichi2.anki.dialogs.registerSavedSearchActionHandler
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class StandardSearchFragment : Fragment(R.layout.fragment_standard_search) {
    @VisibleForTesting
    val binding by viewBinding(FragmentStandardSearchBinding::bind)

    @VisibleForTesting
    val viewModel: CardBrowserSearchViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerSaveSearchHandler {
            viewModel.addSavedSearch(it)
        }
        registerSavedSearchActionHandler {
            when (it) {
                is ManageSavedSearchAction.SelectSearch -> {
                    viewModel.submitSavedSearch(it.search)
                }
                is ManageSavedSearchAction.Delete -> {
                    viewModel.deleteSavedSearch(it.search)
                }
            }
        }
    }

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

                fun openSavedSearchNamePrompt() {
                    Timber.i("opening 'save search' name input dialog")
                    val dialog =
                        SaveBrowserSearchDialogFragment.newInstance(searchQuery = item.query)

                    dialog.show(childFragmentManager, "savedSearchName")
                }

                binding.title.text = item.query
                binding.favorite.setOnClickListener { openSavedSearchNamePrompt() }
                return binding.root
            }
        }

        binding.searchHistory.apply {
            adapter =
                SearchHistoryAdapter(
                    context = requireContext(),
                    // TODO: Fix to take filters into account
                    searches = viewModel.searchHistoryFlow.value.take(MAX_SEARCH_HISTORY_ENTRIES),
                )
            setOnItemClickListener { _, _, position, _ ->
                viewModel.selectSearchHistoryEntry(getItemAtPosition(position) as SearchHistoryEntry)
            }
        }

        binding.seeMore.apply {
            setOnClickListener { showSnackbar("TODO") }
        }

        viewModel.searchHistoryAvailableFlow.launchCollectionInLifecycleScope {
            binding.searchHistoryContainer.isVisible = it
        }
    }

    private fun setupSavedSearches() {
        class SavedSearchAdapter(
            private val context: Context,
            searches: List<SavedSearch>,
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

                val item = getItem(position)!!
                binding.title.text = item.name
                binding.content.text = item.query

                binding.root.setOnClickListener { viewModel.submitSavedSearch(item) }
                binding.insertSavedSearch.setOnClickListener { viewModel.applySavedSearch(item) }

                return binding.root
            }
        }

        val savedSearches = viewModel.savedSearchesFlow.value.toMutableList()
        val adapter =
            SavedSearchAdapter(
                requireContext(),
                savedSearches,
            )
        binding.savedSearches.adapter = adapter

        viewModel.savedSearchesFlow.launchCollectionInLifecycleScope {
            withContext(Dispatchers.Main) {
                savedSearches.clear()
                savedSearches.addAll(it)
                adapter.notifyDataSetChanged()
            }
        }

        viewModel.canManageSavedSearchesFlow.launchCollectionInLifecycleScope {
            binding.manageSavedSearchesContainer.isVisible = it
        }

        binding.manageSavedSearchesContainer.setOnClickListener {
            binding.manageSavedSearches.performClick()
            val dialog = SavedBrowserSearchesDialogFragment.newInstance(savedSearches)
            dialog.show(childFragmentManager, SavedBrowserSearchesDialogFragment.TAG)
        }
    }

    companion object {
        const val TAG = "STANDARD"

        /** Limits the number of history items, so controls appear below without scrolling */
        const val MAX_SEARCH_HISTORY_ENTRIES = 5
    }
}
