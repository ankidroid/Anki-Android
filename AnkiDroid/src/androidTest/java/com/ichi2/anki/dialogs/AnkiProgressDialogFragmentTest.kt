/****************************************************************************************
 *                                                                                      *
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

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.AnkiProgressDialogFragment.Companion.newInstance
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AnkiProgressDialogFragmentTest {
    private lateinit var scenario: FragmentScenario<TestHostFragment>

    @Before
    fun setUp() {
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_Light)
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @Test
    fun testDialogCreationWithDefaultValues() {
        val message = "Loading..."
        val latch = CountDownLatch(1)

        scenario.onFragment { fragment ->
            val dialogFragment = newInstance(message)
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Check if the dialog is showing with the correct message
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(message)))

        // Check that indeterminate progress bar is visible
        onView(withId(R.id.indeterminate_progress_bar))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Check that determinate progress bar is hidden
        onView(withId(R.id.determinate_progress_bar))
            .inRoot(isDialog())
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun testDialogCreationWithCustomParameters() {
        val message = "Processing..."
        val cancelable = true
        val cancelButtonText = "Cancel"
        val cancelCalled = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        scenario.onFragment { fragment ->
            val dialogFragment =
                newInstance(
                    message = message,
                    cancellationConfig =
                        ProgressDialogCancellationConfig(
                            cancelableViaBackButton = cancelable,
                            cancelButtonText = cancelButtonText,
                        ) {
                            cancelCalled.set(true)
                        },
                )
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Check the message is correct
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(message)))

        // Check cancel button exists with correct text
        onView(withText(cancelButtonText))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Click cancel button and verify callback is called
        onView(withText(cancelButtonText))
            .inRoot(isDialog())
            .perform(click())

        // Wait for the cancellation to propagate
        Thread.sleep(500)
        assertTrue("Cancel callback should be called", cancelCalled.get())
    }

    @Test
    fun testMessageUpdate() {
        val initialMessage = "Initial message"
        val updatedMessage = "Updated message"
        val latch = CountDownLatch(1)
        lateinit var dialogFragment: AnkiProgressDialogFragment

        scenario.onFragment { fragment ->
            dialogFragment = newInstance(initialMessage)
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Check initial message
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(initialMessage)))

        // Update message
        scenario.onFragment {
            dialogFragment.updateMessage(updatedMessage)
        }

        // Check updated message
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(updatedMessage)))
    }

    @Test
    fun testProgressUpdateFromIndeterminateToDeterminate() {
        val message = "Processing..."
        val current = 30
        val max = 100
        val latch = CountDownLatch(1)
        lateinit var dialogFragment: AnkiProgressDialogFragment

        scenario.onFragment { fragment ->
            dialogFragment = newInstance(message)
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Initially, indeterminate progress bar should be visible
        onView(withId(R.id.indeterminate_progress_bar))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withId(R.id.determinate_progress_bar))
            .inRoot(isDialog())
            .check(matches(not(isDisplayed())))

        onView(withId(R.id.progress_counter))
            .inRoot(isDialog())
            .check(matches(not(isDisplayed())))

        // Update progress to trigger switch to determinate mode
        scenario.onFragment {
            dialogFragment.updateProgress(current, max)
        }

        // Short delay to allow UI to update
        Thread.sleep(500)

        // Now, determinate progress bar should be visible
        onView(withId(R.id.indeterminate_progress_bar))
            .inRoot(isDialog())
            .check(matches(not(isDisplayed())))

        onView(withId(R.id.determinate_progress_bar))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withId(R.id.progress_counter))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
            .check(matches(withText("$current / $max")))
    }

    @Test
    fun testMultipleProgressUpdates() {
        val message = "Processing..."
        val latch = CountDownLatch(1)
        lateinit var dialogFragment: AnkiProgressDialogFragment

        scenario.onFragment { fragment ->
            dialogFragment = newInstance(message)
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Wait for the dialog to be fully initialized
        Thread.sleep(500)

        // Perform multiple progress updates
        scenario.onFragment {
            // First update - switches to determinate mode
            dialogFragment.updateProgress(10, 100)
        }

        // Allow UI to update
        Thread.sleep(500)

        // Second update - updates progress value
        scenario.onFragment {
            dialogFragment.updateProgress(25, 100)
        }

        // Allow UI to update
        Thread.sleep(500)

        // Third update - updates progress value again
        scenario.onFragment {
            dialogFragment.updateProgress(40, 100)
        }

        // Allow UI to update
        Thread.sleep(500)

        // Check final progress counter displays correct values
        onView(withId(R.id.progress_counter))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
            .check(matches(withText("40 / 100")))
    }

    @Test
    fun testCancellationViaBackButton() {
        val message = "Processing..."
        val cancelCalled = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        lateinit var dialogFragment: AnkiProgressDialogFragment

        scenario.onFragment { fragment ->
            dialogFragment =
                newInstance(
                    message = message,
                    cancellationConfig =
                        ProgressDialogCancellationConfig(
                            cancelableViaBackButton = true,
                            onCancel = { cancelCalled.set(true) },
                        ),
                )
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        scenario.onFragment {
            @Suppress("DEPRECATION")
            dialogFragment.dialog?.onBackPressed()
        }

        // Wait for cancellation to propagate
        Thread.sleep(500)
        assertTrue("Cancel callback should be called on back press", cancelCalled.get())
    }

    @Test
    fun testConfigurationChangeDuringProgress() {
        val message = "Configuration Change Test"
        val latch = CountDownLatch(1)
        lateinit var dialogFragment: AnkiProgressDialogFragment

        scenario.onFragment { fragment ->
            dialogFragment = newInstance(message)
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        scenario.onFragment {
            dialogFragment.updateProgress(45, 100)
        }

        // Allow UI to update
        Thread.sleep(500)

        // Simulate configuration change by recreating the activity
        scenario.recreate()

        // Pause to allow recreation
        Thread.sleep(1000)

        // Check if the dialog is showing
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun testQuickMultipleUpdates() {
        val message = "Multiple updates"
        val latch = CountDownLatch(1)
        lateinit var dialogFragment: AnkiProgressDialogFragment

        scenario.onFragment { fragment ->
            dialogFragment = newInstance(message)
            dialogFragment.show(fragment.childFragmentManager, "test_tag")
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Perform rapid multiple updates in a loop
        scenario.onFragment {
            // Switch to determinate mode
            dialogFragment.updateProgress(0, 100)

            // Simulate a sequence of rapid updates
            for (i in 1..10) {
                dialogFragment.updateMessage("Update $i of 10")
                dialogFragment.updateProgress(i * 10, 100)
            }
        }

        // Wait for updates to complete
        Thread.sleep(500)

        // Check final state after quick updates
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText("Update 10 of 10")))

        onView(withId(R.id.progress_counter))
            .inRoot(isDialog())
            .check(matches(withText("100 / 100")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testMessagePreservationAfterConfigurationChange() {
        val initialMessage = "Initial message before configuration change"
        val dialogTag = "config_change_test"
        val latch = CountDownLatch(1)

        scenario.onFragment { fragment ->
            val dialogFragment = newInstance(initialMessage)
            dialogFragment.show(fragment.childFragmentManager, dialogTag)
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Verify initial message is displayed
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(initialMessage)))

        // Simulate configuration change by recreating the activity
        scenario.recreate()

        // Pause to allow recreation
        Thread.sleep(1000)

        // Verify message is preserved after configuration change
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(initialMessage)))
    }

    @Test
    fun testUpdatedMessagePreservationAfterConfigurationChange() {
        val initialMessage = "Initial message"
        val updatedMessage = "Updated message before config change"
        val dialogTag = "message_preservation_test"
        val latch = CountDownLatch(1)

        var childFragmentManager: androidx.fragment.app.FragmentManager? = null

        scenario.onFragment { fragment ->
            childFragmentManager = fragment.childFragmentManager
            val dialogFragment = newInstance(initialMessage)
            dialogFragment.show(fragment.childFragmentManager, dialogTag)
            latch.countDown()
        }

        assertTrue("Dialog should be shown", latch.await(5, TimeUnit.SECONDS))

        // Verify initial message is displayed
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(initialMessage)))

        scenario.onFragment {
            val dialogFragment =
                childFragmentManager?.findFragmentByTag(dialogTag) as? AnkiProgressDialogFragment
            dialogFragment?.updateMessage(updatedMessage)
        }

        // Allow UI to update
        Thread.sleep(500)

        // Verify message was updated
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(updatedMessage)))

        // Simulate configuration change by recreating the activity
        scenario.recreate()

        // Pause to allow recreation
        Thread.sleep(1000)

        // Verify updated message is preserved after configuration change
        onView(withId(R.id.progress_message))
            .inRoot(isDialog())
            .check(matches(withText(updatedMessage)))
    }
}

/**
 * Host fragment to contain the dialog fragment for testing
 */
class TestHostFragment : androidx.fragment.app.Fragment()
