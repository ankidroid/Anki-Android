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

package com.ichi2.utils;

import org.jetbrains.annotations.Contract;

import androidx.annotation.Nullable;

public class StringUtil {

    /** Trims from the right hand side of a string */
    @Nullable
    @Contract("null -> null; !null -> !null")
    public static String trimRight(@Nullable String s) {
        if (s == null) {
            return null;
        }

        int newLength = s.length();
        while (newLength > 0 && Character.isWhitespace(s.charAt(newLength - 1))) {
            newLength--;
        }
        return newLength < s.length() ? s.substring(0, newLength) : s;
    }
}
