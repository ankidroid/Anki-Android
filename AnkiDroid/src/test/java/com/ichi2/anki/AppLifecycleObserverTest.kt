// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ichi2.testutils.JvmTest
import com.ichi2.widget.WidgetStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse

class AppLifecycleObserverTest : JvmTest() {
    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    private val context = mockk<Context>()
    private lateinit var owner: TestLifecycleOwner

    @Before
    fun mockWidgetUpdates() {
        owner = TestLifecycleOwner()
        mockkObject(WidgetStatus)
        every { WidgetStatus.updateInBackground(any()) } just runs
    }

    @After
    fun cleanUp() {
        owner.registry.currentState = Lifecycle.State.DESTROYED
        unmockkObject(CollectionManager, WidgetStatus)
    }

    /** Issue 19749: blocking on a sync here prevents WorkManager's service timeout callback. */
    @Test
    fun `backgrounding during sync does not wait for the collection queue`() =
        runTest {
            CollectionManager.ensureOpen()
            val syncFinished = CompletableDeferred<Unit>()
            mockkObject(CollectionManager)
            coEvery { CollectionManager.withOpenColOrNull<Boolean>(any()) } coAnswers {
                syncFinished.await()
                true
            }

            AppLifecycleObserver(context).onStop(owner)
            runCurrent()
            verify(exactly = 0) { WidgetStatus.updateInBackground(any()) }

            syncFinished.complete(Unit)
            advanceUntilIdle()
            verify(exactly = 1) { WidgetStatus.updateInBackground(context) }
        }

    @Test
    fun `backgrounding with a closed collection does not open it or update widgets`() =
        runTest {
            CollectionManager.ensureClosed()
            AppLifecycleObserver(context).onStop(owner)
            advanceUntilIdle()

            verify(exactly = 0) { WidgetStatus.updateInBackground(any()) }
            assertFalse(CollectionManager.isOpenUnsafe())
        }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }

        override val lifecycle: Lifecycle get() = registry
    }
}
