// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.anki.tags

import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The filter that constrains the inputted tag.
 * ASCII and Japanese spaces are not allowed in a tag. Inputting either will instead
 * insert "::" at the cursor. If there are already some colons in front of the cursor,
 * complete to 2 colons. For example:
 *   "tag"   -- input a space --> "tag::"
 *   "tag:"  -- input a space --> "tag::"
 *   "tag::" -- input a space --> "tag::"
 */
internal val tagNameInputFilter = createTagNameInputFilter()

/** In Add Tag, pasted indentation should not create an empty first component. */
internal val addTagNameInputFilter = createTagNameInputFilter(ignoreLeadingSpaces = true)

private fun createTagNameInputFilter(ignoreLeadingSpaces: Boolean = false): InputFilter =
    InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned?, destStart: Int, _: Int ->
        val input = source.subSequence(start, end)
        if (input.none { it == ' ' || it == '\u3000' }) {
            return@InputFilter null
        }
        var previousColonsCnt = 0
        if (dest != null) {
            val previousPart = dest.take(destStart)
            if (previousPart.endsWith("::")) {
                previousColonsCnt = 2
            } else if (previousPart.endsWith(":")) {
                previousColonsCnt = 1
            }
        }
        val sb = StringBuilder()
        val offsets = if (source is Spanned) IntArray(input.length + 1) else null
        for ((index, char) in input.withIndex()) {
            if (char == ' ' || char == '\u3000') {
                if (ignoreLeadingSpaces && destStart == 0 && sb.isEmpty()) continue
                if (previousColonsCnt == 0) {
                    sb.append("::")
                } else if (previousColonsCnt == 1) {
                    sb.append(":")
                }
                previousColonsCnt = 2
            } else {
                sb.append(char)
                previousColonsCnt = if (char == ':') previousColonsCnt + 1 else 0
            }
            offsets?.set(index + 1, sb.length)
        }
        if (source !is Spanned || offsets == null) return@InputFilter sb

        // The IME needs its composing span to replace the current phrase on the next edit.
        SpannableString(sb).apply {
            for (span in source.getSpans(start, end, Any::class.java)) {
                val spanStart = source.getSpanStart(span).coerceIn(start, end) - start
                val spanEnd = source.getSpanEnd(span).coerceIn(start, end) - start
                setSpan(span, offsets[spanStart], offsets[spanEnd], source.getSpanFlags(span))
            }
        }
    }

/** Applies the shared tag separator filter after the IME commits text, preserving selection. */
internal fun TextFieldValue.filterTagInput(): TextFieldValue {
    if (composition != null) return this
    val filtered = tagNameInputFilter.filter(text, 0, text.length, null, 0, 0)?.toString() ?: return this

    fun mapOffset(offset: Int): Int = tagNameInputFilter.filter(text, 0, offset, null, 0, 0)?.length ?: offset

    return copy(text = filtered, selection = TextRange(mapOffset(selection.start), mapOffset(selection.end)))
}
