// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeckPickerFocusTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `a refresh focuses a deck selected elsewhere while it runs`() {
        val selectOther = AtomicBoolean(false)
        var other = 0L
        useBackend {
            object : Backend() {
                override fun studiedToday(): String {
                    if (selectOther.compareAndSet(true, false)) setCurrentDeck(other)
                    return super.studiedToday()
                }
            }
        }
        val first = addDeck("First", setAsSelected = true)
        other = addDeck("Other")
        val viewModel = DeckPickerViewModel()

        withCollectionThread {
            val initialRefresh = viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { initialRefresh.isCompleted }
            assertEquals(first, viewModel.focusedDeck)
            selectOther.set(true)
            val refresh = viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { refresh.isCompleted }
        }

        assertEquals(other, col.decks.selected())
        assertEquals(other, viewModel.focusedDeck)
    }

    @Test
    fun `a refresh does not overwrite the focus of a selection made while it runs`() {
        val old = addDeck("Old", setAsSelected = true)
        val latest = addDeck("Latest")
        val viewModel = DeckPickerViewModel()
        val initialRefresh = viewModel.reloadDeckCounts()
        advanceRobolectricLooperUntil { initialRefresh.isCompleted }
        val heldBack = viewModel.holdBackWrite<DeckId?>("flowOfFocusedDeck", old)

        withCollectionThread {
            val refresh = viewModel.reloadDeckCounts()
            assertTrue(heldBack.await(5, TimeUnit.SECONDS))
            val selection = viewModel.selectDeck(latest)
            advanceRobolectricLooperUntil { refresh.isCompleted && selection.isCompleted }
        }

        assertEquals(latest, col.decks.selected())
        assertEquals(latest, viewModel.focusedDeck)
    }

    @Test
    fun `tapping a deck keeps the focus of a selection that finished first`() {
        val tapped = addDeck("Tapped")
        val latest = addDeck("Latest")
        addNoteToDeck(tapped)

        withDeckPicker(deckCount = 0) { deckPicker ->
            val viewModel = deckPicker.viewModel
            advanceRobolectricLooperUntil { viewModel.loadDeckCounts?.isCompleted == true }
            val row = deckPicker.deckRow(tapped)

            withCollectionThread {
                row.performClick()
                viewModel.selectDeck(latest)
                awaitWithoutMainLooper { viewModel.focusedDeck == latest }
                advanceRobolectricLooper()
            }

            assertEquals(latest, col.decks.selected())
            assertEquals(latest, viewModel.focusedDeck)
        }
    }

    @Test
    fun `a replaced refresh does not overwrite the newer studied today line`() {
        val reads = AtomicInteger()
        useBackend {
            object : Backend() {
                override fun studiedToday(): String = "Studied ${reads.incrementAndGet()}"
            }
        }
        val viewModel = DeckPickerViewModel()
        val initialRefresh = viewModel.reloadDeckCounts()
        advanceRobolectricLooperUntil { initialRefresh.isCompleted }

        val heldBack = viewModel.holdBackWrite("flowOfStudiedTodayStats", "Studied ${reads.get() + 1}")

        withCollectionThread {
            val replaced = viewModel.reloadDeckCounts()
            assertTrue(heldBack.await(5, TimeUnit.SECONDS))
            val replacement = viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { replaced.isCompleted && replacement.isCompleted }
        }

        assertEquals("Studied ${reads.get()}", viewModel.flowOfStudiedTodayStats.value)
    }

    private fun useBackend(backend: () -> Backend) = runBlocking { backendOverride.replaceWith(backend) }

    private fun <T> DeckPickerViewModel.holdBackWrite(
        flowName: String,
        stale: T,
    ): CountDownLatch {
        val heldBack = CountDownLatch(1)
        val wroteNewer = CountDownLatch(1)
        val field = javaClass.getDeclaredField(flowName).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val original = field.get(this) as MutableStateFlow<T>
        field.set(
            this,
            object : MutableStateFlow<T> by original {
                override var value: T
                    get() = original.value
                    set(value) {
                        if (value == stale) {
                            heldBack.countDown()
                            wroteNewer.await(2, TimeUnit.SECONDS)
                        } else {
                            wroteNewer.countDown()
                        }
                        original.value = value
                    }
            },
        )
        return heldBack
    }

    private fun withCollectionThread(block: () -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun awaitWithoutMainLooper(condition: () -> Boolean) {
        val start = TimeSource.Monotonic.markNow()
        while (!condition()) {
            check(start.elapsedNow() < 5.seconds) { "Timed out waiting for the collection thread" }
            Thread.sleep(10)
        }
    }

    private fun DeckPicker.deckRow(deckId: DeckId): View {
        val decks = deckPickerBinding.decks
        val adapter = decks.adapter as DeckAdapter
        val position = { adapter.currentList.indexOfFirst { it.did == deckId } }
        advanceRobolectricLooperUntil { position() >= 0 && decks.findViewHolderForAdapterPosition(position()) != null }
        return (decks.findViewHolderForAdapterPosition(position()) as DeckAdapter.ViewHolder).binding.deckLayout
    }
}
