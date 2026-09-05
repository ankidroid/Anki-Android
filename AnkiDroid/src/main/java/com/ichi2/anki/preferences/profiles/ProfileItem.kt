// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

/** UI model for a row in the profile list. */
data class ProfileItem(
    val id: String,
    val name: String,
) {
    /** Uppercased first letter of the name, shown in the avatar circle. */
    val initial: String
        get() = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}
