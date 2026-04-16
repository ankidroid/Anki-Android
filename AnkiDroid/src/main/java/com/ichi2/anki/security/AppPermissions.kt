/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.security

import android.content.Context
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import timber.log.Timber

/**
 * JS API capabilities that reach beyond the card the user is currently reviewing. Every entry is
 * off by default; call sites consult [AppPermissions.requirePermission] and the method throws when
 * the backing pref is not set.
 */
enum class DangerousJsApiPermission {
    /** Arbitrary field reads across the collection. Gates `ankiSearchCardWithCallback`. */
    QUERY_COLLECTION,
}

/**
 * App-level, user-opt-in capabilities. Distinct from [com.ichi2.utils.Permissions], which wraps
 * Android runtime permissions (audio, storage, notifications).
 */
class AppPermissions(private val context: Context) {
    /**
     * Asserts that the user has granted [permission].
     *
     * @throws DangerousJsPermissionDeniedException if [permission] has not been granted.
     */
    fun requirePermission(permission: DangerousJsApiPermission) {
        val key = context.getString(R.string.pref_allow_dangerous_js_api)
        if (!context.sharedPrefs().getBoolean(key, false)) {
            Timber.w("requirePermission denied: %s", permission.name)
            // The exception is exposed to the JS as an error, as with any security failure from a
            // `JavascriptInterface` method.
            throw DangerousJsPermissionDeniedException(permission.name)
        }
    }
}

class DangerousJsPermissionDeniedException(val permission: String) :
    SecurityException("Dangerous permission blocked: $permission.")
