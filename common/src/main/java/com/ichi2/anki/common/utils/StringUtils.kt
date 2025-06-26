/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.

 ----

 This file incorporates code under the following license:

   Copyright (C) 2006 The Android Open Source Project

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.common.annotations.NeedsTest
import org.jetbrains.annotations.Contract
import java.util.Locale
import kotlin.math.min

@NeedsTest("all except toTitleCase is untested")
object StringUtils {
    /** Converts the string to where the first letter is uppercase, and the rest of the string is lowercase  */
    // TODO(low): some libAnki functions can use this instead of capitalize() alternatives
    @Contract("null -> null; !null -> !null")
    fun toTitleCase(s: String?): String? {
        if (s == null) return null
        if (s.isBlank()) return s

        return s.substring(0, 1).uppercase(Locale.getDefault()) + s.substring(1).lowercase(Locale.getDefault())
    }
}

fun String.trimToLength(maxLength: Int): String = this.substring(0, min(this.length, maxLength))

fun String.lastIndexOfOrNull(c: Char): Int? =
    when (val index = this.lastIndexOf(c)) {
        -1 -> null
        else -> index
    }

fun emptyStringMutableList(size: Int): MutableList<String> = MutableList(size) { "" }

fun emptyStringArray(size: Int): Array<String> = Array(size) { "" }

/**
 * Html-encode the string.
 * @receiver the string to be encoded
 * @return the encoded string
 */
// replaces:
// androidx.core.text.htmlEncode
// android.text.TextUtils.htmlEncode
@DuplicatedCode("copied from android.text.TextUtils.htmlEncode, converted to kotlin extension")
fun String.htmlEncode(): String {
    val sb = StringBuilder()
    var c: Char
    for (i in 0..<this.length) {
        c = this[i]
        when (c) {
            '<' -> sb.append("&lt;") // $NON-NLS-1$
            '>' -> sb.append("&gt;") // $NON-NLS-1$
            '&' -> sb.append("&amp;") // $NON-NLS-1$
            '\'' -> // http://www.w3.org/TR/xhtml1
                // The named character reference &apos; (the apostrophe, U+0027) was introduced in
                // XML 1.0 but does not appear in HTML. Authors should therefore use &#39; instead
                // of &apos; to work as expected in HTML 4 user agents.
                sb.append("&#39;") // $NON-NLS-1$
            '"' -> sb.append("&quot;") // $NON-NLS-1$
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
