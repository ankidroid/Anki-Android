// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import androidx.annotation.VisibleForTesting
import java.io.File

/** True in the short-lived ProcessPhoenix process, which only relaunches the app. */
fun isPhoenixProcess(): Boolean = isPhoenixProcessName(currentProcessName())

@VisibleForTesting
internal fun isPhoenixProcessName(name: String?): Boolean = name?.endsWith(":phoenix") == true

private fun currentProcessName(): String? = runCatching { File("/proc/self/cmdline").readText().trim { it <= ' ' } }.getOrNull()
