/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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

import android.os.Bundle

/**
 * Collection of useful methods to be used with [android.os.Bundle]
 */
object BundleUtils {
    /**
     * Retrieves a [Long] value from a [Bundle] using a key, returns null if not found
     *
     * can be null to support nullable bundles like [androidx.fragment.app.Fragment.getArguments]
     * @param key the key to use
     * @return the long value, or null if not found
     */
    fun Bundle.getNullableLong(key: String): Long? {
        return if (!containsKey(key)) {
            null
        } else {
            getLong(key)
        }
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
}
