// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import android.content.Context
import androidx.annotation.StringRes

/** A progress message, resolved to a [String] by the UI. */
sealed interface ProgressText {
    data class Raw(
        val text: String,
    ) : ProgressText

    data class Res(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : ProgressText

    fun resolve(context: Context): String =
        when (this) {
            is Raw -> text
            is Res -> context.getString(resId, *args.toTypedArray())
        }
}
