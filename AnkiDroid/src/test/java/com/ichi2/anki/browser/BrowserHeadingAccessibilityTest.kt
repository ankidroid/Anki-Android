// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.widget.TextView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun `column headings expose their table coordinates`() {
        val heading = TextView(targetContext)
        heading.setBrowserHeadingAccessibility(1)

        val info = AccessibilityNodeInfoCompat.wrap(heading.createAccessibilityNodeInfo())
        assertTrue(info.isHeading)
        val item = assertNotNull(info.collectionItemInfo)
        assertEquals(0, item.rowIndex)
        assertEquals(1, item.columnIndex)
        assertEquals(1, item.rowSpan)
        assertEquals(1, item.columnSpan)
    }
}
