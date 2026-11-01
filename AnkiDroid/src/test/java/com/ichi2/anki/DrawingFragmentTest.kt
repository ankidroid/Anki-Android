// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.graphics.Path
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.reviewer.Axis
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.testutils.joystickEvent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [DrawingFragment] */
@RunWith(AndroidJUnit4::class)
class DrawingFragmentTest : RobolectricTest() {
    @Test
    fun `back press finishes the activity when the whiteboard is empty`() {
        val (activity, _) = launchDrawingActivity()

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        assertNull(ShadowDialog.getLatestDialog(), "no discard dialog should be shown for an empty whiteboard")
        assertThat("activity finishes immediately", activity.isFinishing, equalTo(true))
    }

    @Test
    fun `back press shows the discard dialog when the whiteboard has content`() {
        val (activity, whiteboard) = launchDrawingActivity()
        whiteboard.binding.whiteboardView.onNewPath
            ?.invoke(samplePath())
        advanceRobolectricLooper()

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        val dialog =
            assertNotNull(
                ShadowDialog.getLatestDialog() as? AlertDialog,
                "discard dialog should be shown",
            )
        assertThat("activity is not finishing while dialog is open", activity.isFinishing, equalTo(false))

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertThat("activity finishes after discard is confirmed", activity.isFinishing, equalTo(true))
    }

    @Test
    fun `whiteboard key bindings trigger on the drawing screen`() {
        bindToClear(ReviewerBinding(Binding.KeyCode(KeyEvent.KEYCODE_F5), CardSide.BOTH))
        val (activity, whiteboard) = launchDrawingActivity()
        whiteboard.viewModel.addPath(samplePath())

        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F5))
        advanceRobolectricLooper()

        assertThat("the whiteboard binding should trigger", whiteboard.viewModel.paths.value, empty())
    }

    @Test
    fun `whiteboard shake bindings trigger on the drawing screen`() {
        bindToClear(ReviewerBinding.fromGesture(Gesture.SHAKE))
        val (activity, whiteboard) = launchDrawingActivity()
        whiteboard.viewModel.addPath(samplePath())

        activity.drawingFragment.bindings.hearShake()
        advanceRobolectricLooper()

        assertThat("the whiteboard binding should trigger", whiteboard.viewModel.paths.value, empty())
    }

    @Test
    fun `whiteboard joystick bindings trigger on the drawing screen`() {
        bindToClear(ReviewerBinding(Binding.AxisButtonBinding(Axis.X, 1f), CardSide.BOTH))
        val (activity, whiteboard) = launchDrawingActivity()
        whiteboard.viewModel.addPath(samplePath())
        // joystick events go to the focused view, e.g. a whiteboard button reached with the D-pad
        val eraserButton = whiteboard.binding.whiteboardToolbar.eraserButton
        assertTrue(eraserButton.requestFocusFromTouch(), "focus the whiteboard")

        activity.dispatchGenericMotionEvent(joystickEvent(Axis.X, 1f))
        advanceRobolectricLooper()

        assertThat("the whiteboard binding should trigger", whiteboard.viewModel.paths.value, empty())
    }

    private fun bindToClear(binding: ReviewerBinding) =
        targetContext.sharedPrefs().edit {
            putString(WhiteboardAction.CLEAR.preferenceKey, listOf(binding).toPreferenceString())
        }

    private fun launchDrawingActivity(): Pair<SingleFragmentActivity, WhiteboardFragment> {
        val intent = DrawingFragment.getIntent(targetContext)
        val activity =
            startActivityControllerNormallyOpenCollectionWithIntent(SingleFragmentActivity::class.java, intent)
                .windowFocusChanged(true)
                .get()
        val whiteboardFragment =
            activity.drawingFragment.childFragmentManager.findFragmentById(R.id.fragment_container) as WhiteboardFragment
        return activity to whiteboardFragment
    }

    private val SingleFragmentActivity.drawingFragment
        get() = supportFragmentManager.findFragmentByTag(SingleFragmentActivity.FRAGMENT_TAG) as DrawingFragment

    private fun samplePath(): Path =
        Path().apply {
            moveTo(0f, 0f)
            lineTo(10f, 10f)
        }
}
