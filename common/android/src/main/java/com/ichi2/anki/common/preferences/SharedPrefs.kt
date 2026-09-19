// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** shorthand method to get the default [SharedPreferences] instance */
fun Context.sharedPrefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
