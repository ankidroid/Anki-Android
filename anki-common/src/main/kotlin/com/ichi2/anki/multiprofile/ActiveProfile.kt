// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.content.Context

/** Implemented by the [android.app.Application] that loads profiles. */
interface ActiveProfileProvider {
    val activeProfileId: ProfileId
}

/** The ID of the profile open in this process, or [ProfileId.DEFAULT] if no profile is loaded. */
fun Context.activeProfileId(): ProfileId = (applicationContext as? ActiveProfileProvider)?.activeProfileId ?: ProfileId.DEFAULT
