// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.progress

import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.idleMainLooper
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class ProgressObserverReviewTest : RobolectricTest() {
    @Test
    fun `completed progress disappears when its owner remains stopped on foreground`() {
        val controller = Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val activity = controller.get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, "owner") }
        val gate = CompletableDeferred<Unit>()
        fragment.viewModel.runOp(gate)
        idleMainLooper(1200.milliseconds)
        assertNotNull(activity.loadingDialog())

        controller.pause().stop()
        activity.supportFragmentManager.commitNow(allowStateLoss = true) {
            setMaxLifecycle(fragment, Lifecycle.State.CREATED)
        }
        gate.complete(Unit)
        idleMainLooper(1200.milliseconds)
        controller.start().resume()
        idleMainLooper(1200.milliseconds)

        assertEquals(Lifecycle.State.CREATED, fragment.lifecycle.currentState)
        assertEquals(ViewModelProgress.Idle, fragment.viewModel.progressManager.progress.value)
        assertNull(activity.loadingDialog(), "completed operation must not block the foreground screen")
    }

    @Test
    fun `backgrounded holder cannot retain a completed shared dialog`() {
        val controller = Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val activity = controller.get()
        val first = ProgressObservingFragment()
        val second = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow {
            add(first, "first")
            add(second, "second")
        }
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        first.viewModel.runOp(firstGate)
        second.viewModel.runOp(secondGate)
        idleMainLooper(1200.milliseconds)
        assertNotNull(activity.loadingDialog())

        controller.pause().stop()
        activity.supportFragmentManager.commitNow(allowStateLoss = true) {
            setMaxLifecycle(first, Lifecycle.State.CREATED)
        }
        firstGate.complete(Unit)
        controller.start().resume()
        idleMainLooper(1200.milliseconds)
        assertTrue(assertNotNull(activity.loadingDialog()).requireDialog().isShowing)

        secondGate.complete(Unit)
        idleMainLooper(1200.milliseconds)
        assertNull(activity.loadingDialog(), "a stopped former holder must not retain another owner's completed dialog")
    }

    private fun EmptyAnkiActivity.loadingDialog() =
        supportFragmentManager.fragments.filterIsInstance<LoadingDialogFragment>().singleOrNull()
}
