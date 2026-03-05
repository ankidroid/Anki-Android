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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests for [CardBrowserSearchViewModel] */
@RunWith(AndroidJUnit4::class) // PERF: only required for `Prefs`
class CardBrowserSearchViewModelTest : RobolectricTest() {
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
