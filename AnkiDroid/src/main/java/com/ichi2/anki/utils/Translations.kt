// ktlint-disable filename

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

package com.ichi2.anki.utils

import android.content.Context
import androidx.annotation.StringRes

/**
 * A string that can be localized by calling [toString] with a proper [Context].
 * If instantiated by calling [by] with arguments,
 * the arguments that are also [TranslatableString] are localized as well, e.g.
 *
 *     TranslatableString.by(
 *         R.string.hello_s,
 *         TranslatableString.by(R.string.world)
 *     ).toString(context)
 *
 * will print something along in the language of the context:
 *
 *     Hello, world!
 *
 * When overriding [toString], care must be taken to ensure that
 * only the provided context is used to construct the resulting string.
 */
fun interface TranslatableString {
    fun toString(context: Context): String

    companion object {
        fun by(@StringRes stringId: Int) =
            TranslatableString { context -> context.getString(stringId) }

        fun by(@StringRes stringId: Int, vararg arguments: Any?) =
            TranslatableString { context ->
                context.getString(
                    stringId,
                    *arguments.mapToArray { if (it is TranslatableString) it.toString(context) else it }
                )
            }
    }
}
