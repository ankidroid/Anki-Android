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
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask

class CardStateSheetFragment : BottomSheetFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    private val searchParameters: SearchParameters
        get() = viewModel.searchTerms

    override suspend fun onPrepareAdapter() {
        val cardStateItems = State.entries.map { state ->
            TemplatedTreeAdapter.SourceItem(
                id = state.id,
                icon = state.icon,
                text = state.label,
                subtitle = state.subtitle,
                checkable = true
            )
        }

        val sourceItems = if (searchParameters.states.isEmpty()) {
            cardStateItems
        } else {
            cardStateItems.withItemPrepended(clearFilterSourceItem)
        }

        adapter.update(
            sourceItems = sourceItems,
            checkedItemIds = viewModel.searchTerms.states.map { it.id }.toSet()
        )
    }

    override fun onItemsSelected(ids: Set<Long>) {
        val state = ids.mapNotNull { id -> State.fromId(id) }.toSet()
        launchCatchingTask { viewModel.launchSearchForCards(viewModel.searchTerms.copy(states = state)) }
    }

    companion object { const val TAG = "CardStateSheetFragment" }
}

private val clearFilterSourceItem = TemplatedTreeAdapter.SourceItem(
    id = ALL_ITEMS_ID,
    icon = R.drawable.ic_clear_white,
    text = "Clear filter",
    checkable = false
)

enum class State(
    val id: Long,
    val icon: Int?,
    val subtitle: String,
    val search: String
) {
    New(0, R.drawable.ic_card_state_new, "Cards that have not been studied", "is:new"),
    Learning(1, R.drawable.ic_card_state_learning, "Cards that are still being learnt", "is:learn"),
    Review(2, R.drawable.ic_card_state_review, "Cards that use Anki's long-term scheduler", "is:review"),
    Buried(3, R.drawable.ic_card_state_buried, "Cards which aren't shown today", "is:buried"),
    Suspended(4, R.drawable.ic_suspend, "Cards which aren't shown unless unsuspended", "is:suspended")
    ;

    val label: String
        get() = when (this) {
            New -> TR.statisticsCountsNewCards()
            Learning -> TR.statisticsCountsLearningCards()
            Review -> TR.browsingSidebarCardStateReview()
            Buried -> TR.statisticsCountsBuriedCards()
            Suspended -> TR.browsingSuspended()
        }

    companion object {
        fun fromId(id: Long): State? = State.entries.firstOrNull { it.id == id }
    }
}
