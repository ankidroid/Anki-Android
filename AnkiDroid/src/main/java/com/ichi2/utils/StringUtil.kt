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
import kotlin.math.min

object StringUtil {
    /** Converts the string to where the first letter is uppercase, and the rest of the string is lowercase  */
    @Contract("null -> null; !null -> !null")
    fun toTitleCase(s: String?): String? {
        if (s == null) return null
        if (s.isBlank()) return s

        return s.substring(0, 1).uppercase(Locale.getDefault()) + s.substring(1).lowercase(Locale.getDefault())
    }
}

fun String.trimToLength(maxLength: Int): String {
    return this.substring(0, min(this.length, maxLength))
}
