// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps a loaded DeckPicker background in memory only while the screen is started.
 *
 * [onStart] cancels any prior load, then loads and [apply]s the result.
 * [onStop] cancels an in-flight load and [apply]s `null` so the caller can drop the bitmap.
 * A load that completes after [onStop] is not applied, even if [load] ignores cancellation.
 */
class DeckPickerBackgroundLoader<T>(
    private val scope: CoroutineScope,
    private val load: suspend () -> T,
    private val apply: (T?) -> Unit,
) {
    private var loadJob: Job? = null
    private var generation: Long = 0L

    fun onStart() {
        loadJob?.cancel()
        val expectedGeneration = ++generation
        loadJob =
            scope.launch {
                val result = load()
                if (expectedGeneration != generation) return@launch
                apply(result)
            }
    }

    fun onStop() {
        generation++
        loadJob?.cancel()
        loadJob = null
        apply(null)
    }
}

/** Forwards [Lifecycle.Event.ON_START] / [Lifecycle.Event.ON_STOP] to [loader]. */
class DeckPickerBackgroundLifecycleObserver<T>(
    private val loader: DeckPickerBackgroundLoader<T>,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        loader.onStart()
    }

    override fun onStop(owner: LifecycleOwner) {
        loader.onStop()
    }
}

fun installDeckPickerBackgroundLoader(
    lifecycle: Lifecycle,
    loader: DeckPickerBackgroundLoader<*>,
) {
    lifecycle.addObserver(DeckPickerBackgroundLifecycleObserver(loader))
}
