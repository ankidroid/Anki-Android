// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.pm.Signature
import com.ichi2.anki.FlashCardsContract
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Robolectric doesn't shadow the Tiramisu+ `resolveContentProvider`, see [AddContentApiTest] */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
internal class ProviderDiscoveryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val found get() = ProviderDiscovery.find(context)

    @Test
    fun noProviderInstalled() {
        assertEquals(emptyList(), found)
    }

    /** AnkiDroid releases before the intent filter are still found by their authority */
    @Test
    fun stockWithoutIntentFilter() {
        install(STOCK_PACKAGE, FlashCardsContract.AUTHORITY, declaresInterface = false)

        assertEquals(listOf(STOCK), found)
    }

    @Test
    fun stockIsListedOnce() {
        install(STOCK_PACKAGE, FlashCardsContract.AUTHORITY, declaresInterface = true)

        assertEquals(listOf(STOCK), found)
    }

    @Test
    fun forkIsFoundByIntentFilter() {
        install(FORK_PACKAGE, FORK_AUTHORITY, declaresInterface = true)

        val fork = found.single()
        assertEquals(FORK, fork)
        assertEquals("$FORK_PACKAGE.permission.READ_WRITE_DATABASE", fork.permission)
    }

    @Test
    fun stockAuthorityComesFirst() {
        install(FORK_PACKAGE, FORK_AUTHORITY, declaresInterface = true)
        install(STOCK_PACKAGE, FlashCardsContract.AUTHORITY, declaresInterface = true)

        assertEquals(listOf(STOCK, FORK), found)
    }

    private fun install(
        packageName: String,
        authority: String,
        declaresInterface: Boolean,
    ) = AnkiDroidPackages.install(
        packageName,
        Signing.Current(Signature(byteArrayOf(1, 2, 3))),
        authority = authority,
        declaresInterface = declaresInterface,
    )

    private companion object {
        val STOCK_PACKAGE = BuildConfig.ANKI_PACKAGE_NAME
        const val FORK_PACKAGE = "org.example.ankifork"
        const val FORK_AUTHORITY = "$FORK_PACKAGE.flashcards"
        val STOCK = FlashCardsProvider(STOCK_PACKAGE, FlashCardsContract.AUTHORITY)
        val FORK = FlashCardsProvider(FORK_PACKAGE, FORK_AUTHORITY)
    }
}
