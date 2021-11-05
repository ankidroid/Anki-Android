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
package com.ichi2.libanki.utils

import java.util.ArrayList
import java.util.regex.Pattern

object StringUtils {
    private val WHITESPACE_PATTERN = Pattern.compile("\\s+", Pattern.MULTILINE or Pattern.DOTALL)

    /** Equivalent to the python string.split()  */
    @JvmStatic
    fun splitOnWhitespace(value: String): List<String> {
        val split = WHITESPACE_PATTERN.split(value)
        val ret: MutableList<String> = ArrayList(split.size)
        for (s in split) {
            if (s.length > 0) {
                ret.add(s)
            }
        }
        return ret
    }
}
