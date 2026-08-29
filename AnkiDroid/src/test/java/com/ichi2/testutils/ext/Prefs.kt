// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
package com.ichi2.testutils.ext

import androidx.core.content.edit
import com.ichi2.anki.settings.Prefs

fun Prefs.clear() {
    sharedPrefs.edit { clear() }
}
