// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki.progress

import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.anki.withProgress
import com.ichi2.testutils.EmptyAnkiActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@RunWith(AndroidJUnit4::class)
class ProgressObserverTest : RobolectricTest() {
    private val progressManager = ProgressManager<String>()
    private val viewModel =
        object : HasProgress<String> {
            override val progressManager = this@ProgressObserverTest.progressManager
        }
    private val otherViewModel =
        object : HasProgress<String> {
            override val progressManager = ProgressManager<String>()
        }

    @Test
    fun `dialog appears after the delay and is dismissed when the op ends`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

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
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

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
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

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

    @Test
    fun `the message is resolved into the dialog text`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { message ->
            getString(CommonString.progress_amount_bytes, message, "2 MB")
        }

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate, message = "1 MB")
        idleMainLooper(SHOW_DELAY * 2)

        assertEquals("1 MB/2 MB", activity.loadingDialogText())

        gate.complete(Unit)
        op.cancel()
    }

    @Test
    fun `newInstance respects cancellable flag`() {
        val nonCancellable = LoadingDialogFragment.newInstance("msg", cancellable = false)
        assertEquals(false, nonCancellable.isCancelable)

        val cancellable = LoadingDialogFragment.newInstance("msg", cancellable = true)
        assertEquals(true, cancellable.isCancelable)
    }

    @Test
    fun `dialog is not cancellable when op does not provide onCancel`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        idleMainLooper(SHOW_DELAY * 2)

        val dialogFragment = activity.loadingDialog() as? DialogFragment
        assertNotNull(dialogFragment)
        assertEquals(false, dialogFragment.isCancelable)

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        op.cancel()
    }

    @Test
    fun `dialog is cancellable when op provides onCancel`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate, onCancel = {})
        idleMainLooper(SHOW_DELAY * 2)

        val dialogFragment = activity.loadingDialog() as? DialogFragment
        assertNotNull(dialogFragment)
        assertEquals(true, dialogFragment.isCancelable)

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        op.cancel()
    }

    @Test
    fun `an idle observer does not dismiss another observer's dialog after a restart`() {
        val controller = startActivity()
        val activity = controller.get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }
        activity.observeProgress(otherViewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        idleMainLooper(SHOW_DELAY * 2)
        assertNotNull(activity.loadingDialog(), "dialog must show for the running op")

        controller.pause().stop()
        controller.start().resume()
        idleMainLooper(SHOW_DELAY)

        assertNotNull(activity.loadingDialog(), "the idle observer must not dismiss the running op's dialog")

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertNull(activity.loadingDialog(), "dialog must be dismissed once the op ends")
        op.cancel()
    }

    @Test
    fun `an observer whose op ends does not dismiss another observer's dialog`() {
        val activity = startActivity().get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }
        activity.observeProgress(otherViewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val otherGate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        val otherOp = launchOp(otherGate, manager = otherViewModel.progressManager)
        idleMainLooper(SHOW_DELAY * 2)

        otherGate.complete(Unit)
        idleMainLooper(SHOW_DELAY)

        assertNotNull(activity.loadingDialog(), "the running op's dialog must stay")

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertNull(activity.loadingDialog(), "dialog must be dismissed once both ops end")
        op.cancel()
        otherOp.cancel()
    }

    @Test
    fun `touches are blocked as soon as an op starts and restored when it ends`() {
        val activity = startActivity().get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        assertTrue(activity.touchesBlocked(), "touches must be blocked before the dialog shows")

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertFalse(activity.touchesBlocked(), "touches must be restored once the op ends")
        op.cancel()
    }

    @Test
    fun `back is blocked as soon as an op starts and restored when it ends`() {
        val activity = startActivity().get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(activity.isFinishing, "back must be blocked before the dialog shows")

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing, "back must work again once the op ends")
        op.cancel()
    }

    @Test
    fun `an observer whose op ends does not restore touches while another op runs`() {
        val activity = startActivity().get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }
        activity.observeProgress(otherViewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val otherGate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        val otherOp = launchOp(otherGate, manager = otherViewModel.progressManager)

        otherGate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertTrue(activity.touchesBlocked(), "touches must stay blocked while an op runs")

        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertFalse(activity.touchesBlocked(), "touches must be restored once both ops end")
        op.cancel()
        otherOp.cancel()
    }

    @Test
    fun `an idle observer leaves touches blocked by someone else`() {
        val activity = startActivity().get()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }
        idleMainLooper(SHOW_DELAY)

        assertTrue(activity.touchesBlocked(), "the observer must only restore touches it blocked")
    }

    @Test
    fun `a legacy withProgress ending does not restore touches while an op runs`() {
        val activity = startActivity().get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)
        val legacyGate = CompletableDeferred<Unit>()
        val legacyOp = activity.lifecycleScope.launch { activity.withProgress { legacyGate.await() } }

        legacyGate.complete(Unit)
        idleMainLooper(Duration.ZERO)
        assertTrue(activity.touchesBlocked(), "touches must stay blocked while the op runs")

        gate.complete(Unit)
        idleMainLooper(Duration.ZERO)
        assertFalse(activity.touchesBlocked(), "touches must be restored once both end")
        op.cancel()
        legacyOp.cancel()
    }

    @Test
    fun `an op ending does not restore touches while a legacy withProgress runs`() {
        val activity = startActivity().get()
        activity.observeProgress(viewModel, delayMillis = SHOW_DELAY) { it }

        val legacyGate = CompletableDeferred<Unit>()
        val legacyOp = activity.lifecycleScope.launch { activity.withProgress { legacyGate.await() } }
        val gate = CompletableDeferred<Unit>()
        val op = launchOp(gate)

        gate.complete(Unit)
        idleMainLooper(Duration.ZERO)
        assertTrue(activity.touchesBlocked(), "touches must stay blocked while the legacy op runs")

        legacyGate.complete(Unit)
        idleMainLooper(Duration.ZERO)
        assertFalse(activity.touchesBlocked(), "touches must be restored once both end")
        op.cancel()
        legacyOp.cancel()
    }

    @Test
    fun `a fragment's observer stops when the fragment is destroyed`() {
        val activity = startActivity().get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, null) }
        val progress = fragment.viewModel.progressManager.progress as MutableStateFlow<*>
        assertEquals(1, progress.subscriptionCount.value)

        activity.supportFragmentManager.commitNow { remove(fragment) }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, progress.subscriptionCount.value, "the observer must not outlive its fragment")
    }

    @Test
    fun `a stopped fragment gives back the touches its op blocked`() {
        val activity = startActivity().get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, null) }
        val op = fragment.viewModel.runOp(CompletableDeferred())
        assertTrue(activity.touchesBlocked(), "touches must be blocked while the op runs")

        activity.supportFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.CREATED) }
        assertFalse(activity.touchesBlocked(), "a stopped fragment must not keep the window blocked")

        activity.supportFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.RESUMED) }
        assertTrue(activity.touchesBlocked(), "touches must be blocked again while the op still runs")
        op.cancel()
    }

    @Test
    fun `an operation completing while its fragment is stopped leaves no dialog`() {
        val activity = startActivity().get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, null) }
        val gate = CompletableDeferred<Unit>()
        val op = fragment.viewModel.runOp(gate)
        idleMainLooper(SHOW_DELAY * 2)
        assertTrue(assertNotNull(activity.loadingDialog()).requireDialog().isShowing)

        activity.supportFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.CREATED) }
        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertFalse(activity.touchesBlocked())
        assertNull(activity.loadingDialog(), "completed operation must not leave a modal dialog over the host")
        op.cancel()
    }

    @Test
    fun `destroying a fragment removes its visible progress dialog`() {
        val activity = startActivity().get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, null) }
        fragment.viewModel.runOp(CompletableDeferred())
        idleMainLooper(SHOW_DELAY * 2)
        assertTrue(assertNotNull(activity.loadingDialog()).requireDialog().isShowing)

        activity.supportFragmentManager.commitNow { remove(fragment) }
        idleMainLooper(SHOW_DELAY)
        assertNull(activity.loadingDialog(), "destroyed fragment must not leave a modal dialog over the host")
    }

    @Test
    fun `restarting a fragment after its operation completes dismisses visible progress`() {
        val activity = startActivity().get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, null) }
        val gate = CompletableDeferred<Unit>()
        fragment.viewModel.runOp(gate)
        idleMainLooper(SHOW_DELAY * 2)
        assertTrue(assertNotNull(activity.loadingDialog()).requireDialog().isShowing)
        activity.supportFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.CREATED) }
        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        activity.supportFragmentManager.commitNow { setMaxLifecycle(fragment, Lifecycle.State.RESUMED) }
        idleMainLooper(SHOW_DELAY)
        assertNull(activity.loadingDialog())
    }

    @Test
    fun `recreating the activity during visible fragment progress is safe`() {
        val controller = startActivity()
        val activity = controller.get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, "progress-owner") }
        val gate = CompletableDeferred<Unit>()
        fragment.viewModel.runOp(gate)
        idleMainLooper(SHOW_DELAY * 2)
        assertTrue(assertNotNull(activity.loadingDialog()).requireDialog().isShowing)

        controller.recreate()
        idleMainLooper(SHOW_DELAY)
        gate.complete(Unit)
        idleMainLooper(SHOW_DELAY)
        assertNull(controller.get().loadingDialog())
    }

    @Test
    fun `recreating the activity keeps a fragment's visible dialog`() {
        val controller = startActivity()
        val fragment = ProgressObservingFragment()
        controller.get().supportFragmentManager.commitNow { add(fragment, "progress-owner") }
        fragment.viewModel.runOp(CompletableDeferred())
        idleMainLooper(SHOW_DELAY * 2)

        controller.recreate()
        idleMainLooper(Duration.ZERO)

        assertNotNull(controller.get().loadingDialog())
    }

    @Test
    fun `an operation completing while the activity is stopped leaves no dialog on return`() {
        val controller = startActivity()
        val activity = controller.get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, null) }
        val gate = CompletableDeferred<Unit>()
        fragment.viewModel.runOp(gate)
        idleMainLooper(SHOW_DELAY * 2)
        assertNotNull(activity.loadingDialog())

        controller.pause().stop()
        gate.complete(Unit)
        controller.start().resume()
        idleMainLooper(SHOW_DELAY)

        assertNull(activity.loadingDialog())
    }

    @Test
    fun `removing a fragment while the activity is stopped leaves no dialog on return`() {
        val controller = startActivity()
        val activity = controller.get()
        val fragment = ProgressObservingFragment()
        activity.supportFragmentManager.commitNow { add(fragment, null) }
        fragment.viewModel.runOp(CompletableDeferred())
        idleMainLooper(SHOW_DELAY * 2)
        assertNotNull(activity.loadingDialog())

        controller.pause().stop()
        activity.supportFragmentManager.commitNow(allowStateLoss = true) { remove(fragment) }
        controller.start().resume()
        idleMainLooper(SHOW_DELAY)

        assertNull(activity.loadingDialog())
    }

    private fun launchOp(
        gate: CompletableDeferred<Unit>,
        message: String = "op",
        onCancel: (() -> Unit)? = null,
        manager: ProgressManager<String> = progressManager,
    ) = CoroutineScope(Dispatchers.Unconfined).launch {
        manager.withProgress(message = message, onCancel = onCancel) { gate.await() }
    }

    private fun startActivity(): ActivityController<EmptyAnkiActivity> =
        Robolectric
            .buildActivity(EmptyAnkiActivity::class.java)
            .setup()
            .also { saveControllerForCleanup(it) }

    private fun EmptyAnkiActivity.loadingDialog() =
        supportFragmentManager.fragments.filterIsInstance<LoadingDialogFragment>().singleOrNull()

    private fun EmptyAnkiActivity.loadingDialogText() =
        (loadingDialog() as? DialogFragment)
            ?.dialog
            ?.findViewById<TextView>(R.id.text)
            ?.text
            ?.toString()

    private fun EmptyAnkiActivity.touchesBlocked() = window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0

    private fun idleMainLooper(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration.toJavaDuration())

    companion object {
        private val SHOW_DELAY = 600.milliseconds
    }
}

class ProgressObservingFragment : Fragment() {
    val viewModel: ProgressTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeProgress(viewModel) { it }
    }
}

class ProgressTestViewModel :
    ViewModel(),
    HasProgress<String> {
    override val progressManager = ProgressManager<String>()

    fun runOp(gate: CompletableDeferred<Unit>) = viewModelScope.launch { progressManager.withProgress(message = "op") { gate.await() } }
}
