// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import com.ichi2.anki.api.ProviderChooser.State
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** Robolectric: [FlashCardsProvider.permission] reads [com.ichi2.anki.FlashCardsContract], which parses URIs */
@RunWith(RobolectricTestRunner::class)
internal class ProviderChooserTest {
    private val granted = mutableSetOf<String>()

    private fun chooser(current: FlashCardsProvider? = ANKIDROID) =
        ProviderChooser(listOf(ANKIDROID, FORK), current) { permission ->
            if (permission in granted) PermissionState.GRANTED else PermissionState.DENIED
        }

    @Test
    fun opensWithProviderInUseSelected() {
        assertEquals(State.Choosing(ANKIDROID), chooser().state)
        assertEquals(State.Choosing(FORK), chooser(current = FORK).state)
    }

    @Test
    fun opensWithNothingSelectedBeforeAChoice() {
        assertEquals(State.Choosing(null), chooser(current = null).state)
    }

    @Test
    fun pickWithPermissionIsChosen() {
        granted += FORK.permission

        assertEquals(State.Chosen(FORK), chooser().pick(FORK))
    }

    @Test
    fun pickWithoutPermissionAsksForIt() {
        val chooser = chooser()

        assertEquals(State.AwaitingPermission(FORK), chooser.pick(FORK))
        assertEquals(State.Chosen(FORK), chooser.onPermission(PermissionState.GRANTED))
    }

    @Test
    fun deniedPermissionReturnsToList() {
        val chooser = chooser()
        chooser.pick(FORK)

        assertEquals(State.Choosing(ANKIDROID), chooser.onPermission(PermissionState.DENIED))
    }

    @Test
    fun cancelClosesList() {
        assertEquals(State.Cancelled, chooser().cancel())
    }

    @Test
    fun providerNotListedIsIgnored() {
        val chooser = chooser()

        assertEquals(State.Choosing(ANKIDROID), chooser.pick(OTHER))
    }

    @Test
    fun permissionResultWhileChoosingIsIgnored() {
        val chooser = chooser()

        assertEquals(State.Choosing(ANKIDROID), chooser.onPermission(PermissionState.GRANTED))
    }

    @Test
    fun chosenIsFinal() {
        granted += FORK.permission
        val chooser = chooser()
        chooser.pick(FORK)

        assertEquals(State.Chosen(FORK), chooser.pick(ANKIDROID))
        assertEquals(State.Chosen(FORK), chooser.cancel())
    }

    private companion object {
        val ANKIDROID = provider("com.ichi2.anki")
        val FORK = provider("org.example.fork")
        val OTHER = provider("org.example.other")

        fun provider(packageName: String) = FlashCardsProvider(packageName, "$packageName.flashcards")
    }
}
