/****************************************************************************************
 * Copyright (c) 2021 mikunimaru <com.mikuni0@gmail.com>                          *
 *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program. If not, see <http://www.gnu.org/licenses/>.                            *
 ****************************************************************************************/

package com.ichi2.anki

import java.util.*

object LanguageUtils {
    /**
     * Convert a string representation of a locale, in the format returned by Locale.toString(),
     * into a Locale object, disregarding any script and extensions fields (i.e. using solely the
     * language, country and variant fields).
     *
     *
     * Returns a Locale object constructed from an empty string if the input string is null, empty
     * or contains more than 3 fields separated by underscores.
     */
    fun localeFromStringIgnoringScriptAndExtensions(localeCodeStr: String): Locale {
        val localeCode = stripScriptAndExtensions(localeCodeStr)
        val fields = localeCode.split("_".toRegex()).toTypedArray()
        return when (fields.size) {
            1 -> Locale(fields[0])
            2 -> Locale(fields[0], fields[1])
            3 -> Locale(fields[0], fields[1], fields[2])
            else -> Locale("")
        }
    }

    /**
     * @return if app is using a RTL language
     */
    fun appLanguageIsRTL(): Boolean {
        val directionality = Character.getDirectionality(Locale.getDefault().displayName[0])
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun stripScriptAndExtensions(localeCodeStr: String): String {
        val hashPos = localeCodeStr.indexOf('#')
        return if (hashPos >= 0) localeCodeStr.substring(0, hashPos) else localeCodeStr
    }
}
