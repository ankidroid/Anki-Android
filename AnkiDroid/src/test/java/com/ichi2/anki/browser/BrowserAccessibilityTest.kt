// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.CardBrowserColumn.DECK
import com.ichi2.anki.browser.CardBrowserColumn.SFLD
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BrowserAccessibilityTest : RobolectricTest() {
    @Before
    fun enableAccessibility() {
        // RecyclerView only installs its item accessibility delegates when accessibility is enabled.
        shadowOf(targetContext.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
    }

    @Test
    fun `headings and cells share table coordinates`() = checkTableCoordinates(useSearchView = false)

    @Test
    fun `search view headings and cells share table coordinates`() = checkTableCoordinates(useSearchView = true)

    private fun checkTableCoordinates(useSearchView: Boolean) {
        addBasicNote("first")
        addBasicNote("second")
        withCardBrowserFragment(useSearchView = useSearchView) {
            setColumns(SFLD, DECK)
            val table = requireView().accessibilityInfo()
            val collection = assertNotNull(table.collectionInfo)
            assertEquals(3, collection.rowCount)
            assertEquals(2, collection.columnCount)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, requireView().importantForAccessibility)
            assertNull(cardsListView.accessibilityInfo().collectionInfo, "The list must not introduce a nested collection")

            for (columnIndex in 0..1) {
                val heading = browserColumnHeadings.getChildAt(columnIndex).accessibilityInfo()
                assertTrue(heading.isHeading)
                assertTrue(heading.isClickable)
                assertTrue(heading.isLongClickable)
                val item = assertNotNull(heading.collectionItemInfo)
                assertEquals(0, item.rowIndex)
                assertEquals(columnIndex, item.columnIndex)
                assertEquals(1, item.rowSpan)
                assertEquals(1, item.columnSpan)
            }

            for (rowIndex in 0..1) {
                val row =
                    assertIs<BrowserMultiColumnAdapter.MultiColumnViewHolder>(
                        cardsListView.findViewHolderForAdapterPosition(rowIndex),
                    )
                val item = assertNotNull(row.itemView.accessibilityInfo().collectionItemInfo)
                assertEquals(rowIndex + 1, item.rowIndex)
                assertEquals(0, item.columnIndex)
                assertEquals(1, item.rowSpan)
                assertEquals(2, item.columnSpan)
                for ((columnIndex, cell) in row.columnViews.withIndex()) {
                    val cellInfo = assertNotNull(cell.accessibilityInfo().collectionItemInfo)
                    assertEquals(rowIndex + 1, cellInfo.rowIndex)
                    assertEquals(columnIndex, cellInfo.columnIndex)
                    assertEquals(1, cellInfo.rowSpan)
                    assertEquals(1, cellInfo.columnSpan)
                }
                assertTrue(row.itemView.isClickable)
                assertTrue(row.itemView.isLongClickable)
            }

            val unboundHolder = cardsAdapter.createViewHolder(cardsListView, 0)
            unboundHolder.numberOfColumns = 2
            unboundHolder.columnViews.forEach { cell ->
                assertNull(cell.accessibilityInfo().collectionItemInfo, "An unbound cell has no table position")
            }
        }
    }

    private suspend fun CardBrowserFragment.setColumns(vararg columns: CardBrowserColumn) {
        assertTrue(activityViewModel.updateActiveColumns(columns.toList(), activityViewModel.cardsOrNotes))
        activityViewModel.flowOfColumnHeadings.first { headings -> headings.map { it.ankiColumnKey } == columns.map { it.ankiColumnKey } }
        advanceRobolectricLooper()
    }

    private fun View.accessibilityInfo() = AccessibilityNodeInfoCompat.wrap(createAccessibilityNodeInfo())
}
