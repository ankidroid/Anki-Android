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

import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.createCardBrowserViewModel
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.ColumnUiModel
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.Companion.ARG_CURRENT_SORT_TYPE
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.SortOrderHolderAdapter
import com.ichi2.anki.databinding.ViewBrowserSortOrderBottomSheetItemBinding
import com.ichi2.anki.model.SortType
import com.ichi2.anki.testutils.SingleViewModelFactory
import com.ichi2.testutils.launchFragment
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Tests [SortOrderBottomSheetFragment] */
@RunWith(AndroidJUnit4::class)
class SortOrderBottomSheetFragmentTest : RobolectricTest() {
    private val defaultBrowserColumnKey = BrowserColumnKey("noteFld")

    fun sampleSortType() =
        SortType.CollectionOrdering(
            key = defaultBrowserColumnKey,
            reverse = true,
        )

    @Test
    fun `sort order argument is used`() {
        withFragment(currentSortType = sampleSortType()) {
            assertEquals(
                SortType.CollectionOrdering(
                    key = defaultBrowserColumnKey,
                    reverse = true,
                ),
                this.currentSortType,
            )
        }
    }

    @Test
    fun `no ordering row - if usable`() =
        withFragment(sampleSortType()) {
            val binding = this.listAdapter.noOrderingBinding
            assertEquals("No sorting (faster)", binding.text.text)
            assertEquals(true, binding.noSortOrder.isEnabled, "noSortOrder.isEnabled - available")
            assertEquals(false, binding.sortReverse.isVisible, "sortReverse.isVisible")
            assertEquals(false, binding.sortStandard.isVisible, "sortStandard.isVisible")
            assertSearchAfter { binding.noSortOrder.performClick() }
        }

    @Test
    fun `no ordering row - if already selected`() =
        withFragment(SortType.NoOrdering) {
            val binding = this.listAdapter.noOrderingBinding
            assertEquals("No sorting (faster)", binding.text.text)
            assertEquals(false, binding.noSortOrder.isEnabled, "noSortOrder.isEnabled - unavailable as selected")
            assertEquals(false, binding.sortReverse.isVisible, "sortReverse.isVisible")
            assertEquals(false, binding.sortStandard.isVisible, "sortStandard.isVisible")
            assertNoSearchAfter { binding.noSortOrder.performClick() }
        }

    @Test
    fun `standard row - not selected - properties`() =
        withFragment(SortType.NoOrdering) {
            val binding = this.listAdapter.getBinding("noteFld")
            assertEquals("Sort Field", binding.text.text)
            assertEquals(false, binding.noSortOrder.isVisible, "noSortOrder.isVisible")
            assertEquals(true, binding.sortReverse.isVisible, "sortReverse.isVisible")
            assertEquals(true, binding.sortStandard.isVisible, "sortStandard.isVisible")
            assertEquals(true, binding.sortReverse.isEnabled, "sortReverse.isEnabled")
            assertEquals(true, binding.sortStandard.isEnabled, "sortStandard.isEnabled")
            assertNull(binding.sortReverse.colorFilter, "sortReverse.colorFilter")
            assertNull(binding.sortStandard.colorFilter, "sortStandard.colorFilter")
            assertSearchAfter { binding.sortReverse.performClick() }
        }

    @Test
    fun `standard row - not selected - standard sort`() =
        withFragment(SortType.NoOrdering) {
            val binding = this.listAdapter.getBinding("noteFld")
            assertSearchAfter { binding.sortStandard.performClick() }
        }

    @Test
    fun `standard row - not selected - reverse sort`() =
        withFragment(SortType.NoOrdering) {
            val binding = this.listAdapter.getBinding("noteFld")
            assertSearchAfter { binding.sortReverse.performClick() }
        }

    @Test
    fun `standard row - selected standard - can use alternate sort`() =
        withFragment(
            SortType.CollectionOrdering(
                key = defaultBrowserColumnKey,
                reverse = false,
            ),
        ) {
            val binding = this.listAdapter.getBinding("noteFld")
            assertEquals(false, binding.noSortOrder.isVisible, "noSortOrder.isVisible")
            assertEquals(true, binding.sortReverse.isVisible, "sortReverse.isVisible")
            assertEquals(true, binding.sortStandard.isVisible, "sortStandard.isVisible")
            assertEquals(true, binding.sortReverse.isEnabled, "sortReverse.isEnabled")
            assertEquals(false, binding.sortStandard.isEnabled, "sortStandard.isEnabled")
            assertNull(binding.sortReverse.colorFilter, "sortReverse.colorFilter")
            assertNotNull(binding.sortStandard.colorFilter, "sortStandard.colorFilter")
            assertNoSearchAfter { binding.sortStandard.performClick() }
            assertSearchAfter { binding.sortReverse.performClick() }
        }

