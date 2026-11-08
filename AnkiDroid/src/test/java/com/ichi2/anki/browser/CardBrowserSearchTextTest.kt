// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.text.style.CharacterStyle
import android.widget.EditText
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.ext.requireSystemService
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.robolectric.ParameterizedRobolectricTestRunner as Parameterized

@RunWith(Parameterized::class)
class CardBrowserSearchTextTest : RobolectricTest() {
    @JvmField
    @Parameterized.Parameter
    var useSearchView: Boolean = false

    @Test
    fun `pasting HTML inserts unformatted text - Issue 23042`() =
        withSearchField {
            setText("before selected after")
            advanceRobolectricLooper()
            setSelection(7, 15)
            paste(
                ClipData.newHtmlText(
                    "text",
                    "link bold italic",
                    """<a href="https://apps.ankiweb.net/">link</a> <b>bold</b> <i>italic</i>""",
                ),
            )

            assertEquals("before link bold italic after", text.toString())
            assertTrue(text.getSpans(0, text.length, CharacterStyle::class.java).isEmpty())
            assertEquals(23, selectionStart)
            assertEquals(23, selectionEnd)
        }

    @Test
    fun `pasting plain text preserves literal HTML`() =
        withSearchField {
            paste(ClipData.newPlainText("text", "<b>literal</b>"))

            assertEquals("<b>literal</b>", text.toString())
        }

    private fun withSearchField(block: EditText.() -> Unit) =
        withCardBrowserFragment(useSearchView) {
            searchViewModel.isScreenOpenFlow.value = true
            advanceRobolectricLooper()
            val field =
                if (useSearchView) {
                    searchView!!.editText
                } else {
                    legacySearchView!!.findViewById(androidx.appcompat.R.id.search_src_text)
                }
            field.requestFocus()
            field.setText("")
            advanceRobolectricLooper()
            field.setSelection(0)
            try {
                field.block()
            } finally {
                advanceRobolectricLooper()
            }
        }

    private fun EditText.paste(clip: ClipData) {
        context.requireSystemService<ClipboardManager>().setPrimaryClip(clip)
        assertTrue(onTextContextMenuItem(android.R.id.paste))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "useSearchView = {0}")
        fun searchViews() = listOf(false, true)
    }
}
