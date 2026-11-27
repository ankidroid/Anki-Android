// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.Context

/**
 * The provider the user chose, pinned to the SHA-256 digests of the certificates
 * it was signed with when chosen.
 */
internal class Choice(
    val packageName: String,
    val authority: String,
    val certificate: String,
)

/** Keeps the user's [Choice] in the client app's own preferences */
internal class ChoiceStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Choice? {
        val packageName = prefs.getString(PREF_PACKAGE, null) ?: return null
        val authority = prefs.getString(PREF_AUTHORITY, null) ?: return null
        val certificate = prefs.getString(PREF_CERTIFICATE, null) ?: return null
        return Choice(packageName, authority, certificate)
    }

    fun save(choice: Choice) {
        prefs
            .edit()
            .putString(PREF_PACKAGE, choice.packageName)
            .putString(PREF_AUTHORITY, choice.authority)
            .putString(PREF_CERTIFICATE, choice.certificate)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "com.ichi2.anki.api.provider_choice"
        const val PREF_PACKAGE = "package"
        const val PREF_AUTHORITY = "authority"
        const val PREF_CERTIFICATE = "certificate"
    }
}
