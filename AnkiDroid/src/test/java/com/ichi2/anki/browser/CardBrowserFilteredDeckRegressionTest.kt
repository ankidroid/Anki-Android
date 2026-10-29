// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.libanki.DeckNameId
import kotlinx.coroutines.flow.update
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.empty
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class CardBrowserFilteredDeckRegressionTest : RobolectricTest() {
    @Test
    fun `filtered deck preserves a simple selected deck search`() =
        withCardBrowserFragment {
            val selected = addBasicNote("first").apply { moveToDeck("Selected") }
            addBasicNote("first").apply { moveToDeck("Other") }

            val search = filteredDeckSearch(deckName = "Selected", query = "first")

            assertThat(col.findCards(search), containsInAnyOrder(selected.firstCard().id))
        }

    @Test
    @Ignore("TODO: escape the literal '_*' deck name instead of matching every deck")
    fun `filtered deck treats a deck named underscore star literally`() =
        withCardBrowserFragment {
            val selected = addBasicNote("selected").apply { moveToDeck("_*") }
            addBasicNote("unrelated").apply { moveToDeck("Other") }

            val search = filteredDeckSearch(deckName = "_*")

            assertThat(col.findCards(search), containsInAnyOrder(selected.firstCard().id))
        }

    @Test
    @Ignore("TODO: group OR alternatives before combining them with the selected deck")
    fun `filtered deck keeps both OR alternatives within the selected deck`() =
        withCardBrowserFragment {
            val first = addBasicNote("first").apply { moveToDeck("Selected") }
            val second = addBasicNote("second").apply { moveToDeck("Selected") }
            addBasicNote("second").apply { moveToDeck("Other") }

            val search = filteredDeckSearch(deckName = "Selected", query = "first OR second")

            assertThat(col.findCards(search), containsInAnyOrder(first.firstCard().id, second.firstCard().id))
        }

    @Test
    @Ignore("TODO: retain the selected deck constraint when the query contains 'deck:'")
    fun `filtered deck preserves selected deck when query contains a deck restriction`() =
        withCardBrowserFragment {
            addBasicNote("selected").apply { moveToDeck("Selected") }
            addBasicNote("unrelated").apply { moveToDeck("Other") }

            val search = filteredDeckSearch(deckName = "Selected", query = "deck:Other")

            assertThat(col.findCards(search), empty())
        }

    private fun CardBrowserFragment.filteredDeckSearch(
        deckName: String,
        query: String = "",
    ): String {
        val deckId = requireNotNull(col.decks.idForName(deckName))
        activityViewModel.searchRequestFlow.update { request ->
            request.copy(query = query).copyFilters { it.copy(decks = listOf(DeckNameId(deckName, deckId))) }
        }

        showFilteredDeckScreen()

        val activity = shadowOf(requireActivity())
        advanceRobolectricLooperUntil { activity.peekNextStartedActivity() != null }
        val intent = requireNotNull(activity.nextStartedActivity)
        assertThat(
            intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME),
            equalTo(FilteredDeckOptionsFragment::class.java.name),
        )
        val arguments = requireNotNull(intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))
        return requireNotNull(arguments.getString(FilteredDeckOptionsFragment.ARG_SEARCH))
    }
}
