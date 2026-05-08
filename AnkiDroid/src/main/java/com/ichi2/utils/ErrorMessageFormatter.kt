// SPDX-FileCopyrightText: 2026 Aryan Jaiswal <aryanjaiswal123123@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.TypefaceSpan
import androidx.annotation.ColorInt
import com.ichi2.themes.Themes

private val PRE_TAG_REGEX = Regex("<pre>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)

/**
 * Renders `<pre>...</pre>` blocks from backend error messages as monospaced
 * code blocks instead of leaking the raw markup to the user.
 */
fun formatErrorMessage(
    message: String,
    @ColorInt codeBackgroundColor: Int,
): CharSequence {
    val matches = PRE_TAG_REGEX.findAll(message).toList()
    if (matches.isEmpty()) return message

    val builder = SpannableStringBuilder()
    var cursor = 0
    for (match in matches) {
        builder.append(message, cursor, match.range.first)

        val codeStart = builder.length
        builder.append(match.groupValues[1])
        val codeEnd = builder.length

        builder.setSpan(TypefaceSpan("monospace"), codeStart, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(BackgroundColorSpan(codeBackgroundColor), codeStart, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        cursor = match.range.last + 1
    }
    if (cursor < message.length) {
        builder.append(message, cursor, message.length)
    }
    return builder
}

fun Context.formatErrorMessage(message: String): CharSequence =
    formatErrorMessage(
        message,
        Themes.getColorFromAttr(this, com.google.android.material.R.attr.colorSurfaceContainerHighest),
    )
