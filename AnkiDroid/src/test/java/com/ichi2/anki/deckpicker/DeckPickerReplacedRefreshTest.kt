// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki.deckpicker

import anki.decks.DeckTreeNode
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeckPickerReplacedRefreshTest : JvmTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `a refresh replaced mid-read still reports its failure`() {
        val reads = AtomicInteger()
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = IllegalStateException("deck list read failed")
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (reads.incrementAndGet() != 1) return super.deckTree(now)
                    reading.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    throw failure
                }
            }
        }
        throwOnShowError = false
        runTest {
            val viewModel = DeckPickerViewModel()
            val replaced = viewModel.reloadDeckCounts()
            assertTrue(withContext(Dispatchers.IO) { reading.await(10, TimeUnit.SECONDS) })

            val replacement = viewModel.reloadDeckCounts()
            release.countDown()
            replaced.join()
            replacement.join()

            assertSame(failure, viewModel.onError.tryReceive().getOrNull())
            assertEquals(1, viewModel.flowOfDeckListLoadFailed.replayCache.size)
        }
    }
}
