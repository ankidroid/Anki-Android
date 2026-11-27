// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Build
import com.ichi2.anki.FlashCardsContract

/**
 * Finds installed apps offering the [FlashCardsContract] content provider.
 */
internal object ProviderDiscovery {
    /** Separates the authorities in [ProviderInfo.authority] */
    private const val AUTHORITY_SEPARATOR = ';'

    /**
     * Installed providers, whether or not they are trusted:
     * the holder of [FlashCardsContract.AUTHORITY] first, then the others by package name.
     */
    fun find(context: Context): List<FlashCardsProvider> {
        val manager = context.packageManager

        // AnkiDroid releases older than FlashCardsContract.ACTION_FLASHCARDS_PROVIDER only answer to their authority
        val stock = AnkiDroidProvider.find(manager, 0)

        // Current AnkiDroid builds, parallel builds and forks declare the intent filter
        val declared = manager.queryDeclared()

        return (listOfNotNull(stock) + declared)
            .map { it.toProvider() }
            .distinct()
            .sortedBy { it.packageName }
            .sortedBy { it.authority != FlashCardsContract.AUTHORITY }
    }

    private fun PackageManager.queryDeclared(): List<ProviderInfo> {
        // queryIntentContentProviders was added in API 19
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return emptyList()
        }

        val intent = Intent(FlashCardsContract.ACTION_FLASHCARDS_PROVIDER)
        val matches =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                queryIntentContentProviders(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                queryIntentContentProviders(intent, 0)
            }
        return matches.mapNotNull { it.providerInfo }
    }

    private fun ProviderInfo.toProvider(): FlashCardsProvider =
        FlashCardsProvider(packageName, authority.substringBefore(AUTHORITY_SEPARATOR))
}
