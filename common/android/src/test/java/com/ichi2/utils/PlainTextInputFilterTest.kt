// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.ext.hasBitFlag
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [24, Config.TARGET_SDK])
class PlainTextInputFilterTest {
    /**
     * A non-composing URLSpan forces the filter to copy the input. A builder's subSequence()
     * drops NoCopySpan objects, including Android's composing marker, so that copy must preserve
     * the composing range as well as its visual style. Also check clipping to the inserted range.
     */
    @Test
    fun `removing formatting from a builder retains the keyboard composing range`() {
        val composingStyle = StyleSpan(Typeface.BOLD)
        val link = URLSpan("https://apps.ankiweb.net/")
        val source =
            SpannableStringBuilder(" été ").apply {
                setSpan(composingStyle, 1, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                BaseInputConnection.setComposingSpans(this)
                setSpan(link, 1, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

        val filtered =
            assertIs<Spannable>(PlainTextInputFilter.filter(source, 1, 4, SpannableString(""), 0, 0))

        assertEquals("été", filtered.toString())
        assertEquals(-1, filtered.getSpanStart(link))
        assertEquals(0, BaseInputConnection.getComposingSpanStart(filtered))
        assertEquals(3, BaseInputConnection.getComposingSpanEnd(filtered))
        assertEquals(0, filtered.getSpanStart(composingStyle))
        assertEquals(3, filtered.getSpanEnd(composingStyle))
        assertTrue(filtered.getSpanFlags(composingStyle).hasBitFlag(Spanned.SPAN_COMPOSING))

        // Filtering must leave the source's formatting and composing range intact.
        assertEquals(1, source.getSpanStart(link))
        assertEquals(0, BaseInputConnection.getComposingSpanStart(source))
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(source))
    }

    /**
     * Input filters run for keyboard input as well as paste. IMEs use SPAN_COMPOSING to mark
     * unfinished text and can attach a composing StyleSpan to highlight the current candidate.
     * Converting the input with toString() would discard these spans, losing the range that the next
     * composing update or commit must replace. Subsequent input would then append to that text.
     */
    @Test
    fun `keyboard composing spans survive so updates replace unfinished text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val field = EditText(context).apply { filters += PlainTextInputFilter }
        val composingStyle = StyleSpan(Typeface.BOLD)
        val composingText =
            SpannableString("été").apply {
                setSpan(composingStyle, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
            }
        val connection = assertNotNull(field.onCreateInputConnection(EditorInfo()))

        assertTrue(connection.setComposingText("é", 1))
        assertEquals("é", field.text.toString())

        assertTrue(connection.setComposingText(composingText, 1))
        assertEquals("été", field.text.toString())
        assertEquals(0, field.text.getSpanStart(composingStyle))
        assertEquals(3, field.text.getSpanEnd(composingStyle))
        assertTrue(field.text.getSpanFlags(composingStyle).hasBitFlag(Spanned.SPAN_COMPOSING))

        assertTrue(connection.commitText("été", 1))
        assertEquals("été", field.text.toString())
        assertEquals(3, field.selectionStart)
    }
}
