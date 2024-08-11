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
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask

class FlagsSheetFragment : BottomSheetFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    private val searchParameters: SearchParameters
        get() = viewModel.searchTerms

    override suspend fun onPrepareAdapter() {
        val flagsSourceItems = Flag.queryDisplayNames()
            .map { (flag, displayName) ->
                TemplatedTreeAdapter.SourceItem(
                    id = flag.code.toLong(),
                    icon = flag.drawableRes,
                    text = displayName,
                    indent = 0,
                    checkable = true
                )
            }

        val sourceItems = if (searchParameters.flags.isEmpty()) {
            flagsSourceItems
        } else {
            flagsSourceItems.withItemPrepended(
                TemplatedTreeAdapter.SourceItem(
                    id = ALL_ITEMS_ID,
                    icon = R.drawable.ic_clear_white,
                    text = "Clear filter",
                    indent = -1,
                    checkable = false
                )
            )
        }

        adapter.update(
            sourceItems = sourceItems,
            checkedItemIds = searchParameters.flags.map { it.code.toLong() }.toSet()
        )
    }

    override fun onItemsSelected(ids: Set<Long>) {
        launchCatchingTask {
            viewModel.launchSearchForCards(
                searchParameters.copy(flags = ids.map { id -> Flag.fromCode(id.toInt()) }.toSet())
            )
        }
    }

    companion object { const val TAG = "FlagsSheetFragment" }
}
