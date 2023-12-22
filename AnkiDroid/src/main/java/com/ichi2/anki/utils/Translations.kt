

/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("ktlint:standard:filename")

package com.ichi2.anki.utils

import android.content.Context
import androidx.annotation.StringRes

/**
 * A string that can be constructed now and localized later
 * by calling [toTranslatedString] with a suitable [Context].
 *
 *     TranslatableString.by(R.string.hello_world)
 *         .toTranslatedString(context)
 *
 *     TranslatableString { getString(R.string.hello_s, "world") }
 *         .toTranslatedString(context)
 *
 */
fun interface TranslatableString {
    fun Context.toTranslatedString(): String

    companion object {
        fun by(
            @StringRes stringId: Int,
        ) = TranslatableString { getString(stringId) }
    }
}

// This method exists as the way to call `TranslatableString.toTranslatedString()` without it
// would be to say `with (translatableString) context.toTranslatableString()`.
// TODO Once using context receivers, remove this method,
//   instead writing the SAM as `context(Context) fun toTranslatedString()`.
fun TranslatableString.toTranslatedString(context: Context) = context.toTranslatedString()
