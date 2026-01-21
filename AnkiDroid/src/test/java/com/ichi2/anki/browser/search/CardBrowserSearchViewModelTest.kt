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

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.testutils.assertFalse
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests for [CardBrowserSearchViewModel] */
@RunWith(AndroidJUnit4::class) // PERF: only required for `Prefs`
class CardBrowserSearchViewModelTest : RobolectricTest() {
    @Before
    override fun setUp() {
        SearchHistory().clear()
        super.setUp()
    }

    @Test
    fun `initial state`() =
        withViewModel {
            // TODO: design the initial state
            assertThat("no initial search history", searchHistory, empty())
            searchHistoryAvailableFlow.test {
                assertFalse("no history => history unavailable", expectMostRecentItem())
            }
        }

    @Test
    fun `initial state with no cards`() =
        withViewModel(cardCount = 0) {
            // TODO: design the initial state
            assertThat("no initial search history", searchHistory, empty())
            searchHistoryAvailableFlow.test {
                assertFalse("no history => history unavailable", expectMostRecentItem())
            }
        }

    @Test
    fun `search updated after submit`() =
        withViewModel {
            searchHistoryFlow.test {
                submitSearch("Hello")

                val mostRecentItem = expectMostRecentItem()
                assertThat(mostRecentItem, not(empty()))
                val item = mostRecentItem.single()
                assertThat(item, equalTo(SearchHistoryEntry("Hello")))
            }
        }

    @Test
    fun `search closed after submit`() =
        withViewModel {
            closeSearchViewFlow.test {
                expectNoEvents()
                submitCurrentSearch("Hello")
                expectMostRecentItem()
            }
        }

    @Test
    fun `selecting search history entry`() =
        withViewModel {
            submittedSearchFlow.test {
                submitCurrentSearch("Hello")
                assertThat(expectMostRecentItem(), equalTo("Hello"))
            }
        }

    @Test
    fun `appendAdvancedSearch appends spacing`() =
        withViewModel {
            this.searchTextFlow.test {
                assertEquals("", this.expectMostRecentItem())

                toggleAdvancedSearch()

                appendAdvancedSearch("hello")
                appendAdvancedSearch("deck:aa")

                // space at the end: if a user types, it does not interfere with previous append calls
                assertEquals("hello deck:aa ", this.expectMostRecentItem())
            }
        }

    @Test
    fun `onSearchTextChanged takes priority over appendAdvancedSearch`() =
        withViewModel {
            this.searchTextFlow.test {
                assertEquals("", this.expectMostRecentItem())

                toggleAdvancedSearch()

                appendAdvancedSearch("hello")
                onSearchTextChanged("replaced")

                // the append call had no effect
                assertEquals("replaced", this.expectMostRecentItem())
            }
        }

    @Test
    fun `text can be updated when a different screen is selected`() =
        withViewModel {
            searchTextFlow.test {
                expectMostRecentItem()

                appendAdvancedSearch("advanced, while in standard")

                toggleAdvancedSearch()

                assertEquals("advanced, while in standard ", expectMostRecentItem())
            }
        }

    @Test
    fun `text can be updated in Basic mode`() =
        withViewModel {
            searchTextFlow.test {
                expectMostRecentItem()

                onSearchTextChanged("hi")

                assertEquals("hi", expectMostRecentItem())
            }
        }

    @Test
    fun `submitCurrentSearch trims spaces`() =
        withViewModel {
            submittedSearchFlow.test {
                submitCurrentSearch(" aa ")
                assertThat(expectMostRecentItem(), equalTo("aa"))
            }
        }

    // there's no point in having this in the history as a user can trivially clear the search box.
    @Test
    fun `a submitted empty string does not appear in the history`() =
        withViewModel {
            submittedSearchFlow.test {
                submitSearch("")
                assertThat(searchHistory, empty())
                assertThat(expectMostRecentItem(), equalTo(""))
            }
        }

    @Test
    fun `search history - submitted searches are trimmed and deduplicated`() =
        withViewModel {
            searchHistoryFlow.test {
                submitSearch("aa")
                assertThat(expectMostRecentItem(), hasSize(1))

                submitSearch(" aa ")
                expectNoEvents()

                // confirm a new event would have appeared
                submitSearch(" bb ")
                expectMostRecentItem()
            }
        }

    fun withViewModel(
        cardCount: Int = 1,
        block: suspend CardBrowserSearchViewModel.() -> Unit,
    ) = runTest {
        addNotes(count = cardCount)
        block(
            CardBrowserSearchViewModel(
                SavedStateHandle(),
            ),
        )
    }
}

/**
 * @see com.ichi2.anki.browser.SearchHistory
 */
val CardBrowserSearchViewModel.searchHistory get() = this.searchHistoryFlow.value

fun CardBrowserSearchViewModel.submitSearch(submittedText: String) = submitCurrentSearch(submittedText)