    @Test
    fun `standard row - selected reverse - can use alternate sort`() =
        withFragment(
            SortType.CollectionOrdering(
                key = defaultBrowserColumnKey,
                reverse = true,
            ),
        ) {
            val binding = this.listAdapter.getBinding("noteFld")
            assertEquals(false, binding.noSortOrder.isVisible, "noSortOrder.isEnabled")
            assertEquals(true, binding.sortReverse.isVisible, "sortReverse.isVisible")
            assertEquals(true, binding.sortStandard.isVisible, "sortStandard.isVisible")
            assertEquals(false, binding.sortReverse.isEnabled, "sortReverse.isEnabled")
            assertEquals(true, binding.sortStandard.isEnabled, "sortStandard.isEnabled")
            assertNotNull(binding.sortReverse.colorFilter, "sortReverse.colorFilter")
            assertNull(binding.sortStandard.colorFilter, "sortStandard.colorFilter")
            assertNoSearchAfter { binding.sortReverse.performClick() }
            assertSearchAfter { binding.sortStandard.performClick() }
        }

    @Test
    fun `disabled row - unusable`() =
        withFragment(
            SortType.CollectionOrdering(
                key = BrowserColumnKey("question"),
                reverse = true,
            ),
        ) {
            val binding = this.listAdapter.getBinding("question")
            assertEquals(false, binding.noSortOrder.isVisible, "noSortOrder.isEnabled")
            assertEquals(true, binding.sortReverse.isVisible, "sortReverse.isVisible")
            assertEquals(true, binding.sortStandard.isVisible, "sortStandard.isVisible")
            assertEquals(false, binding.sortReverse.isEnabled, "sortReverse.isEnabled")
            assertEquals(false, binding.sortStandard.isEnabled, "sortStandard.isEnabled")
            // even on a bug, the filters are applied
            assertNotNull(binding.sortReverse.colorFilter, "sortReverse.colorFilter")
            assertNull(binding.sortStandard.colorFilter, "sortStandard.colorFilter")
            assertNoSearchAfter { binding.sortReverse.performClick() }
            assertNoSearchAfter { binding.sortStandard.performClick() }
        }

    fun withFragment(
        currentSortType: SortType = SortType.NoOrdering,
        block: suspend SortOrderBottomSheetFragment.() -> Unit,
    ) {
        val viewModelFactory = SingleViewModelFactory.create(createCardBrowserViewModel())

        val fragmentArgs =
            bundleOf(
                ARG_CURRENT_SORT_TYPE to currentSortType,
            )
        launchFragment(fragmentArgs) {
            SortOrderBottomSheetFragment(viewModelFactory)
        }.use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onFragment { fragment ->
                runBlocking {
                    block(fragment)
                }
            }
        }
    }
}

private suspend fun SortOrderBottomSheetFragment.assertSearchAfter(block: () -> Unit) {
    viewModel.flowOfSearchState.test {
        expectNoEvents()
        block()
        expectMostRecentItem()
    }
}

private suspend fun SortOrderBottomSheetFragment.assertNoSearchAfter(block: () -> Unit) {
    viewModel.flowOfSearchState.test {
        expectNoEvents()
        block()
        expectNoEvents()
    }
}

val SortOrderBottomSheetFragment.list get() = binding.list
val SortOrderBottomSheetFragment.listAdapter get() = list.adapter as SortOrderHolderAdapter

context(fragment: SortOrderBottomSheetFragment)
val SortOrderHolderAdapter.noOrderingBinding: ViewBrowserSortOrderBottomSheetItemBinding
    get() {
        val position = this.columns.indexOfFirst { it == ColumnUiModel.NoOrdering }
        fragment.binding.list.scrollToPosition(position)
        advanceRobolectricLooper()
        val holder = fragment.list.findViewHolderForAdapterPosition(position) as SortOrderHolderAdapter.Holder
        return holder.binding
    }

context(fragment: SortOrderBottomSheetFragment)
fun SortOrderHolderAdapter.getBinding(key: String): ViewBrowserSortOrderBottomSheetItemBinding {
    val position = this.columns.indexOfFirst { it.keyOrNull == key }
    fragment.binding.list.scrollToPosition(position)
    advanceRobolectricLooper()
    val holder = fragment.list.findViewHolderForAdapterPosition(position) as SortOrderHolderAdapter.Holder
    return holder.binding
}
