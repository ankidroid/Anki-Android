// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.os.Build
import android.widget.TextView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat.SORT_DIRECTION_ASCENDING
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat.SORT_DIRECTION_DESCENDING
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat.SORT_DIRECTION_NONE
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.model.SortType
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25, 36, 37], application = EmptyApplication::class)
class BrowserHeadingAccessibilityTest : AndroidTest {
    @Test
    fun `sort direction is exposed only on supported Android versions`() {
        var sortType: SortType = SortType.NoOrdering
        val heading = TextView(targetContext)
        heading.setBrowserHeadingAccessibility(1, "noteFld") { sortType }

        fun assertHeading(direction: Int) {
            val info = AccessibilityNodeInfoCompat.wrap(heading.createAccessibilityNodeInfo())
            assertTrue(info.isHeading)
            val item = assertNotNull(info.collectionItemInfo)
            assertEquals(0, item.rowIndex)
            assertEquals(1, item.columnIndex)
            assertEquals(1, item.rowSpan)
            assertEquals(1, item.columnSpan)
            assertEquals(if (Build.VERSION.SDK_INT >= 37) direction else SORT_DIRECTION_NONE, item.sortDirection)
        }

        assertHeading(SORT_DIRECTION_NONE)
        sortType = SortType.CollectionOrdering(BrowserColumnKey("noteFld"), reverse = false)
        assertHeading(SORT_DIRECTION_ASCENDING)
        sortType = SortType.CollectionOrdering(BrowserColumnKey("noteFld"), reverse = true)
        assertHeading(SORT_DIRECTION_DESCENDING)
        sortType = SortType.CollectionOrdering(BrowserColumnKey("deck"), reverse = true)
        assertHeading(SORT_DIRECTION_NONE)
        sortType = SortType.NoOrdering
        assertHeading(SORT_DIRECTION_NONE)
    }
}
