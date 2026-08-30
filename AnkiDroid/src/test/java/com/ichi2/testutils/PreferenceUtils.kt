// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.annotation.StringRes
import androidx.core.content.edit
import com.ichi2.anki.settings.Prefs

fun withBooleanPreference(
    @StringRes keyResource: Int,
    value: Boolean,
    action: () -> Unit,
) {
    val preferences = Prefs.sharedPrefs
    val key = Prefs.key(keyResource)
    val previousValue = if (preferences.contains(key)) preferences.getBoolean(key, false) else null
    preferences.edit { putBoolean(key, value) }
    try {
        action()
    } finally {
        preferences.edit {
            if (previousValue == null) remove(key) else putBoolean(key, previousValue)
        }
    }
}
