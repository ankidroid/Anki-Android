/****************************************************************************************
 *
 * Copyright (c) 2025 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.AnkiProgressDialogFragment.Companion.newInstance
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AnkiProgressDialogFragmentTest : RobolectricTest() {
    private lateinit var activityController: ActivityController<FragmentActivity>
    private lateinit var fragmentManager: FragmentManager

    override fun setUp() {
        super.setUp()
        activityController =
            Robolectric
                .buildActivity(FragmentActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()

        saveControllerForCleanup(activityController)
        fragmentManager = activityController.get().supportFragmentManager
    }

    @Test
    fun testDialogCreationWithDefaultValues() {
        val message = "Loading..."

        val fragment = newInstance(message)
        fragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertThat("Dialog should be created", dialog, notNullValue())

        assertThat("Message should match", fragment.message, equalTo(message))

        val messageView = dialog.findViewById<TextView>(R.id.progress_message)
        assertThat(
            "Message view should display text",
            messageView?.text.toString(),
            equalTo(message),
        )
    }

    @Test
    fun testDialogCreationWithCustomParameters() {
        val message = "Processing..."
        val cancelable = true
        val cancelButtonText = android.R.string.cancel
        val cancelCalled = AtomicBoolean(false)

        val fragment =
            newInstance(
                message = message,
                cancelableViaBackButton = cancelable,
                cancelButtonTextResId = cancelButtonText,
            ) {
                cancelCalled.set(true)
            }

        fragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog

        assertThat("Message should match", fragment.message, equalTo(message))

        // Check that the button exists
        val negativeButton = dialog.getButton(Dialog.BUTTON_NEGATIVE)
        assertThat("Cancel button should be visible", negativeButton, notNullValue())
        assertThat("Cancel button should be enabled", negativeButton.isEnabled, equalTo(true))

        // Simulate clicking the cancel button
        negativeButton.performClick()
        advanceRobolectricLooperWithSleep()

        assertThat("Cancel callback should be called", cancelCalled.get(), equalTo(true))
    }

    @Test
    fun testMessageUpdate() {
        val initialMessage = "Initial message"
        val updatedMessage = "Updated message"

        val fragment = newInstance(initialMessage)
        fragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val messageView = dialog.findViewById<TextView>(R.id.progress_message)

        assertThat(
            "Initial message should match",
            messageView?.text.toString(),
            equalTo(initialMessage),
        )

        fragment.updateMessage(updatedMessage)
        advanceRobolectricLooperWithSleep()

        assertThat("Message should be updated", fragment.message, equalTo(updatedMessage))
        assertThat(
            "Text view should display updated message",
            messageView?.text.toString(),
            equalTo(updatedMessage),
        )
    }

    @Test
    fun testProgressUpdateFromIndeterminateToDeterminate() {
        val message = "Processing..."
        val current = 30
        val max = 100

        val fragment = newInstance(message)
        fragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val indeterminateProgressBar = dialog.findViewById<ProgressBar>(R.id.indeterminate_progress_bar)
        val determinateProgressBar = dialog.findViewById<ProgressBar>(R.id.determinate_progress_bar)
        val counterView = dialog.findViewById<TextView>(R.id.progress_counter)

        assertThat(
            "Indeterminate progress bar should be visible initially",
            indeterminateProgressBar?.visibility,
            equalTo(View.VISIBLE),
        )
        assertThat(
            "Determinate progress bar should be hidden initially",
            determinateProgressBar?.visibility,
            equalTo(View.GONE),
        )

        // Update progress to trigger switch to determinate mode
        fragment.updateProgress(current, max)
        advanceRobolectricLooperWithSleep()

        assertThat(
            "Indeterminate progress bar should be hidden after update",
            indeterminateProgressBar?.visibility,
            equalTo(View.GONE),
        )
        assertThat(
            "Determinate progress bar should be visible after update",
            determinateProgressBar?.visibility,
            equalTo(View.VISIBLE),
        )

        assertThat("Progress bar max should match", determinateProgressBar?.max, equalTo(max))
        assertThat("Progress bar progress should match", determinateProgressBar?.progress, equalTo(current))

        assertThat("Counter view should be visible", counterView?.visibility, equalTo(View.VISIBLE))
        assertThat(
            "Counter text should match",
            counterView?.text.toString(),
            equalTo("$current / $max"),
        )
    }

    @Test
    fun testProgressUpdateWithMultipleUpdates() {
        val message = "Processing..."

        val fragment = newInstance(message)
        fragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val counterView = dialog.findViewById<TextView>(R.id.progress_counter)

        // Update progress multiple times
        fragment.updateProgress(10, 100)
        advanceRobolectricLooperWithSleep()

        fragment.updateProgress(25, 100)
        advanceRobolectricLooperWithSleep()

        assertThat(
            "Counter text should match latest update",
            counterView?.text.toString(),
            equalTo("25 / 100"),
        )
    }

    @Test
    fun testDialogCancelationViaBackButton() {
        val message = "Processing..."
        val cancelableViaBackButton = true
        val cancelCalled = AtomicBoolean(false)

        val fragment =
            newInstance(
                message = message,
                cancelableViaBackButton = cancelableViaBackButton,
                onCancelListener = { cancelCalled.set(true) },
            )

        fragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertThat("Dialog should be created", dialog, notNullValue())

        // Simulate cancellation
        dialog.cancel()
        advanceRobolectricLooperWithSleep()

        assertThat(
            "Cancel callback should be called on back press",
            cancelCalled.get(),
            equalTo(true),
        )
    }

    @Test
    fun testStatePreservationOnConfigurationChange() {
        val initialMessage = "Loading..."
        val current = 42
        val max = 100

        val fragment = newInstance(initialMessage)
        fragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        fragment.updateMessage("Updated message")
        fragment.updateProgress(current, max)
        advanceRobolectricLooperWithSleep()

        val savedState = fragmentManager.saveFragmentInstanceState(fragment)

        fragment.dismissAllowingStateLoss()
        advanceRobolectricLooperWithSleep()

        // Create a new fragment with the saved state
        val newFragment = newInstance(initialMessage)
        newFragment.setInitialSavedState(savedState)
        newFragment.show(fragmentManager, "test_tag")
        advanceRobolectricLooperWithSleep()

        val newDialog = ShadowDialog.getLatestDialog() as AlertDialog
        val newIndeterminateProgressBar = newDialog.findViewById<ProgressBar>(R.id.indeterminate_progress_bar)
        val newDeterminateProgressBar = newDialog.findViewById<ProgressBar>(R.id.determinate_progress_bar)
        val newCounterView = newDialog.findViewById<TextView>(R.id.progress_counter)
        val newMessageView = newDialog.findViewById<TextView>(R.id.progress_message)

        assertThat(
            "Message should be preserved",
            newMessageView?.text.toString(),
            equalTo("Updated message"),
        )
        assertThat(
            "Indeterminate progress bar should be hidden",
            newIndeterminateProgressBar?.visibility,
            equalTo(View.GONE),
        )
        assertThat(
            "Determinate progress bar should be visible",
            newDeterminateProgressBar?.visibility,
            equalTo(View.VISIBLE),
        )
        assertThat(
            "Determinate progress bar max should match",
            newDeterminateProgressBar?.max,
            equalTo(max),
        )
        assertThat(
            "Determinate progress bar progress should match",
            newDeterminateProgressBar?.progress,
            equalTo(current),
        )
        assertThat(
            "Counter text should match",
            newCounterView?.text.toString(),
            equalTo("$current / $max"),
        )
    }
}
