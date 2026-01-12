/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

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
package com.ichi2.anki.utils.ext

import android.os.Bundle
import androidx.core.os.bundleOf

/**
 * Retrieves a [Long] value from a [Bundle] using a key, returns null if not found
 *
 * can be null to support nullable bundles like [androidx.fragment.app.Fragment.getArguments]
 * @param key the key to use
 * @return the long value, or null if not found
 */
fun Bundle.getLongOrNull(key: String): Long? =
    if (!containsKey(key)) {
        null
    } else {
        getLong(key)
    }

/**
 * Retrieves a [Long] value from a [Bundle] using a key, throws if not found
 *
 * @param key A string key
 * @return the value associated with [key]
 * @throws IllegalStateException If [key] does not exist in the bundle
 */
fun Bundle.requireLong(key: String): Long {
    if (!this.containsKey(key)) {
        throw IllegalStateException("key: '$key' not found")
    }
    return getLong(key)
}

/**
 * Retrieves a [Int] value from a [Bundle] using a key, returns null if not found
 *
 * can be null to support nullable bundles like [androidx.fragment.app.Fragment.getArguments]
 * @param key the key to use
 * @return the int value, or null if not found
 */
fun Bundle.getIntOrNull(key: String): Int? =
    if (!containsKey(key)) {
        null
    } else {
        getInt(key)
    }

/**
 * Retrieves a [Boolean] value from a [Bundle] using a key, throws if not found
 *
 * @param key A string key
 * @return the value associated with [key]
 * @throws IllegalStateException If [key] does not exist in the bundle
 */
fun Bundle.requireBoolean(key: String): Boolean {
    check(containsKey(key)) { "key: '$key' not found" }
    return getBoolean(key)
}

/**
 * Returns a new [Bundle] with the given key/value pairs as elements.
 *
 * Convenience method, allowing a `null` pair to mean 'exclude from the bundle'
 *
 * ```kotlin
 * bundleOfNotNull(
 *     optional?.let { KEY to it }
 * )
 * ```
 *
 * @throws IllegalArgumentException When a value is not a supported type of [Bundle].
 */
fun bundleOfNotNull(vararg pairs: Pair<String, Any>?): Bundle = bundleOf(*pairs.mapNotNull { it }.toTypedArray())
