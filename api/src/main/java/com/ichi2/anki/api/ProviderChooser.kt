// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

/** Whether the client app holds a provider's permission */
internal enum class PermissionState { GRANTED, DENIED }

/**
 * Logic of [ProviderChooserActivity]: the user picks one of the installed [providers],
 * and a provider is only chosen once its permission is granted.
 *
 * @param providers installed providers the client app can use
 * @param current the provider in use, selected when the chooser opens
 * @param awaitingPermission the provider awaiting a permission result after recreation
 * @param permissions whether the client app holds a permission
 */
internal class ProviderChooser(
    val providers: List<FlashCardsProvider>,
    current: FlashCardsProvider?,
    awaitingPermission: FlashCardsProvider? = null,
    private val permissions: (String) -> PermissionState,
) {
    sealed interface State {
        /** The list is shown; [selected] is the provider in use, if any */
        data class Choosing(
            val selected: FlashCardsProvider?,
        ) : State

        /** Android's permission dialog is shown for [provider] */
        data class AwaitingPermission(
            val provider: FlashCardsProvider,
        ) : State

        /** The user picked [provider] and the client app holds its permission */
        data class Chosen(
            val provider: FlashCardsProvider,
        ) : State

        /** The user closed the chooser without picking */
        data object Cancelled : State
    }

    /** The provider in use, kept selected when a permission is denied */
    private val inUse = current?.takeIf { it in providers }

    var state: State =
        awaitingPermission
            ?.takeIf { it in providers }
            ?.let { State.AwaitingPermission(it) }
            ?: State.Choosing(inUse)
        private set

    /** The user taps [provider]: choose it, or first ask for its permission */
    fun pick(provider: FlashCardsProvider): State {
        if (state !is State.Choosing || provider !in providers) {
            return state
        }

        state =
            when (permissions(provider.permission)) {
                PermissionState.GRANTED -> State.Chosen(provider)
                PermissionState.DENIED -> State.AwaitingPermission(provider)
            }
        return state
    }

    /** Android's permission dialog closed: a denied permission returns to the list */
    fun onPermission(result: PermissionState): State {
        val awaiting = state as? State.AwaitingPermission ?: return state

        state =
            when (result) {
                PermissionState.GRANTED -> State.Chosen(awaiting.provider)
                PermissionState.DENIED -> State.Choosing(inUse)
            }
        return state
    }

    /** The user closes the list */
    fun cancel(): State {
        if (state !is State.Choosing) {
            return state
        }

        state = State.Cancelled
        return state
    }
}
