// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber

/**
 * Something the blocker can gate access to: an installed app or a website domain.
 *
 * [key] is the stable string form used for preferences and intent extras:
 * `app:<packageName>` or `domain:<host>`.
 */
sealed interface BlockTarget {
    val key: String

    data class App(
        val packageName: String,
    ) : BlockTarget {
        override val key get() = "$APP_PREFIX$packageName"
    }

    data class Domain(
        val host: String,
    ) : BlockTarget {
        override val key get() = "$DOMAIN_PREFIX$host"
    }

    /**
     * Name to show the user: an app's launcher label, or the domain itself.
     * Falls back to the package name for an app that can't be resolved.
     */
    fun displayName(context: Context): String =
        when (this) {
            is Domain -> host
            is App ->
                try {
                    context.packageManager
                        .getApplicationInfo(packageName, 0)
                        .loadLabel(context.packageManager)
                        .toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    Timber.w(e, "Blocker: no label for %s", packageName)
                    packageName
                }
        }

    companion object {
        private const val APP_PREFIX = "app:"
        private const val DOMAIN_PREFIX = "domain:"

        fun fromKey(key: String): BlockTarget? =
            when {
                key.startsWith(APP_PREFIX) -> App(key.removePrefix(APP_PREFIX))
                key.startsWith(DOMAIN_PREFIX) -> Domain(key.removePrefix(DOMAIN_PREFIX))
                else -> null
            }
    }
}
