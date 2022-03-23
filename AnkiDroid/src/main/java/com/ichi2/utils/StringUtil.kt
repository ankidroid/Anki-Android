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

 */

package com.ichi2.utils

import org.jetbrains.annotations.Contract
import java.util.*

object StringUtil {
    /** Trims from the right hand side of a string  */
    @JvmStatic
    @Contract("null -> null; !null -> !null")
    fun trimRight(s: String?): String? {
        if (s == null) return null

        var newLength = s.length
        while (newLength > 0 && Character.isWhitespace(s[newLength - 1])) {
            newLength--
        }
        return if (newLength < s.length) s.substring(0, newLength) else s
    }

    /**
     * Remove all whitespace from the start and the end of a [String].
     *
     * A whitespace is defined by [Character.isWhitespace]
     *
     * @param string the string to be stripped, may be null
     * @return the stripped string
     */
    @JvmStatic
    @Contract("null -> null; !null -> !null")
    fun strip(string: String?): String? {
        if (string.isNullOrEmpty()) return string

        var start = 0
        while (start < string.length && Character.isWhitespace(string[start])) {
            start++
        }
        if (start == string.length) {
            return ""
        }
        var end = string.length
        while (end > start && Character.isWhitespace(string[end - 1])) {
            end--
        }
        return if (start == 0 && end == string.length) {
            string
        } else string.substring(start, end)
    }

    /** Converts the string to where the first letter is uppercase, and the rest of the string is lowercase  */
    @JvmStatic
    @Contract("null -> null; !null -> !null")
    fun toTitleCase(s: String?): String? {
        if (s == null) return null
        if (s.isBlank()) return s

        return s.substring(0, 1).uppercase(Locale.getDefault()) + s.substring(1).lowercase(Locale.getDefault())
    }
}
