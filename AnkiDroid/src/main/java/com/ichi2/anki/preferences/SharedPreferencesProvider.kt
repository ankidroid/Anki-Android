/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.preferences

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Provides a reference to [SharedPreferences] without a required Android dependency */
// SharedPreferences is an interface, so this remains Android-free
fun interface SharedPreferencesProvider {
    fun sharedPrefs(): SharedPreferences
}

@VisibleForTesting
fun SharedPreferencesProvider.booleanPref(
    key: String,
    defaultValue: Boolean,
): ReadWriteProperty<Any?, Boolean> =
    object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ): Boolean = sharedPrefs().getBoolean(key, defaultValue)

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Boolean,
        ) {
            sharedPrefs().edit { putBoolean(key, value) }
        }
    }
