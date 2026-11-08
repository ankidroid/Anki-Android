// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.ParagraphStyle
import com.ichi2.anki.common.utils.ext.hasBitFlag

/** Removes text formatting while preserving composing spans used by input methods. */
object PlainTextInputFilter : InputFilter {
    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int,
    ): CharSequence? {
        // Accept the original replacement; plain text needs no filtering.
        if (source !is Spanned) return null
        val formattingSpans =
            source.getSpans(start, end, Any::class.java).filter {
                // keep SPAN_COMPOSING spans so the IME can replace unfinished text.
                (it is CharacterStyle || it is ParagraphStyle) && !source.getSpanFlags(it).hasBitFlag(Spanned.SPAN_COMPOSING)
            }
        if (formattingSpans.isEmpty()) return null

        val filteredText = source.toSpannableString(start, end)

        for (span in formattingSpans) {
            // Remove the span (e.g. URLSpan), the link text remains.
            filteredText.removeSpan(span)
        }
        return filteredText
    }

    /**
     * Converts this [Spanned] to a [SpannableString], copying [start] (inclusive) to [end] (exclusive).
     *
     * All spans are preserved, including `NoCopySpan` markers.
     */
    private fun Spanned.toSpannableString(
        start: Int,
        end: Int,
    ): SpannableString {
        val copy = SpannableString(TextUtils.substring(this, start, end))
        TextUtils.copySpansFrom(this, start, end, Any::class.java, copy, 0)
        return copy
    }
}
