// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.mockito.kotlin.mock

class DeckPickerBackgroundLoaderTest {
    @Test
    fun onStartAppliesLoadedValue() =
        runTest {
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = this,
                    load = { "background" },
                    apply = { applied.add(it) },
                )

            loader.onStart()
            advanceUntilIdle()

            assertThat(applied, equalTo(listOf("background")))
        }

    @Test
    fun onStopClearsToNull() =
        runTest {
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = this,
                    load = { "background" },
                    apply = { applied.add(it) },
                )

            loader.onStart()
            advanceUntilIdle()
            loader.onStop()

            assertThat(applied, equalTo(listOf("background", null)))
        }

    @Test
    fun onStopWhileLoadSuspendedDoesNotApplyLateResult() =
        runTest {
            val gate = CompletableDeferred<String>()
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = CoroutineScope(coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                    load = { gate.await() },
                    apply = { applied.add(it) },
                )

            loader.onStart()
            loader.onStop()
            gate.complete("late")
            advanceUntilIdle()

            assertThat(applied, equalTo(listOf(null)))
        }

    @Test
    fun onStopWhileNonCancellableLoadDoesNotApplyLateResult() =
        runTest {
            val gate = CompletableDeferred<String>()
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = CoroutineScope(coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                    load = {
                        withContext(NonCancellable) {
                            gate.await()
                        }
                    },
                    apply = { applied.add(it) },
                )

            loader.onStart()
            loader.onStop()
            gate.complete("late")
            advanceUntilIdle()

            assertThat(applied, equalTo(listOf(null)))
        }

    @Test
    fun onStartAfterOnStopLoadsAgain() =
        runTest {
            var loadCount = 0
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = this,
                    load = { "background-${++loadCount}" },
                    apply = { applied.add(it) },
                )

            loader.onStart()
            advanceUntilIdle()
            loader.onStop()
            loader.onStart()
            advanceUntilIdle()

            assertThat(applied, equalTo(listOf("background-1", null, "background-2")))
            assertThat(loadCount, equalTo(2))
        }

    @Test
    fun onStartCancelsInFlightLoad() =
        runTest {
            val first = CompletableDeferred<String>()
            var loads = 0
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = CoroutineScope(coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                    load = {
                        loads++
                        if (loads == 1) first.await() else "second"
                    },
                    apply = { applied.add(it) },
                )

            loader.onStart()
            loader.onStart()
            first.complete("first")
            advanceUntilIdle()

            assertThat(applied, equalTo(listOf("second")))
        }

    @Test
    fun onStopWithoutOnStartAppliesNull() =
        runTest {
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = this,
                    load = { "background" },
                    apply = { applied.add(it) },
                )

            loader.onStop()

            assertThat(applied, equalTo(listOf(null)))
        }

    @Test
    fun lifecycleObserverForwardsStartAndStop() =
        runTest {
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = this,
                    load = { "background" },
                    apply = { applied.add(it) },
                )
            val observer = DeckPickerBackgroundLifecycleObserver(loader)
            val owner = mock<LifecycleOwner>()

            observer.onStart(owner)
            advanceUntilIdle()
            observer.onStop(owner)

            assertThat(applied, equalTo(listOf("background", null)))
        }
}
