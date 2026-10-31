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
    fun `filtered deck escapes wildcards in the deck name`() =
        withCardBrowserFragment {
            // '_' is a single character wildcard, so an unescaped name also matches 'Chapter11'
            val selected = addBasicNote("selected").apply { moveToDeck("Chapter_1") }
            addBasicNote("unrelated").apply { moveToDeck("Chapter11") }

            val search = filteredDeckSearch(deckName = "Chapter_1")

            assertThat(col.findCards(search), containsInAnyOrder(selected.firstCard().id))
        }

    @Test
    fun `filtered deck treats a deck named underscore star literally`() =
        withCardBrowserFragment {
            val selected = addBasicNote("selected").apply { moveToDeck("_*") }
            addBasicNote("unrelated").apply { moveToDeck("Other") }

            val search = filteredDeckSearch(deckName = "_*")

            assertThat(col.findCards(search), containsInAnyOrder(selected.firstCard().id))
        }

    @Test
    fun `filtered deck keeps both OR alternatives within the selected deck`() =
        withCardBrowserFragment {
            val first = addBasicNote("first").apply { moveToDeck("Selected") }
            val second = addBasicNote("second").apply { moveToDeck("Selected") }
            addBasicNote("second").apply { moveToDeck("Other") }

            val search = filteredDeckSearch(deckName = "Selected", query = "first OR second")

            assertThat(col.findCards(search), containsInAnyOrder(first.firstCard().id, second.firstCard().id))
        }

    @Test
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
