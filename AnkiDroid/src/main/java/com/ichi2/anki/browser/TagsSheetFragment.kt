/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

import androidx.fragment.app.activityViewModels
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask

class TagsSheetFragment : BottomSheetFragment() {
    override val filterHintResource = R.string.filter_hint_search_tags

    override val layoutResource get() = if (tagRows.size > 10) {
        R.layout.bottom_sheet_list_with_filter
    } else {
        R.layout.bottom_sheet_list_without_filter
    }

    // TODO: onPrepareAdapter is needed first
    private var tagRows: List<Row> = listOf()
    private var tagIdToName: Map<Long, String> = mapOf()

    private val viewModel: CardBrowserViewModel by activityViewModels()

    override suspend fun onPrepareAdapter() {
        tagRows = withCol { tags.all() }
            .toSortedChainSetWithMissingParentsAdded()
            .mapIndexed { index, chain ->
                val icon = when {
                    chain.equals("marked", ignoreCase = true) -> R.drawable.ic_marked
                    chain.equals("leech", ignoreCase = true) -> R.drawable.ic_leech
                    else -> R.drawable.ic_tag
                }
                Row(id = index.toLong(), icon, chain)
            }
            // PERF: This can be O(n) rather than O(n log n)
            // order: [marked; leech; ...rest].
            .sortedBy { row ->
                when {
                    row.chain.equals("marked", ignoreCase = true) -> 0
                    row.chain.equals("leech", ignoreCase = true) -> 1
                    else -> 2
                }
            }

        tagIdToName = tagRows.associate { (id, _, chain) -> id to chain }

        val tagNameToId = tagRows.associate { (id, _, chain) -> chain to id }

        val sourceItems = if (viewModel.searchTerms.tags.any()) {
            tagRows.toSourceItems(filter = "").withItemPrepended(allTagsSourceItem)
        } else {
            tagRows.toSourceItems(filter = "")
        }

        adapter.update(
            sourceItems = sourceItems,
            checkedItemIds = tagNameToId.values(viewModel.searchTerms.tags)
        )
    }

    override fun onFilterInputChanged(filterInput: String) {
        val tagsSourceItems = tagRows.toSourceItems(filterInput)
        val sourceItems = if (viewModel.searchTerms.tags.any()) {
            tagsSourceItems.withItemPrepended(allTagsSourceItem)
        } else {
            tagsSourceItems
        }

        adapter.update(
            sourceItems = sourceItems,
            collapsedItemIds = if (filterInput.isEmpty()) null else emptySet()
        )
    }

    override fun onItemsSelected(ids: Set<Long>) {
        launchCatchingTask {
            viewModel.launchSearchForCards(viewModel.searchTerms.copy(tags = tagIdToName.values(ids)))
        }
    }

    companion object { const val TAG = "TagsSheetFragment" }
}

private val allTagsSourceItem = TemplatedTreeAdapter.SourceItem(
    id = ALL_ITEMS_ID,
    icon = R.drawable.close_icon,
    text = "Clear filter",
    indent = -1,
    checkable = false
)

private fun <K, V> Map<K, V>.values(keys: Set<K>) = keys.mapNotNull { this[it] }.toSet()
