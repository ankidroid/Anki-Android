// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import com.ichi2.testutils.rules.IcuCaseFoldingRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TagSearchUnicodeTest {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    @Test
    fun `filter matches normalized names and Unicode case variants`() {
        for ((name, query) in listOf("café" to "cafe\u0301", "ος" to "οσ", "Straße" to "STRASSE", "ﬃ" to "ffi")) {
            val row = TagListItemState(name, name, 0, hasChildren = false, collapsed = false)
            assertEquals(listOf(row), ManageTagsViewModel.applySearchFilter(listOf(row), query), "$name / $query")
        }
    }

    @Test
    fun `normalized path search reveals matching children`() {
        val parent = TagListItemState("café", "café", 0, hasChildren = true, collapsed = true)
        val child = TagListItemState("café::ος", "ος", 1, hasChildren = false, collapsed = false)
        assertEquals(
            listOf(parent.copy(collapsed = false), child),
            ManageTagsViewModel.applySearchFilter(listOf(parent, child), "cafe\u0301::οσ"),
        )
    }

    @Test
    fun `display search uses Unicode folding independently of backend identity`() {
        val row = TagListItemState("\ua7d0", "\ua7d0", 0, hasChildren = false, collapsed = false)
        assertEquals(listOf(row), ManageTagsViewModel.applySearchFilter(listOf(row), "\ua7d1"))
    }
}
