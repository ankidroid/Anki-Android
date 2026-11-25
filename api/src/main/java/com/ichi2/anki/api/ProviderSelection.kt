// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ichi2.anki.FlashCardsContract

/**
 * Picks the provider the API talks to:
 * - the user's choice, while its app is installed, holds the authority and keeps its certificate
 * - otherwise AnkiDroid, if it's the only usable provider and passes [AnkiDroidProvider]'s check
 * - otherwise the user has to choose: several providers are installed, or the only one isn't
 *   AnkiDroid signed with its release key
 *
 * A provider is usable only if the client app requests its permission in its manifest. The API
 * library requests AnkiDroid's, so other providers are offered only by clients that request theirs.
 */
internal class ProviderSelection(
    context: Context,
) {
    private val context: Context = context.applicationContext
    private val store = ChoiceStore(context)

    /** The installed providers this app can use, AnkiDroid's authority first */
    fun installed(): List<FlashCardsProvider> {
        val requested = requestedPermissions()
        val (usable, unusable) = ProviderDiscovery.find(context).partition { it.permission in requested }
        for (provider in unusable) {
            Log.w(TAG, "Not offering ${provider.packageName}: this app doesn't request ${provider.permission}")
        }
        return usable
    }

    /** The provider in use when one is available and the user does not need to choose. */
    fun inUse(): FlashCardsProvider? {
        val providers = installed()
        return chosen(providers) ?: providers.singleOrNull()?.takeIf(::isAnkiDroid)
    }

    /** Whether [provider] is AnkiDroid and passes the certificate check */
    fun isAnkiDroid(provider: FlashCardsProvider): Boolean =
        provider.authority == FlashCardsContract.AUTHORITY &&
            AnkiDroidProvider.resolve(context)?.packageName == provider.packageName

    /** Stores the user's choice of [provider], pinned to its current certificate */
    fun choose(provider: FlashCardsProvider): Boolean {
        if (provider !in installed() || !holdsAuthority(provider)) {
            return false
        }
        val certificate = AnkiDroidProvider.signer(context, provider.packageName) ?: return false
        store.save(Choice(provider.packageName, provider.authority, certificate))
        return true
    }

    /** The stored choice if still valid; an invalid choice is dropped */
    private fun chosen(providers: List<FlashCardsProvider>): FlashCardsProvider? {
        val choice = store.load() ?: return null

        val provider = providers.firstOrNull { it.packageName == choice.packageName && it.authority == choice.authority }
        val valid =
            provider != null &&
                holdsAuthority(provider) &&
                AnkiDroidProvider.signer(context, provider.packageName) == choice.certificate
        if (!valid) {
            Log.w(TAG, "Dropping the choice of ${choice.packageName}: uninstalled, unusable or signed with another key")
            store.clear()
            return null
        }
        return provider
    }

    private fun holdsAuthority(provider: FlashCardsProvider): Boolean =
        AnkiDroidProvider.find(context.packageManager, 0, provider.authority)?.packageName == provider.packageName

    @Suppress("DEPRECATION") // getPackageInfo(String, Int), used only before API 33
    private fun requestedPermissions(): Set<String> {
        val manager = context.packageManager
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                )
            } else {
                manager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
        return info.requestedPermissions?.toSet() ?: emptySet()
    }

    private companion object {
        const val TAG = "AnkiDroidApi"
    }
}
