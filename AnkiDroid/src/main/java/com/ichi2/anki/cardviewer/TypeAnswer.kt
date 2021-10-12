/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import java.util.regex.Pattern

class TypeAnswer(
    @get:JvmName("useInputTag") val useInputTag: Boolean,
    @get:JvmName("doNotUseCodeFormatting") val doNotUseCodeFormatting: Boolean,
    /** Preference: Whether the user wants to focus "type in answer" */
    val autoFocus: Boolean
) {

    companion object {
        @JvmField
        /** Regular expression in card data for a 'type answer' after processing has occurred */
        val PATTERN: Pattern = Pattern.compile("\\[\\[type:(.+?)]]")

        @JvmStatic
        fun createInstance(preferences: SharedPreferences): TypeAnswer {
            return TypeAnswer(
                useInputTag = preferences.getBoolean("useInputTag", false),
                doNotUseCodeFormatting = preferences.getBoolean("noCodeFormatting", false),
                autoFocus = preferences.getBoolean("autoFocusTypeInAnswer", false)
            )
        }
    }
}
