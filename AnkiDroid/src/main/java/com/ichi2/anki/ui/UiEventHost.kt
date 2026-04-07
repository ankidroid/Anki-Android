/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot UI events from a ViewModel to its UI, using a single hot Flow.
 *
 * Each event must be delivered EXACTLY once, buffered while the UI is in the background,
 * and never replayed on re-subscription.
 *
 * This is the wrong choice for ongoing state (use `StateFlow` instead).
 */
interface UiEventHost<T : Any> {
    val uiEvents: Flow<T>

    suspend fun emit(event: T)

    fun tryEmit(event: T): Boolean
}

/**
 * Implements [UiEventHost] using a [Channel]
 *
 * @see UiEventHost
 */
class ChannelUiEventHost<T : Any>(
    capacity: Int = Channel.BUFFERED,
) : UiEventHost<T> {
    private val channel = Channel<T>(capacity)
    override val uiEvents: Flow<T> = channel.receiveAsFlow()

    override suspend fun emit(event: T) = channel.send(event)

    override fun tryEmit(event: T): Boolean = channel.trySend(event).isSuccess
}
