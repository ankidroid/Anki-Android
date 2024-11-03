/*
* This program is free software; you can redistribute it and/or modify it under
* the terms of the GNU General Public License as published by the Free Software
* Foundation; either version 3 of the License, or (at your option) any later
* version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License along with
* this program. If not, see <http://www.gnu.org/licenses/>.
*/
package com.ichi2.anki.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.workarounds.BottomSheetDialogFragmentFix
import kotlinx.coroutines.launch

class BrowserFlagsFilteringFragment : BottomSheetDialogFragmentFix() {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val content = inflater.inflate(R.layout.fragment_flags_filter_sheet, container, false)
        content
            .findViewById<LinearLayout>(R.id.clear_filter_container)
            .setOnClickListener {
                searchFor(emptySet())
                dismiss()
            }
        return content
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val container = view.findViewById<LinearLayout>(R.id.flags_container)
        val currentSelection =
            if (savedInstanceState != null) {
                savedInstanceState
                    .getString(KEY_SELECTED_FLAGS)
                    ?.split("|")
                    ?.map { Flag.fromCode(it.toInt()) }
                    ?.toSet() ?: viewModel.searchTerms.flags
            } else {
                viewModel.searchTerms.flags
            }
        val clearContainer = view.findViewById<LinearLayout>(R.id.clear_filter_container)
        clearContainer.isVisible = currentSelection.isNotEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            Flag.queryDisplayNames().forEach { (flag, displayName) ->
                buildFlagFilterView(container, clearContainer, flag, displayName, currentSelection.contains(flag))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(
            KEY_SELECTED_FLAGS,
            viewModel.searchTerms.flags
                .map { it.code }
                .joinToString("|"),
        )
    }

    private fun buildFlagFilterView(
        container: LinearLayout,
        clearContainer: LinearLayout,
        flag: Flag,
        displayName: String,
        isAlreadySelected: Boolean,
    ) {
        val view =
            requireActivity().layoutInflater.inflate(R.layout.item_browser_flag_filter, container, false).apply {
                findViewById<ImageView>(R.id.icon).setImageResource(flag.drawableRes)
                findViewById<TextView>(R.id.text).text = displayName
                setOnClickListener {
                    searchFor(setOf(flag))
                    dismiss() // direct selection clears everything and closes the filter
                }
            }
        view.findViewById<CheckBox>(R.id.checkbox).apply {
            setChecked(isAlreadySelected)
            setOnCheckedChangeListener { _, isChecked ->
                val newSelection =
                    viewModel.searchTerms.flags.toMutableSet().apply {
                        if (isChecked) add(flag) else remove(flag)
                    }
                clearContainer.isVisible = container.children.any { it.findViewById<CheckBox>(R.id.checkbox).isChecked }
                searchFor(newSelection)
            }
        }
        container.addView(view)
    }

    private fun searchFor(flagsSelection: Set<Flag>) {
        requireActivity().launchCatchingTask {
            viewModel.launchSearchForCards(
                viewModel.searchTerms.copy(flags = flagsSelection),
            )
        }
    }

    companion object {
        const val TAG = "BrowserFlagsFilteringFragment"
        private const val KEY_SELECTED_FLAGS = "key_selected_flags"
    }
}
