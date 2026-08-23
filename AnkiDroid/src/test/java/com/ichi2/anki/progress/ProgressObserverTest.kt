// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki.progress

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.testutils.EmptyAnkiActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@RunWith(AndroidJUnit4::class)
class ProgressObserverTest : RobolectricTest() {
    private val progressManager = ProgressManager()
    private val viewModel =
        object : HasProgress {
            override val progressManager = this@ProgressObserverTest.progressManager
        }

    @Test
    fun `dialog appears after the delay and is dismissed when the op ends`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY)

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        idleMainLooper(SHOW_DELAY / 2)
        assertNull(activity.loadingDialog(), "dialog must not show before the delay elapses")

        idleMainLooper(SHOW_DELAY)
        assertNotNull(activity.loadingDialog(), "dialog must show once the delay elapses")

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertNull(activity.loadingDialog(), "dialog must be dismissed once the op ends")
        op.cancel()
    }

    @Test
    fun `dialog is not shown for an op that ends within the delay`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY)

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        idleMainLooper(SHOW_DELAY / 2)
        gate.complete(Unit)

        idleMainLooper(SHOW_DELAY * 2)
        assertNull(activity.loadingDialog(), "a quick op must not flash a dialog")
        op.cancel()
    }

    @Test
    fun `dialog is shown when the activity is stopped and restarted during the show delay`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY)

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        idleMainLooper(SHOW_DELAY / 2)
        assertNull(activity.loadingDialog(), "dialog must not show before the delay elapses")

        // stopping cancels the pending show; the op is still running when the activity comes back
        controller.pause().stop()
        controller.start().resume()
        idleMainLooper(SHOW_DELAY * 2)

        assertNotNull(activity.loadingDialog(), "dialog must show after the activity restarts mid-op")

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertNull(activity.loadingDialog(), "dialog must be dismissed once the op ends")
        op.cancel()
    }

    private fun launchOp(gate: CompletableDeferred<Unit>) =
        CoroutineScope(Dispatchers.Unconfined).launch {
            progressManager.withProgress(message = "op") { gate.await() }
        }

    private fun startActivity(): ActivityController<EmptyAnkiActivity> =
        Robolectric
            .buildActivity(EmptyAnkiActivity::class.java)
            .setup()
            .also { saveControllerForCleanup(it) }

    private fun EmptyAnkiActivity.loadingDialog() = supportFragmentManager.findFragmentByTag(LoadingDialogFragment.TAG)

    private fun idleMainLooper(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration.toJavaDuration())

    companion object {
        private val SHOW_DELAY = 600.milliseconds
    }
}
