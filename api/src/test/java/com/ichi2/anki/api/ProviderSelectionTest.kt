// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.pm.ApplicationInfo
import android.content.pm.Signature
import android.os.Build
import com.ichi2.anki.FlashCardsContract
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When the user has to choose a provider, see [ProviderSelection] */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S_V2])
internal class ProviderSelectionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val selection get() = ProviderSelection(context)

    @Before
    fun setUp() {
        // A released client, which requests the fork's permission as well as AnkiDroid's
        val info = context.applicationInfo
        info.flags = info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        AnkiDroidPackages.request(FlashCardsContract.READ_WRITE_PERMISSION, FORK.permission)
    }

    @Test
    fun nothingInstalled() {
        assertNull(selection.inUse())
        assertEquals(emptyList(), selection.installed())
    }

    @Test
    fun signedAnkiDroidAloneNeedsNoChoice() {
        installAnkiDroid(AnkiDroidPackages.RELEASE)

        assertEquals(ANKIDROID, selection.inUse())
    }

    @Test
    fun unsignedAnkiDroidNeedsChoice() {
        installAnkiDroid(UNKNOWN)

        assertEquals(listOf(ANKIDROID), selection.installed())
        assertNull(selection.inUse())
    }

    @Test
    fun forkAloneNeedsChoice() {
        installFork()

        assertEquals(listOf(FORK), selection.installed())
        assertNull(selection.inUse())
    }

    @Test
    fun severalProvidersNeedChoice() {
        installAnkiDroid(AnkiDroidPackages.RELEASE)
        installFork()

        assertEquals(listOf(ANKIDROID, FORK), selection.installed())
        assertNull(selection.inUse())
    }

    @Test
    fun choiceIsKept() {
        installAnkiDroid(AnkiDroidPackages.RELEASE)
        installFork()

        assertTrue(selection.choose(FORK))
        assertEquals(FORK, selection.inUse())

        assertTrue(selection.choose(ANKIDROID))
        assertEquals(ANKIDROID, selection.inUse())
    }

    @Test
    fun choiceIsDroppedWhenSignedWithAnotherKey() {
        installAnkiDroid(AnkiDroidPackages.RELEASE)
        installFork()
        assertTrue(selection.choose(FORK))

        installFork(OTHER_UNKNOWN)

        assertNull(selection.inUse())
    }

    /** The client can only be granted permissions it requests, so other providers are not offered */
    @Test
    fun providerWithUnrequestedPermissionIsNotOffered() {
        AnkiDroidPackages.request(FlashCardsContract.READ_WRITE_PERMISSION)
        installFork()

        assertEquals(emptyList(), selection.installed())
        assertNull(selection.inUse())

        installAnkiDroid(AnkiDroidPackages.RELEASE)
        assertEquals(ANKIDROID, selection.inUse())
    }

    @Test
    fun unavailableProviderDoesNotReplacePreviousChoice() {
        installAnkiDroid(AnkiDroidPackages.RELEASE)
        assertTrue(selection.choose(ANKIDROID))
        assertFalse(selection.choose(FORK))
        installFork()
        assertEquals(ANKIDROID, selection.inUse())
    }

    private fun installAnkiDroid(key: Signature) =
        AnkiDroidPackages.install(ANKIDROID.packageName, Signing.Current(key), declaresInterface = true)

    private fun installFork(key: Signature = UNKNOWN) =
        AnkiDroidPackages.install(FORK.packageName, Signing.Current(key), authority = FORK.authority, declaresInterface = true)

    private companion object {
        val ANKIDROID = FlashCardsProvider(BuildConfig.ANKI_PACKAGE_NAME, FlashCardsContract.AUTHORITY)
        val FORK = FlashCardsProvider("org.example.ankifork", "org.example.ankifork.flashcards")
        val UNKNOWN = Signature(byteArrayOf(1, 2, 3))
        val OTHER_UNKNOWN = Signature(byteArrayOf(4, 5, 6))
    }
}
