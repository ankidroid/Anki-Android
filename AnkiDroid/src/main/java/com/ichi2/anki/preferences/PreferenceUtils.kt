/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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

import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.utils.getJavaFieldAsAccessible

/**
 * Sets the callback to be invoked when this preference is changed by the user
 * (but before the internal state has been updated) on the internal onPreferenceChangeListener,
 * returning true on it by default
 * @param onPreferenceChangeListener The callback to be invoked
 */
fun Preference.setOnPreferenceChangeListener(onPreferenceChangeListener: (newValue: Any) -> Unit) {
    this.setOnPreferenceChangeListener { _, newValue ->
        onPreferenceChangeListener(newValue)
        true
    }
}

/** Obtains a non-null reference to the preference defined by the key, or throws  */
inline fun <reified T : Preference> PreferenceFragmentCompat.requirePreference(key: String): T {
    val preference = findPreference<Preference>(key)
        ?: throw IllegalStateException("missing preference: '$key'")
    return preference as T
}

/**
 * Obtains a non-null reference to the preference whose
 * key is defined with given [resId] or throws
 * e.g. `requirePreference(R.string.day_theme_key)` returns
 * the preference whose key is `@string/day_theme_key`
 * The resource IDs with preferences keys can be found on `res/values/preferences.xml`
 */
inline fun <reified T : Preference> PreferenceFragmentCompat.requirePreference(@StringRes resId: Int): T {
    val key = getString(resId)
    return requirePreference(key)
}

fun Preference.getDefaultValue(): Any? {
    return getJavaFieldAsAccessible(Preference::class.java, "mDefaultValue").get(this)
}
