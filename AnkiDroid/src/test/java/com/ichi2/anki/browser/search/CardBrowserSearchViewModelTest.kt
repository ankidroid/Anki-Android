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
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/** Tests for [CardBrowserSearchViewModel] */
class CardBrowserSearchViewModelTest {
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
    fun `toggleAdvancedSearch updates flow`() =
        withViewModel {
            searchTextFlow.test {
                expectMostRecentItem()
                expectNoEvents()

                toggleAdvancedSearch()

                expectMostRecentItem()
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

    fun withViewModel(block: suspend CardBrowserSearchViewModel.() -> Unit) =
        runTest {
            val viewModel = CardBrowserSearchViewModel(SavedStateHandle())
            block(viewModel)
        }
}
