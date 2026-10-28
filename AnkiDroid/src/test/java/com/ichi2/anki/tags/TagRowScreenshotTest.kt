// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ComposeScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w320dp-h200dp-mdpi")
class TagRowScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun expansion_indicators(
        @TestParameter layoutDirection: LayoutDirection,
    ) {
        capture(name = "expansion_${layoutDirection.name.lowercase()}") {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Surface {
                    Column {
                        for (item in listOf(
                            TagListItemState("History", "History", 0, hasChildren = true, collapsed = true),
                            TagListItemState("Science", "Science", 0, hasChildren = true, collapsed = false),
                            TagListItemState("Science::Biology", "Biology", 1, hasChildren = false, collapsed = false),
                        )) {
                            TagRow(item, actionsEnabled = true, onToggleCollapsed = {}, onOverflowClick = {})
                        }
                    }
                }
            }
        }
    }
}
