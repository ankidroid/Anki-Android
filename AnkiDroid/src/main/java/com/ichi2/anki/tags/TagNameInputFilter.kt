// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.anki.tags

import android.text.InputFilter
import android.text.Spanned

/**
 * The filter that constrains the inputted tag.
 * Space is not allowed in a tag. For UX of hierarchical tag, inputting a space will instead
 * insert "::" at the cursor. If there are already some colons in front of the cursor,
 * complete to 2 colons. For example:
 *   "tag"   -- input a space --> "tag::"
 *   "tag:"  -- input a space --> "tag::"
 *   "tag::" -- input a space --> "tag::"
 */
internal val tagNameInputFilter =
    InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned?, destStart: Int, _: Int ->
        if (!source.subSequence(start, end).contains(' ')) {
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
        for (char in source.subSequence(start, end)) {
            if (char == ' ') {
                if (previousColonsCnt == 0) {
                    sb.append("::")
                } else if (previousColonsCnt == 1) {
                    sb.append(":")
                }
            } else {
                sb.append(char)
            }
            previousColonsCnt =
                if (char == ':') {
                    previousColonsCnt + 1
                } else {
                    0
                }
        }
        sb
    }
