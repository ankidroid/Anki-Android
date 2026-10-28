// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

/**
 * U+00A0 is valid inside a tag, but the backend's TagMatcher ignores it in verbose regex mode.
 * See [Issue 23222](https://github.com/ankidroid/Anki-Android/issues/23222).
 */
class TagNonbreakingSpaceRegressionTest : InMemoryAnkiTest() {
    @Test
    @Ignore("Issue 23222: backend TagMatcher ignores U+00A0 and renames the wrong tag")
    fun `renaming a tag containing a nonbreaking space leaves the tag without that space unchanged`() {
        val source = addBasicNote().update { addTag("a\u00a0b") }
        val unrelated = addBasicNote().update { addTag("ab") }

        col.tags.rename("a\u00a0b", "renamed")

        assertEquals(listOf("ab"), col.getNote(unrelated.id).tags, "Rename must not change the unrelated tag")
        assertEquals(listOf("renamed"), col.getNote(source.id).tags)
        assertEquals(listOf("ab", "renamed"), col.tags.all().sorted())
    }

    @Test
    @Ignore("Issue 23222: backend TagMatcher ignores U+00A0 and deletes the wrong tag")
    fun `deleting a tag containing a nonbreaking space leaves the tag without that space unchanged`() {
        val source = addBasicNote().update { addTag("a\u00a0b") }
        val unrelated = addBasicNote().update { addTag("ab") }

        col.tags.remove("a\u00a0b")

        assertEquals(listOf("ab"), col.getNote(unrelated.id).tags, "Delete must not change the unrelated tag")
        assertEquals(emptyList(), col.getNote(source.id).tags)
        assertEquals(listOf("ab"), col.tags.all())
    }
}
